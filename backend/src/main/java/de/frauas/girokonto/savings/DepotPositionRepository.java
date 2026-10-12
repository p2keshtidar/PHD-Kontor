package de.frauas.girokonto.savings;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotPositionRepository extends JpaRepository<DepotPosition, Long> {

    List<DepotPosition> findByPlanIdOrderByFundAsc(Long planId);

    Optional<DepotPosition> findByPlanIdAndFund(Long planId, Fund fund);
}
