package rw.ikimina.loans.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanDisbursementRepository extends JpaRepository<LoanDisbursement, Long> {

    Optional<LoanDisbursement> findByGroupIdAndLoanId(Long groupId, Long loanId);

    Optional<LoanDisbursement> findByGroupIdAndIdempotencyKey(Long groupId, String idempotencyKey);
}
