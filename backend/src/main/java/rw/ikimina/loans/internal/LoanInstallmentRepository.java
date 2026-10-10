package rw.ikimina.loans.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, Long> {

    List<LoanInstallment> findByGroupIdAndLoanIdOrderByInstallmentNo(Long groupId, Long loanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from LoanInstallment i where i.groupId = :groupId and i.loanId = :loanId order by i.installmentNo")
    List<LoanInstallment> findByLoanForUpdate(@Param("groupId") Long groupId, @Param("loanId") Long loanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from LoanInstallment i where i.groupId = :groupId and i.id in :ids order by i.installmentNo")
    List<LoanInstallment> findByIdsForUpdate(@Param("groupId") Long groupId, @Param("ids") Collection<Long> ids);

    /** Loans with an unpaid installment whose due date plus the loan's grace days is before {@code today} (spec 9.7). */
    @Query(value = """
            SELECT DISTINCT i.loan_id FROM loan_installments i
            JOIN loans l ON l.id = i.loan_id AND l.group_id = i.group_id
            WHERE i.group_id = :groupId AND l.status IN ('DISBURSED', 'OVERDUE')
              AND (i.status IN ('PENDING', 'PARTIAL') AND i.due_date + l.grace_days < :today
                   OR i.status = 'OVERDUE' AND l.status = 'DISBURSED')
            ORDER BY i.loan_id""", nativeQuery = true)
    List<Long> loansWithNewlyOverdueInstallments(@Param("groupId") long groupId, @Param("today") LocalDate today);
}
