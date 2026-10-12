package de.frauas.girokonto.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Eine Zeile einer Buchung: positiver Betrag = Eingang, negativer Betrag = Ausgang (in Cent). */
@Entity
@Table(name = "booking_line")
public class BookingLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    /** Kategorie aus Sicht dieses Kontos (/F560/), als Name des Enums insights.Category. */
    private String category;

    protected BookingLine() {
    }

    public BookingLine(Long bookingId, Long accountId, long amountCents, String category) {
        this.bookingId = bookingId;
        this.accountId = accountId;
        this.amountCents = amountCents;
        this.category = category;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public Long getAccountId() { return accountId; }
    public long getAmountCents() { return amountCents; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
