package de.frauas.girokonto.multibank;

import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.insights.Category;
import de.frauas.girokonto.multibank.MultiBankDtos.TxItem;
import de.frauas.girokonto.multibank.MultiBankDtos.TxPage;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alle Umsätze an einem Ort: Bankkonten der Projektbank (Girokonto, weitere Girokonten, Tagesgeld – Simulation),
 * verbundene Banken (nur lesend, wie von der Bank geliefert) und per CSV importierte Umsätze anderer Banken. Digitale Konten, Sparpläne und Kredite sind Teil der
 * Projektbank-Buchungen (Umbuchungen, Raten) und erscheinen dort.
 */
@Service
public class ConsolidatedService {

    public static final int MAX_PAGE_SIZE = 50;

    /** Kategorien, die weder Einnahme noch Ausgabe sind (wie im Finanzüberblick). */
    static final List<String> NEUTRAL = Arrays.stream(Category.values())
            .filter(c -> c.kind() == Category.Kind.EXCLUDED).map(Enum::name).toList();

    private static final String TX = """
            WITH tx AS (
              SELECT 'PROJEKTBANK' AS source, l.id AS sort_id, b.id AS ref_id, a.id AS account_id,
                     a.name AS account_name, :bank AS institution, (b.booked_at AT TIME ZONE :zone)::date AS d,
                     b.booked_at AS ts, l.amount_cents AS amount, 'EUR' AS currency,
                     CASE WHEN cp.type IS NULL OR (cp.type = 'SYSTEM' AND cp.name <> 'BANK') THEN NULL
                          WHEN cp.type = 'SYSTEM' THEN :bank
                          WHEN cp.owner_id = :user THEN cp.name
                          ELSE u.first_name || ' ' || u.last_name END AS counterparty,
                     b.description AS text, COALESCE(l.category, 'SONSTIGES') AS category, 'BOOKED' AS status,
                     FALSE AS excluded, n.note AS note
                FROM booking_line l
                JOIN booking b ON b.id = l.booking_id
                JOIN account a ON a.id = l.account_id
                LEFT JOIN booking_note n ON n.booking_id = b.id AND n.account_id = l.account_id AND n.user_id = :user
                LEFT JOIN LATERAL (
                      SELECT a2.owner_id, a2.type, a2.name
                        FROM booking_line l2 JOIN account a2 ON a2.id = l2.account_id
                       WHERE l2.booking_id = l.booking_id AND l2.account_id <> l.account_id
                       ORDER BY abs(l2.amount_cents) DESC, l2.id
                       LIMIT 1
                     ) cp ON TRUE
                LEFT JOIN app_user u ON u.id = cp.owner_id
               WHERE a.owner_id = :user AND a.type IN ('MAIN', 'CURRENT', 'CALL')
              UNION ALL
              SELECT 'EXTERNAL', t.id, t.id, ea.id, ea.name, c.institution_name, t.booking_date, NULL::timestamptz,
                     t.amount_cents, t.currency, t.counterparty, t.remittance,
                     COALESCE(e.category, t.auto_category), t.status, COALESCE(e.excluded, FALSE), e.note
                FROM external_transaction t
                JOIN external_account ea ON ea.id = t.external_account_id
                JOIN bank_connection c ON c.id = ea.connection_id
                LEFT JOIN external_transaction_edit e ON e.transaction_id = t.id
               WHERE t.user_id = :user AND t.status IN ('BOOKED', 'PENDING')
              UNION ALL
              SELECT 'IMPORT', i.id, i.id, ci.id, ci.file_name, 'CSV-Import', i.booked_on, NULL::timestamptz,
                     i.amount_cents, 'EUR', i.counterparty, i.description, i.category, 'BOOKED', FALSE, NULL
                FROM imported_transaction i
                JOIN csv_import ci ON ci.id = i.import_id
               WHERE i.user_id = :user
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;
    private final de.frauas.girokonto.i18n.ServerTexts texts;
    private final Clock clock;

    public ConsolidatedService(NamedParameterJdbcTemplate jdbc, AppProperties props,
                               de.frauas.girokonto.i18n.ServerTexts texts, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.texts = texts;
        this.clock = clock;
    }

    /** Gemeinsamer Filter für Liste, Export und Duplikat-Suche. */
    private record Query(String where, MapSqlParameterSource params, String source) {}

    private Query query(Long userId, String source, String q, LocalDate from, LocalDate to, String direction,
                        boolean includeInternal) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("user", userId)
                .addValue("bank", props.bank().name())
                .addValue("zone", clock.getZone().getId());
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        String src = source == null || source.isBlank() ? "ALL" : source.trim().toUpperCase(Locale.ROOT);
        switch (src) {
            case "ALL" -> { }
            case "PROJEKTBANK", "EXTERNAL", "IMPORT" -> {
                where.append(" AND source = :source");
                params.addValue("source", src);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE",
                    "Unbekannte Quelle. Möglich: ALL, PROJEKTBANK, EXTERNAL, IMPORT.");
        }
        if (direction != null && !direction.isBlank()) {
            switch (direction.trim().toUpperCase(Locale.ROOT)) {
                case "IN" -> where.append(" AND amount > 0");
                case "OUT" -> where.append(" AND amount < 0");
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DIRECTION",
                        "Unbekannte Richtung. Möglich: IN, OUT.");
            }
        }
        if (q != null && !q.isBlank()) {
            if (q.length() > 100) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "QUERY_TOO_LONG", "Der Suchbegriff ist zu lang.");
            }
            where.append(" AND (text ILIKE :q ESCAPE '\\' OR counterparty ILIKE :q ESCAPE '\\'"
                    + " OR account_name ILIKE :q ESCAPE '\\' OR note ILIKE :q ESCAPE '\\')");
            params.addValue("q", "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (from != null) {
            where.append(" AND d >= :from");
            params.addValue("from", Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND d <= :to");
            params.addValue("to", Date.valueOf(to));
        }
        if (!includeInternal) {
            where.append(" AND category <> 'UMBUCHUNG'");
        }
        return new Query(where.toString(), params, src);
    }

    private static final String ORDER = " ORDER BY d DESC, ts DESC NULLS LAST, sort_id DESC";

    /**
     * @param source          ALL (Standard), PROJEKTBANK, EXTERNAL oder IMPORT
     * @param direction       IN, OUT oder null
     * @param includeInternal Umbuchungen zwischen eigenen Konten anzeigen
     */
    @Transactional(readOnly = true)
    public TxPage find(Long userId, String source, String q, LocalDate from, LocalDate to, String direction,
                       boolean includeInternal, int page, int size) {
        int s = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int p = Math.max(page, 0);
        Query qy = query(userId, source, q, from, to, direction, includeInternal);
        MapSqlParameterSource params = qy.params();
        params.addValue("limit", s + 1).addValue("offset", (long) p * s);
        List<TxItem> rows = new ArrayList<>(jdbc.query(TX + "SELECT * FROM tx" + qy.where()
                + ORDER + " LIMIT :limit OFFSET :offset", params, this::map));
        boolean more = rows.size() > s;
        if (more) {
            rows.remove(rows.size() - 1);
        }

        params.addValue("neutral", NEUTRAL);
        long[] sums = jdbc.query(TX + "SELECT COALESCE(SUM(amount) FILTER (WHERE amount > 0), 0) AS income,"
                + " COALESCE(-SUM(amount) FILTER (WHERE amount < 0), 0) AS expense FROM tx" + qy.where()
                + " AND status = 'BOOKED' AND currency = 'EUR' AND NOT excluded AND category NOT IN (:neutral)",
                params, rs -> {
                    rs.next();
                    return new long[] {rs.getLong("income"), rs.getLong("expense")};
                });

        String src = qy.source();
        List<String> notes = new ArrayList<>();
        notes.add("Konten der Projektbank sind eine Simulation mit Spielgeld.");
        if (!"PROJEKTBANK".equals(src) && !"IMPORT".equals(src)) {
            notes.add("Verbundene Banken: Umsätze wie von der Bank geliefert, nur lesend; vorgemerkte Umsätze können sich noch ändern.");
        }
        if ("ALL".equals(src) || "IMPORT".equals(src)) {
            notes.add("CSV-Import: Umsätze aus importierten Dateien – sie ändern keine Kontostände.");
        }
        notes.add("Summen ohne Umbuchungen zwischen eigenen Konten, Sparraten, Kreditauszahlungen und vorgemerkte Umsätze.");
        return new TxPage(rows, p, s, more, sums[0], sums[1], notes);
    }

    // ------------------------------------------------------------------ CSV-Export

    public static final int MAX_EXPORT_ROWS = 10_000;

    public record CsvExport(String content, int rows, boolean truncated) {}

    /**
     * Umsätze im Filter als CSV mit BOM (UTF-8) für Tabellenprogramme. Deutsch: Semikolon und Dezimalkomma;
     * Englisch: Komma und Dezimalpunkt.
     */
    @Transactional(readOnly = true)
    public CsvExport export(Long userId, String source, String q, LocalDate from, LocalDate to, String direction,
                            boolean includeInternal, boolean english) {
        Query qy = query(userId, source, q, from, to, direction, includeInternal);
        qy.params().addValue("limit", MAX_EXPORT_ROWS + 1);
        List<TxItem> rows = jdbc.query(TX + "SELECT * FROM tx" + qy.where() + ORDER + " LIMIT :limit",
                qy.params(), this::map);
        boolean truncated = rows.size() > MAX_EXPORT_ROWS;
        String sep = english ? "," : ";";
        java.util.function.UnaryOperator<String> t = x -> x == null ? null : english ? texts.toEnglish(x) : x;
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(english
                ? "Date,Source,Account,Bank,Counterparty,Reference,Category,Amount,Currency,Status,Note"
                : "Datum;Quelle;Konto;Bank;Gegenpartei;Verwendungszweck;Kategorie;Betrag;Währung;Status;Notiz")
                .append("\r\n");
        java.time.format.DateTimeFormatter date = java.time.format.DateTimeFormatter.ofPattern(
                english ? "yyyy-MM-dd" : "dd.MM.yyyy");
        for (TxItem x : truncated ? rows.subList(0, MAX_EXPORT_ROWS) : rows) {
            String sourceLabel = switch (x.source()) {
                case "PROJEKTBANK" -> "Projektbank (Simulation)";
                case "EXTERNAL" -> "Verbundene Bank";
                default -> "CSV-Import";
            };
            csv.append(String.join(sep,
                    x.date().format(date), cell(t.apply(sourceLabel), sep), cell(t.apply(x.accountName()), sep),
                    cell(t.apply(x.institution()), sep), cell(x.counterpartyName(), sep),
                    cell(t.apply(x.description()), sep), cell(t.apply(x.categoryLabel()), sep),
                    amount(x.amountCents(), english), cell(x.currency(), sep),
                    t.apply("PENDING".equals(x.status()) ? "vorgemerkt" : "gebucht"), cell(x.note(), sep)))
                    .append("\r\n");
        }
        return new CsvExport(csv.toString(), truncated ? MAX_EXPORT_ROWS : rows.size(), truncated);
    }

    /** Betrag ohne Tausendertrennzeichen, damit Tabellenprogramme ihn als Zahl lesen: "-1234,56" bzw. "-1234.56". */
    static String amount(long cents, boolean english) {
        long abs = Math.abs(cents);
        return (cents < 0 ? "-" : "") + (abs / 100) + (english ? "." : ",") + String.format("%02d", abs % 100);
    }

    /**
     * Textzelle: in Anführungszeichen, wenn nötig; Zellen, die mit =, +, -, @, Tab oder Zeilenumbruch beginnen,
     * bekommen ein Hochkomma vorangestellt (Schutz vor Formeln in Tabellenprogrammen, „CSV-Injection“).
     */
    static String cell(String value, String sep) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String v = value;
        char c = v.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r' || c == '\n') {
            v = "'" + v;
        }
        if (v.contains(sep) || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ------------------------------------------------------------------ Doppelte Umsätze

    /** So weit dürfen zwei gleiche Umsätze auseinanderliegen, um als mögliche Doppelung zu gelten. */
    public static final int DUPLICATE_WINDOW_DAYS = 3;
    static final int MAX_PAIRS = 50;
    static final int MAX_DISMISSALS = 2_000;
    private static final java.util.regex.Pattern PAIR_KEY = java.util.regex.Pattern.compile(
            "(PROJEKTBANK|EXTERNAL|IMPORT):\\d{1,19}:\\d{1,19}\\|(PROJEKTBANK|EXTERNAL|IMPORT):\\d{1,19}:\\d{1,19}");
    /** Wörter, die in vielen Umsätzen vorkommen und nichts über die Gegenpartei sagen. */
    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of("sepa", "lastschrift", "überweisung",
            "ueberweisung", "gutschrift", "kartenzahlung", "zahlung", "dauerauftrag", "einkauf", "danke", "vielen",
            "rechnung", "eigene", "eigener", "simulation");

    /**
     * Ein mögliches Duplikat.
     *
     * @param kind SAME_ACCOUNT (zweimal auf demselben Konto, z. B. doppelt abgebucht) oder CROSS_SOURCE (derselbe
     *             Umsatz an zwei Stellen erfasst, z. B. selbst eingetragen und von der Bank geliefert)
     */
    public record DuplicatePair(String key, String kind, String reason, TxItem first, TxItem second) {}

    public record Duplicates(List<DuplicatePair> pairs, int days, String explanation) {}

    /**
     * Sucht in den letzten {@code days} Tagen Paare gebuchter Umsätze mit gleichem Betrag, höchstens
     * {@value #DUPLICATE_WINDOW_DAYS} Tage auseinander und mit gemeinsamem Stichwort (Gegenpartei/Verwendungszweck).
     * Umbuchungen zwischen eigenen Konten und als „kein Duplikat“ markierte Paare zählen nicht.
     */
    @Transactional(readOnly = true)
    public Duplicates duplicates(Long userId, int days) {
        int d = Math.min(Math.max(days, 7), 365);
        Query qy = query(userId, "ALL", null, LocalDate.now(clock).minusDays(d), null, null, false);
        qy.params().addValue("limit", 3_000);
        List<TxItem> rows = jdbc.query(TX + "SELECT * FROM tx" + qy.where() + " AND status = 'BOOKED'" + ORDER
                + " LIMIT :limit", qy.params(), this::map);
        java.util.Set<String> dismissed = new java.util.HashSet<>(jdbc.getJdbcTemplate().queryForList(
                "SELECT pair_key FROM duplicate_dismissal WHERE user_id = ?", String.class, userId));
        java.util.Map<Long, List<TxItem>> byAmount = new java.util.HashMap<>();
        for (TxItem x : rows) {
            byAmount.computeIfAbsent(x.amountCents(), k -> new ArrayList<>()).add(x);
        }
        List<DuplicatePair> pairs = new ArrayList<>();
        for (List<TxItem> group : byAmount.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    TxItem a = group.get(i);
                    TxItem b = group.get(j);
                    if (Math.abs(a.date().toEpochDay() - b.date().toEpochDay()) > DUPLICATE_WINDOW_DAYS
                            || (a.source().equals(b.source()) && a.id().equals(b.id()))
                            || !similar(a, b)) {
                        continue;
                    }
                    TxItem first = a.date().isAfter(b.date()) ? b : a;   // ältere zuerst
                    TxItem second = first == a ? b : a;
                    String key = pairKey(first, second);
                    if (dismissed.contains(key)) {
                        continue;
                    }
                    boolean same = a.source().equals(b.source()) && a.accountId().equals(b.accountId());
                    pairs.add(new DuplicatePair(key, same ? "SAME_ACCOUNT" : "CROSS_SOURCE", same
                            ? "Gleicher Betrag und gleiche Gegenpartei innerhalb weniger Tage auf demselben Konto "
                                    + "– vielleicht doppelt abgebucht."
                            : "Derselbe Umsatz steht vielleicht doppelt da: in „" + first.accountName() + "“ und in „"
                                    + second.accountName() + "“ (z. B. selbst eingetragen und von der Bank geliefert).",
                            first, second));
                }
            }
        }
        pairs.sort(java.util.Comparator.comparing((DuplicatePair x) -> x.second().date()).reversed()
                .thenComparing(DuplicatePair::key));
        return new Duplicates(pairs.size() > MAX_PAIRS ? pairs.subList(0, MAX_PAIRS) : pairs, d,
                "Gleicher Betrag, höchstens " + DUPLICATE_WINDOW_DAYS + " Tage auseinander und ein gemeinsames "
                        + "Stichwort. Prüfe selbst – oft sind es zwei echte Umsätze.");
    }

    /** Ein Paar als „kein Duplikat“ markieren; es erscheint danach nicht mehr. */
    @Transactional
    public void dismiss(Long userId, String key) {
        if (key == null || !PAIR_KEY.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAIR", "Unbekanntes Paar.");
        }
        Integer count = jdbc.getJdbcTemplate().queryForObject(
                "SELECT count(*) FROM duplicate_dismissal WHERE user_id = ?", Integer.class, userId);
        if (count != null && count >= MAX_DISMISSALS) {
            throw new ApiException(HttpStatus.CONFLICT, "TOO_MANY_DISMISSALS", "Zu viele markierte Paare.");
        }
        jdbc.getJdbcTemplate().update("INSERT INTO duplicate_dismissal (user_id, pair_key) VALUES (?, ?)"
                + " ON CONFLICT DO NOTHING", userId, key);
    }

    static String pairKey(TxItem a, TxItem b) {
        String x = a.source() + ":" + a.id() + ":" + a.accountId();
        String y = b.source() + ":" + b.id() + ":" + b.accountId();
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }

    /** Gemeinsames Stichwort (mindestens 4 Buchstaben, keine Allerweltswörter) in Gegenpartei oder Verwendungszweck. */
    static boolean similar(TxItem a, TxItem b) {
        java.util.Set<String> wa = words(a);
        for (String w : words(b)) {
            if (wa.contains(w)) {
                return true;
            }
        }
        return false;
    }

    static java.util.Set<String> words(TxItem x) {
        java.util.Set<String> result = new java.util.HashSet<>();
        for (String part : List.of(x.counterpartyName() == null ? "" : x.counterpartyName(),
                x.description() == null ? "" : x.description())) {
            for (String w : part.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                if (w.length() >= 4 && !STOP_WORDS.contains(w) && !w.chars().allMatch(Character::isDigit)) {
                    result.add(w);
                }
            }
        }
        return result;
    }

    private TxItem map(ResultSet rs, int i) throws SQLException {
        String category = rs.getString("category");
        String label;
        try {
            label = Category.valueOf(category).label();
        } catch (IllegalArgumentException | NullPointerException e) {
            label = Category.SONSTIGES.label();
        }
        Timestamp ts = rs.getTimestamp("ts");
        return new TxItem(rs.getString("source"), rs.getLong("ref_id"), rs.getLong("account_id"),
                rs.getString("account_name"), rs.getString("institution"), rs.getDate("d").toLocalDate(),
                ts == null ? null : ts.toInstant(), rs.getLong("amount"), rs.getString("currency"),
                rs.getString("counterparty"), rs.getString("text"), category, label, rs.getString("status"),
                "UMBUCHUNG".equals(category), rs.getBoolean("excluded"), rs.getString("note"));
    }
}
