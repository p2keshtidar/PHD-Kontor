package de.frauas.girokonto.csvimport;

import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.insights.Category;
import de.frauas.girokonto.insights.CategoryService;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * /K040/ CSV-Import. Importierte Umsätze landen in einer eigenen Tabelle: Sie ändern keine Kontostände
 * (es ist ja Geld bei einer anderen Bank), werden aber im Finanzüberblick mitgezählt.
 */
@Service
public class CsvImportService {

    public static final int MAX_CHARS = 2_000_000;

    public record ImportResult(Long importId, String format, int imported, int skipped, List<String> errors,
                               LocalDate firstDate, LocalDate lastDate, long incomeCents, long expenseCents) {}

    public record ImportDto(Long id, String fileName, String format, int rowsImported, int rowsSkipped,
                            LocalDate firstDate, LocalDate lastDate, Instant createdAt) {}

    public record TransactionDto(Long id, LocalDate date, long amountCents, String counterparty, String description,
                                 Category category, String categoryLabel) {}

    private final JdbcTemplate jdbc;
    private final CategoryService categories;
    private final NotificationService notifications;
    private final Clock clock;

    public CsvImportService(JdbcTemplate jdbc, CategoryService categories, NotificationService notifications, Clock clock) {
        this.jdbc = jdbc;
        this.categories = categories;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public ImportResult importCsv(Long userId, String fileName, String content) {
        if (content == null || content.isBlank()) {
            throw bad("EMPTY_FILE", "Die Datei ist leer.");
        }
        if (content.length() > MAX_CHARS) {
            throw bad("FILE_TOO_LARGE", "Die Datei ist zu groß (höchstens ca. 2 MB).");
        }
        CsvParser.Result parsed;
        try {
            parsed = CsvParser.parse(content);
        } catch (IllegalArgumentException e) {
            throw bad("INVALID_CSV", e.getMessage());
        }
        String name = fileName == null || fileName.isBlank() ? "import.csv" : fileName.trim();
        if (name.length() > 200) {
            name = name.substring(0, 200);
        }

        GeneratedKeyHolder key = new GeneratedKeyHolder();
        String finalName = name;
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO csv_import (user_id, file_name, format, rows_imported, rows_skipped,"
                    + " created_at) VALUES (?, ?, ?, 0, 0, ?)", new String[] {"id"});
            ps.setLong(1, userId);
            ps.setString(2, finalName);
            ps.setString(3, parsed.format());
            ps.setTimestamp(4, Timestamp.from(Instant.now(clock)));
            return ps;
        }, key);
        long importId = key.getKey().longValue();

        int imported = 0;
        long income = 0;
        long expense = 0;
        LocalDate first = null;
        LocalDate last = null;
        Map<String, Integer> occurrences = new HashMap<>();
        for (CsvParser.Row r : parsed.rows()) {
            String base = r.date() + "|" + r.amountCents() + "|" + r.counterparty() + "|" + r.description();
            int n = occurrences.merge(base, 1, Integer::sum);        // gleiche Zeile zweimal am selben Tag ist erlaubt
            String dedup = sha256(base + "#" + n);
            Category cat = categories.categorize(r.amountCents() > 0 ? BookingType.INCOME : BookingType.DIRECT_DEBIT,
                    (r.counterparty() + " " + r.description()).trim(), r.amountCents());
            int inserted = jdbc.update("""
                    INSERT INTO imported_transaction (import_id, user_id, booked_on, amount_cents, counterparty,
                                                      description, category, dedup_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id, dedup_key) DO NOTHING
                    """, importId, userId, Date.valueOf(r.date()), r.amountCents(), r.counterparty(), r.description(),
                    cat.name(), dedup);
            if (inserted == 0) {
                continue;
            }
            imported++;
            if (cat.isIncome()) {
                income += r.amountCents();
            } else if (cat.isExpense()) {
                expense -= r.amountCents();
            }
            first = first == null || r.date().isBefore(first) ? r.date() : first;
            last = last == null || r.date().isAfter(last) ? r.date() : last;
        }
        int skipped = parsed.rows().size() - imported;
        if (imported == 0) {
            jdbc.update("DELETE FROM csv_import WHERE id = ?", importId);
            return new ImportResult(null, parsed.format(), 0, skipped, parsed.errors(), null, null, 0, 0);
        }
        jdbc.update("UPDATE csv_import SET rows_imported = ?, rows_skipped = ?, first_date = ?, last_date = ? WHERE id = ?",
                imported, skipped, Date.valueOf(first), Date.valueOf(last), importId);
        notifications.send(userId, NotificationType.INFO, "Umsätze importiert",
                imported + " Umsätze aus „" + name + "“ (" + first + " bis " + last + ") sind jetzt im Finanzüberblick.",
                null);
        return new ImportResult(importId, parsed.format(), imported, skipped, parsed.errors(), first, last, income, expense);
    }

    @Transactional(readOnly = true)
    public List<ImportDto> list(Long userId) {
        return jdbc.query("""
                SELECT id, file_name, format, rows_imported, rows_skipped, first_date, last_date, created_at
                  FROM csv_import WHERE user_id = ? ORDER BY id DESC
                """, (rs, i) -> new ImportDto(rs.getLong("id"), rs.getString("file_name"), rs.getString("format"),
                rs.getInt("rows_imported"), rs.getInt("rows_skipped"),
                rs.getDate("first_date") == null ? null : rs.getDate("first_date").toLocalDate(),
                rs.getDate("last_date") == null ? null : rs.getDate("last_date").toLocalDate(),
                rs.getTimestamp("created_at").toInstant()), userId);
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> transactions(Long userId, Long importId) {
        requireOwn(userId, importId);
        return jdbc.query("""
                SELECT id, booked_on, amount_cents, counterparty, description, category
                  FROM imported_transaction WHERE import_id = ? ORDER BY booked_on DESC, id DESC LIMIT 300
                """, (rs, i) -> {
            Category c = Category.valueOf(rs.getString("category"));
            return new TransactionDto(rs.getLong("id"), rs.getDate("booked_on").toLocalDate(), rs.getLong("amount_cents"),
                    rs.getString("counterparty"), rs.getString("description"), c, c.label());
        }, importId);
    }

    @Transactional
    public void delete(Long userId, Long importId) {
        requireOwn(userId, importId);
        jdbc.update("DELETE FROM csv_import WHERE id = ?", importId);       // Umsätze per ON DELETE CASCADE
    }

    /**
     * Beispieldatei zum Ausprobieren im Format einer Sparkasse (Semikolon, deutsche Beträge):
     * die drei vollen Monate vor dem aktuellen (Simulations-)Monat.
     */
    public String sampleCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"Auftragskonto\";\"Buchungstag\";\"Valutadatum\";\"Buchungstext\";\"Verwendungszweck\";")
          .append("\"Beguenstigter/Zahlungspflichtiger\";\"Kontonummer/IBAN\";\"Betrag\";\"Waehrung\"\n");
        YearMonth current = YearMonth.now(clock);
        List<String[]> month = new ArrayList<>();
        month.add(new String[] {"1", "GUTSCHRIFT", "Gehalt", "Muster GmbH", "2400,00"});
        month.add(new String[] {"3", "DAUERAUFTRAG", "Miete Wohnung", "Hausverwaltung Main", "-850,00"});
        month.add(new String[] {"5", "LASTSCHRIFT", "Abschlag Strom", "Stadtwerke Frankfurt", "-65,00"});
        month.add(new String[] {"6", "KARTENZAHLUNG", "REWE Markt", "REWE", "-54,37"});
        month.add(new String[] {"9", "LASTSCHRIFT", "Monatsabo Streaming", "Netflix", "-13,99"});
        month.add(new String[] {"12", "KARTENZAHLUNG", "Tankstelle", "Aral", "-61,20"});
        month.add(new String[] {"13", "KARTENZAHLUNG", "Supermarkt Einkauf", "Lidl", "-38,45"});
        month.add(new String[] {"18", "KARTENZAHLUNG", "Restaurant Pizza", "Pizzeria Roma", "-32,50"});
        month.add(new String[] {"20", "KARTENZAHLUNG", "REWE Markt", "REWE", "-71,10"});
        month.add(new String[] {"24", "KARTENZAHLUNG", "Kinobesuch", "Cinestar", "-24,00"});
        month.add(new String[] {"27", "KARTENZAHLUNG", "Drogerie", "dm", "-18,95"});
        for (int m = 3; m >= 1; m--) {
            YearMonth ym = current.minusMonths(m);
            for (String[] t : month) {
                String date = String.format("%02d.%02d.%02d", Integer.parseInt(t[0]), ym.getMonthValue(), ym.getYear() % 100);
                sb.append("\"DE00500105170000000001\";\"").append(date).append("\";\"").append(date).append("\";\"")
                  .append(t[1]).append("\";\"").append(t[2]).append("\";\"").append(t[3])
                  .append("\";\"DE00500105170000000002\";\"").append(t[4]).append("\";\"EUR\"\n");
            }
        }
        return sb.toString();
    }

    private void requireOwn(Long userId, Long importId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM csv_import WHERE id = ? AND user_id = ?",
                Integer.class, importId, userId);
        if (n == null || n == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", "Import nicht gefunden.");
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
