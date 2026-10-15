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
 * Alle Umsätze an einem Ort: Bankkonten der Projektbank (Girokonto, weitere Girokonten, Tagesgeld – Simulation) und
 * verbundene Banken (nur lesend, wie von der Bank geliefert). Digitale Konten, Sparpläne und Kredite sind Teil der
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
                     FALSE AS excluded
                FROM booking_line l
                JOIN booking b ON b.id = l.booking_id
                JOIN account a ON a.id = l.account_id
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
                     COALESCE(e.category, t.auto_category), t.status, COALESCE(e.excluded, FALSE)
                FROM external_transaction t
                JOIN external_account ea ON ea.id = t.external_account_id
                JOIN bank_connection c ON c.id = ea.connection_id
                LEFT JOIN external_transaction_edit e ON e.transaction_id = t.id
               WHERE t.user_id = :user AND t.status IN ('BOOKED', 'PENDING')
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;
    private final Clock clock;

    public ConsolidatedService(NamedParameterJdbcTemplate jdbc, AppProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    /**
     * @param source          ALL (Standard), PROJEKTBANK oder EXTERNAL
     * @param direction       IN, OUT oder null
     * @param includeInternal Umbuchungen zwischen eigenen Konten anzeigen
     */
    @Transactional(readOnly = true)
    public TxPage find(Long userId, String source, String q, LocalDate from, LocalDate to, String direction,
                       boolean includeInternal, int page, int size) {
        int s = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int p = Math.max(page, 0);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("user", userId)
                .addValue("bank", props.bank().name())
                .addValue("zone", clock.getZone().getId());
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        String src = source == null || source.isBlank() ? "ALL" : source.trim().toUpperCase(Locale.ROOT);
        switch (src) {
            case "ALL" -> { }
            case "PROJEKTBANK", "EXTERNAL" -> {
                where.append(" AND source = :source");
                params.addValue("source", src);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE",
                    "Unbekannte Quelle. Möglich: ALL, PROJEKTBANK, EXTERNAL.");
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
                    + " OR account_name ILIKE :q ESCAPE '\\')");
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

        params.addValue("limit", s + 1).addValue("offset", (long) p * s);
        List<TxItem> rows = new ArrayList<>(jdbc.query(TX + "SELECT * FROM tx" + where
                + " ORDER BY d DESC, ts DESC NULLS LAST, sort_id DESC LIMIT :limit OFFSET :offset", params, this::map));
        boolean more = rows.size() > s;
        if (more) {
            rows.remove(rows.size() - 1);
        }

        params.addValue("neutral", NEUTRAL);
        long[] sums = jdbc.query(TX + "SELECT COALESCE(SUM(amount) FILTER (WHERE amount > 0), 0) AS income,"
                + " COALESCE(-SUM(amount) FILTER (WHERE amount < 0), 0) AS expense FROM tx" + where
                + " AND status = 'BOOKED' AND currency = 'EUR' AND NOT excluded AND category NOT IN (:neutral)",
                params, rs -> {
                    rs.next();
                    return new long[] {rs.getLong("income"), rs.getLong("expense")};
                });

        List<String> notes = new ArrayList<>();
        notes.add("Konten der Projektbank sind eine Simulation mit Spielgeld.");
        if (!"PROJEKTBANK".equals(src)) {
            notes.add("Verbundene Banken: Umsätze wie von der Bank geliefert, nur lesend; vorgemerkte Umsätze können sich noch ändern.");
        }
        notes.add("Summen ohne Umbuchungen zwischen eigenen Konten, Sparraten, Kreditauszahlungen und vorgemerkte Umsätze.");
        return new TxPage(rows, p, s, more, sums[0], sums[1], notes);
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
                "UMBUCHUNG".equals(category), rs.getBoolean("excluded"));
    }
}
