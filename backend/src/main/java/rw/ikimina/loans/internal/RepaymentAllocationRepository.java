package rw.ikimina.loans.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepaymentAllocationRepository extends JpaRepository<RepaymentAllocation, Long> {

    List<RepaymentAllocation> findByGroupIdAndRepaymentIdAndReversedAtIsNull(Long groupId, Long repaymentId);
}
