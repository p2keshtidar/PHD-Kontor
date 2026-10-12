package de.frauas.girokonto.loan;

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
import java.util.List;
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
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final BookingService bookingService;
    private final SavingsPlanService savings;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final Clock clock;

    public LoanService(LoanRepository loans, AccountRepository accounts, AccountService accountService,
                       BookingService bookingService, SavingsPlanService savings, NotificationService notifications,
                       TransactionTemplate tx, Clock clock) {
        this.loans = loans;
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
                        r.principalCents(), r.remainingCents(), r.month() <= paid))
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
                        + Money.format(offer.monthlyRateCents()) + ") am " + loan.getNextDueDate() + ".", b.getId());
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
                "Kreditrate " + number + "/" + loan.getTermMonths() + " – " + loan.getBankName(),
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
        Loan l = loans.findByIdAndUserId(loanId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Kredit nicht gefunden."));
        List<ScheduleRow> rows = schedule(l.getPrincipalCents(), l.getInterestRate(), l.getTermMonths(),
                l.getStartDate(), l.getPaidInstallments());
        long totalInterest = rows.stream().mapToLong(ScheduleRow::interestCents).sum();
        return new LoanDetail(toDto(l), totalInterest, rows);
    }

    @Transactional(readOnly = true)
    public long totalDebt(Long userId) {
        return loans.findByUserIdOrderByIdAsc(userId).stream()
                .filter(l -> l.getStatus() == Loan.Status.ACTIVE)
                .mapToLong(l -> -bookingService.balanceOf(l.getAccountId())).sum();
    }

    private LoanDto toDto(Loan l) {
        long remaining = l.getStatus() == Loan.Status.ACTIVE ? -bookingService.balanceOf(l.getAccountId()) : 0;
        double progress = l.getPrincipalCents() == 0 ? 100
                : Math.round((l.getPrincipalCents() - remaining) * 1000.0 / l.getPrincipalCents()) / 10.0;
        return new LoanDto(l.getId(), l.getBank(), l.getBankName(), l.getPurpose(), l.getPurpose().label(),
                l.getPrincipalCents(), l.getEquityCents(), l.getInterestRate(), l.getTermMonths(),
                l.getMonthlyRateCents(), l.getStartDate(),
                l.getStatus() == Loan.Status.ACTIVE ? l.getNextDueDate() : null,
                l.getPaidInstallments(), remaining, progress, l.getStatus(), l.getAccountId());
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
