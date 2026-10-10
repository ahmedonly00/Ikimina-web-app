package rw.ikimina.loans.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.ikimina.loans.internal.LoanStateMachine.Status;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findByGroupIdAndPublicId(Long groupId, UUID publicId);

    /** Locks the loan row: approvals, disbursement and repayments of one loan run one at a time (spec 9.4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.groupId = :groupId and l.publicId = :publicId")
    Optional<Loan> findByGroupIdAndPublicIdForUpdate(@Param("groupId") Long groupId, @Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.groupId = :groupId and l.id = :id")
    Optional<Loan> findByGroupIdAndIdForUpdate(@Param("groupId") Long groupId, @Param("id") Long id);

    boolean existsByGroupIdAndBorrowerMembershipIdAndStatusIn(Long groupId, Long borrowerMembershipId, Collection<Status> statuses);

    List<Loan> findByGroupIdAndStatusIn(Long groupId, Collection<Status> statuses);

    @Query("""
            select l from Loan l
            where l.groupId = :groupId
              and (:borrower is null or l.borrowerMembershipId = :borrower)
              and (:status is null or l.status = :status)""")
    Page<Loan> search(@Param("groupId") Long groupId, @Param("borrower") Long borrower, @Param("status") Status status,
                      Pageable pageable);
}
