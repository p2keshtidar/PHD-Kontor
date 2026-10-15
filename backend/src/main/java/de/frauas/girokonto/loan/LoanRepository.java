package de.frauas.girokonto.loan;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByUserIdOrderByIdAsc(Long userId);

    Optional<Loan> findByIdAndUserId(Long id, Long userId);

    List<Loan> findByStatusAndNextDueDateLessThanEqual(Loan.Status status, LocalDate date);

    /** Auszahlung und Widerruf nacheinander (nie doppelt auszahlen). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id and l.userId = :userId")
    Optional<Loan> findForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
