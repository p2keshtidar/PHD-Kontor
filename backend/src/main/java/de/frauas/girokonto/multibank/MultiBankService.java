package de.frauas.girokonto.multibank;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountDto;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.account.AccountStatus;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.account.ProductInfo;
import de.frauas.girokonto.audit.AuditLog;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.IbanGenerator;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.multibank.MultiBankDtos.CloseResult;
import de.frauas.girokonto.multibank.MultiBankDtos.OpenRequest;
import de.frauas.girokonto.multibank.MultiBankDtos.Product;
import de.frauas.girokonto.multibank.MultiBankDtos.Products;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weitere Konten in der Projektbank (Simulation mit Spielgeld): ein weiteres Girokonto mit eigener IBAN für Eingänge
 * und Tagesgeld mit fiktivem Zinssatz. Geld wird zwischen eigenen Konten umgebucht; Überweisungen an andere Nutzer
 * gehen weiterhin nur vom Haupt-Girokonto.
 */
@Service
public class MultiBankService {

    static final String DISCLAIMER = "Konten der Projektbank sind eine Simulation mit Spielgeld – keine echten Bankkonten, "
            + "keine Einlagensicherung. Der Zinssatz des Tagesgelds ist fiktiv.";

    private final AccountRepository accounts;
    private final AccountService accountService;
    private final BookingService bookings;
    private final IbanGenerator ibanGenerator;
    private final AppProperties appProps;
    private final MultiBankProperties props;
    private final InterestService interest;
    private final NotificationService notifications;
    private final AuditLog audit;
    private final Clock clock;

    public MultiBankService(AccountRepository accounts, AccountService accountService, BookingService bookings,
                            IbanGenerator ibanGenerator, AppProperties appProps, MultiBankProperties props,
                            InterestService interest, NotificationService notifications, AuditLog audit, Clock clock) {
        this.accounts = accounts;
        this.accountService = accountService;
        this.bookings = bookings;
        this.ibanGenerator = ibanGenerator;
        this.appProps = appProps;
        this.props = props;
        this.interest = interest;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Products products(Long userId) {
        int open = extraCount(userId);
        return new Products(List.of(
                new Product("CURRENT", "Weiteres Girokonto",
                        "Eigene IBAN für Eingänge, z. B. als Haushaltskonto. Geld bewegst du per Umbuchung zwischen "
                                + "deinen Konten; Überweisungen an andere gehen nur vom Girokonto.", null, true),
                new Product("CALL", "Tagesgeld",
                        "Zinsen jeden Monat, täglich verfügbar. Ein- und Auszahlungen nur über deine eigenen Konten.",
                        props.callInterestPercent(), true)),
                open, props.maxExtraPerUser(), open < props.maxExtraPerUser(), DISCLAIMER);
    }

    @Transactional
    public AccountDto open(Long userId, OpenRequest req) {
        AccountType type = parseType(req.type());
        // Zeile des Girokontos sperren: zwei gleichzeitige Anfragen können die Grenze nicht gemeinsam überschreiten
        accounts.findByIdForUpdate(accountService.mainAccount(userId).getId()).orElseThrow();
        if (extraCount(userId) >= props.maxExtraPerUser()) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_LIMIT",
                    "Du hast schon " + props.maxExtraPerUser() + " weitere Konten. Löse zuerst eines auf.");
        }
        String name = req.name() == null || req.name().isBlank()
                ? (type == AccountType.CALL ? "Tagesgeld" : "Girokonto 2") : req.name().trim();
        String iban = ibanGenerator.generate(appProps.bank().blz(), accounts.nextAccountNumber());
        Account a = new Account(userId, type, name, null, iban, Instant.now(clock));
        if (type == AccountType.CALL) {
            a.setInterestRate(props.callInterestPercent());
        }
        accounts.save(a);
        audit.record(userId, "ACCOUNT_OPENED", "account:" + a.getId(), type.name());
        notifications.send(userId, NotificationType.INFO, "Konto eröffnet",
                "„" + name + "“ (" + ProductInfo.of(a).productLabel() + ", Simulation) ist bereit.", null);
        return dto(a, 0);
    }

    @Transactional
    public AccountDto rename(Long userId, Long accountId, String name) {
        Account a = requireExtra(userId, accountId);
        a.setName(name.trim());
        return dto(a, bookings.balanceOf(a.getId()));
    }

    /** Tagesgeld: Zinsen bis gestern gutschreiben; Restguthaben aufs Girokonto; Konto schließen. */
    @Transactional
    public CloseResult close(Long userId, Long accountId) {
        Account a = requireExtra(userId, accountId);
        accounts.findByIdForUpdate(a.getId()).orElseThrow();
        LocalDate today = LocalDate.now(clock);
        long interestCents = 0;
        if (a.getType() == AccountType.CALL) {
            YearMonth current = YearMonth.from(today);
            // Vormonat, falls der Monatslauf heute noch nicht gelaufen ist, dann der laufende Monat bis gestern
            interestCents += interest.book(a, current.minusMonths(1), current.minusMonths(1).atEndOfMonth());
            interestCents += interest.book(a, current, today.minusDays(1));
        }
        long balance = bookings.balanceOf(a.getId());
        if (balance > 0) {
            Account main = accountService.mainAccount(userId);
            bookings.post(BookingType.INTERNAL, "Auflösung " + a.getName(), "account-close:" + a.getId(), userId,
                    List.of(new BookingService.Line(a.getId(), -balance), new BookingService.Line(main.getId(), balance)));
        }
        a.setStatus(AccountStatus.CLOSED);
        audit.record(userId, "ACCOUNT_CLOSED", "account:" + a.getId(), a.getType().name());
        String message = balance > 0
                ? "„" + a.getName() + "“ aufgelöst: " + Money.format(balance) + " aufs Girokonto gebucht."
                : "„" + a.getName() + "“ aufgelöst.";
        notifications.send(userId, NotificationType.INTERNAL, "Konto aufgelöst", message, null);
        return new CloseResult(interestCents, Math.max(balance, 0), message);
    }

    private int extraCount(Long userId) {
        return (int) accounts.findByOwnerIdAndStatusOrderByIdAsc(userId, AccountStatus.ACTIVE).stream()
                .filter(x -> x.getType() == AccountType.CURRENT || x.getType() == AccountType.CALL).count();
    }

    private Account requireExtra(Long userId, Long accountId) {
        Account a = accountService.requireOwned(accountId, userId);
        if (a.getStatus() != AccountStatus.ACTIVE
                || (a.getType() != AccountType.CURRENT && a.getType() != AccountType.CALL)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_AN_EXTRA_ACCOUNT",
                    a.getType() == AccountType.MAIN
                            ? "Das Girokonto bleibt bestehen – es kann nicht aufgelöst oder umbenannt werden."
                            : "Das ist kein aktives weiteres Konto der Projektbank.");
        }
        return a;
    }

    private static AccountType parseType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "CURRENT" -> AccountType.CURRENT;
            case "CALL" -> AccountType.CALL;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_TYPE",
                    "Diese Kontoart gibt es nicht. Möglich: weiteres Girokonto oder Tagesgeld.");
        };
    }

    private static AccountDto dto(Account a, long balance) {
        return new AccountDto(a.getId(), a.getType(), a.getName(), a.getIcon(), a.getIban(), balance, false, null, 0,
                null, ProductInfo.of(a));
    }
}
