package de.frauas.girokonto.loan;

import de.frauas.girokonto.common.Dates;
import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.account.AccountStatus;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.booking.Booking;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.insights.OverviewDto;
import de.frauas.girokonto.loan.LoanDtos.Affordability;
import de.frauas.girokonto.loan.LoanDtos.ExtraPaymentDto;
import de.frauas.girokonto.loan.LoanDtos.ExtraPreview;
import de.frauas.girokonto.loan.LoanDtos.LoanDetail;
import de.frauas.girokonto.loan.LoanDtos.LoanDto;
import de.frauas.girokonto.loan.LoanDtos.Offer;
import de.frauas.girokonto.loan.LoanDtos.OfferResult;
import de.frauas.girokonto.loan.LoanDtos.ScheduleRow;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.savings.SavingsPlanService;
import de.frauas.girokonto.simulation.DailyJob;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kreditplan (/F1010/ bis /F1070/): Tragfähigkeit, Angebote fiktiver Banken, Tilgungsplan,
 * simulierter Abschluss und automatische Raten.
 *
 * Buchungen: Auszahlung = Kreditkonto −K, Girokonto +K. Rate = Girokonto −Rate, Kreditkonto +Tilgung,
 * System „ZINSEN“ +Zins.
 */
@Service
public class LoanService implements DailyJob {

    private final LoanRepository loans;
    private final LoanExtraPaymentRepository extras;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final BookingService bookingService;
    private final SavingsPlanService savings;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final Clock clock;

    public LoanService(LoanRepository loans, LoanExtraPaymentRepository extras, AccountRepository accounts, AccountService accountService,
                       BookingService bookingService, SavingsPlanService savings, NotificationService notifications,
                       TransactionTemplate tx, Clock clock) {
        this.loans = loans;
        this.extras = extras;
        this.accounts = accounts;
        this.accountService = accountService;
        this.bookingService = bookingService;
        this.savings = savings;
        this.notifications = notifications;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public int order() {
        return 30;
    }

    // ------------------------------------------------------------------ Planen

    @Transactional(readOnly = true)
    public Affordability affordability(Long userId) {
        OverviewDto ov = savings.overview(userId);
        return new Affordability(ov.hasData(), ov.availableCents(), ov.maxLoanRateCents());
    }

    /** Angebote aller Banken, sortiert nach Gesamtkosten (/F1030/). */
    @Transactional(readOnly = true)
    public OfferResult offers(Long userId, LoanPurpose purpose, long principal, long equity, int term) {
        validate(purpose, principal, equity, term);
        OverviewDto ov = savings.overview(userId);
        BigDecimal baseRate = purpose.rateFor(term, principal, equity);

        List<Offer> offers = new ArrayList<>();
        for (Bank bank : Bank.values()) {
            BigDecimal rate = baseRate.add(bank.adjustment());
            long monthly = LoanMath.annuity(principal, rate, term);
            long interest = LoanMath.schedule(principal, rate, term).stream().mapToLong(LoanMath.Row::interestCents).sum();
            String reason = null;
            if (!ov.hasData()) {
                reason = "Keine Einnahmen bekannt. Bitte erst Beispielmonate erzeugen.";
            } else if (ov.availableCents() <= 0) {
                reason = "Kein freier Betrag für eine Kreditrate.";
            } else if (monthly > ov.availableCents() * bank.tolerance()) {
                reason = "Rate zu hoch: höchstens " + Math.round(bank.tolerance() * 100) + " % von "
                        + Money.format(ov.availableCents()) + " möglich.";
            }
            boolean affordable = ov.hasData() && monthly <= ov.maxLoanRateCents();
            offers.add(new Offer(bank, bank.label(), rate, monthly, interest, principal + interest,
                    affordable, reason != null, reason, false));
        }
        offers.sort(Comparator.comparingLong(Offer::totalCostCents));
        Offer best = offers.stream().filter(o -> !o.declined() && o.affordable()).findFirst().orElse(null);
        List<Offer> marked = offers.stream()
                .map(o -> o == best ? new Offer(o.bank(), o.bankName(), o.interestRate(), o.monthlyRateCents(),
                        o.totalInterestCents(), o.totalCostCents(), o.affordable(), o.declined(), o.declineReason(), true) : o)
                .toList();

        long maxPrincipal = LoanMath.maxPrincipal(ov.maxLoanRateCents(), baseRate, term);
        return new OfferResult(purpose, purpose.label(), principal, equity, term, ov.hasData(), ov.availableCents(),
                ov.maxLoanRateCents(), Math.min(maxPrincipal, purpose.maxCents()), marked);
    }

    public List<ScheduleRow> schedule(long principal, BigDecimal rate, int term, LocalDate start, int paid) {
        if (principal <= 0 || term <= 0 || term > 480 || rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(30)) > 0) {
            throw bad("INVALID_SCHEDULE", "Ungültige Werte für den Tilgungsplan.");
        }
        return LoanMath.schedule(principal, rate, term).stream()
                .map(r -> new ScheduleRow(r.month(), start.plusMonths(r.month()), r.rateCents(), r.interestCents(),
                        r.principalCents(), r.remainingCents(), r.month() <= paid, 0))
                .toList();
    }

    // ------------------------------------------------------------------ Abschluss (/F1050/)

    @Transactional
    public LoanDetail create(Long userId, LoanDtos.CreateRequest r) {
        long equity = r.equityCents() == null ? 0 : r.equityCents();
        OfferResult result = offers(userId, r.purpose(), r.principalCents(), equity, r.termMonths());
        Offer offer = result.offers().stream().filter(o -> o.bank() == r.bank()).findFirst().orElseThrow();
        if (offer.declined()) {
            throw bad("LOAN_DECLINED", "Die " + offer.bankName() + " lehnt ab: " + offer.declineReason());
        }
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        Account loanAccount = accounts.save(new Account(userId, AccountType.LOAN,
                "Kredit " + r.purpose().label() + " (" + offer.bankName() + ")", "loan", null, now));
        Account main = accountService.mainAccount(userId);
        Loan loan = loans.save(new Loan(userId, loanAccount.getId(), r.bank(), r.purpose(), r.principalCents(), equity,
                offer.interestRate(), r.termMonths(), offer.monthlyRateCents(), today, now));
        Booking b = bookingService.post(BookingType.LOAN_PAYOUT, "Kreditauszahlung " + offer.bankName(), null, userId, List.of(
                new BookingService.Line(loanAccount.getId(), -r.principalCents()),
                new BookingService.Line(main.getId(), r.principalCents())));
        notifications.send(userId, NotificationType.INFO, "Kredit ausgezahlt",
                Money.format(r.principalCents()) + " von der " + offer.bankName() + ". Erste Rate ("
                        + Money.format(offer.monthlyRateCents()) + ") am " + Dates.de(loan.getNextDueDate()) + ".", b.getId());
        return detail(userId, loan.getId());
    }

    // ------------------------------------------------------------------ Raten (/F1060/)

    @Override
    public void runFor(LocalDate date) {
        for (Loan l : loans.findByStatusAndNextDueDateLessThanEqual(Loan.Status.ACTIVE, date)) {
            Long id = l.getId();
            tx.executeWithoutResult(s -> payInstallment(id));
        }
    }

    void payInstallment(Long loanId) {
        Loan loan = loans.findById(loanId).orElseThrow();
        if (loan.getStatus() != Loan.Status.ACTIVE) {
            return;
        }
        Account main = accounts.findByIdForUpdate(accountService.mainAccount(loan.getUserId()).getId()).orElseThrow();
        long remaining = -bookingService.balanceOf(loan.getAccountId());
        long interest = LoanMath.interest(remaining, loan.getInterestRate());
        long principal = loan.getMonthlyRateCents() - interest;
        if (loan.getPaidInstallments() + 1 >= loan.getTermMonths() || principal >= remaining) {
            principal = remaining;
        }
        long rate = principal + interest;
        int number = loan.getPaidInstallments() + 1;

        if (bookingService.balanceOf(main.getId()) < rate) {
            notifications.send(loan.getUserId(), NotificationType.INFO, "Kreditrate offen",
                    "Rate " + number + " (" + Money.format(rate) + ") an die " + loan.getBankName()
                            + " konnte nicht abgebucht werden. Neuer Versuch morgen.", null);
            return;
        }
        List<BookingService.Line> lines = new ArrayList<>();
        lines.add(new BookingService.Line(main.getId(), -rate));
        lines.add(new BookingService.Line(loan.getAccountId(), principal));
        if (interest > 0) {
            lines.add(new BookingService.Line(accountService.systemAccount("ZINSEN").getId(), interest));
        }
        Booking b = bookingService.post(BookingType.LOAN_RATE,
                "Kreditrate " + number + "/" + plannedRows(loan, Map.of()).size() + " – " + loan.getBankName(),
                "loan:" + loan.getId() + ":" + number, loan.getUserId(), lines);
        loan.installmentPaid();

        if (remaining - principal == 0) {
            loan.markPaidOff();
            accounts.findById(loan.getAccountId()).ifPresent(a -> a.setStatus(AccountStatus.CLOSED));
            notifications.send(loan.getUserId(), NotificationType.INFO, "Kredit abbezahlt",
                    "Der Kredit bei der " + loan.getBankName() + " ist vollständig zurückgezahlt.", b.getId());
        } else {
            notifications.send(loan.getUserId(), NotificationType.INFO, "Kreditrate gebucht",
                    "-" + Money.format(rate) + " an die " + loan.getBankName() + " (Zins " + Money.format(interest)
                            + ", Tilgung " + Money.format(principal) + ")", b.getId());
        }
    }

    // ------------------------------------------------------------------ Lesen (/F1070/)

    @Transactional(readOnly = true)
    public List<LoanDto> list(Long userId) {
        return loans.findByUserIdOrderByIdAsc(userId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LoanDetail detail(Long userId, Long loanId) {
        Loan l = requireLoan(userId, loanId);
        List<LoanExtraPayment> paid = extras.findByLoanIdOrderByIdAsc(l.getId());
        Map<Integer, Long> extraMap = extraMap(paid);
        List<ScheduleRow> rows = plannedRows(l, Map.of()).stream()
                .map(r -> new ScheduleRow(r.month(), l.getStartDate().plusMonths(r.month()), r.rateCents(),
                        r.interestCents(), r.principalCents(), r.remainingCents(), r.month() <= l.getPaidInstallments(),
                        extraMap.getOrDefault(r.month(), 0L)))
                .toList();
        long totalInterest = rows.stream().mapToLong(ScheduleRow::interestCents).sum();
        long withoutExtras = LoanMath.schedule(l.getPrincipalCents(), l.getInterestRate(), l.getTermMonths()).stream()
                .mapToLong(LoanMath.Row::interestCents).sum();
        List<ExtraPaymentDto> extraDtos = paid.stream()
                .map(e -> new ExtraPaymentDto(e.getId(), e.getAmountCents(), e.getPaidOn(), e.getAfterInstallment()))
                .toList();
        return new LoanDetail(toDto(l), totalInterest, Math.max(0, withoutExtras - totalInterest), rows, extraDtos);
    }

    // ------------------------------------------------------------------ Sondertilgung (/K020/)

    /** Was würde eine Sondertilgung bewirken? (ohne zu buchen) */
    @Transactional(readOnly = true)
    public ExtraPreview previewExtra(Long userId, Long loanId, long amount) {
        Loan l = requireActive(userId, loanId);
        long remaining = -bookingService.balanceOf(l.getAccountId());
        validateExtra(amount, remaining);
        List<LoanMath.Row> before = plannedRows(l, Map.of());
        List<LoanMath.Row> after = plannedRows(l, Map.of(l.getPaidInstallments(), amount));
        long interestBefore = before.stream().mapToLong(LoanMath.Row::interestCents).sum();
        long interestAfter = after.stream().mapToLong(LoanMath.Row::interestCents).sum();
        long available = bookingService.balanceOf(accountService.mainAccount(userId).getId());
        return new ExtraPreview(amount, remaining, remaining - amount,
                before.size() - l.getPaidInstallments(), after.size() - l.getPaidInstallments(),
                l.getStartDate().plusMonths(before.size()), l.getStartDate().plusMonths(after.size()),
                Math.max(0, interestBefore - interestAfter), available);
    }

    /** Sondertilgung buchen: Girokonto → Kreditkonto. Rate bleibt, Laufzeit und Zinsen sinken. */
    @Transactional
    public LoanDetail extraPayment(Long userId, Long loanId, long amount) {
        ExtraPreview preview = previewExtra(userId, loanId, amount);
        Loan loan = requireActive(userId, loanId);
        Account main = accounts.findByIdForUpdate(accountService.mainAccount(userId).getId()).orElseThrow();
        if (bookingService.balanceOf(main.getId()) < amount) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS",
                    "Nicht genug Guthaben auf dem Girokonto für " + Money.format(amount) + ".");
        }
        Booking b = bookingService.post(BookingType.LOAN_EXTRA, "Sondertilgung – " + loan.getBankName(), null, userId,
                List.of(new BookingService.Line(main.getId(), -amount),
                        new BookingService.Line(loan.getAccountId(), amount)));
        extras.save(new LoanExtraPayment(loan.getId(), b.getId(), amount, loan.getPaidInstallments(),
                LocalDate.now(clock), Instant.now(clock)));
        if (preview.remainingAfterCents() == 0) {
            loan.markPaidOff();
            accounts.findById(loan.getAccountId()).ifPresent(a -> a.setStatus(AccountStatus.CLOSED));
            notifications.send(userId, NotificationType.INFO, "Kredit abbezahlt",
                    "Mit der Sondertilgung von " + Money.format(amount) + " ist der Kredit bei der "
                            + loan.getBankName() + " vollständig zurückgezahlt.", b.getId());
        } else {
            int saved = preview.installmentsBefore() - preview.installmentsAfter();
            notifications.send(userId, NotificationType.INFO, "Sondertilgung gebucht",
                    "-" + Money.format(amount) + " an die " + loan.getBankName() + ". Restschuld jetzt "
                            + Money.format(preview.remainingAfterCents())
                            + (saved > 0 ? ", " + saved + (saved == 1 ? " Rate" : " Raten") + " weniger" : "")
                            + ", Zinsersparnis " + Money.format(preview.interestSavedCents()) + ".", b.getId());
        }
        return detail(userId, loanId);
    }

    private static void validateExtra(long amount, long remaining) {
        if (amount < 100) {
            throw bad("INVALID_AMOUNT", "Die Sondertilgung muss mindestens 1,00 € betragen.");
        }
        if (amount > remaining) {
            throw bad("INVALID_AMOUNT", "Höchstens die Restschuld von " + Money.format(remaining) + ".");
        }
    }

    /** Tilgungsplan mit fester Rate, allen bisherigen Sondertilgungen und optional weiteren. */
    private List<LoanMath.Row> plannedRows(Loan l, Map<Integer, Long> additional) {
        Map<Integer, Long> map = new HashMap<>(extraMap(extras.findByLoanIdOrderByIdAsc(l.getId())));
        additional.forEach((k, v) -> map.merge(k, v, Long::sum));
        return LoanMath.schedule(l.getPrincipalCents(), l.getInterestRate(), l.getMonthlyRateCents(),
                l.getTermMonths(), map);
    }

    private static Map<Integer, Long> extraMap(List<LoanExtraPayment> list) {
        Map<Integer, Long> map = new HashMap<>();
        list.forEach(e -> map.merge(e.getAfterInstallment(), e.getAmountCents(), Long::sum));
        return map;
    }

    private Loan requireLoan(Long userId, Long loanId) {
        return loans.findByIdAndUserId(loanId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Kredit nicht gefunden."));
    }

    private Loan requireActive(Long userId, Long loanId) {
        Loan l = requireLoan(userId, loanId);
        if (l.getStatus() != Loan.Status.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_CLOSED", "Der Kredit ist bereits zurückgezahlt.");
        }
        return l;
    }

    @Transactional(readOnly = true)
    public long totalDebt(Long userId) {
        return loans.findByUserIdOrderByIdAsc(userId).stream()
                .filter(l -> l.getStatus() == Loan.Status.ACTIVE)
                .mapToLong(l -> -bookingService.balanceOf(l.getAccountId())).sum();
    }

    private LoanDto toDto(Loan l) {
        List<LoanMath.Row> planned = plannedRows(l, Map.of());
        long remaining = l.getStatus() == Loan.Status.ACTIVE ? -bookingService.balanceOf(l.getAccountId()) : 0;
        double progress = l.getPrincipalCents() == 0 ? 100
                : Math.round((l.getPrincipalCents() - remaining) * 1000.0 / l.getPrincipalCents()) / 10.0;
        return new LoanDto(l.getId(), l.getBank(), l.getBankName(), l.getPurpose(), l.getPurpose().label(),
                l.getPrincipalCents(), l.getEquityCents(), l.getInterestRate(), l.getTermMonths(),
                l.getMonthlyRateCents(), l.getStartDate(),
                l.getStatus() == Loan.Status.ACTIVE ? l.getNextDueDate() : null,
                l.getPaidInstallments(), remaining, progress, l.getStatus(), l.getAccountId(),
                l.getStatus() == Loan.Status.ACTIVE ? planned.size() - l.getPaidInstallments() : 0,
                l.getStartDate().plusMonths(planned.size()),
                extras.findByLoanIdOrderByIdAsc(l.getId()).stream().mapToLong(LoanExtraPayment::getAmountCents).sum());
    }

    private static void validate(LoanPurpose p, long principal, long equity, int term) {
        if (principal < p.minCents() || principal > p.maxCents()) {
            throw bad("INVALID_AMOUNT", "Kreditbetrag für " + p.label() + " zwischen " + Money.format(p.minCents())
                    + " und " + Money.format(p.maxCents()) + ".");
        }
        if (term < p.minTerm() || term > p.maxTerm()) {
            throw bad("INVALID_TERM", "Laufzeit für " + p.label() + " zwischen " + p.minTerm() + " und "
                    + p.maxTerm() + " Monaten.");
        }
        if (equity < 0) {
            throw bad("INVALID_EQUITY", "Eigenkapital darf nicht negativ sein.");
        }
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
