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

    public record EntryDto(Long id, String action, String target, String details, Instant createdAt) {}

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
                """, (rs, i) -> new EntryDto(rs.getLong("id"), rs.getString("action"), rs.getString("target"),
                rs.getString("details"), rs.getTimestamp("created_at").toInstant()), userId, s + 1, p * s);
        boolean more = rows.size() > s;
        return new PageDto<>(more ? rows.subList(0, s) : rows, p, s, more);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
