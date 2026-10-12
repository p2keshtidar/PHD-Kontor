package de.frauas.girokonto.savings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Verlaufspunkt für das Diagramm (/F830/). */
@Entity
@Table(name = "plan_snapshot")
public class PlanSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "value_cents", nullable = false)
    private long valueCents;

    @Column(name = "deposited_cents", nullable = false)
    private long depositedCents;

    protected PlanSnapshot() {
    }

    public PlanSnapshot(Long planId, LocalDate snapshotDate, long valueCents, long depositedCents) {
        this.planId = planId;
        this.snapshotDate = snapshotDate;
        this.valueCents = valueCents;
        this.depositedCents = depositedCents;
    }

    public LocalDate getSnapshotDate() { return snapshotDate; }
    public long getValueCents() { return valueCents; }
    public long getDepositedCents() { return depositedCents; }
}
