package de.frauas.girokonto.subaccount;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountDto;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.account.AccountStatus;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unterkonten ("Töpfe") anlegen, Geld umbuchen und auflösen (/F410/ bis /F430/).
 * Die Summe aller eigenen Konten bleibt beim Umbuchen immer gleich.
 */
@Service
public class SubaccountService {

    private final AccountRepository accounts;
    private final AccountService accountService;
    private final BookingService bookingService;
    private final NotificationService notifications;
    private final AppProperties props;
    private final Clock clock;

    public SubaccountService(AccountRepository accounts, AccountService accountService, BookingService bookingService,
                             NotificationService notifications, AppProperties props, Clock clock) {
        this.accounts = accounts;
        this.accountService = accountService;
        this.bookingService = bookingService;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public AccountDto create(Long userId, SubaccountRequests.Save req) {
        long count = accounts.findByOwnerIdAndStatusOrderByIdAsc(userId, AccountStatus.ACTIVE).stream()
                .filter(a -> a.getType() == AccountType.SUB).count();
        if (count >= props.subaccounts().maxPerUser()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_SUBACCOUNTS",
                    "Höchstens " + props.subaccounts().maxPerUser() + " Unterkonten möglich.");
        }
        Account a = accounts.save(new Account(userId, AccountType.SUB, req.name().trim(), iconOrDefault(req.icon()),
                null, Instant.now(clock)));
        return new AccountDto(a.getId(), a.getType(), a.getName(), a.getIcon(), null, 0);
    }

    @Transactional
    public AccountDto rename(Long userId, Long accountId, SubaccountRequests.Save req) {
        Account a = requireSub(userId, accountId);
        a.setName(req.name().trim());
        a.setIcon(iconOrDefault(req.icon()));
        return new AccountDto(a.getId(), a.getType(), a.getName(), a.getIcon(), null, bookingService.balanceOf(a.getId()));
    }

    /** Umbuchen zwischen Girokonto und Unterkonten (in beide Richtungen und zwischen Unterkonten). */
    @Transactional
    public void move(Long userId, SubaccountRequests.Move req) {
        if (req.fromAccountId().equals(req.toAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT", "Quelle und Ziel sind gleich.");
        }
        Account from = requireMovable(userId, req.fromAccountId());
        Account to = requireMovable(userId, req.toAccountId());
        accounts.findByIdForUpdate(from.getId()).orElseThrow();
        long balance = bookingService.balanceOf(from.getId());
        if (balance < req.amountCents()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS",
                    "Nicht genug Guthaben auf „" + from.getName() + "“. Verfügbar: " + Money.format(balance) + ".");
        }
        var booking = bookingService.post(BookingType.INTERNAL, "Umbuchung " + from.getName() + " → " + to.getName(),
                null, userId, List.of(
                        new BookingService.Line(from.getId(), -req.amountCents()),
                        new BookingService.Line(to.getId(), req.amountCents())));
        notifications.send(userId, NotificationType.INTERNAL, "Umbuchung",
                Money.format(req.amountCents()) + " von „" + from.getName() + "“ nach „" + to.getName() + "“",
                booking.getId());
    }

    /** Unterkonto auflösen; Restguthaben geht aufs Girokonto (/F430/). */
    @Transactional
    public void close(Long userId, Long accountId) {
        Account sub = requireSub(userId, accountId);
        accounts.findByIdForUpdate(sub.getId()).orElseThrow();
        long balance = bookingService.balanceOf(sub.getId());
        if (balance > 0) {
            Account main = accountService.mainAccount(userId);
            var booking = bookingService.post(BookingType.INTERNAL, "Auflösung " + sub.getName(), null, userId, List.of(
                    new BookingService.Line(sub.getId(), -balance),
                    new BookingService.Line(main.getId(), balance)));
            notifications.send(userId, NotificationType.INTERNAL, "Unterkonto aufgelöst",
                    "„" + sub.getName() + "“: " + Money.format(balance) + " aufs Girokonto gebucht", booking.getId());
        }
        sub.setStatus(AccountStatus.CLOSED);
    }

    private Account requireSub(Long userId, Long accountId) {
        Account a = accountService.requireOwned(accountId, userId);
        if (a.getType() != AccountType.SUB || a.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_SUBACCOUNT",
                    a.getType() == AccountType.SAVINGS
                            ? "Sparziel-Konten werden über das Sparziel verwaltet."
                            : "Das ist kein aktives Unterkonto.");
        }
        return a;
    }

    private Account requireMovable(Long userId, Long accountId) {
        Account a = accountService.requireOwned(accountId, userId);
        if (a.getStatus() != AccountStatus.ACTIVE || (a.getType() != AccountType.MAIN && a.getType() != AccountType.SUB)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_MOVABLE",
                    "Umbuchungen sind nur zwischen Girokonto und Unterkonten möglich.");
        }
        return a;
    }

    private static String iconOrDefault(String icon) {
        return icon == null || icon.isBlank() ? "savings" : icon.trim();
    }
}
