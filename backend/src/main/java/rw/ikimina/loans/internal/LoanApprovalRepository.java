package rw.ikimina.loans.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanApprovalRepository extends JpaRepository<LoanApproval, Long> {

    List<LoanApproval> findByGroupIdAndLoanIdOrderByDecidedAtAscIdAsc(Long groupId, Long loanId);

    boolean existsByGroupIdAndLoanIdAndApproverMembershipId(Long groupId, Long loanId, Long approverMembershipId);
}
