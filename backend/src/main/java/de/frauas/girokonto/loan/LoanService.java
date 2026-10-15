package de.frauas.girokonto.loan;

import de.frauas.girokonto.common.Dates;
import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.audit.AuditLog;
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
import de.frauas.girokonto.loan.LoanDtos.ApplicationDto;
import de.frauas.girokonto.loan.LoanDtos.ApplicationRequest;
import de.frauas.girokonto.loan.LoanDtos.ProviderDto;
import de.frauas.girokonto.loan.provider.LoanOfferProvider;
import de.frauas.girokonto.loan.provider.MarketRateService;
import de.frauas.girokonto.loan.provider.SimulatedBanksProvider;
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
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final List<LoanOfferProvider> providers;
    private final MarketRateService marketRates;
    private final JdbcTemplate jdbc;
    private final AuditLog audit;

    /** Zusagen der (fiktiven) Banken gelten so viele Tage. */
    static final int OFFER_VALID_DAYS = 14;
    static final int MAX_OPEN_APPLICATIONS = 10;
    static final String DISCLAIMER = "Alle Angebote stammen von fiktiven Banken (Simulation der Projektbank). Es sind "
            + "unverbindliche Rechenbeispiele – keine echten Kreditangebote, keine Abfrage bei einer Auskunftei, keine "
            + "Auszahlung echten Geldes.";

    public LoanService(LoanRepository loans, LoanExtraPaymentRepository extras, AccountRepository accounts, AccountService accountService,
                       BookingService bookingService, SavingsPlanService savings, NotificationService notifications,
                       TransactionTemplate tx, Clock clock, List<LoanOfferProvider> providers,
                       MarketRateService marketRates, JdbcTemplate jdbc, AuditLog audit) {
        this.loans = loans;
        this.extras = extras;
        this.accounts = accounts;
        this.accountService = accountService;
        this.bookingService = bookingService;
        this.savings = savings;
        this.notifications = notifications;
        this.tx = tx;
        this.clock = clock;
        this.providers = providers;
        this.marketRates = marketRates;
        this.jdbc = jdbc;
        this.audit = audit;
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

    /** Angebote aller Banken, sortiert nach Gesamtkosten (/F1030/), Bonität "gut", ohne Filter. */
    @Transactional(readOnly = true)
    public OfferResult offers(Long userId, LoanPurpose purpose, long principal, long equity, int term) {
        return offers(userId, purpose, principal, equity, term, CreditClass.GUT, null, null, false);
    }

    /**
     * Vergleich (Schritt 1): Rechenbeispiele aller Anbieter mit Bonitätsaufschlag, Tragfähigkeit nach den Regeln
     * der jeweiligen Bank und Filtern. Es wird nichts gespeichert und nichts beantragt.
     */
    @Transactional(readOnly = true)
    public OfferResult offers(Long userId, LoanPurpose purpose, long principal, long equity, int term,
                              CreditClass creditClass, Long maxMonthly, Long maxTotal, boolean onlyEligible) {
        validate(purpose, principal, equity, term);
        OverviewDto ov = savings.overview(userId);
        LoanOfferProvider.Query query = new LoanOfferProvider.Query(purpose, principal, equity, term, creditClass);
        List<MarketRateService.ReferenceRate> refs = marketRates.references(purpose, term);
        Double marketOverall = refs.stream().filter(MarketRateService.ReferenceRate::overall)
                .map(MarketRateService.ReferenceRate::ratePercent).findFirst().orElse(null);

        List<Offer> offers = new ArrayList<>();
        int hidden = 0;
        for (LoanOfferProvider provider : providers) {
            for (LoanOfferProvider.Quote q : provider.quotes(query)) {
                String reason = q.ineligibleReason();
                if (reason == null) {
                    if (!ov.hasData()) {
                        reason = "Keine Einnahmen bekannt. Bitte erst Beispielmonate erzeugen.";
                    } else if (ov.availableCents() <= 0) {
                        reason = "Kein freier Betrag für eine Kreditrate.";
                    } else if (q.monthlyRateCents() > ov.availableCents() * q.maxIncomeShare()) {
                        reason = "Rate zu hoch: höchstens " + Math.round(q.maxIncomeShare() * 100) + " % von "
                                + Money.format(ov.availableCents()) + " möglich.";
                    }
                }
                boolean affordable = ov.hasData() && q.monthlyRateCents() <= ov.maxLoanRateCents();
                Offer o = new Offer(q.bankCode(), q.bankName(), q.interestRate(), q.monthlyRateCents(),
                        q.totalInterestCents(), q.totalCents(), affordable, reason != null, reason, false,
                        provider.id(), provider.simulation(), q.effectiveRate(), q.feesCents(), q.minClass().name(),
                        q.minClass().label(), q.eligible(), q.requirements(), q.conditions(), q.exampleText(),
                        marketOverall == null ? null
                                : Math.round((q.effectiveRate().doubleValue() - marketOverall) * 100) / 100.0);
                boolean filtered = (maxMonthly != null && o.monthlyRateCents() > maxMonthly)
                        || (maxTotal != null && o.totalCostCents() > maxTotal)
                        || (onlyEligible && o.declined());
                if (filtered) {
                    hidden++;
                } else {
                    offers.add(o);
                }
            }
        }
        offers.sort(Comparator.comparingLong(Offer::totalCostCents));
        Offer best = offers.stream().filter(o -> !o.declined() && o.affordable()).findFirst().orElse(null);
        List<Offer> marked = offers.stream().map(o -> o == best ? o.recommend() : o).toList();

        BigDecimal baseRate = purpose.rateFor(term, principal, equity).add(creditClass.addOn(purpose));
        long maxPrincipal = LoanMath.maxPrincipal(ov.maxLoanRateCents(), baseRate, term);
        return new OfferResult(purpose, purpose.label(), principal, equity, term, ov.hasData(), ov.availableCents(),
                ov.maxLoanRateCents(), Math.min(maxPrincipal, purpose.maxCents()), marked, creditClass.name(),
                creditClass.label(), hidden, refs, providerStatus(), DISCLAIMER);
    }

    /** Alle Anbieter mit Zustand: Angebote, Referenzzinsen, echte Angebote (nicht angebunden). */
    public List<ProviderDto> providerStatus() {
        List<ProviderDto> list = new ArrayList<>();
        for (LoanOfferProvider p : providers) {
            LoanOfferProvider.Status st = p.status();
            list.add(new ProviderDto(p.id(), p.displayName(), "OFFERS", p.simulation(), st.state(),
                    stateLabel(st.state()), st.message(), st.updatedAt()));
        }
        LoanOfferProvider.Status m = marketRates.status();
        list.add(new ProviderDto("BUNDESBANK", "Deutsche Bundesbank – Durchschnittszinsen", "REFERENCE", false,
                m.state(), stateLabel(m.state()), m.message(), m.updatedAt()));
        list.add(new ProviderDto("PARTNER", "Echte Kreditangebote", "PARTNER", false, "NOT_CONNECTED",
                stateLabel("NOT_CONNECTED"), "Nicht angebunden: Dafür sind ein Vertrag mit einer Bank oder einem "
                        + "Kreditvermittler und die nötigen Erlaubnisse erforderlich.", null));
        return list;
    }

    static String stateLabel(String state) {
        return switch (state) {
            case "OK" -> "Verfügbar";
            case "STALE" -> "Daten veraltet";
            case "DISABLED" -> "Abgeschaltet";
            case "NOT_CONNECTED" -> "Nicht angebunden";
            default -> "Gestört";
        };
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

    // ------------------------------------------------------------------ Anfrage, Abschluss, Auszahlung (/F1050/)

    /**
     * Abkürzung für Demo und ältere App-Versionen: Anfrage (Bonität „gut“), Vertrag und Auszahlung nacheinander –
     * jeder Schritt einzeln gespeichert wie im getrennten Ablauf.
     */
    public LoanDetail create(Long userId, LoanDtos.CreateRequest r) {
        ApplicationDto app = request(userId, new ApplicationRequest(SimulatedBanksProvider.ID, r.bank(), r.purpose(),
                r.principalCents(), r.equityCents(), r.termMonths(), CreditClass.GUT.name(), true));
        if (app.status().equals("DECLINED")) {
            throw bad("LOAN_DECLINED", "Die " + app.bankName() + " lehnt ab: " + app.decisionReason());
        }
        LoanDetail contracted = accept(userId, app.id(), true);
        return tx.execute(st -> payout(userId, contracted.loan().id()));
    }

    /** Schritt 2: unverbindliche Anfrage. Die (fiktive) Bank entscheidet sofort; eine Zusage gilt 14 Tage. */
    public ApplicationDto request(Long userId, ApplicationRequest r) {
        long equity = r.equityCents() == null ? 0 : r.equityCents();
        CreditClass cls = CreditClass.parse(r.creditClass());
        if (cls == null) {
            throw bad("INVALID_CREDIT_CLASS", "Bonität: sehr gut, gut, mittel oder schwach.");
        }
        if (!Boolean.TRUE.equals(r.consent())) {
            throw bad("CONSENT_REQUIRED", "Bitte stimme zu, dass deine Angaben für diese Anfrage verwendet werden.");
        }
        String providerId = r.provider() == null || r.provider().isBlank() ? SimulatedBanksProvider.ID : r.provider();
        LoanOfferProvider provider = providers.stream().filter(p -> p.id().equals(providerId)).findFirst()
                .orElseThrow(() -> bad("UNKNOWN_PROVIDER", "Unbekannter Anbieter."));
        if (!provider.supportsApplications()) {
            throw bad("NO_APPLICATIONS", "Bei diesem Anbieter sind keine Anfragen möglich.");
        }
        Integer open = jdbc.queryForObject("SELECT count(*) FROM loan_application WHERE user_id = ? AND status = 'APPROVED'",
                Integer.class, userId);
        if (open != null && open >= MAX_OPEN_APPLICATIONS) {
            throw bad("TOO_MANY_APPLICATIONS", "Höchstens " + MAX_OPEN_APPLICATIONS + " offene Zusagen. Bitte erst welche zurückziehen.");
        }
        OfferResult result = offers(userId, r.purpose(), r.principalCents(), equity, r.termMonths(), cls, null, null,
                false);
        Offer o = result.offers().stream().filter(x -> x.providerId().equals(providerId) && x.bank().equals(r.bank().name()))
                .findFirst().orElseThrow(() -> bad("UNKNOWN_BANK", "Diese Bank ist nicht im Vergleich."));
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        boolean approved = !o.declined();
        LocalDate validUntil = approved ? today.plusDays(OFFER_VALID_DAYS) : null;
        String reason = approved ? "Zusage (Simulation) – gültig bis " + Dates.de(validUntil) + ". Noch kein Vertrag."
                : o.declineReason();
        Long id = jdbc.queryForObject("""
                INSERT INTO loan_application (user_id, provider, bank_code, bank_name, purpose, principal_cents,
                       equity_cents, term_months, credit_class, interest_rate, effective_rate, monthly_rate_cents,
                       total_cents, status, decision_reason, valid_until, created_at, decided_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, userId, providerId, o.bank(), o.bankName(), r.purpose().name(), r.principalCents(),
                equity, r.termMonths(), cls.name(), approved ? o.interestRate() : null,
                approved ? o.effectiveRate() : null, approved ? o.monthlyRateCents() : null,
                approved ? o.totalCostCents() : null, approved ? "APPROVED" : "DECLINED", reason,
                validUntil == null ? null : Date.valueOf(validUntil), Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now));
        audit.record(userId, "LOAN_APPLICATION_" + (approved ? "APPROVED" : "DECLINED"), "loan-application:" + id,
                o.bankName());
        notifications.send(userId, NotificationType.INFO, "Kreditanfrage beantwortet", approved
                ? "Die " + o.bankName() + " sagt " + Money.format(r.principalCents()) + " zu (Simulation). Das Angebot gilt bis "
                        + Dates.de(validUntil) + "."
                : "Die " + o.bankName() + " lehnt ab: " + o.declineReason(), null);
        return application(userId, id);
    }

    public List<ApplicationDto> applications(Long userId) {
        return jdbc.query("SELECT * FROM loan_application WHERE user_id = ? ORDER BY id DESC", this::applicationDto, userId);
    }

    public ApplicationDto application(Long userId, Long id) {
        List<ApplicationDto> r = jdbc.query("SELECT * FROM loan_application WHERE id = ? AND user_id = ?",
                this::applicationDto, id, userId);
        if (r.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Kreditanfrage nicht gefunden.");
        }
        return r.get(0);
    }

    /** Anfrage zurückziehen (vor dem Vertrag). */
    public ApplicationDto withdraw(Long userId, Long id) {
        ApplicationDto a = application(userId, id);
        if (!a.status().equals("APPROVED")) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_CLOSED", "Diese Anfrage ist schon abgeschlossen.");
        }
        jdbc.update("UPDATE loan_application SET status = 'WITHDRAWN', updated_at = ? WHERE id = ? AND status = 'APPROVED'",
                Timestamp.from(Instant.now(clock)), id);
        return application(userId, id);
    }

    /**
     * Schritt 3: Vertrag abschließen (Simulation). Es entsteht ein Kreditkonto ohne Buchung – das Geld fließt erst
     * mit der Auszahlung.
     */
    public LoanDetail accept(Long userId, Long applicationId, Boolean confirm) {
        if (!Boolean.TRUE.equals(confirm)) {
            throw bad("CONFIRM_REQUIRED", "Bitte bestätige den Vertragsschluss.");
        }
        Long loanId = tx.execute(s -> {
            List<String> st = jdbc.queryForList("SELECT status FROM loan_application WHERE id = ? AND user_id = ? FOR UPDATE",
                    String.class, applicationId, userId);
            if (st.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Kreditanfrage nicht gefunden.");
            }
            ApplicationDto a = application(userId, applicationId);
            if (a.status().equals("EXPIRED")) {
                throw new ApiException(HttpStatus.CONFLICT, "OFFER_EXPIRED", "Das Angebot ist abgelaufen. Bitte neu anfragen.");
            }
            if (!a.status().equals("APPROVED")) {
                throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_CLOSED", a.status().equals("ACCEPTED")
                        ? "Für diese Zusage gibt es schon einen Vertrag." : "Diese Anfrage hat keine gültige Zusage.");
            }
            LocalDate today = LocalDate.now(clock);
            if (today.isAfter(a.validUntil())) {
                return null;   // abgelaufen: außerhalb der Transaktion melden
            }
            Instant now = Instant.now(clock);
            Bank bank = Bank.valueOf(a.bank());
            Account loanAccount = accounts.save(new Account(userId, AccountType.LOAN,
                    "Kredit " + a.purpose().label() + " (" + a.bankName() + ")", "loan", null, now));
            Loan loan = loans.save(Loan.contracted(userId, loanAccount.getId(), bank, a.purpose(), a.principalCents(),
                    a.equityCents(), a.interestRate(), a.effectiveRate(), a.termMonths(), a.monthlyRateCents(), today,
                    a.id(), now));
            jdbc.update("""
                    UPDATE loan_application SET status = 'ACCEPTED', loan_id = ?, accepted_at = ?, updated_at = ?
                     WHERE id = ?
                    """, loan.getId(), Timestamp.from(now), Timestamp.from(now), a.id());
            audit.record(userId, "LOAN_CONTRACTED", "loan:" + loan.getId(), a.bankName());
            notifications.send(userId, NotificationType.INFO, "Kreditvertrag geschlossen (Simulation)",
                    Money.format(a.principalCents()) + " bei der " + a.bankName() + ": Das Geld wird erst ausgezahlt, "
                            + "wenn du die Auszahlung anforderst. Widerruf bis dahin jederzeit möglich.", null);
            return loan.getId();
        });
        if (loanId == null) {
            ApplicationDto a = application(userId, applicationId);
            jdbc.update("UPDATE loan_application SET status = 'EXPIRED', updated_at = ? WHERE id = ? AND status = 'APPROVED'",
                    Timestamp.from(Instant.now(clock)), applicationId);
            throw new ApiException(HttpStatus.CONFLICT, "OFFER_EXPIRED",
                    "Das Angebot ist am " + Dates.de(a.validUntil()) + " abgelaufen. Bitte neu anfragen.");
        }
        return detail(userId, loanId);
    }

    /** Schritt 4: Auszahlung aufs Girokonto (Buchung, genau einmal). Erst ab jetzt laufen die Raten. */
    @Transactional
    public LoanDetail payout(Long userId, Long loanId) {
        Loan loan = loans.findForUpdate(loanId, userId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Kredit nicht gefunden."));
        if (loan.getStatus() != Loan.Status.CONTRACTED) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_NOT_CONTRACTED", switch (loan.getStatus()) {
                case CANCELLED -> "Der Kreditvertrag wurde widerrufen.";
                case PAID_OFF -> "Der Kredit ist bereits zurückgezahlt.";
                default -> "Der Kredit ist bereits ausgezahlt.";
            });
        }
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        Account main = accountService.mainAccount(userId);
        Booking b = bookingService.post(BookingType.LOAN_PAYOUT, "Kreditauszahlung " + loan.getBankName(),
                "loan-payout:" + loan.getId(), userId, List.of(
                        new BookingService.Line(loan.getAccountId(), -loan.getPrincipalCents()),
                        new BookingService.Line(main.getId(), loan.getPrincipalCents())));
        loan.paidOut(today, now);
        audit.record(userId, "LOAN_PAID_OUT", "loan:" + loan.getId(), null);
        notifications.send(userId, NotificationType.INFO, "Kredit ausgezahlt",
                Money.format(loan.getPrincipalCents()) + " von der " + loan.getBankName() + ". Erste Rate ("
                        + Money.format(loan.getMonthlyRateCents()) + ") am " + Dates.de(loan.getNextDueDate()) + ".",
                b.getId());
        return detail(userId, loan.getId());
    }

    /** Vertrag vor der Auszahlung widerrufen: es ist kein Geld geflossen, das Kreditkonto wird geschlossen. */
    @Transactional
    public LoanDetail cancelContract(Long userId, Long loanId) {
        Loan loan = loans.findForUpdate(loanId, userId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "Kredit nicht gefunden."));
        if (loan.getStatus() != Loan.Status.CONTRACTED) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_NOT_CONTRACTED", loan.getStatus() == Loan.Status.ACTIVE
                    ? "Nach der Auszahlung ist ein Widerruf in dieser Simulation nicht möglich. Nutze die Sondertilgung."
                    : "Dieser Kreditvertrag ist nicht mehr offen.");
        }
        loan.cancel();
        accounts.findById(loan.getAccountId()).ifPresent(a -> a.setStatus(AccountStatus.CLOSED));
        audit.record(userId, "LOAN_CANCELLED", "loan:" + loan.getId(), null);
        notifications.send(userId, NotificationType.INFO, "Kreditvertrag widerrufen",
                "Der Vertrag mit der " + loan.getBankName() + " ist widerrufen. Es wurde kein Geld ausgezahlt.", null);
        return detail(userId, loan.getId());
    }

    private ApplicationDto applicationDto(ResultSet rs, int i) throws SQLException {
        LoanPurpose purpose = LoanPurpose.valueOf(rs.getString("purpose"));
        CreditClass cls = CreditClass.valueOf(rs.getString("credit_class"));
        long monthly = rs.getLong("monthly_rate_cents");
        Long monthlyRate = rs.wasNull() ? null : monthly;
        long total = rs.getLong("total_cents");
        Long totalCents = rs.wasNull() ? null : total;
        long loan = rs.getLong("loan_id");
        Long loanId = rs.wasNull() ? null : loan;
        Date valid = rs.getDate("valid_until");
        String status = rs.getString("status");
        String provider = rs.getString("provider");
        return new ApplicationDto(rs.getLong("id"), provider, provider.equals(SimulatedBanksProvider.ID),
                rs.getString("bank_code"), rs.getString("bank_name"), purpose, purpose.label(),
                rs.getLong("principal_cents"), rs.getLong("equity_cents"), rs.getInt("term_months"), cls.name(),
                cls.label(), rs.getBigDecimal("interest_rate"), rs.getBigDecimal("effective_rate"), monthlyRate,
                totalCents, status, applicationStatusLabel(status), rs.getString("decision_reason"),
                valid == null ? null : valid.toLocalDate(), loanId, rs.getTimestamp("created_at").toInstant(),
                nextStep(status));
    }

    static String applicationStatusLabel(String status) {
        return switch (status) {
            case "APPROVED" -> "Zusage";
            case "DECLINED" -> "Abgelehnt";
            case "ACCEPTED" -> "Vertrag geschlossen";
            case "WITHDRAWN" -> "Zurückgezogen";
            default -> "Abgelaufen";
        };
    }

    static String nextStep(String status) {
        return switch (status) {
            case "APPROVED" -> "Nächster Schritt: Vertrag abschließen. Bis dahin ist nichts verbindlich.";
            case "ACCEPTED" -> "Nächster Schritt: Auszahlung anfordern.";
            case "DECLINED" -> "Andere Bank, kleineren Betrag oder längere Laufzeit wählen.";
            default -> "Bei Bedarf neu anfragen.";
        };
    }

    static String loanStatusLabel(Loan.Status status) {
        return switch (status) {
            case ACTIVE -> "Ausgezahlt – Raten laufen";
            case CONTRACTED -> "Vertrag geschlossen – Auszahlung ausstehend";
            case PAID_OFF -> "Zurückgezahlt";
            case CANCELLED -> "Widerrufen";
        };
    }

    // ------------------------------------------------------------------ Raten (/F1060/)

    @Override
    public void runFor(LocalDate date) {
        for (Loan l : loans.findByStatusAndNextDueDateLessThanEqual(Loan.Status.ACTIVE, date)) {
            Long id = l.getId();
            tx.executeWithoutResult(s -> payInstallment(id));
        }
        // abgelaufene Zusagen
        jdbc.query("""
                UPDATE loan_application SET status = 'EXPIRED', updated_at = now()
                 WHERE status = 'APPROVED' AND valid_until < ? RETURNING user_id, bank_name, principal_cents
                """, rs -> {
            notifications.send(rs.getLong("user_id"), NotificationType.INFO, "Kreditzusage abgelaufen",
                    "Das Angebot der " + rs.getString("bank_name") + " über " + Money.format(rs.getLong("principal_cents"))
                            + " ist abgelaufen.", null);
        }, Date.valueOf(date));
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
        if (l.getStatus() == Loan.Status.CONTRACTED) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_NOT_PAID_OUT", "Der Kredit ist noch nicht ausgezahlt.");
        }
        if (l.getStatus() == Loan.Status.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_CANCELLED", "Der Kreditvertrag wurde widerrufen.");
        }
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
        boolean active = l.getStatus() == Loan.Status.ACTIVE;
        boolean open = active || l.getStatus() == Loan.Status.CONTRACTED;
        long remaining = active ? -bookingService.balanceOf(l.getAccountId()) : 0;
        double progress = !active && l.getStatus() != Loan.Status.PAID_OFF ? 0 : l.getPrincipalCents() == 0 ? 100
                : Math.round((l.getPrincipalCents() - remaining) * 1000.0 / l.getPrincipalCents()) / 10.0;
        return new LoanDto(l.getId(), l.getBank(), l.getBankName(), l.getPurpose(), l.getPurpose().label(),
                l.getPrincipalCents(), l.getEquityCents(), l.getInterestRate(), l.getTermMonths(),
                l.getMonthlyRateCents(), l.getStartDate(),
                active ? l.getNextDueDate() : null,
                l.getPaidInstallments(), remaining, progress, l.getStatus(), l.getAccountId(),
                active ? planned.size() - l.getPaidInstallments() : l.getStatus() == Loan.Status.CONTRACTED ? planned.size() : 0,
                open || l.getStatus() == Loan.Status.PAID_OFF ? l.getStartDate().plusMonths(planned.size()) : null,
                extras.findByLoanIdOrderByIdAsc(l.getId()).stream().mapToLong(LoanExtraPayment::getAmountCents).sum(),
                loanStatusLabel(l.getStatus()),
                l.getEffectiveRate() != null ? l.getEffectiveRate() : SimulatedBanksProvider.effectiveRate(l.getInterestRate()),
                true, l.getApplicationId(), l.getContractedAt(), l.getPaidOutAt());
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
