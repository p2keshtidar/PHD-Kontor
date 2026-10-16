package de.frauas.girokonto.group;

import de.frauas.girokonto.audit.AuditLog;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.transfer.Recipient;
import de.frauas.girokonto.transfer.RecipientResolver;
import de.frauas.girokonto.transfer.TransferRequest;
import de.frauas.girokonto.transfer.TransferResultDto;
import de.frauas.girokonto.transfer.TransferService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gemeinsame Ausgaben (WG, Reise, Paar): wer hat was bezahlt, wie wird aufgeteilt, wer schuldet wem wie viel.
 *
 * Salden: bezahlt − Anteil + geleistete Ausgleiche − erhaltene Ausgleiche. Die Summe aller Salden ist immer 0.
 * Ausgleich per Überweisung nutzt den normalen {@link TransferService} (Prüfungen, Tageslimit, Idempotenz) –
 * also Spielgeld der Projektbank. Ein Barausgleich kann nur vom Empfänger bestätigt werden.
 */
@Service
public class GroupService {

    public static final int MAX_MEMBERS = 10;
    public static final int MAX_GROUPS = 20;

    public record Member(Long userId, String name, boolean owner, boolean me, long balanceCents) {}
    public record Share(Long userId, String name, long shareCents) {}
    public record Expense(Long id, Long payerId, String payerName, long amountCents, String description,
                          LocalDate date, List<Share> shares, boolean deletable, long myShareCents) {}
    public record Settlement(Long id, Long fromUserId, String fromName, Long toUserId, String toName,
                             long amountCents, String method, String methodLabel, Instant createdAt) {}
    public record Suggestion(Long fromUserId, String fromName, Long toUserId, String toName, long amountCents) {}
    public record GroupDetail(Long id, String name, boolean owner, long myBalanceCents, long totalSpentCents,
                              List<Member> members, List<Suggestion> suggestions, List<Expense> expenses,
                              List<Settlement> settlements, String note) {}
    public record GroupSummary(Long id, String name, int memberCount, long myBalanceCents, long totalSpentCents) {}
    public record ShareInput(Long userId, long shareCents) {}
    public record ExpenseInput(String description, long amountCents, Long payerId, List<Long> participants,
                               List<ShareInput> shares, LocalDate date) {}

    public static final String NOTE = "Ausgleich per Überweisung über die Projektbank (Spielgeld). "
            + "Barzahlungen bestätigt der Empfänger.";

    private final JdbcTemplate jdbc;
    private final RecipientResolver recipients;
    private final AccountService accountService;
    private final TransferService transfers;
    private final NotificationService notifications;
    private final Clock clock;

    private final AuditLog audit;

    public GroupService(JdbcTemplate jdbc, RecipientResolver recipients, AccountService accountService,
                        TransferService transfers, NotificationService notifications, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.jdbc = jdbc;
        this.recipients = recipients;
        this.accountService = accountService;
        this.transfers = transfers;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- Gruppen

    @Transactional(readOnly = true)
    public List<GroupSummary> list(Long userId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT g.id FROM expense_group g JOIN expense_group_member m ON m.group_id = g.id "
                        + "WHERE m.user_id = ? ORDER BY g.created_at, g.id", Long.class, userId);
        List<GroupSummary> list = new ArrayList<>();
        for (Long id : ids) {
            Map<Long, Long> bal = balances(id);
            list.add(new GroupSummary(id, name(id), bal.size(), bal.getOrDefault(userId, 0L), totalSpent(id)));
        }
        return list;
    }

    @Transactional
    public GroupDetail create(Long userId, String name) {
        String n = cleanName(name);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM expense_group_member WHERE user_id = ?",
                Integer.class, userId);
        if (count != null && count >= MAX_GROUPS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_GROUPS", "Höchstens " + MAX_GROUPS + " Gruppen.");
        }
        Instant now = Instant.now(clock);
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement("INSERT INTO expense_group (owner_id, name, created_at) VALUES (?, ?, ?)",
                    new String[] {"id"});
            ps.setLong(1, userId);
            ps.setString(2, n);
            ps.setTimestamp(3, Timestamp.from(now));
            return ps;
        }, kh);
        long id = kh.getKey().longValue();
        jdbc.update("INSERT INTO expense_group_member (group_id, user_id, joined_at) VALUES (?, ?, ?)",
                id, userId, Timestamp.from(now));
        return detail(userId, id);
    }

    @Transactional
    public GroupDetail rename(Long userId, Long groupId, String name) {
        requireOwner(userId, groupId);
        jdbc.update("UPDATE expense_group SET name = ? WHERE id = ?", cleanName(name), groupId);
        return detail(userId, groupId);
    }

    /** Gruppe löschen: nur der Ersteller und nur, wenn alle Salden ausgeglichen sind. */
    @Transactional
    public void delete(Long userId, Long groupId) {
        requireOwner(userId, groupId);
        if (balances(groupId).values().stream().anyMatch(b -> b != 0)) {
            throw new ApiException(HttpStatus.CONFLICT, "GROUP_NOT_SETTLED",
                    "Erst alle Schulden ausgleichen, dann kann die Gruppe gelöscht werden.");
        }
        jdbc.update("DELETE FROM expense_group WHERE id = ?", groupId);
    }

    @Transactional(readOnly = true)
    public GroupDetail detail(Long userId, Long groupId) {
        requireMember(userId, groupId);
        Long ownerId = jdbc.queryForObject("SELECT owner_id FROM expense_group WHERE id = ?", Long.class, groupId);
        Map<Long, Long> bal = balances(groupId);
        Map<Long, String> names = new HashMap<>();
        for (Long id : bal.keySet()) {
            names.put(id, accountService.ownerName(id));
        }
        List<Member> members = new ArrayList<>();
        for (var e : bal.entrySet()) {
            members.add(new Member(e.getKey(), names.get(e.getKey()), e.getKey().equals(ownerId),
                    e.getKey().equals(userId), e.getValue()));
        }
        List<Suggestion> suggestions = suggest(bal).stream()
                .map(s -> new Suggestion(s.fromUserId(), name(names, s.fromUserId()), s.toUserId(),
                        name(names, s.toUserId()), s.amountCents()))
                .toList();
        return new GroupDetail(groupId, name(groupId), userId.equals(ownerId), bal.getOrDefault(userId, 0L),
                totalSpent(groupId), members, suggestions, expenses(userId, groupId, ownerId, names),
                settlements(groupId, names), NOTE);
    }

    // ---------------------------------------------------------------- Mitglieder

    @Transactional
    public GroupDetail addMember(Long userId, Long groupId, String recipient) {
        requireMember(userId, groupId);
        Recipient r = recipients.resolve(recipient);
        Long newMember = r.user().getId();
        if (isMember(groupId, newMember)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", r.fullName() + " ist schon dabei.");
        }
        if (balances(groupId).size() >= MAX_MEMBERS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_MEMBERS",
                    "Höchstens " + MAX_MEMBERS + " Personen pro Gruppe.");
        }
        jdbc.update("INSERT INTO expense_group_member (group_id, user_id, joined_at) VALUES (?, ?, ?)",
                groupId, newMember, Timestamp.from(Instant.now(clock)));
        notifications.send(newMember, NotificationType.INFO, "Gemeinsame Ausgaben",
                accountService.ownerName(userId) + " hat dich zur Gruppe „" + name(groupId) + "“ hinzugefügt.", null);
        return detail(userId, groupId);
    }

    /** Mitglied entfernen (Ersteller) oder selbst austreten – nur mit ausgeglichenem Saldo. */
    @Transactional
    public void removeMember(Long userId, Long groupId, Long memberId) {
        requireMember(userId, groupId);
        Long ownerId = jdbc.queryForObject("SELECT owner_id FROM expense_group WHERE id = ?", Long.class, groupId);
        if (!memberId.equals(userId) && !userId.equals(ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_GROUP_OWNER", "Nur der Ersteller kann Mitglieder entfernen.");
        }
        if (memberId.equals(ownerId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OWNER_CANNOT_LEAVE",
                    "Der Ersteller kann nicht austreten – lösche stattdessen die Gruppe.");
        }
        if (!isMember(groupId, memberId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Diese Person ist kein Mitglied.");
        }
        if (balances(groupId).getOrDefault(memberId, 0L) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMBER_NOT_SETTLED",
                    "Austritt erst möglich, wenn der Saldo ausgeglichen ist.");
        }
        jdbc.update("DELETE FROM expense_group_member WHERE group_id = ? AND user_id = ?", groupId, memberId);
    }

    // ---------------------------------------------------------------- Ausgaben

    @Transactional
    public GroupDetail addExpense(Long userId, Long groupId, ExpenseInput in) {
        requireMember(userId, groupId);
        String desc = in.description() == null ? "" : in.description().trim();
        if (desc.isEmpty() || desc.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DESCRIPTION", "Bitte eine Beschreibung (bis 100 Zeichen) angeben.");
        }
        long amount = in.amountCents();
        if (amount <= 0 || amount > 10_000_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Der Betrag muss zwischen 0,01 € und 100.000 € liegen.");
        }
        Long payer = in.payerId() == null ? userId : in.payerId();
        if (!isMember(groupId, payer)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYER_NOT_MEMBER", "Wer bezahlt hat, muss Mitglied der Gruppe sein.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate date = in.date() == null ? today : in.date();
        if (date.isAfter(today) || date.isBefore(today.minusYears(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "Das Datum muss im letzten Jahr liegen, nicht in der Zukunft.");
        }
        Map<Long, Long> shares = shares(groupId, amount, in);

        Instant now = Instant.now(clock);
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement("INSERT INTO group_expense (group_id, payer_id, amount_cents, description, "
                    + "expense_date, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", new String[] {"id"});
            ps.setLong(1, groupId);
            ps.setLong(2, payer);
            ps.setLong(3, amount);
            ps.setString(4, desc);
            ps.setObject(5, date);
            ps.setLong(6, userId);
            ps.setTimestamp(7, Timestamp.from(now));
            return ps;
        }, kh);
        long expenseId = kh.getKey().longValue();
        for (var e : shares.entrySet()) {
            jdbc.update("INSERT INTO group_expense_share (expense_id, user_id, share_cents) VALUES (?, ?, ?)",
                    expenseId, e.getKey(), e.getValue());
        }
        String actor = accountService.ownerName(userId);
        for (var e : shares.entrySet()) {
            if (!e.getKey().equals(userId) && e.getValue() > 0) {
                notifications.send(e.getKey(), NotificationType.INFO, "Neue gemeinsame Ausgabe",
                        actor + " hat „" + desc + "“ über " + Money.format(amount) + " eingetragen – dein Anteil: "
                                + Money.format(e.getValue()) + ".", null);
            }
        }
        return detail(userId, groupId);
    }

    /** Aufteilung: gleichmäßig auf die Beteiligten (Rest-Cent an die ersten) oder feste Anteile, die genau aufgehen. */
    private Map<Long, Long> shares(Long groupId, long amount, ExpenseInput in) {
        Map<Long, Long> result = new LinkedHashMap<>();
        if (in.shares() != null && !in.shares().isEmpty()) {
            long sum = 0;
            for (ShareInput s : in.shares()) {
                if (s.userId() == null || !isMember(groupId, s.userId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "PARTICIPANT_NOT_MEMBER", "Alle Beteiligten müssen Mitglied der Gruppe sein.");
                }
                if (s.shareCents() < 0 || result.containsKey(s.userId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHARES", "Die Anteile sind ungültig.");
                }
                result.put(s.userId(), s.shareCents());
                sum += s.shareCents();
            }
            if (sum != amount) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SHARES_DO_NOT_ADD_UP",
                        "Die Anteile ergeben " + Money.format(sum) + ", die Ausgabe beträgt " + Money.format(amount) + ".");
            }
            return result;
        }
        List<Long> people = in.participants() == null || in.participants().isEmpty()
                ? new ArrayList<>(balances(groupId).keySet())
                : in.participants().stream().distinct().sorted().toList();
        for (Long p : people) {
            if (p == null || !isMember(groupId, p)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PARTICIPANT_NOT_MEMBER", "Alle Beteiligten müssen Mitglied der Gruppe sein.");
            }
        }
        long base = amount / people.size();
        long rest = amount % people.size();
        for (int i = 0; i < people.size(); i++) {
            result.put(people.get(i), base + (i < rest ? 1 : 0));
        }
        return result;
    }

    /** Ausgabe löschen: wer sie eingetragen oder bezahlt hat, oder der Ersteller der Gruppe. */
    @Transactional
    public GroupDetail deleteExpense(Long userId, Long groupId, Long expenseId) {
        requireMember(userId, groupId);
        Long ownerId = jdbc.queryForObject("SELECT owner_id FROM expense_group WHERE id = ?", Long.class, groupId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT payer_id, created_by FROM group_expense WHERE id = ? AND group_id = ?", expenseId, groupId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EXPENSE_NOT_FOUND", "Ausgabe nicht gefunden.");
        }
        Map<String, Object> r = rows.get(0);
        if (!userId.equals(ownerId) && !userId.equals(((Number) r.get("payer_id")).longValue())
                && !userId.equals(((Number) r.get("created_by")).longValue())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EXPENSE_NOT_DELETABLE",
                    "Nur wer die Ausgabe eingetragen oder bezahlt hat, kann sie löschen.");
        }
        jdbc.update("DELETE FROM group_expense WHERE id = ?", expenseId);
        return detail(userId, groupId);
    }

    // ---------------------------------------------------------------- Ausgleich

    /** Schuld per Überweisung begleichen (normale Überweisung der Projektbank, Spielgeld). */
    @Transactional
    public GroupDetail settleByTransfer(Long userId, Long groupId, Long toUserId, long amountCents, String idempotencyKey) {
        requireMember(userId, groupId);
        de.frauas.girokonto.common.IdempotencyKeys.require(idempotencyKey, 40);
        String key = "group:" + groupId + ":" + idempotencyKey;
        Integer done = jdbc.queryForObject(
                "SELECT count(*) FROM group_settlement s JOIN booking b ON b.id = s.booking_id "
                        + "WHERE s.group_id = ? AND b.idempotency_key = ?", Integer.class, groupId,
                "transfer:" + userId + ":" + key);
        if (done != null && done > 0) {
            return detail(userId, groupId);
        }
        checkSettlement(groupId, userId, toUserId, amountCents);
        String iban = accountService.mainAccount(toUserId).getIban();
        TransferResultDto t = transfers.execute(userId, key,
                new TransferRequest(iban, amountCents, "Ausgleich „" + name(groupId) + "“"));
        insertSettlement(groupId, userId, toUserId, amountCents, "TRANSFER", t.bookingId(), userId);
        audit.record(userId, "GROUP_SETTLED", "group:" + groupId, Money.format(amountCents));
        return detail(userId, groupId);
    }

    /** Barzahlung festhalten: nur der Empfänger kann bestätigen, dass er Geld bekommen hat. */
    @Transactional
    public GroupDetail confirmCash(Long userId, Long groupId, Long fromUserId, long amountCents) {
        requireMember(userId, groupId);
        checkSettlement(groupId, fromUserId, userId, amountCents);
        insertSettlement(groupId, fromUserId, userId, amountCents, "CASH", null, userId);
        audit.record(userId, "GROUP_CASH_CONFIRMED", "group:" + groupId, Money.format(amountCents));
        notifications.send(fromUserId, NotificationType.INFO, "Gemeinsame Ausgaben",
                accountService.ownerName(userId) + " hat deine Barzahlung über " + Money.format(amountCents)
                        + " in „" + name(groupId) + "“ bestätigt.", null);
        return detail(userId, groupId);
    }

    private void checkSettlement(Long groupId, Long from, Long to, long amountCents) {
        if (from == null || to == null || from.equals(to) || !isMember(groupId, from) || !isMember(groupId, to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SETTLEMENT", "Ausgleich nur zwischen zwei verschiedenen Mitgliedern.");
        }
        Map<Long, Long> bal = balances(groupId);
        long debt = -bal.getOrDefault(from, 0L);
        long credit = bal.getOrDefault(to, 0L);
        long max = Math.min(debt, credit);
        if (amountCents <= 0 || amountCents > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SETTLEMENT_TOO_HIGH",
                    max <= 0 ? "Hier ist nichts offen." : "Höchstens " + Money.format(max) + " sind offen.");
        }
    }

    private void insertSettlement(Long groupId, Long from, Long to, long amount, String method, Long bookingId, Long by) {
        jdbc.update("INSERT INTO group_settlement (group_id, from_user, to_user, amount_cents, method, booking_id, "
                + "created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", groupId, from, to, amount, method,
                bookingId, by, Timestamp.from(Instant.now(clock)));
    }

    // ---------------------------------------------------------------- Berechnung

    /** Saldo je Mitglied in Cent (positiv: bekommt Geld, negativ: schuldet). Reihenfolge: Beitritt. */
    Map<Long, Long> balances(Long groupId) {
        Map<Long, Long> bal = new LinkedHashMap<>();
        for (Long m : jdbc.queryForList("SELECT user_id FROM expense_group_member WHERE group_id = ? "
                + "ORDER BY joined_at, user_id", Long.class, groupId)) {
            bal.put(m, 0L);
        }
        jdbc.query("SELECT payer_id, SUM(amount_cents) s FROM group_expense WHERE group_id = ? GROUP BY payer_id",
                rs -> { bal.merge(rs.getLong(1), rs.getLong(2), Long::sum); }, groupId);
        jdbc.query("SELECT sh.user_id, SUM(sh.share_cents) FROM group_expense_share sh JOIN group_expense e "
                + "ON e.id = sh.expense_id WHERE e.group_id = ? GROUP BY sh.user_id",
                rs -> { bal.merge(rs.getLong(1), -rs.getLong(2), Long::sum); }, groupId);
        jdbc.query("SELECT from_user, to_user, amount_cents FROM group_settlement WHERE group_id = ?", rs -> {
            bal.merge(rs.getLong(1), rs.getLong(3), Long::sum);
            bal.merge(rs.getLong(2), -rs.getLong(3), Long::sum);
        }, groupId);
        return bal;
    }

    /** Wenige Überweisungen: größter Schuldner zahlt an größten Gläubiger, bis alles aufgeht. */
    static List<Suggestion> suggest(Map<Long, Long> balances) {
        List<long[]> debtors = new ArrayList<>();
        List<long[]> creditors = new ArrayList<>();
        for (var e : balances.entrySet()) {
            if (e.getValue() < 0) {
                debtors.add(new long[] {e.getKey(), -e.getValue()});
            } else if (e.getValue() > 0) {
                creditors.add(new long[] {e.getKey(), e.getValue()});
            }
        }
        Comparator<long[]> byAmount = Comparator.<long[]>comparingLong(a -> -a[1]).thenComparingLong(a -> a[0]);
        debtors.sort(byAmount);
        creditors.sort(byAmount);
        List<Suggestion> list = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            long pay = Math.min(debtors.get(i)[1], creditors.get(j)[1]);
            list.add(new Suggestion(debtors.get(i)[0], null, creditors.get(j)[0], null, pay));
            debtors.get(i)[1] -= pay;
            creditors.get(j)[1] -= pay;
            if (debtors.get(i)[1] == 0) {
                i++;
            }
            if (creditors.get(j)[1] == 0) {
                j++;
            }
        }
        return list;
    }

    private List<Expense> expenses(Long userId, Long groupId, Long ownerId, Map<Long, String> names) {
        Map<Long, List<Share>> shares = new HashMap<>();
        jdbc.query("SELECT sh.expense_id, sh.user_id, sh.share_cents FROM group_expense_share sh JOIN group_expense e "
                + "ON e.id = sh.expense_id WHERE e.group_id = ? ORDER BY sh.user_id", rs -> {
            long uid = rs.getLong(2);
            shares.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                    .add(new Share(uid, name(names, uid), rs.getLong(3)));
        }, groupId);
        return jdbc.query("SELECT id, payer_id, amount_cents, description, expense_date, created_by FROM group_expense "
                + "WHERE group_id = ? ORDER BY expense_date DESC, id DESC LIMIT 200", (rs, n) -> {
            long id = rs.getLong(1);
            long payer = rs.getLong(2);
            List<Share> s = shares.getOrDefault(id, List.of());
            long mine = s.stream().filter(x -> x.userId().equals(userId)).mapToLong(Share::shareCents).sum();
            boolean deletable = userId.equals(ownerId) || userId.equals(payer) || userId.equals(rs.getLong(6));
            return new Expense(id, payer, name(names, payer), rs.getLong(3), rs.getString(4),
                    rs.getObject(5, LocalDate.class), s, deletable, mine);
        }, groupId);
    }

    private List<Settlement> settlements(Long groupId, Map<Long, String> names) {
        return jdbc.query("SELECT id, from_user, to_user, amount_cents, method, created_at FROM group_settlement "
                + "WHERE group_id = ? ORDER BY created_at DESC, id DESC LIMIT 100", (rs, n) -> {
            String method = rs.getString(5);
            return new Settlement(rs.getLong(1), rs.getLong(2), name(names, rs.getLong(2)), rs.getLong(3),
                    name(names, rs.getLong(3)), rs.getLong(4), method,
                    "CASH".equals(method) ? "bar (vom Empfänger bestätigt)" : "per Überweisung",
                    rs.getTimestamp(6).toInstant());
        }, groupId);
    }

    private String name(Map<Long, String> names, Long id) {
        return names.computeIfAbsent(id, accountService::ownerName);
    }

    private long totalSpent(Long groupId) {
        Long s = jdbc.queryForObject("SELECT COALESCE(SUM(amount_cents), 0) FROM group_expense WHERE group_id = ?",
                Long.class, groupId);
        return s == null ? 0 : s;
    }

    private String name(Long groupId) {
        return jdbc.queryForObject("SELECT name FROM expense_group WHERE id = ?", String.class, groupId);
    }

    private boolean isMember(Long groupId, Long userId) {
        Integer c = jdbc.queryForObject("SELECT count(*) FROM expense_group_member WHERE group_id = ? AND user_id = ?",
                Integer.class, groupId, userId);
        return c != null && c > 0;
    }

    /** Fremde Gruppen sind „nicht gefunden“ – so verrät die API nicht, welche IDs existieren. */
    private void requireMember(Long userId, Long groupId) {
        if (!isMember(groupId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Gruppe nicht gefunden.");
        }
    }

    private void requireOwner(Long userId, Long groupId) {
        requireMember(userId, groupId);
        Long ownerId = jdbc.queryForObject("SELECT owner_id FROM expense_group WHERE id = ?", Long.class, groupId);
        if (!userId.equals(ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_GROUP_OWNER", "Nur der Ersteller kann das ändern.");
        }
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 60) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NAME", "Bitte einen Namen (bis 60 Zeichen) angeben.");
        }
        return n;
    }
}
