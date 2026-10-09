package rw.ikimina.loans.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.ledger.JournalReversed;
import rw.ikimina.ledger.JournalType;
import rw.ikimina.loans.internal.LoanStateMachine.Action;
import rw.ikimina.loans.internal.LoanStateMachine.Status;
import rw.ikimina.shared.time.BusinessTime;

/**
 * Keeps the loan records in step with the ledger: when a repayment's journal is reversed (two-step,
 * owner decision Phase 3), the repayment is marked reversed and exactly what it paid is owed again -
 * in the same transaction as the reversing journal. A settled loan reopens.
 */
@Component
class RepaymentReversalListener {

    private final LoanRepaymentRepository repayments;
    private final RepaymentAllocationRepository allocations;
    private final LoanInstallmentRepository installments;
    private final LoanRepository loans;
    private final AuditService audit;
    private final Clock clock;

    RepaymentReversalListener(LoanRepaymentRepository repayments, RepaymentAllocationRepository allocations,
                              LoanInstallmentRepository installments, LoanRepository loans, AuditService audit, Clock clock) {
        this.repayments = repayments;
        this.allocations = allocations;
        this.installments = installments;
        this.loans = loans;
        this.audit = audit;
        this.clock = clock;
    }

    @EventListener
    void onReversal(JournalReversed event) {
        if (event.originalType() != JournalType.LOAN_REPAYMENT) {
            return;
        }
        repayments.findByGroupIdAndJournalId(event.groupId(), event.journalId()).ifPresent(repayment -> {
            Loan loan = loans.findByGroupIdAndIdForUpdate(event.groupId(), repayment.getLoanId()).orElseThrow();
            LocalDate today = BusinessTime.today(clock);
            repayment.markReversed(clock.instant());
            List<RepaymentAllocation> paid = allocations.findByGroupIdAndRepaymentIdAndReversedAtIsNull(event.groupId(), repayment.getId());
            List<LoanInstallment> schedule = installments.findByLoanForUpdate(event.groupId(), loan.getId());
            for (RepaymentAllocation allocation : paid) {
                schedule.stream().filter(i -> i.getId().equals(allocation.getInstallmentId())).findFirst().orElseThrow()
                        .applyPayment(allocation.getInterest().negate(), allocation.getPrincipal().negate(), today, loan.getGraceDays());
                allocation.markReversed(clock.instant());
            }
            Status before = loan.getStatus();
            if (before == Status.SETTLED) {
                loan.apply(Action.REOPEN, clock.instant());
            }
            RepaymentService.refreshLoanStatus(loan, schedule, clock.instant());
            repayments.flush();
            allocations.flush();
            installments.flush();
            loans.flush();
            audit.record(AuditEvent.of("LOAN_REPAYMENT_REVERSED").entity("loan_repayment", repayment.getPublicId())
                    .before(Map.of("loanStatus", before))
                    .after(Map.of("loan", loan.getPublicId(), "amount", repayment.getAmount(), "loanStatus", loan.getStatus())));
        });
    }
}
