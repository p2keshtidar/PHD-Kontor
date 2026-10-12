package de.frauas.girokonto.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** /K020/: eine Sondertilgung. Sie senkt die Restschuld sofort; die Monatsrate bleibt gleich, die Laufzeit sinkt. */
@Entity
@Table(name = "loan_extra_payment")
public class LoanExtraPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    /** Gezahlt nach Rate Nr. X (0 = vor der ersten Rate). */
    @Column(name = "after_installment", nullable = false)
    private int afterInstallment;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoanExtraPayment() {
    }

    public LoanExtraPayment(Long loanId, Long bookingId, long amountCents, int afterInstallment, LocalDate paidOn,
                            Instant createdAt) {
        this.loanId = loanId;
        this.bookingId = bookingId;
        this.amountCents = amountCents;
        this.afterInstallment = afterInstallment;
        this.paidOn = paidOn;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getLoanId() { return loanId; }
    public Long getBookingId() { return bookingId; }
    public long getAmountCents() { return amountCents; }
    public int getAfterInstallment() { return afterInstallment; }
    public LocalDate getPaidOn() { return paidOn; }
}
