package de.frauas.girokonto.subaccount;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountDto;
import de.frauas.girokonto.account.AccountMemberRepository;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.account.AccountStatus;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.account.DigitalInfo;
import de.frauas.girokonto.account.DigitalPurpose;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.transfer.Recipient;
import de.frauas.girokonto.transfer.RecipientResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Digitale Konten ("Töpfe", früher Unterkonten) anlegen, Geld umbuchen und auflösen (/F410/ bis /F430/).
 * Die Summe aller eigenen Konten bleibt beim Umbuchen immer gleich. Ein digitales Konto ist eine eigene Zeile im
 * Hauptbuch der Projektbank – kein eigenes Bankkonto und ohne eigene Einlagensicherung.
 */
@Service
public class SubaccountService {

    private final AccountRepository accounts;
    private final AccountService accountService;
    private final BookingService bookingService;
    private final NotificationService notifications;
    private final AppProperties props;
    private final Clock clock;
    private final AccountMemberRepository members;
    private final RecipientResolver recipients;

    public static final int MAX_MEMBERS = 5;

    /**
     * /K060/ Mitglied eines geteilten digitalen Kontos (der Besitzer steht mit owner=true in der Liste).
     *
     * @param role OWNER, FULL (einzahlen + auszahlen), DEPOSIT (nur einzahlen), VIEW (nur ansehen)
     */
    public record MemberDto(Long userId, String name, boolean owner, boolean you, String role, String roleLabel) {}

    public SubaccountService(AccountRepository accounts, AccountService accountService, BookingService bookingService,
                             NotificationService notifications, AppProperties props, Clock clock,
                             AccountMemberRepository members, RecipientResolver recipients) {
        this.accounts = accounts;
        this.accountService = accountService;
        this.bookingService = bookingService;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
        this.members = members;
        this.recipients = recipients;
    }

    @Transactional
    public AccountDto create(Long userId, SubaccountRequests.Save req) {
        long count = accounts.findByOwnerIdAndStatusOrderByIdAsc(userId, AccountStatus.ACTIVE).stream()
                .filter(a -> a.getType() == AccountType.SUB).count();
        if (count >= props.subaccounts().maxPerUser()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_SUBACCOUNTS",
                    "Höchstens " + props.subaccounts().maxPerUser() + " digitale Konten möglich.");
        }
        DigitalPurpose purpose = purpose(req.purpose());
        checkTarget(req);
        Account a = new Account(userId, AccountType.SUB, req.name().trim(),
                req.icon() == null || req.icon().isBlank() ? purpose.icon() : req.icon().trim(), null, Instant.now(clock));
        a.setPurpose(purpose.name());
        a.setTargetCents(req.targetCents());
        a.setTargetDate(req.targetDate());
        a = accounts.save(a);
        return dto(a, 0, userId);
    }

    /** Name, Symbol, Zweck und Ziel ändern (nur Besitzer). */
    @Transactional
    public AccountDto rename(Long userId, Long accountId, SubaccountRequests.Save req) {
        Account a = requireSub(userId, accountId);
        DigitalPurpose purpose = purpose(req.purpose() == null ? a.getPurpose() : req.purpose());
        checkTarget(req);
        a.setName(req.name().trim());
        a.setIcon(req.icon() == null || req.icon().isBlank() ? purpose.icon() : req.icon().trim());
        a.setPurpose(purpose.name());
        long balance = bookingService.balanceOf(a.getId());
        if (!java.util.Objects.equals(a.getTargetCents(), req.targetCents())) {
            // neues Ziel: schon erreicht? Dann nicht noch einmal melden
            a.setGoalReachedAt(req.targetCents() != null && balance >= req.targetCents() ? Instant.now(clock) : null);
        }
        a.setTargetCents(req.targetCents());
        a.setTargetDate(req.targetDate());
        return dto(a, balance, userId);
    }

    private AccountDto dto(Account a, long balance, Long userId) {
        int count = members.count(a.getId());
        String role = a.getOwnerId().equals(userId) ? "OWNER" : members.role(a.getId(), userId);
        return new AccountDto(a.getId(), a.getType(), a.getName(), a.getIcon(), null, balance, count > 0,
                a.getOwnerId().equals(userId) ? null : accountService.ownerName(a.getOwnerId()), count,
                DigitalInfo.of(a, balance, role, LocalDate.now(clock)));
    }

    private static DigitalPurpose purpose(String s) {
        if (s == null || s.isBlank()) {
            return DigitalPurpose.SPARZIEL;
        }
        for (DigitalPurpose p : DigitalPurpose.values()) {
            if (p.name().equals(s.trim())) {
                return p;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PURPOSE", "Unbekannter Zweck.");
    }

    private void checkTarget(SubaccountRequests.Save req) {
        if (req.targetDate() != null && !req.targetDate().isAfter(LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TARGET_DATE_PAST", "Das Zieldatum muss in der Zukunft liegen.");
        }
        if (req.targetDate() != null && req.targetDate().isAfter(LocalDate.now(clock).plusYears(50))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TARGET_DATE_FAR", "Das Zieldatum liegt zu weit in der Zukunft.");
        }
    }

    /** Umbuchen zwischen Girokonto und Unterkonten (in beide Richtungen und zwischen Unterkonten). */
    @Transactional
    public void move(Long userId, SubaccountRequests.Move req) {
        if (req.fromAccountId().equals(req.toAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT", "Quelle und Ziel sind gleich.");
        }
        Account from = requireMovable(userId, req.fromAccountId());
        Account to = requireMovable(userId, req.toAccountId());
        // Rollen in geteilten digitalen Konten: auszahlen nur mit FULL, einzahlen nicht mit VIEW
        String fromRole = role(from, userId);
        if (fromRole.equals("DEPOSIT") || fromRole.equals("VIEW")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_NO_WITHDRAW",
                    "Du darfst aus „" + from.getName() + "“ nichts auszahlen.");
        }
        if (role(to, userId).equals("VIEW")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_VIEW_ONLY",
                    "Du darfst „" + to.getName() + "“ nur ansehen.");
        }
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
        goalCheck(to, from);
        // /K060/: die anderen Beteiligten eines geteilten Unterkontos informieren
        String actor = accountService.ownerName(userId);
        for (Account shared : List.of(to, from)) {
            if (shared.getType() != AccountType.SUB || members.count(shared.getId()) == 0) {
                continue;
            }
            String what = shared == to ? "eingezahlt in" : "ausgezahlt aus";
            for (Long other : participants(shared)) {
                if (!other.equals(userId)) {
                    notifications.send(other, NotificationType.INTERNAL, "Geteiltes digitales Konto",
                            actor + " hat " + Money.format(req.amountCents()) + " " + what + " „" + shared.getName() + "“",
                            booking.getId());
                }
            }
        }
    }

    // ------------------------------------------------------------------ /K060/ Teilen

    @Transactional(readOnly = true)
    public List<MemberDto> members(Long userId, Long accountId) {
        Account a = accountService.requireAccess(accountId, userId);
        List<MemberDto> list = new java.util.ArrayList<>();
        list.add(new MemberDto(a.getOwnerId(), accountService.ownerName(a.getOwnerId()), true,
                a.getOwnerId().equals(userId), "OWNER", DigitalInfo.roleLabel("OWNER")));
        for (Long m : members.members(accountId)) {
            String role = members.role(accountId, m);
            list.add(new MemberDto(m, accountService.ownerName(m), false, m.equals(userId), role,
                    DigitalInfo.roleLabel(role)));
        }
        return list;
    }

    @Transactional
    public List<MemberDto> addMember(Long userId, Long accountId, String recipient) {
        return addMember(userId, accountId, recipient, null);
    }

    @Transactional
    public List<MemberDto> addMember(Long userId, Long accountId, String recipient, String role) {
        Account a = requireSub(userId, accountId);
        String r0 = role == null || role.isBlank() ? "FULL" : role.trim();
        if (!List.of("FULL", "DEPOSIT", "VIEW").contains(r0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Unbekannte Rolle.");
        }
        Recipient r = recipients.resolve(recipient);
        Long newMember = r.user().getId();
        if (newMember.equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_SHARE", "Du bist schon Besitzer dieses digitalen Kontos.");
        }
        if (members.isMember(accountId, newMember)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", r.fullName() + " ist schon dabei.");
        }
        if (members.count(accountId) >= MAX_MEMBERS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_MEMBERS",
                    "Höchstens " + MAX_MEMBERS + " Personen pro digitalem Konto.");
        }
        members.add(accountId, newMember, Instant.now(clock), r0);
        notifications.send(newMember, NotificationType.INFO, "Digitales Konto geteilt",
                accountService.ownerName(userId) + " hat „" + a.getName() + "“ mit dir geteilt. " + rights(r0), null);
        return members(userId, accountId);
    }

    /** Rolle eines Mitglieds ändern (nur der Besitzer). */
    @Transactional
    public List<MemberDto> setRole(Long userId, Long accountId, Long memberId, String role) {
        Account a = requireSub(userId, accountId);
        if (members.setRole(accountId, memberId, role) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Diese Person ist kein Mitglied.");
        }
        notifications.send(memberId, NotificationType.INFO, "Digitales Konto geteilt",
                "Deine Rechte für „" + a.getName() + "“ wurden geändert. " + rights(role), null);
        return members(userId, accountId);
    }

    private static String rights(String role) {
        return switch (role) {
            case "DEPOSIT" -> "Du kannst einzahlen und die Umsätze sehen.";
            case "VIEW" -> "Du kannst die Umsätze sehen.";
            default -> "Du kannst einzahlen, auszahlen und die Umsätze sehen.";
        };
    }

    /** OWNER für eigene Konten (auch das Girokonto), sonst die Rolle im geteilten Konto. */
    private String role(Account a, Long userId) {
        if (userId.equals(a.getOwnerId())) {
            return "OWNER";
        }
        String r = members.role(a.getId(), userId);
        return r == null ? "VIEW" : r;
    }

    /** "Ziel erreicht" einmal melden; fällt der Stand wieder unter das Ziel, darf es erneut gemeldet werden. */
    private void goalCheck(Account to, Account from) {
        Instant now = Instant.now(clock);
        if (to.getType() == AccountType.SUB && to.getTargetCents() != null && to.getGoalReachedAt() == null
                && bookingService.balanceOf(to.getId()) >= to.getTargetCents()) {
            to.setGoalReachedAt(now);
            for (Long p : participants(to)) {
                notifications.send(p, NotificationType.GOAL, "Ziel erreicht",
                        "„" + to.getName() + "“ hat das Ziel von " + Money.format(to.getTargetCents()) + " erreicht.", null);
            }
        }
        if (from.getType() == AccountType.SUB && from.getTargetCents() != null && from.getGoalReachedAt() != null
                && bookingService.balanceOf(from.getId()) < from.getTargetCents()) {
            from.setGoalReachedAt(null);
        }
    }

    /** Besitzer entfernt ein Mitglied – oder ein Mitglied tritt selbst aus. */
    @Transactional
    public void removeMember(Long userId, Long accountId, Long memberId) {
        Account a = accountService.requireAccess(accountId, userId);
        boolean owner = a.getOwnerId().equals(userId);
        if (!owner && !memberId.equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Nur der Besitzer kann Mitglieder entfernen.");
        }
        if (members.remove(accountId, memberId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Diese Person ist kein Mitglied.");
        }
        Long notify = owner ? memberId : a.getOwnerId();
        notifications.send(notify, NotificationType.INFO, "Digitales Konto geteilt",
                owner ? "Du bist nicht mehr Mitglied von „" + a.getName() + "“."
                        : accountService.ownerName(userId) + " ist aus „" + a.getName() + "“ ausgetreten.", null);
    }

    private List<Long> participants(Account a) {
        List<Long> all = new java.util.ArrayList<>(members.members(a.getId()));
        all.add(0, a.getOwnerId());
        return all;
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
            notifications.send(userId, NotificationType.INTERNAL, "Digitales Konto aufgelöst",
                    "„" + sub.getName() + "“: " + Money.format(balance) + " aufs Girokonto gebucht", booking.getId());
        }
        for (Long m : members.members(sub.getId())) {
            notifications.send(m, NotificationType.INFO, "Digitales Konto aufgelöst",
                    "„" + sub.getName() + "“ wurde von " + accountService.ownerName(userId) + " aufgelöst.", null);
        }
        sub.setStatus(AccountStatus.CLOSED);
    }

    private Account requireSub(Long userId, Long accountId) {
        Account a = accountService.requireOwned(accountId, userId);
        if (a.getType() != AccountType.SUB || a.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_SUBACCOUNT",
                    a.getType() == AccountType.SAVINGS
                            ? "Sparziel-Konten werden über das Sparziel verwaltet."
                            : "Das ist kein aktives digitales Konto.");
        }
        return a;
    }

    private Account requireMovable(Long userId, Long accountId) {
        Account a = accountService.requireAccess(accountId, userId);   // /K060/: auch geteilte Unterkonten
        if (a.getStatus() != AccountStatus.ACTIVE || (a.getType() != AccountType.MAIN && a.getType() != AccountType.SUB)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_MOVABLE",
                    "Umbuchungen sind nur zwischen Girokonto und digitalen Konten möglich.");
        }
        return a;
    }

    private static String iconOrDefault(String icon) {
        return icon == null || icon.isBlank() ? "savings" : icon.trim();
    }
}
