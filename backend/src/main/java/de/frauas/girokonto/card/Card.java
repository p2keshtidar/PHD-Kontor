package de.frauas.girokonto.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Virtuelle Debitkarte, eine pro Nutzer, gebunden an das Girokonto (/F310/). */
@Entity
@Table(name = "card")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "pan_encrypted", nullable = false)
    private String panEncrypted;

    @Column(name = "pan_last4", nullable = false)
    private String panLast4;

    @Column(name = "cvc_encrypted", nullable = false)
    private String cvcEncrypted;

    @Column(name = "expiry_month", nullable = false)
    private int expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private int expiryYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status = CardStatus.ACTIVE;

    @Column(name = "daily_limit_cents", nullable = false)
    private long dailyLimitCents;

    @Column(name = "online_enabled", nullable = false)
    private boolean onlineEnabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Card() {
    }

    public Card(Long userId, Long accountId, String panEncrypted, String panLast4, String cvcEncrypted,
                int expiryMonth, int expiryYear, long dailyLimitCents, Instant createdAt) {
        this.userId = userId;
        this.accountId = accountId;
        this.panEncrypted = panEncrypted;
        this.panLast4 = panLast4;
        this.cvcEncrypted = cvcEncrypted;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.dailyLimitCents = dailyLimitCents;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAccountId() { return accountId; }
    public String getPanEncrypted() { return panEncrypted; }
    public String getPanLast4() { return panLast4; }
    public String getCvcEncrypted() { return cvcEncrypted; }
    public int getExpiryMonth() { return expiryMonth; }
    public int getExpiryYear() { return expiryYear; }
    public CardStatus getStatus() { return status; }
    public void setStatus(CardStatus status) { this.status = status; }
    public long getDailyLimitCents() { return dailyLimitCents; }
    public void setDailyLimitCents(long dailyLimitCents) { this.dailyLimitCents = dailyLimitCents; }
    public boolean isOnlineEnabled() { return onlineEnabled; }
    public void setOnlineEnabled(boolean onlineEnabled) { this.onlineEnabled = onlineEnabled; }
    public Instant getCreatedAt() { return createdAt; }
}
