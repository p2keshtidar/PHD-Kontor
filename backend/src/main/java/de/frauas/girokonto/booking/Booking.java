package de.frauas.girokonto.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingType type;

    @Column(nullable = false, length = 140)
    private String description;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_by")
    private Long createdBy;

    protected Booking() {
    }

    public Booking(BookingType type, String description, Instant bookedAt, String idempotencyKey, Long createdBy) {
        this.type = type;
        this.description = description;
        this.bookedAt = bookedAt;
        this.idempotencyKey = idempotencyKey;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public BookingType getType() { return type; }
    public String getDescription() { return description; }
    public Instant getBookedAt() { return bookedAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getCreatedBy() { return createdBy; }
}
