package de.frauas.girokonto.account;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Vormerkungen für digitale Konten aus verbundenen Bankkonten (Tabelle allocation_run, Arten EARMARK/RELEASE).
 *
 * <p>Eine Vormerkung bewegt kein Geld: Der Betrag bleibt auf dem Konto bei der anderen Bank und wird dem Ziel
 * nur zugerechnet. Wird die Bankverbindung samt Daten gelöscht, verschwinden auch ihre Vormerkungen
 * (source_external_id wird NULL). Aufgelöste digitale Konten halten nichts mehr zurück.
 */
@Repository
public class EarmarkRepository {

    private static final String SIGNED = "CASE r.kind WHEN 'EARMARK' THEN r.amount_cents ELSE -r.amount_cents END";

    private final JdbcTemplate jdbc;

    public EarmarkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Summe der Vormerkungen für ein digitales Konto (alle Quellen, alle Personen). */
    public long forAccount(Long accountId) {
        Long sum = jdbc.queryForObject("SELECT COALESCE(SUM(" + SIGNED + "), 0) FROM allocation_run r"
                + " WHERE r.target_account_id = ? AND r.status = 'COMPLETED' AND r.kind IN ('EARMARK', 'RELEASE')"
                + " AND r.source_external_id IS NOT NULL", Long.class, accountId);
        return sum == null ? 0 : Math.max(0, sum);
    }

    /** Vormerkungen je digitalem Konto (für Übersichten). */
    public Map<Long, Long> forAccounts(Collection<Long> accountIds) {
        Map<Long, Long> result = new HashMap<>();
        if (accountIds.isEmpty()) {
            return result;
        }
        String in = String.join(",", accountIds.stream().map(id -> "?").toList());
        jdbc.query("SELECT r.target_account_id, SUM(" + SIGNED + ") AS s FROM allocation_run r"
                        + " WHERE r.target_account_id IN (" + in + ") AND r.status = 'COMPLETED'"
                        + " AND r.kind IN ('EARMARK', 'RELEASE') AND r.source_external_id IS NOT NULL"
                        + " GROUP BY r.target_account_id",
                rs -> {
                    result.put(rs.getLong(1), Math.max(0, rs.getLong(2)));
                }, accountIds.toArray());
        return result;
    }

    /** Was von einem verbundenen Bankkonto schon vorgemerkt ist (nur noch aktive digitale Konten). */
    public long forSource(Long externalAccountId) {
        Long sum = jdbc.queryForObject("SELECT COALESCE(SUM(" + SIGNED + "), 0) FROM allocation_run r"
                + " JOIN account a ON a.id = r.target_account_id"
                + " WHERE r.source_external_id = ? AND r.status = 'COMPLETED' AND r.kind IN ('EARMARK', 'RELEASE')"
                + " AND a.status = 'ACTIVE'", Long.class, externalAccountId);
        return sum == null ? 0 : Math.max(0, sum);
    }

    /** Was von einem verbundenen Bankkonto für ein bestimmtes digitales Konto vorgemerkt ist. */
    public long forSourceAndAccount(Long externalAccountId, Long accountId) {
        Long sum = jdbc.queryForObject("SELECT COALESCE(SUM(" + SIGNED + "), 0) FROM allocation_run r"
                + " WHERE r.source_external_id = ? AND r.target_account_id = ? AND r.status = 'COMPLETED'"
                + " AND r.kind IN ('EARMARK', 'RELEASE')", Long.class, externalAccountId, accountId);
        return sum == null ? 0 : Math.max(0, sum);
    }
}
