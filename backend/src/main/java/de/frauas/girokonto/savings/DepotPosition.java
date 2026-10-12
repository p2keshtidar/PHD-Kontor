package de.frauas.girokonto.savings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Anteile eines Fonds im Musterdepot eines Plans (/F820/). */
@Entity
@Table(name = "depot_position")
public class DepotPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fund_code", nullable = false)
    private Fund fund;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal units = BigDecimal.ZERO;

    @Column(name = "invested_cents", nullable = false)
    private long investedCents;

    protected DepotPosition() {
    }

    public DepotPosition(Long planId, Fund fund) {
        this.planId = planId;
        this.fund = fund;
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public Fund getFund() { return fund; }
    public BigDecimal getUnits() { return units; }
    public long getInvestedCents() { return investedCents; }

    public void buy(BigDecimal addUnits, long cents) {
        this.units = this.units.add(addUnits);
        this.investedCents += cents;
    }

    public void clear() {
        this.units = BigDecimal.ZERO;
        this.investedCents = 0;
    }
}
