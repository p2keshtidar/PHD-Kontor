package de.frauas.girokonto.account;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** /K060/ Mitglieder geteilter Unterkonten (einfache Tabelle, darum JDBC statt JPA). */
@Repository
public class AccountMemberRepository {

    private final JdbcTemplate jdbc;

    public AccountMemberRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isMember(Long accountId, Long userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM account_member WHERE account_id = ? AND user_id = ?",
                Integer.class, accountId, userId);
        return n != null && n > 0;
    }

    public List<Long> members(Long accountId) {
        return jdbc.queryForList("SELECT user_id FROM account_member WHERE account_id = ? ORDER BY added_at",
                Long.class, accountId);
    }

    public List<Long> sharedWith(Long userId) {
        return jdbc.queryForList("SELECT account_id FROM account_member WHERE user_id = ? ORDER BY account_id",
                Long.class, userId);
    }

    public int count(Long accountId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM account_member WHERE account_id = ?", Integer.class, accountId);
        return n == null ? 0 : n;
    }

    public void add(Long accountId, Long userId, Instant now) {
        add(accountId, userId, now, "FULL");
    }

    /** @param role FULL (einzahlen + auszahlen), DEPOSIT (nur einzahlen) oder VIEW (nur ansehen) */
    public void add(Long accountId, Long userId, Instant now, String role) {
        jdbc.update("INSERT INTO account_member (account_id, user_id, added_at, role) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                accountId, userId, Timestamp.from(now), role);
    }

    /** Rolle des Mitglieds oder null, wenn kein Mitglied. */
    public String role(Long accountId, Long userId) {
        List<String> r = jdbc.queryForList("SELECT role FROM account_member WHERE account_id = ? AND user_id = ?",
                String.class, accountId, userId);
        return r.isEmpty() ? null : r.get(0);
    }

    public int setRole(Long accountId, Long userId, String role) {
        return jdbc.update("UPDATE account_member SET role = ? WHERE account_id = ? AND user_id = ?", role, accountId, userId);
    }

    public int remove(Long accountId, Long userId) {
        return jdbc.update("DELETE FROM account_member WHERE account_id = ? AND user_id = ?", accountId, userId);
    }
}
