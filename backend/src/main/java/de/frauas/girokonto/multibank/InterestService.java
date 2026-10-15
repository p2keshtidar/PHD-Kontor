package de.frauas.girokonto.multibank;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.account.AccountStatus;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.simulation.DailyJob;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Zinsen für Tagesgeldkonten (Simulation, fiktiver Zinssatz). Tageslauf Nr. 45: am Monatsersten werden die Zinsen
 * des Vormonats gutgeschrieben – genau einmal je Konto und Monat (Idempotenz-Schlüssel {@code interest:<Konto>:<Monat>}).
 */
@Service
public class InterestService implements DailyJob {

    private static final Logger log = LoggerFactory.getLogger(InterestService.class);
    static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final BookingService bookings;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final Clock clock;

    public InterestService(JdbcTemplate jdbc, AccountRepository accounts, AccountService accountService,
                           BookingService bookings, NotificationService notifications, TransactionTemplate tx,
                           Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.accountService = accountService;
        this.bookings = bookings;
        this.notifications = notifications;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public int order() {
        return 45;
    }

    @Override
    public void runFor(LocalDate date) {
        if (date.getDayOfMonth() != 1) {
            return;
        }
        YearMonth month = YearMonth.from(date.minusDays(1));
        List<Long> ids = jdbc.queryForList("SELECT id FROM account WHERE type = 'CALL' AND status = 'ACTIVE' ORDER BY id",
                Long.class);
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(s -> {
                    Account a = accounts.findByIdForUpdate(id).orElseThrow();
                    if (a.getStatus() == AccountStatus.ACTIVE) {
                        book(a, month, month.atEndOfMonth());
                    }
                });
            } catch (RuntimeException e) {
                log.error("Zinsen für Konto {} im Monat {} nicht gebucht", id, month, e);
            }
        }
    }

    /**
     * Zinsen für einen Monat bis einschließlich {@code lastDay} berechnen und gutschreiben (einmalig je Monat).
     * Das Konto muss gesperrt sein (Aufrufer).
     *
     * @return gutgeschriebene Zinsen in Cent (0, wenn nichts anfällt oder schon gebucht)
     */
    long book(Account a, YearMonth month, LocalDate lastDay) {
        if (a.getType() != AccountType.CALL || a.getInterestRate() == null || a.getInterestRate().signum() <= 0) {
            return 0;
        }
        String key = "interest:" + a.getId() + ":" + month;
        Integer done = jdbc.queryForObject("SELECT count(*) FROM booking WHERE idempotency_key = ?", Integer.class, key);
        if (done != null && done > 0) {
            return 0;
        }
        LocalDate opened = LocalDate.ofInstant(a.getCreatedAt(), clock.getZone());
        LocalDate from = month.atDay(1).isBefore(opened) ? opened : month.atDay(1);
        LocalDate to = lastDay.isAfter(month.atEndOfMonth()) ? month.atEndOfMonth() : lastDay;
        if (to.isBefore(from)) {
            return 0;
        }
        long cents = InterestMath.interestCents(dailyDeltas(a.getId(), to), from, to, a.getInterestRate());
        if (cents <= 0) {
            return 0;
        }
        Account bank = accountService.systemAccount(AccountService.SYSTEM_BANK);
        var booking = bookings.post(BookingType.INTEREST, "Zinsen " + a.getName() + " " + month.format(MONTH)
                + " (Simulation)", key, null, List.of(
                new BookingService.Line(bank.getId(), -cents),
                new BookingService.Line(a.getId(), cents)));
        notifications.send(a.getOwnerId(), NotificationType.INFO, "Zinsen gutgeschrieben",
                "„" + a.getName() + "“: " + Money.format(cents) + " für " + month.format(MONTH)
                        + " (Simulation, fiktiver Zinssatz)", booking.getId());
        return cents;
    }

    /** Summe der Buchungen je Kalendertag (Zeitzone der Simulation) bis einschließlich {@code to}. */
    TreeMap<LocalDate, Long> dailyDeltas(Long accountId, LocalDate to) {
        TreeMap<LocalDate, Long> deltas = new TreeMap<>();
        Timestamp end = Timestamp.from(to.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
        jdbc.query("""
                SELECT (b.booked_at AT TIME ZONE ?)::date AS d, SUM(l.amount_cents) AS s
                  FROM booking_line l JOIN booking b ON b.id = l.booking_id
                 WHERE l.account_id = ? AND b.booked_at < ?
                 GROUP BY 1
                """, rs -> {
            deltas.put(rs.getDate("d").toLocalDate(), rs.getLong("s"));
        }, clock.getZone().getId(), accountId, end);
        return deltas;
    }
}
