package rw.ikimina.loans.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {

    Optional<LoanRepayment> findByGroupIdAndJournalId(Long groupId, Long journalId);

    Optional<LoanRepayment> findByGroupIdAndIdempotencyKey(Long groupId, String idempotencyKey);

    List<LoanRepayment> findByGroupIdAndLoanIdOrderByBusinessDateAscIdAsc(Long groupId, Long loanId);

    boolean existsByGroupIdAndExternalRefAndReversedAtIsNull(Long groupId, String externalRef);
}
