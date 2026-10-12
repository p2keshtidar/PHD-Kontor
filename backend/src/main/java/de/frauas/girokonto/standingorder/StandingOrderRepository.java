package de.frauas.girokonto.standingorder;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandingOrderRepository extends JpaRepository<StandingOrder, Long> {

    List<StandingOrder> findByUserIdAndStatusNotOrderByIdAsc(Long userId, StandingOrder.Status status);

    List<StandingOrder> findByStatusAndExecutionDay(StandingOrder.Status status, int executionDay);

    Optional<StandingOrder> findByIdAndUserId(Long id, Long userId);
}
