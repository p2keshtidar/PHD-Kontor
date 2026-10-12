package de.frauas.girokonto.loan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanExtraPaymentRepository extends JpaRepository<LoanExtraPayment, Long> {

    List<LoanExtraPayment> findByLoanIdOrderByIdAsc(Long loanId);
}
