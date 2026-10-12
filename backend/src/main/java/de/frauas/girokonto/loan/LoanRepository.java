package de.frauas.girokonto.loan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByUserIdOrderByIdAsc(Long userId);

    Optional<Loan> findByIdAndUserId(Long id, Long userId);

    List<Loan> findByStatusAndNextDueDateLessThanEqual(Loan.Status status, LocalDate date);
}
