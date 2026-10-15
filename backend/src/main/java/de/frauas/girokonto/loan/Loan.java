package de.frauas.girokonto.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Simuliert abgeschlossener Kredit (/F1050/). Restschuld = negativer Saldo des Kreditkontos. */
@Entity
@Table(name = "loan")
public class Loan {

    /**
     * CONTRACTED = Vertrag geschlossen, noch kein Geld geflossen; ACTIVE = ausgezahlt, Raten laufen;
     * PAID_OFF = zurückgezahlt; CANCELLED = Vertrag vor der Auszahlung widerrufen.
     */
    public enum Status { ACTIVE, PAID_OFF, CONTRACTED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_code", nullable = false)
    private Bank bank;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanPurpose purpose;

    @Column(name = "principal_cents", nullable = false)
    private long principalCents;

    @Column(name = "equity_cents", nullable = false)
    private long equityCents;

    @Column(name = "interest_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "monthly_rate_cents", nullable = false)
    private long monthlyRateCents;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "paid_installments", nullable = false)
    private int paidInstallments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "effective_rate", precision = 6, scale = 4)
    private BigDecimal effectiveRate;

    @Column(name = "contracted_at")
    private Instant contractedAt;

    @Column(name = "paid_out_at")
    private Instant paidOutAt;

    protected Loan() {
    }

    public Loan(Long userId, Long accountId, Bank bank, LoanPurpose purpose, long principalCents, long equityCents,
                BigDecimal interestRate, int termMonths, long monthlyRateCents, LocalDate startDate, Instant createdAt) {
        this.userId = userId;
        this.accountId = accountId;
        this.bank = bank;
        this.bankName = bank.label();
        this.purpose = purpose;
        this.principalCents = principalCents;
        this.equityCents = equityCents;
        this.interestRate = interestRate;
        this.termMonths = termMonths;
        this.monthlyRateCents = monthlyRateCents;
        this.startDate = startDate;
        this.nextDueDate = startDate.plusMonths(1);
        this.createdAt = createdAt;
    }

    /** Vertrag geschlossen, aber noch nicht ausgezahlt (Schritt 3 von 4). */
    public static Loan contracted(Long userId, Long accountId, Bank bank, LoanPurpose purpose, long principalCents,
                                  long equityCents, BigDecimal interestRate, BigDecimal effectiveRate, int termMonths,
                                  long monthlyRateCents, LocalDate today, Long applicationId, Instant now) {
        Loan l = new Loan(userId, accountId, bank, purpose, principalCents, equityCents, interestRate, termMonths,
                monthlyRateCents, today, now);
        l.status = Status.CONTRACTED;
        l.nextDueDate = null;
        l.effectiveRate = effectiveRate;
        l.applicationId = applicationId;
        l.contractedAt = now;
        return l;
    }

    /** Auszahlung (Schritt 4): ab jetzt laufen die Raten, die erste einen Monat später. */
    public void paidOut(LocalDate today, Instant now) {
        status = Status.ACTIVE;
        startDate = today;
        nextDueDate = today.plusMonths(1);
        paidOutAt = now;
    }

    public void cancel() {
        status = Status.CANCELLED;
        nextDueDate = null;
    }

    public Long getApplicationId() { return applicationId; }
    public BigDecimal getEffectiveRate() { return effectiveRate; }
    public Instant getContractedAt() { return contractedAt; }
    public Instant getPaidOutAt() { return paidOutAt; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAccountId() { return accountId; }
    public Bank getBank() { return bank; }
    public String getBankName() { return bankName; }
    public LoanPurpose getPurpose() { return purpose; }
    public long getPrincipalCents() { return principalCents; }
    public long getEquityCents() { return equityCents; }
    public BigDecimal getInterestRate() { return interestRate; }
    public int getTermMonths() { return termMonths; }
    public long getMonthlyRateCents() { return monthlyRateCents; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public int getPaidInstallments() { return paidInstallments; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    /** Nach einer bezahlten Rate: nächste Fälligkeit einen Monat nach Start + Anzahl Raten. */
    public void installmentPaid() {
        paidInstallments++;
        nextDueDate = startDate.plusMonths(paidInstallments + 1L);
    }

    public void markPaidOff() {
        status = Status.PAID_OFF;
    }
}
