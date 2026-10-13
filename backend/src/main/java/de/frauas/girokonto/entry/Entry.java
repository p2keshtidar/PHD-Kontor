package de.frauas.girokonto.entry;

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

/**
 * Selbst eingetragene Einnahme oder Ausgabe (z. B. Gehalt, Miete, Urlaub).
 * Einmalig ({@code intervalMonths == 0}) oder regelmäßig alle 1, 3, 6 oder 12 Monate.
 */
@Entity
@Table(name = "entry")
public class Entry {

    public enum Kind { INCOME, EXPENSE }

    public enum Status { ACTIVE, PAUSED, DONE, DELETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "interval_months", nullable = false)
    private int intervalMonths;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "last_booked_date")
    private LocalDate lastBookedDate;

    @Column(name = "booked_count", nullable = false)
    private int bookedCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Entry() {
    }

    public Entry(Long userId, Kind kind, String title, String category, long amountCents, int intervalMonths,
                 LocalDate startDate, LocalDate endDate, Instant createdAt) {
        this.userId = userId;
        this.kind = kind;
        this.title = title;
        this.category = category;
        this.amountCents = amountCents;
        this.intervalMonths = intervalMonths;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
    }

    public boolean isOnce() {
        return intervalMonths == 0;
    }

    /** Buchungstag im Monat. Regelmäßige Einträge: Tag des Startdatums (höchstens 28). */
    public int day() {
        return startDate.getDayOfMonth();
    }

    /** Ist an diesem Tag eine Buchung fällig? (unabhängig vom Status) */
    public boolean isDueOn(LocalDate date) {
        if (isOnce()) {
            return date.equals(startDate);
        }
        if (date.getDayOfMonth() != day() || date.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && date.isAfter(endDate)) {
            return false;
        }
        long months = ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(date));
        return months % intervalMonths == 0;
    }

    /** Nächster Buchungstag nach {@code today} oder null, wenn keiner mehr kommt. */
    public LocalDate nextAfter(LocalDate today) {
        if (status == Status.DONE || status == Status.DELETED) {
            return null;
        }
        if (isOnce()) {
            return startDate.isAfter(today) ? startDate : null;
        }
        LocalDate d = startDate;
        while (!d.isAfter(today)) {
            d = d.plusMonths(intervalMonths);
        }
        d = d.withDayOfMonth(day());
        return endDate != null && d.isAfter(endDate) ? null : d;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Kind getKind() { return kind; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }
    public int getIntervalMonths() { return intervalMonths; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDate getLastBookedDate() { return lastBookedDate; }
    public void setLastBookedDate(LocalDate lastBookedDate) { this.lastBookedDate = lastBookedDate; }
    public int getBookedCount() { return bookedCount; }
    public void setBookedCount(int bookedCount) { this.bookedCount = bookedCount; }
    public Instant getCreatedAt() { return createdAt; }
}
