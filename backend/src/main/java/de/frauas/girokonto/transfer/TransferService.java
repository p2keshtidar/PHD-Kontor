package de.frauas.girokonto.transfer;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.booking.Booking;
import de.frauas.girokonto.booking.BookingLine;
import de.frauas.girokonto.booking.BookingLineRepository;
import de.frauas.girokonto.booking.BookingRepository;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.user.AppUser;
import de.frauas.girokonto.user.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Überweisungen zwischen App-Nutzern (/F210/ bis /F250/).
 *
 * Ablauf von execute():
 * 1. Girokonto des Senders sperren (SELECT ... FOR UPDATE), damit parallele Anfragen nacheinander laufen
 * 2. Idempotenz: wurde dieselbe Anfrage schon gebucht, das alte Ergebnis zurückgeben
 * 3. Empfänger suchen und alle Prüfungen durchführen (/F230/)
 * 4. Über den BookingService buchen (beide Zeilen in einer Transaktion)
 */
@Service
public class TransferService {

    public static final String DEFAULT_DESCRIPTION = "Überweisung";

    private final AccountService accountService;
    private final AccountRepository accounts;
    private final BookingService bookingService;
    private final BookingRepository bookings;
    private final BookingLineRepository bookingLines;
    private final RecipientResolver recipientResolver;
    private final AppUserRepository users;
    private final NotificationService notifications;
    private final AppProperties props;
    private final Clock clock;

    public TransferService(AccountService accountService, AccountRepository accounts, BookingService bookingService,
                           BookingRepository bookings, BookingLineRepository bookingLines,
                           RecipientResolver recipientResolver, AppUserRepository users,
                           NotificationService notifications, AppProperties props, Clock clock) {
        this.accountService = accountService;
        this.accounts = accounts;
        this.bookingService = bookingService;
        this.bookings = bookings;
        this.bookingLines = bookingLines;
        this.recipientResolver = recipientResolver;
        this.users = users;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    /** Prüft alles, bucht aber nichts. Für die Zusammenfassung in der App (/F240/). */
    @Transactional(readOnly = true)
    public TransferCheckDto check(Long userId, TransferRequest req) {
        Account sender = accountService.mainAccount(userId);
        Recipient recipient = recipientResolver.resolve(req.recipient());
        long remaining = validate(userId, sender, recipient, req.amountCents());
        return new TransferCheckDto(recipient.fullName(), recipient.account().getIban(), req.amountCents(),
                description(req), bookingService.balanceOf(sender.getId()), remaining);
    }

    @Transactional
    public TransferResultDto execute(Long userId, String idempotencyKey, TransferRequest req) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_MISSING",
                    "Header Idempotency-Key fehlt oder ist zu lang.");
        }
        Account sender = accounts.findByIdForUpdate(accountService.mainAccount(userId).getId()).orElseThrow();

        String fullKey = "transfer:" + userId + ":" + idempotencyKey;
        Optional<Booking> existing = bookings.findByIdempotencyKey(fullKey);
        if (existing.isPresent()) {
            return resultOf(existing.get(), sender);
        }

        Recipient recipient = recipientResolver.resolve(req.recipient());
        validate(userId, sender, recipient, req.amountCents());

        Booking booking = bookingService.post(BookingType.TRANSFER, description(req), fullKey, userId, List.of(
                new BookingService.Line(sender.getId(), -req.amountCents()),
                new BookingService.Line(recipient.account().getId(), req.amountCents())));

        // Benachrichtigungen (/F510/), werden nach dem Commit live verschickt
        AppUser me = users.findById(userId).orElseThrow();
        String amount = Money.format(req.amountCents());
        notifications.send(userId, NotificationType.TRANSFER_OUT, "Überweisung gesendet",
                "-" + amount + " an " + recipient.fullName(), booking.getId());
        notifications.send(recipient.user().getId(), NotificationType.TRANSFER_IN, "Geld erhalten",
                "+" + amount + " von " + me.getFirstName() + " " + me.getLastName() + ": " + booking.getDescription(),
                booking.getId());
        return resultOf(booking, sender);
    }

    /** @return verbleibendes Tageslimit nach dieser Überweisung */
    private long validate(Long userId, Account sender, Recipient recipient, long amountCents) {
        if (amountCents <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Der Betrag muss größer als 0 sein.");
        }
        if (amountCents > props.transfer().maxAmountCents()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_TOO_HIGH",
                    "Der Betrag ist höher als das Tageslimit.");
        }
        if (recipient.user().getId().equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_TRANSFER",
                    "Überweisungen an dich selbst sind nicht möglich. Nutze dafür die digitalen Konten.");
        }
        long balance = bookingService.balanceOf(sender.getId());
        if (balance < amountCents) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS",
                    "Nicht genug Guthaben. Verfügbar: " + formatEuro(balance) + ".");
        }
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        long usedToday = -bookingLines.sumOutgoingSince(sender.getId(), BookingType.TRANSFER, startOfDay);
        long remaining = props.transfer().dailyLimitCents() - usedToday - amountCents;
        if (remaining < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DAILY_LIMIT_EXCEEDED",
                    "Tageslimit überschritten. Heute noch möglich: "
                            + formatEuro(Math.max(0, props.transfer().dailyLimitCents() - usedToday)) + ".");
        }
        return remaining;
    }

    private TransferResultDto resultOf(Booking booking, Account sender) {
        List<BookingLine> lines = bookingLines.findByBookingId(booking.getId());
        BookingLine senderLine = lines.stream().filter(l -> l.getAccountId().equals(sender.getId()))
                .findFirst().orElseThrow();
        BookingLine recipientLine = lines.stream().filter(l -> !l.getAccountId().equals(sender.getId()))
                .findFirst().orElseThrow();
        Account recipientAccount = accounts.findById(recipientLine.getAccountId()).orElseThrow();
        AppUser recipientUser = users.findById(recipientAccount.getOwnerId()).orElseThrow();
        return new TransferResultDto(
                booking.getId(),
                recipientUser.getFirstName() + " " + recipientUser.getLastName(),
                recipientAccount.getIban(),
                recipientUser.getId(),
                -senderLine.getAmountCents(),
                booking.getDescription(),
                booking.getBookedAt(),
                bookingService.balanceOf(sender.getId()));
    }

    private static String description(TransferRequest req) {
        return req.description() == null || req.description().isBlank()
                ? DEFAULT_DESCRIPTION : req.description().trim();
    }

    static String formatEuro(long cents) {
        return Money.format(cents);
    }
}
