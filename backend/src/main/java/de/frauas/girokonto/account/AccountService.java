package de.frauas.girokonto.account;

import de.frauas.girokonto.booking.BookingLineRepository;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.insights.Category;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.IbanGenerator;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.user.AppUser;
import de.frauas.girokonto.user.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    public static final String SYSTEM_BANK = "BANK";

    private final AccountRepository accounts;
    private final BookingService bookingService;
    private final BookingLineRepository bookingLines;
    private final IbanGenerator ibanGenerator;
    private final AppProperties props;
    private final Clock clock;
    private final AccountMemberRepository members;
    private final AppUserRepository users;
    private final EarmarkRepository earmarks;

    public AccountService(AccountRepository accounts, BookingService bookingService, BookingLineRepository bookingLines,
                          IbanGenerator ibanGenerator, AppProperties props, Clock clock,
                          AccountMemberRepository members, AppUserRepository users, EarmarkRepository earmarks) {
        this.accounts = accounts;
        this.bookingService = bookingService;
        this.bookingLines = bookingLines;
        this.ibanGenerator = ibanGenerator;
        this.props = props;
        this.clock = clock;
        this.members = members;
        this.users = users;
        this.earmarks = earmarks;
    }

    /** Legt das Girokonto mit fiktiver IBAN an und bucht das Startguthaben (/F010/). */
    @Transactional
    public Account openMainAccount(AppUser user) {
        long number = accounts.nextAccountNumber();
        String iban = ibanGenerator.generate(props.bank().blz(), number);
        Account main = accounts.save(new Account(user.getId(), AccountType.MAIN, "Girokonto", null, iban, Instant.now(clock)));

        long start = props.startBalanceCents();
        if (start > 0) {
            Account bank = systemAccount(SYSTEM_BANK);
            bookingService.post(BookingType.INITIAL, "Startguthaben " + props.bank().name(), null, null, List.of(
                    new BookingService.Line(bank.getId(), -start),
                    new BookingService.Line(main.getId(), start)));
        }
        return main;
    }

    /** Alle aktiven Konten des Nutzers mit Kontostand (/F110/). */
    @Transactional(readOnly = true)
    public List<AccountDto> listForUser(Long userId) {
        List<AccountDto> result = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now(clock);
        List<Account> own = accounts.findByOwnerIdAndStatusOrderByIdAsc(userId, AccountStatus.ACTIVE);
        java.util.Map<Long, Long> earmarked = earmarks.forAccounts(own.stream()
                .filter(a -> a.getType() == AccountType.SUB).map(Account::getId).toList());
        for (Account a : own) {
            int count = a.getType() == AccountType.SUB ? members.count(a.getId()) : 0;
            long balance = bookingService.balanceOf(a.getId());
            result.add(new AccountDto(a.getId(), a.getType(), a.getName(), a.getIcon(), a.getIban(),
                    balance, count > 0, null, count,
                    a.getType() == AccountType.SUB
                            ? DigitalInfo.of(a, balance, earmarked.getOrDefault(a.getId(), 0L), "OWNER", today)
                            : null));
        }
        return result;
    }

    /** /K060/: Unterkonten anderer Nutzer, die mit diesem Nutzer geteilt sind. */
    @Transactional(readOnly = true)
    public List<AccountDto> sharedWithUser(Long userId) {
        List<AccountDto> result = new ArrayList<>();
        for (Long id : members.sharedWith(userId)) {
            accounts.findById(id).filter(a -> a.getStatus() == AccountStatus.ACTIVE && a.getType() == AccountType.SUB)
                    .ifPresent(a -> {
                        long balance = bookingService.balanceOf(a.getId());
                        result.add(new AccountDto(a.getId(), a.getType(), a.getName(), a.getIcon(), null,
                                balance, true, ownerName(a.getOwnerId()), members.count(a.getId()),
                                DigitalInfo.of(a, balance, earmarks.forAccount(a.getId()),
                                        members.role(a.getId(), userId), java.time.LocalDate.now(clock))));
                    });
        }
        return result;
    }

    /** /K060/: Lesen und Ein-/Auszahlen erlaubt für Besitzer und Mitglieder geteilter Unterkonten. */
    @Transactional(readOnly = true)
    public Account requireAccess(Long accountId, Long userId) {
        Account account = accounts.findById(accountId).orElse(null);
        if (account != null && (userId.equals(account.getOwnerId())
                || (account.getType() == AccountType.SUB && account.getStatus() == AccountStatus.ACTIVE
                        && members.isMember(accountId, userId)))) {
            return account;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Kein Zugriff auf dieses Konto.");
    }

    public String ownerName(Long ownerId) {
        return users.findById(ownerId).map(u -> u.getFirstName() + " " + u.getLastName()).orElse("?");
    }

    /** Wirft 403, wenn das Konto nicht dem Nutzer gehört (/NF010/, Testfall T14). */
    @Transactional(readOnly = true)
    public Account requireOwned(Long accountId, Long userId) {
        Account account = accounts.findById(accountId).orElse(null);
        if (account == null || !userId.equals(account.getOwnerId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Kein Zugriff auf dieses Konto.");
        }
        return account;
    }

    /** Kategorie einer Buchungszeile ändern (/F560/). */
    @Transactional
    public void changeCategory(Long userId, Long accountId, Long bookingId, Category category) {
        requireOwned(accountId, userId);
        if (category == null || !category.isUserSelectable()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "Diese Kategorie kann nicht gewählt werden.");
        }
        var line = bookingLines.findFirstByBookingIdAndAccountId(bookingId, accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Umsatz nicht gefunden."));
        Category current = line.getCategory() == null ? Category.SONSTIGES : Category.valueOf(line.getCategory());
        if (current.kind() == Category.Kind.EXCLUDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_FIXED",
                    "Umbuchungen, Sparraten und Kreditauszahlungen haben eine feste Kategorie.");
        }
        line.setCategory(category.name());
    }

    /** Das Girokonto des Nutzers. */
    @Transactional(readOnly = true)
    public Account mainAccount(Long userId) {
        return accounts.findFirstByOwnerIdAndType(userId, AccountType.MAIN)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_MAIN_ACCOUNT", "Kein Girokonto gefunden."));
    }

    @Transactional(readOnly = true)
    public Account systemAccount(String name) {
        return accounts.findFirstByTypeAndName(AccountType.SYSTEM, name)
                .orElseThrow(() -> new IllegalStateException("System-Konto fehlt: " + name));
    }
}
