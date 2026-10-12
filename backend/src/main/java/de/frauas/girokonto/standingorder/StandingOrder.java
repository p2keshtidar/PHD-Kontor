package de.frauas.girokonto.standingorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** /K030/: Dauerauftrag – regelmäßige Überweisung an einen anderen Nutzer. */
@Entity
@Table(name = "standing_order")
public class StandingOrder {

    public enum Status { ACTIVE, PAUSED, ENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipient_iban", nullable = false)
    private String recipientIban;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false)
    private String reference;

    @Column(name = "execution_day", nullable = false)
    private int executionDay;

    @Column(name = "interval_months", nullable = false)
    private int intervalMonths;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "last_executed_month")
    private String lastExecutedMonth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StandingOrder() {
    }

    public StandingOrder(Long userId, String recipientIban, String recipientName, long amountCents, String reference,
                         int executionDay, int intervalMonths, LocalDate startDate, LocalDate endDate,
                         Instant createdAt) {
        this.userId = userId;
        this.recipientIban = recipientIban;
        this.recipientName = recipientName;
        this.amountCents = amountCents;
        this.reference = reference;
        this.executionDay = executionDay;
        this.intervalMonths = intervalMonths;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
    }

    /** Ist der Auftrag an diesem Tag fällig? (Tag, Intervall ab Startmonat, Zeitraum) */
    public boolean isDueOn(LocalDate date) {
        if (date.getDayOfMonth() != executionDay || date.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && date.isAfter(endDate)) {
            return false;
        }
        long months = ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(date));
        return months % intervalMonths == 0;
    }

    /** Nächster Ausführungstag nach {@code today} (oder null, wenn keiner mehr kommt). */
    public LocalDate nextExecutionAfter(LocalDate today) {
        if (status == Status.ENDED) {
            return null;
        }
        LocalDate d = startDate.isAfter(today) ? startDate : null;
        if (d == null) {
            YearMonth ym = YearMonth.from(startDate);
            while (!ym.atDay(executionDay).isAfter(today)) {
                ym = ym.plusMonths(intervalMonths);
            }
            d = ym.atDay(executionDay);
        }
        return endDate != null && d.isAfter(endDate) ? null : d;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getRecipientIban() { return recipientIban; }
    public String getRecipientName() { return recipientName; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public int getExecutionDay() { return executionDay; }
    public void setExecutionDay(int executionDay) { this.executionDay = executionDay; }
    public int getIntervalMonths() { return intervalMonths; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getLastExecutedMonth() { return lastExecutedMonth; }
    public void setLastExecutedMonth(String lastExecutedMonth) { this.lastExecutedMonth = lastExecutedMonth; }
    public Instant getCreatedAt() { return createdAt; }
}
