package de.frauas.girokonto.subaccount;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountMemberRepository;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.account.AccountStatus;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.audit.AuditLog;
import de.frauas.girokonto.booking.BookingLineRepository;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.card.CardService;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo-Karte für ein digitales Konto (z. B. „Lebensmittel“ oder WG-Kasse): Zahlungen gehen direkt vom digitalen Konto ab.
 * Virtuell und ohne Kartennummer – es wird keine Karte bestellt oder ausgegeben. Zahlen darf, wer auszahlen darf
 * (Besitzer oder Mitglied mit vollen Rechten).
 */
@Service
public class SubaccountCardService {

    public static final String NOTE = "Demo: virtuelle Karte für dieses digitale Konto – keine echte Karte, "
            + "Zahlungen nur mit Spielgeld bei Demo-Händlern.";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record View(boolean exists, String last4, String status, long dailyLimitCents, long spentTodayCents,
                       boolean canPay, String note) {}
    public record PaymentResult(boolean approved, String message, long amountCents, String merchant) {}

    private final JdbcTemplate jdbc;
    private final AccountService accountService;
    private final AccountRepository accounts;
    private final AccountMemberRepository members;
    private final BookingService bookings;
    private final BookingLineRepository bookingLines;
    private final NotificationService notifications;
    private final AuditLog audit;
    private final Clock clock;

    public SubaccountCardService(JdbcTemplate jdbc, AccountService accountService, AccountRepository accounts,
                                 AccountMemberRepository members, BookingService bookings,
                                 BookingLineRepository bookingLines, NotificationService notifications, AuditLog audit,
                                 Clock clock) {
        this.jdbc = jdbc;
        this.accountService = accountService;
        this.accounts = accounts;
        this.members = members;
        this.bookings = bookings;
        this.bookingLines = bookingLines;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    private Account requireSub(Long userId, Long accountId) {
        Account a = accountService.requireAccess(accountId, userId);
        if (a.getType() != AccountType.SUB || a.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_SUBACCOUNT", "Das ist kein aktives digitales Konto.");
        }
        return a;
    }

    private boolean canPay(Account a, Long userId) {
        return userId.equals(a.getOwnerId()) || "FULL".equals(members.role(a.getId(), userId));
    }

    private Account requirePayer(Long userId, Long accountId) {
        Account a = requireSub(userId, accountId);
        if (!canPay(a, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_NO_WITHDRAW", "Du darfst aus „" + a.getName() + "“ nichts auszahlen.");
        }
        return a;
    }

    private long spentToday(Long accountId) {
        Instant start = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        return -bookingLines.sumOutgoingSince(accountId, BookingType.CARD, start);
    }

    @Transactional(readOnly = true)
    public View view(Long userId, Long accountId) {
        Account a = requireSub(userId, accountId);
        List<View> l = jdbc.query("SELECT last4, status, daily_limit_cents FROM subaccount_card WHERE account_id = ?",
                (rs, n) -> new View(true, rs.getString(1), rs.getString(2), rs.getLong(3), spentToday(accountId),
                        canPay(a, userId), NOTE), accountId);
        return l.isEmpty() ? new View(false, null, null, 0, 0, canPay(a, userId), NOTE) : l.get(0);
    }

    @Transactional
    public View create(Long userId, Long accountId, long dailyLimitCents) {
        Account a = requirePayer(userId, accountId);
        checkLimit(dailyLimitCents);
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM subaccount_card WHERE account_id = ?", Integer.class, accountId);
        if (exists != null && exists > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CARD_EXISTS", "Für dieses digitale Konto gibt es schon eine Karte.");
        }
        String last4 = String.format("%04d", RANDOM.nextInt(10_000));
        jdbc.update("INSERT INTO subaccount_card (account_id, created_by, last4, status, daily_limit_cents, created_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)", accountId, userId, last4, dailyLimitCents, Timestamp.from(Instant.now(clock)));
        audit.record(userId, "SUBACCOUNT_CARD_CREATED", "account:" + a.getId(), null);
        return view(userId, accountId);
    }

    @Transactional
    public View lock(Long userId, Long accountId, boolean locked) {
        requirePayer(userId, accountId);
        requireCard(accountId);
        jdbc.update("UPDATE subaccount_card SET status = ? WHERE account_id = ?", locked ? "LOCKED" : "ACTIVE", accountId);
        return view(userId, accountId);
    }

    @Transactional
    public View limit(Long userId, Long accountId, long dailyLimitCents) {
        requirePayer(userId, accountId);
        requireCard(accountId);
        checkLimit(dailyLimitCents);
        jdbc.update("UPDATE subaccount_card SET daily_limit_cents = ? WHERE account_id = ?", dailyLimitCents, accountId);
        return view(userId, accountId);
    }

    @Transactional
    public void delete(Long userId, Long accountId) {
        requirePayer(userId, accountId);
        requireCard(accountId);
        jdbc.update("DELETE FROM subaccount_card WHERE account_id = ?", accountId);
    }

    /** Demo-Zahlung direkt vom digitalen Konto: gesperrt → Tageslimit → Guthaben. */
    @Transactional
    public PaymentResult pay(Long userId, Long accountId, String merchant, long amountCents) {
        Account a = requirePayer(userId, accountId);
        String m = merchant == null || merchant.isBlank() ? "Demo-Händler" : merchant.trim();
        if (m.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MERCHANT", "Der Händlername darf höchstens 100 Zeichen haben.");
        }
        if (amountCents <= 0 || amountCents > 200_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Der Betrag muss zwischen 0,01 € und 2.000 € liegen.");
        }
        accounts.findByIdForUpdate(accountId).orElseThrow();      // parallele Zahlungen nacheinander prüfen
        List<String[]> card = jdbc.query("SELECT status, daily_limit_cents FROM subaccount_card WHERE account_id = ?",
                (rs, n) -> new String[] {rs.getString(1), rs.getString(2)}, accountId);
        if (card.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "Für dieses digitale Konto gibt es keine Karte.");
        }
        String message = null;
        long limit = Long.parseLong(card.get(0)[1]);
        long spent = spentToday(accountId);
        if ("LOCKED".equals(card.get(0)[0])) {
            message = "Karte gesperrt";
        } else if (spent + amountCents > limit) {
            message = "Tageslimit überschritten (noch " + Money.format(Math.max(0, limit - spent)) + " verfügbar)";
        } else if (bookings.balanceOf(accountId) < amountCents) {
            message = "Nicht genug Guthaben";
        }
        if (message != null) {
            notifications.send(userId, NotificationType.CARD_DECLINED, "Kartenzahlung abgelehnt",
                    Money.format(amountCents) + " bei " + m + ": " + message, null);
            return new PaymentResult(false, message, amountCents, m);
        }
        Account merchantAccount = accountService.systemAccount(CardService.SYSTEM_MERCHANT);
        var booking = bookings.post(BookingType.CARD, m, null, userId, List.of(
                new BookingService.Line(accountId, -amountCents),
                new BookingService.Line(merchantAccount.getId(), amountCents)));
        notifications.send(userId, NotificationType.CARD_PAYMENT, "Kartenzahlung",
                "-" + Money.format(amountCents) + " bei " + m + " (" + a.getName() + ")", booking.getId());
        return new PaymentResult(true, "Zahlung erfolgreich", amountCents, m);
    }

    private void requireCard(Long accountId) {
        Integer c = jdbc.queryForObject("SELECT count(*) FROM subaccount_card WHERE account_id = ?", Integer.class, accountId);
        if (c == null || c == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "Für dieses digitale Konto gibt es keine Karte.");
        }
    }

    private static void checkLimit(long cents) {
        if (cents < 100 || cents > 200_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "Das Tageslimit muss zwischen 1 € und 2.000 € liegen.");
        }
    }
}
