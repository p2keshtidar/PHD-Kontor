package de.frauas.girokonto.audit;

import de.frauas.girokonto.common.PageDto;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Prüfprotokoll für sicherheitsrelevante Aktionen (z. B. Bank verbunden, Freigabe widerrufen).
 * Es enthält nie Passwörter, Freigabe-Kennungen oder vollständige Bankdaten.
 */
@Service
public class AuditLog {

    public record EntryDto(Long id, String action, String actionLabel, String target, String details, Instant createdAt) {}

    /** Verständliche Bezeichnungen der Aktionen (werden für Englisch übersetzt). */
    static final java.util.Map<String, String> LABELS = java.util.Map.ofEntries(
            java.util.Map.entry("REGISTERED", "Konto eröffnet"),
            java.util.Map.entry("LOGIN", "Anmeldung"),
            java.util.Map.entry("LOGIN_FAILED", "Fehlgeschlagene Anmeldung"),
            java.util.Map.entry("LOGOUT", "Abmeldung auf allen Geräten"),
            java.util.Map.entry("PASSWORD_CHANGED", "Passwort geändert"),
            java.util.Map.entry("TWO_FACTOR_ENABLED", "Zwei-Faktor-Anmeldung aktiviert"),
            java.util.Map.entry("TWO_FACTOR_DISABLED", "Zwei-Faktor-Anmeldung ausgeschaltet"),
            java.util.Map.entry("CARD_DETAILS_SHOWN", "Kartendaten angezeigt"),
            java.util.Map.entry("CARD_LOCKED", "Karte gesperrt"),
            java.util.Map.entry("CARD_UNLOCKED", "Karte entsperrt"),
            java.util.Map.entry("CARD_LIMIT_CHANGED", "Kartenlimit geändert"),
            java.util.Map.entry("TRANSFER_SENT", "Überweisung gesendet"),
            java.util.Map.entry("ACCOUNT_OPENED", "Konto eröffnet"),
            java.util.Map.entry("ACCOUNT_CLOSED", "Konto aufgelöst"),
            java.util.Map.entry("TRANSACTIONS_EXPORTED", "Umsätze exportiert"),
            java.util.Map.entry("BANK_CONSENT_STARTED", "Bankfreigabe gestartet"),
            java.util.Map.entry("BANK_CONNECTED", "Bank verbunden"),
            java.util.Map.entry("BANK_CONSENT_EXPIRED", "Bankfreigabe abgelaufen"),
            java.util.Map.entry("BANK_REVOKED", "Bankfreigabe widerrufen"),
            java.util.Map.entry("LOAN_APPLICATION_APPROVED", "Kreditanfrage zugesagt"),
            java.util.Map.entry("LOAN_APPLICATION_DECLINED", "Kreditanfrage abgelehnt"),
            java.util.Map.entry("LOAN_CONTRACTED", "Kreditvertrag abgeschlossen"),
            java.util.Map.entry("LOAN_PAID_OUT", "Kredit ausgezahlt"),
            java.util.Map.entry("LOAN_CANCELLED", "Kredit widerrufen"),
            java.util.Map.entry("CREDIT_CARD_CREATED", "Kreditkarte eingerichtet"),
            java.util.Map.entry("CREDIT_CARD_LOCKED", "Kreditkarte gesperrt"),
            java.util.Map.entry("CREDIT_CARD_UNLOCKED", "Kreditkarte entsperrt"),
            java.util.Map.entry("CREDIT_CARD_CLOSED", "Kreditkarte aufgelöst"),
            java.util.Map.entry("MARKET_APPLICATION", "Antrag im Marktplatz (Simulation)"),
            java.util.Map.entry("GROUP_SETTLED", "Gemeinsame Ausgaben ausgeglichen"),
            java.util.Map.entry("GROUP_CASH_CONFIRMED", "Barzahlung bestätigt"),
            java.util.Map.entry("DOCUMENT_UPLOADED", "Dokument hochgeladen"),
            java.util.Map.entry("DOCUMENT_DOWNLOADED", "Dokument abgerufen"),
            java.util.Map.entry("DOCUMENT_DELETED", "Dokument gelöscht"),
            java.util.Map.entry("CONTRACT_CANCEL_NOTED", "Kündigung vermerkt"),
            java.util.Map.entry("SUBACCOUNT_CARD_CREATED", "Karte für digitales Konto eingerichtet"));

    static String label(String action) {
        String l = LABELS.get(action);
        if (l != null) {
            return l;
        }
        return action.startsWith("BANK_CONSENT_") ? "Bankfreigabe: Status geändert" : action;
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuditLog(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void record(Long userId, String action, String target, String details) {
        jdbc.update("INSERT INTO audit_log (user_id, action, target, details, created_at) VALUES (?, ?, ?, ?, ?)",
                userId, cut(action, 60), cut(target, 100), cut(details, 300), Timestamp.from(Instant.now(clock)));
    }

    public PageDto<EntryDto> list(Long userId, int page, int size) {
        int s = Math.min(Math.max(size, 1), 100);
        int p = Math.max(page, 0);
        List<EntryDto> rows = jdbc.query("""
                SELECT id, action, target, details, created_at FROM audit_log
                 WHERE user_id = ? ORDER BY id DESC LIMIT ? OFFSET ?
                """, (rs, i) -> new EntryDto(rs.getLong("id"), rs.getString("action"), label(rs.getString("action")),
                rs.getString("target"),
                rs.getString("details"), rs.getTimestamp("created_at").toInstant()), userId, s + 1, p * s);
        boolean more = rows.size() > s;
        return new PageDto<>(more ? rows.subList(0, s) : rows, p, s, more);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
