package rw.ikimina.savings.internal;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContributionAllocationRepository extends JpaRepository<ContributionAllocation, Long> {

    List<ContributionAllocation> findByTransactionIdAndReversedAtIsNull(Long transactionId);

    List<ContributionAllocation> findByTransactionIdInAndReversedAtIsNull(Collection<Long> transactionIds);
}
