package de.frauas.girokonto.insights;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.simulation.DailyJob;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.SplittableRandom;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Erzeugt Beispielmonate für Demo-Profile (/F590/).
 * Jede Beispielbuchung hat einen festen Idempotenz-Schlüssel (Nutzer, Datum, Nummer),
 * dadurch entsteht sie garantiert nur einmal, auch wenn ein Tag doppelt verarbeitet würde.
 */
@Service
public class DemoDataService implements DailyJob {

    public static final int SAMPLE_MONTHS = 6;

    private final JdbcTemplate jdbc;
    private final AccountService accountService;
    private final BookingService bookingService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DemoDataService(JdbcTemplate jdbc, AccountService accountService, BookingService bookingService,
                           TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.accountService = accountService;
        this.bookingService = bookingService;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public int order() {
        return 10; // zuerst: Einnahmen und Ausgaben des Tages
    }

    /** Bucht die letzten 6 Monate bis heute und merkt sich das Profil für kommende Monate. */
    @Transactional
    public int createSampleMonths(Long userId, DemoProfile profile) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM demo_profile WHERE user_id = ?", Integer.class, userId);
        if (exists != null && exists > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DEMO_EXISTS", "Für dieses Konto gibt es schon Beispielmonate.");
        }
        jdbc.update("INSERT INTO demo_profile (user_id, profile, created_at) VALUES (?, ?, ?)",
                userId, profile.name(), Timestamp.from(Instant.now(clock)));
        LocalDate today = LocalDate.now(clock);
        int count = 0;
        for (LocalDate d = today.minusMonths(SAMPLE_MONTHS).withDayOfMonth(1); !d.isAfter(today); d = d.plusDays(1)) {
            count += bookDayForUser(userId, profile, d);
        }
        return count;
    }

    public String profileOf(Long userId) {
        List<String> p = jdbc.queryForList("SELECT profile FROM demo_profile WHERE user_id = ?", String.class, userId);
        return p.isEmpty() ? null : p.get(0);
    }

    /** Tageslauf: Buchungen aller Demo-Profile für dieses Datum. */
    @Override
    public void runFor(LocalDate date) {
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("SELECT user_id, profile FROM demo_profile");
        for (var row : rows) {
            Long userId = ((Number) row.get("user_id")).longValue();
            DemoProfile profile = DemoProfile.valueOf((String) row.get("profile"));
            tx.executeWithoutResult(s -> bookDayForUser(userId, profile, date));
        }
    }

    private int bookDayForUser(Long userId, DemoProfile profile, LocalDate date) {
        Account main = accountService.mainAccount(userId);
        List<DemoProfile.Item> items = profile.items();
        int booked = 0;
        for (int idx = 0; idx < items.size(); idx++) {
            DemoProfile.Item item = items.get(idx);
            if (item.day() != date.getDayOfMonth()) {
                continue;
            }
            long cents = amountFor(userId, date, idx, item);
            Instant at = date.atTime(LocalTime.of(8, 0).plusMinutes(idx * 7L)).atZone(clock.getZone()).toInstant();
            String key = "demo:" + userId + ":" + date + ":" + idx;
            switch (item.type()) {
                case INCOME -> bookingService.post(BookingType.INCOME, item.description(), key, null, List.of(
                        new BookingService.Line(accountService.systemAccount("ARBEITGEBER").getId(), -cents),
                        new BookingService.Line(main.getId(), cents)), at);
                case DIRECT_DEBIT -> bookingService.post(BookingType.DIRECT_DEBIT, item.description(), key, null, List.of(
                        new BookingService.Line(main.getId(), -cents),
                        new BookingService.Line(accountService.systemAccount("LASTSCHRIFT").getId(), cents)), at);
                default -> bookingService.post(BookingType.CARD, item.description(), key, null, List.of(
                        new BookingService.Line(main.getId(), -cents),
                        new BookingService.Line(accountService.systemAccount("HAENDLER").getId(), cents)), at);
            }
            booked++;
        }
        return booked;
    }

    /** Variable Beträge schwanken reproduzierbar um ±25 % (fester Startwert aus Nutzer, Monat und Position). */
    static long amountFor(Long userId, LocalDate date, int idx, DemoProfile.Item item) {
        if (!item.variable()) {
            return item.cents();
        }
        long seed = userId * 1_000_003L + date.getYear() * 12L + date.getMonthValue() * 31L + idx;
        double factor = 0.75 + new SplittableRandom(seed).nextDouble() * 0.5;
        return Math.max(100, Math.round(item.cents() * factor));
    }
}
