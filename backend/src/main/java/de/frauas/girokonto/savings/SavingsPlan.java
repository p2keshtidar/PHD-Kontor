package de.frauas.girokonto.savings;

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

/** Spar- oder Investplan (/F610/, /F840/). */
@Entity
@Table(name = "savings_plan")
public class SavingsPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Unterkonto für den Sparkonto-Anteil (nur Sparplan) */
    @Column(name = "account_id")
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType type;

    @Column(nullable = false)
    private String name;

    private String icon;

    @Column(name = "target_cents")
    private Long targetCents;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "monthly_rate_cents", nullable = false)
    private long monthlyRateCents;

    @Column(name = "execution_day", nullable = false)
    private int executionDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status = PlanStatus.ACTIVE;

    @Column(name = "deposited_cents", nullable = false)
    private long depositedCents;

    @Column(name = "last_executed_month")
    private String lastExecutedMonth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SavingsPlan() {
    }

    public SavingsPlan(Long userId, Long accountId, PlanType type, String name, String icon, Long targetCents,
                       LocalDate targetDate, long monthlyRateCents, int executionDay, RiskLevel riskLevel,
                       Instant createdAt) {
        this.userId = userId;
        this.accountId = accountId;
        this.type = type;
        this.name = name;
        this.icon = icon;
        this.targetCents = targetCents;
        this.targetDate = targetDate;
        this.monthlyRateCents = monthlyRateCents;
        this.executionDay = executionDay;
        this.riskLevel = riskLevel;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAccountId() { return accountId; }
    public PlanType getType() { return type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Long getTargetCents() { return targetCents; }
    public void setTargetCents(Long targetCents) { this.targetCents = targetCents; }
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
    public long getMonthlyRateCents() { return monthlyRateCents; }
    public void setMonthlyRateCents(long monthlyRateCents) { this.monthlyRateCents = monthlyRateCents; }
    public int getExecutionDay() { return executionDay; }
    public void setExecutionDay(int executionDay) { this.executionDay = executionDay; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }
    public long getDepositedCents() { return depositedCents; }
    public void addDeposited(long cents) { this.depositedCents += cents; }
    public String getLastExecutedMonth() { return lastExecutedMonth; }
    public void setLastExecutedMonth(String lastExecutedMonth) { this.lastExecutedMonth = lastExecutedMonth; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isOpen() {
        return status == PlanStatus.ACTIVE || status == PlanStatus.PAUSED;
    }
}
