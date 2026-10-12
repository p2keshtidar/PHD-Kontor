package de.frauas.girokonto.savings;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsPlanRepository extends JpaRepository<SavingsPlan, Long> {

    List<SavingsPlan> findByUserIdAndStatusInOrderByIdAsc(Long userId, Collection<PlanStatus> statuses);

    List<SavingsPlan> findByStatusAndExecutionDay(PlanStatus status, int executionDay);

    Optional<SavingsPlan> findByIdAndUserId(Long id, Long userId);
}
