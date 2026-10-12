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

    public enum Status { ACTIVE, PAID_OFF }

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

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "paid_installments", nullable = false)
    private int paidInstallments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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
