package de.frauas.girokonto.card;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.booking.Booking;
import de.frauas.girokonto.booking.BookingLineRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Virtuelle Karte: anzeigen, sperren, Limits und Zahlungen über den Händler-Simulator (/F310/ bis /F340/). */
@Service
public class CardService {

    public static final String SYSTEM_MERCHANT = "HAENDLER";

    private final CardRepository cards;
    private final CardCrypto crypto;
    private final AccountService accountService;
    private final AccountRepository accounts;
    private final BookingService bookingService;
    private final BookingLineRepository bookingLines;
    private final NotificationService notifications;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final Clock clock;

    public CardService(CardRepository cards, CardCrypto crypto, AccountService accountService,
                       AccountRepository accounts, BookingService bookingService,
                       BookingLineRepository bookingLines, NotificationService notifications,
                       AppUserRepository users, PasswordEncoder passwordEncoder, AppProperties props, Clock clock) {
        this.cards = cards;
        this.crypto = crypto;
        this.accountService = accountService;
        this.accounts = accounts;
        this.bookingService = bookingService;
        this.bookingLines = bookingLines;
        this.notifications = notifications;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.clock = clock;
    }

    /** Gibt die Karte des Nutzers zurück und legt sie an, falls sie noch fehlt (z. B. Nutzer aus Phase 1/2). */
    @Transactional
    public Card getOrCreate(Long userId) {
        return cards.findByUserId(userId).orElseGet(() -> issue(userId));
    }

    private Card issue(Long userId) {
        Account main = accountService.mainAccount(userId);
        String pan = CardNumberGenerator.generate(props.card().bin());
        LocalDate expiry = LocalDate.now(clock).plusYears(4);
        return cards.save(new Card(userId, main.getId(), crypto.encrypt(pan), pan.substring(12),
                crypto.encrypt(CardNumberGenerator.randomCvc()), expiry.getMonthValue(), expiry.getYear(),
                props.card().defaultDailyLimitCents(), Instant.now(clock)));
    }

    @Transactional
    public CardDto view(Long userId) {
        return toDto(getOrCreate(userId));
    }

    @Transactional
    public CardDetailsDto details(Long userId, String password) {
        AppUser user = users.findById(userId).orElseThrow();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Das Passwort ist falsch.");
        }
        Card card = getOrCreate(userId);
        return new CardDetailsDto(crypto.decrypt(card.getPanEncrypted()), crypto.decrypt(card.getCvcEncrypted()),
                expiry(card));
    }

    @Transactional
    public CardDto setLocked(Long userId, boolean locked) {
        Card card = getOrCreate(userId);
        card.setStatus(locked ? CardStatus.LOCKED : CardStatus.ACTIVE);
        return toDto(card);
    }

    @Transactional
    public CardDto setLimits(Long userId, CardRequests.Limits req) {
        if (req.dailyLimitCents() > props.card().maxDailyLimitCents()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LIMIT_TOO_HIGH",
                    "Das Tageslimit darf höchstens " + Money.format(props.card().maxDailyLimitCents()) + " betragen.");
        }
        Card card = getOrCreate(userId);
        card.setDailyLimitCents(req.dailyLimitCents());
        card.setOnlineEnabled(req.onlineEnabled());
        return toDto(card);
    }

    /**
     * Simulierte Kartenzahlung bei einem fiktiven Händler (/F340/).
     * Prüfreihenfolge: gesperrt -> online erlaubt -> Tageslimit -> Deckung.
     */
    @Transactional
    public CardPaymentResultDto pay(Long userId, CardRequests.Payment req) {
        Card card = getOrCreate(userId);
        // Konto sperren, damit parallele Zahlungen nacheinander geprüft werden
        Account account = accounts.findByIdForUpdate(card.getAccountId()).orElseThrow();
        String merchant = req.merchant().trim();
        long amount = req.amountCents();

        String reason = null;
        String message = null;
        long spentToday = spentToday(card);
        if (card.getStatus() == CardStatus.LOCKED) {
            reason = "CARD_LOCKED";
            message = "Karte gesperrt";
        } else if (req.online() && !card.isOnlineEnabled()) {
            reason = "ONLINE_DISABLED";
            message = "Online-Zahlungen sind ausgeschaltet";
        } else if (spentToday + amount > card.getDailyLimitCents()) {
            reason = "DAILY_LIMIT";
            message = "Tageslimit überschritten (noch " + Money.format(Math.max(0, card.getDailyLimitCents() - spentToday))
                    + " verfügbar)";
        } else if (bookingService.balanceOf(account.getId()) < amount) {
            reason = "INSUFFICIENT_FUNDS";
            message = "Nicht genug Guthaben";
        }

        if (reason != null) {
            notifications.send(userId, NotificationType.CARD_DECLINED, "Kartenzahlung abgelehnt",
                    Money.format(amount) + " bei " + merchant + ": " + message, null);
            return new CardPaymentResultDto(false, reason, message, null, amount, merchant);
        }

        Account merchantAccount = accountService.systemAccount(SYSTEM_MERCHANT);
        Booking booking = bookingService.post(BookingType.CARD, merchant, null, userId, List.of(
                new BookingService.Line(account.getId(), -amount),
                new BookingService.Line(merchantAccount.getId(), amount)));
        notifications.send(userId, NotificationType.CARD_PAYMENT, "Kartenzahlung",
                "-" + Money.format(amount) + " bei " + merchant, booking.getId());
        return new CardPaymentResultDto(true, null, "Zahlung erfolgreich", booking.getId(), amount, merchant);
    }

    private long spentToday(Card card) {
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        return -bookingLines.sumOutgoingSince(card.getAccountId(), BookingType.CARD, startOfDay);
    }

    private CardDto toDto(Card card) {
        AppUser user = users.findById(card.getUserId()).orElseThrow();
        return new CardDto(card.getId(),
                (user.getFirstName() + " " + user.getLastName()).toUpperCase(),
                "•••• •••• •••• " + card.getPanLast4(),
                card.getPanLast4(),
                expiry(card),
                card.getStatus(),
                card.getDailyLimitCents(),
                props.card().maxDailyLimitCents(),
                card.isOnlineEnabled(),
                spentToday(card));
    }

    private static String expiry(Card card) {
        return String.format("%02d/%02d", card.getExpiryMonth(), card.getExpiryYear() % 100);
    }
}
