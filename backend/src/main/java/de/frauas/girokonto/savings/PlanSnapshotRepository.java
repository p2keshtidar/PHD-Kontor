package de.frauas.girokonto.savings;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanSnapshotRepository extends JpaRepository<PlanSnapshot, Long> {

    List<PlanSnapshot> findByPlanIdOrderBySnapshotDateAscIdAsc(Long planId);
}
