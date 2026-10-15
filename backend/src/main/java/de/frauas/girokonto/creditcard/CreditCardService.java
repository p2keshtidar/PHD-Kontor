package de.frauas.girokonto.creditcard;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.audit.AuditLog;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.simulation.DailyJob;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Virtuelle Kreditkarte als Simulation mit Spielgeld: Limit, sperren, Demo-Zahlungen (optional in Fremdwährung mit
 * Auslandseinsatzentgelt) und monatliche Abrechnung, die am Monatsersten vom Girokonto eingezogen wird (Tageslauf
 * Nr. 60). Es wird keine echte Karte bestellt oder ausgestellt und keine Kreditprüfung gemacht.
 */
@Service
public class CreditCardService implements DailyJob {

    static final long MIN_LIMIT = 10_000;
    static final long MAX_LIMIT = 500_000;
    static final BigDecimal FOREIGN_FEE = new BigDecimal("1.75");
    static final String NOTE = "Simulation: virtuelle Kreditkarte mit Spielgeld – keine echte Karte, keine Kreditprüfung, "
            + "keine Bestellung.";

    public record Tx(Long id, Instant bookedAt, String merchant, long amountCents, long feeCents, boolean foreign) {}

    public record Statement(Long id, String period, long totalCents, long feeCents, String status, Instant paidAt) {}

    /**
     * @param openCents Umsätze und Gebühren seit der letzten Abrechnung (inkl. nicht eingezogener Abrechnungen)
     */
    public record View(boolean exists, Long id, String maskedNumber, String status, long limitCents, long openCents,
                       long availableCents, BigDecimal foreignFeePercent, List<Tx> transactions,
                       List<Statement> statements, String note, boolean simulation) {}

    public record PaymentResult(boolean approved, String message, long amountCents, long feeCents) {}

    private record Card(long id, long userId, String status, long limit, BigDecimal fee, String masked) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final AccountRepository accountRepo;
    private final BookingService bookings;
    private final NotificationService notifications;
    private final AuditLog audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CreditCardService(JdbcTemplate jdbc, AccountService accounts, AccountRepository accountRepo,
                             BookingService bookings, NotificationService notifications, AuditLog audit,
                             TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.accountRepo = accountRepo;
        this.bookings = bookings;
        this.notifications = notifications;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    private Card card(Long userId) {
        List<Card> c = jdbc.query("SELECT * FROM credit_card WHERE user_id = ? AND status <> 'CLOSED'",
                (rs, i) -> new Card(rs.getLong("id"), rs.getLong("user_id"), rs.getString("status"),
                        rs.getLong("limit_cents"), rs.getBigDecimal("foreign_fee_percent"), rs.getString("masked_number")),
                userId);
        return c.isEmpty() ? null : c.get(0);
    }

    private Card require(Long userId) {
        Card c = card(userId);
        if (c == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_CREDIT_CARD", "Du hast noch keine Kreditkarte (Simulation).");
        }
        return c;
    }

    private long open(long cardId) {
        Long unbilled = jdbc.queryForObject("SELECT COALESCE(SUM(amount_cents + fee_cents), 0) FROM credit_card_tx"
                + " WHERE card_id = ? AND statement_id IS NULL", Long.class, cardId);
        Long unpaid = jdbc.queryForObject("SELECT COALESCE(SUM(total_cents), 0) FROM credit_card_statement"
                + " WHERE card_id = ? AND status = 'OPEN'", Long.class, cardId);
        return (unbilled == null ? 0 : unbilled) + (unpaid == null ? 0 : unpaid);
    }

    @Transactional(readOnly = true)
    public View view(Long userId) {
        Card c = card(userId);
        if (c == null) {
            return new View(false, null, null, null, 0, 0, 0, FOREIGN_FEE, List.of(), List.of(), NOTE, true);
        }
        long open = open(c.id());
        List<Tx> txs = jdbc.query("SELECT * FROM credit_card_tx WHERE card_id = ? AND statement_id IS NULL"
                + " ORDER BY booked_at DESC, id DESC", (rs, i) -> new Tx(rs.getLong("id"),
                rs.getTimestamp("booked_at").toInstant(), rs.getString("merchant"), rs.getLong("amount_cents"),
                rs.getLong("fee_cents"), rs.getBoolean("foreign_currency")), c.id());
        List<Statement> st = jdbc.query("SELECT * FROM credit_card_statement WHERE card_id = ? ORDER BY period DESC"
                + " LIMIT 6", (rs, i) -> new Statement(rs.getLong("id"), rs.getString("period"), rs.getLong("total_cents"),
                rs.getLong("fee_cents"), rs.getString("status"),
                rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toInstant()), c.id());
        return new View(true, c.id(), c.masked(), c.status(), c.limit(), open, Math.max(0, c.limit() - open), c.fee(),
                txs, st, NOTE, true);
    }

    private static void checkLimit(long limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "Das Limit muss zwischen "
                    + Money.format(MIN_LIMIT) + " und " + Money.format(MAX_LIMIT) + " liegen.");
        }
    }

    @Transactional
    public View create(Long userId, long limit) {
        checkLimit(limit);
        if (card(userId) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "CREDIT_CARD_EXISTS", "Du hast schon eine Kreditkarte (Simulation).");
        }
        String masked = "•••• " + String.format("%04d", random.nextInt(10_000));
        jdbc.update("INSERT INTO credit_card (user_id, masked_number, limit_cents, foreign_fee_percent, created_at)"
                + " VALUES (?, ?, ?, ?, ?)", userId, masked, limit, FOREIGN_FEE, Timestamp.from(Instant.now(clock)));
        audit.record(userId, "CREDIT_CARD_CREATED", null, "Limit " + limit);
        return view(userId);
    }

    @Transactional
    public View limit(Long userId, long limit) {
        checkLimit(limit);
        Card c = require(userId);
        jdbc.update("UPDATE credit_card SET limit_cents = ? WHERE id = ?", limit, c.id());
        return view(userId);
    }

    @Transactional
    public View lock(Long userId, boolean locked) {
        Card c = require(userId);
        jdbc.update("UPDATE credit_card SET status = ? WHERE id = ?", locked ? "BLOCKED" : "ACTIVE", c.id());
        audit.record(userId, locked ? "CREDIT_CARD_LOCKED" : "CREDIT_CARD_UNLOCKED", "credit_card:" + c.id(), null);
        return view(userId);
    }

    /** Demo-Zahlung beim Händler-Simulator. */
    @Transactional
    public PaymentResult pay(Long userId, String merchant, long amount, boolean foreign) {
        Card c = require(userId);
        jdbc.queryForObject("SELECT id FROM credit_card WHERE id = ? FOR UPDATE", Long.class, c.id());
        String m = merchant == null || merchant.isBlank() ? "Händler" : merchant.trim();
        long fee = foreign ? BigDecimal.valueOf(amount).multiply(c.fee())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_EVEN).longValueExact() : 0;
        if (!c.status().equals("ACTIVE")) {
            return declined(userId, m, amount, "Karte gesperrt");
        }
        long available = c.limit() - open(c.id());
        if (amount + fee > available) {
            return declined(userId, m, amount, "Verfügungsrahmen reicht nicht (frei: " + Money.format(Math.max(0, available)) + ")");
        }
        jdbc.update("INSERT INTO credit_card_tx (card_id, booked_at, merchant, amount_cents, fee_cents, foreign_currency)"
                + " VALUES (?, ?, ?, ?, ?, ?)", c.id(), Timestamp.from(Instant.now(clock)), m, amount, fee, foreign);
        notifications.send(userId, NotificationType.CARD_PAYMENT, "Kreditkarte (Simulation)",
                "-" + Money.format(amount) + " bei " + m + (fee > 0 ? " + " + Money.format(fee) + " Auslandseinsatz" : ""),
                null);
        return new PaymentResult(true, "Zahlung erfolgreich (Simulation)", amount, fee);
    }

    private PaymentResult declined(Long userId, String merchant, long amount, String reason) {
        notifications.send(userId, NotificationType.CARD_DECLINED, "Kreditkarte abgelehnt (Simulation)",
                Money.format(amount) + " bei " + merchant + ": " + reason, null);
        return new PaymentResult(false, reason, amount, 0);
    }

    /** Karte schließen: offene Umsätze werden sofort abgerechnet. */
    @Transactional
    public View close(Long userId) {
        Card c = require(userId);
        settle(c, YearMonth.now(clock), true);
        if (open(c.id()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CREDIT_CARD_OPEN",
                    "Die offene Abrechnung konnte nicht eingezogen werden – bitte Girokonto aufladen.");
        }
        jdbc.update("UPDATE credit_card SET status = 'CLOSED' WHERE id = ?", c.id());
        audit.record(userId, "CREDIT_CARD_CLOSED", "credit_card:" + c.id(), null);
        return view(userId);
    }

    @Override
    public int order() {
        return 60;
    }

    /** Am Monatsersten: Abrechnung für den Vormonat erstellen; offene Abrechnungen täglich erneut einziehen. */
    @Override
    public void runFor(LocalDate date) {
        for (Long id : jdbc.queryForList("SELECT id FROM credit_card WHERE status <> 'CLOSED' ORDER BY id", Long.class)) {
            tx.executeWithoutResult(s -> {
                Card c = jdbc.query("SELECT * FROM credit_card WHERE id = ? FOR UPDATE", rs -> rs.next()
                        ? new Card(rs.getLong("id"), rs.getLong("user_id"), rs.getString("status"),
                                rs.getLong("limit_cents"), rs.getBigDecimal("foreign_fee_percent"),
                                rs.getString("masked_number")) : null, id);
                if (c != null) {
                    settle(c, YearMonth.from(date).minusMonths(1), date.getDayOfMonth() == 1);
                }
            });
        }
    }

    /**
     * @param createStatement neue Abrechnung aus noch nicht abgerechneten Umsätzen bis Ende {@code period} anlegen
     */
    void settle(Card c, YearMonth period, boolean createStatement) {
        if (createStatement) {
            Timestamp end = Timestamp.from(period.plusMonths(1).atDay(1).atStartOfDay(clock.getZone()).toInstant());
            long[] sums = jdbc.query("SELECT COALESCE(SUM(amount_cents + fee_cents), 0) AS t, COALESCE(SUM(fee_cents), 0) AS f"
                    + " FROM credit_card_tx WHERE card_id = ? AND statement_id IS NULL AND booked_at < ?",
                    rs -> {
                        rs.next();
                        return new long[] {rs.getLong("t"), rs.getLong("f")};
                    }, c.id(), end);
            if (sums[0] > 0) {
                Long st = jdbc.query("""
                        INSERT INTO credit_card_statement (card_id, period, total_cents, fee_cents, status, created_at)
                        VALUES (?, ?, ?, ?, 'OPEN', ?) ON CONFLICT (card_id, period) DO NOTHING RETURNING id
                        """, rs -> rs.next() ? rs.getLong(1) : null, c.id(), period.toString(), sums[0], sums[1],
                        Timestamp.from(Instant.now(clock)));
                if (st != null) {
                    jdbc.update("UPDATE credit_card_tx SET statement_id = ? WHERE card_id = ? AND statement_id IS NULL"
                            + " AND booked_at < ?", st, c.id(), end);
                }
            }
        }
        // offene Abrechnungen einziehen, wenn das Girokonto reicht (kein Dispo in der Simulation)
        record Open(long id, String period, long total) {}
        List<Open> open = jdbc.query("SELECT id, period, total_cents FROM credit_card_statement WHERE card_id = ?"
                + " AND status = 'OPEN' ORDER BY period", (rs, i) -> new Open(rs.getLong("id"), rs.getString("period"),
                rs.getLong("total_cents")), c.id());
        if (open.isEmpty()) {
            return;
        }
        Account main = accountRepo.findByIdForUpdate(accounts.mainAccount(c.userId()).getId()).orElseThrow();
        for (Open o : open) {
            long balance = bookings.balanceOf(main.getId());
            String label = YearMonth.parse(o.period()).format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy"));
            if (balance < o.total()) {
                notifications.send(c.userId(), NotificationType.INFO, "Kreditkartenabrechnung offen",
                        "Abrechnung " + label + " über " + Money.format(o.total())
                                + " konnte nicht eingezogen werden – nicht genug Guthaben auf dem Girokonto.", null);
                return;
            }
            Account bank = accounts.systemAccount(AccountService.SYSTEM_BANK);
            var booking = bookings.post(BookingType.CARD_STATEMENT, "Kreditkartenabrechnung " + label + " (Simulation)",
                    "cc-statement:" + o.id(), c.userId(), List.of(
                            new BookingService.Line(main.getId(), -o.total()),
                            new BookingService.Line(bank.getId(), o.total())));
            jdbc.update("UPDATE credit_card_statement SET status = 'PAID', paid_at = ?, booking_id = ? WHERE id = ?",
                    Timestamp.from(Instant.now(clock)), booking.getId(), o.id());
            notifications.send(c.userId(), NotificationType.INFO, "Kreditkartenabrechnung",
                    "Abrechnung " + label + ": " + Money.format(o.total()) + " vom Girokonto eingezogen (Simulation).",
                    booking.getId());
        }
    }
}
