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
        jdbc.update("INSERT INTO account_member (account_id, user_id, added_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                accountId, userId, Timestamp.from(now));
    }

    public int remove(Long accountId, Long userId) {
        return jdbc.update("DELETE FROM account_member WHERE account_id = ? AND user_id = ?", accountId, userId);
    }
}
