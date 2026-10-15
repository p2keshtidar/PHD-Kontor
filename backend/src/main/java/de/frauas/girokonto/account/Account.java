package de.frauas.girokonto.account;

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
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id")
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private String name;

    private String icon;

    @Column(unique = true)
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Digitale Konten: Zweck (siehe {@link DigitalPurpose}), optional Zielbetrag und Zieldatum. */
    private String purpose;

    @Column(name = "target_cents")
    private Long targetCents;

    @Column(name = "target_date")
    private java.time.LocalDate targetDate;

    @Column(name = "goal_reached_at")
    private Instant goalReachedAt;

    /** Tagesgeld: Zinssatz der Simulation in Prozent pro Jahr (fiktiv, beim Eröffnen festgelegt). */
    @Column(name = "interest_rate")
    private java.math.BigDecimal interestRate;

    protected Account() {
    }

    public Account(Long ownerId, AccountType type, String name, String icon, String iban, Instant createdAt) {
        this.ownerId = ownerId;
        this.type = type;
        this.name = name;
        this.icon = icon;
        this.iban = iban;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public AccountType getType() { return type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getIban() { return iban; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public java.math.BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(java.math.BigDecimal interestRate) { this.interestRate = interestRate; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Long getTargetCents() { return targetCents; }
    public void setTargetCents(Long targetCents) { this.targetCents = targetCents; }
    public java.time.LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(java.time.LocalDate targetDate) { this.targetDate = targetDate; }
    public Instant getGoalReachedAt() { return goalReachedAt; }
    public void setGoalReachedAt(Instant goalReachedAt) { this.goalReachedAt = goalReachedAt; }
}
