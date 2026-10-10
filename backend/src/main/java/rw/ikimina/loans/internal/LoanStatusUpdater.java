package rw.ikimina.loans.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.loans.LoanBecameOverdue;
import rw.ikimina.loans.internal.LoanStateMachine.Action;
import rw.ikimina.loans.internal.LoanStateMachine.Status;

/**
 * Keeps a disbursed loan's status in step with its installments: settled when nothing is owed,
 * otherwise overdue exactly while an installment is. Every path that can change it - a repayment,
 * a reversed repayment, the nightly job - comes here, so a loan never becomes OVERDUE or SETTLED
 * without its audit row (H8) and, for OVERDUE, the {@link LoanBecameOverdue} event (spec 9.7).
 */
@Component
class LoanStatusUpdater {

    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    LoanStatusUpdater(AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** @return the status the loan had before */
    Status refresh(Loan loan, List<LoanInstallment> schedule) {
        Status before = loan.getStatus();
        apply(loan, schedule, clock.instant());
        Status after = loan.getStatus();
        if (after == Status.OVERDUE && before != Status.OVERDUE) {
            audit.record(AuditEvent.of("LOAN_OVERDUE").entity("loan", loan.getPublicId())
                    .before(Map.of("status", before)).after(Map.of("status", after)));
            events.publishEvent(new LoanBecameOverdue(loan.getGroupId(), loan.getPublicId(), loan.getBorrowerMembershipId()));
        } else if (after == Status.SETTLED && before != Status.SETTLED) {
            audit.record(AuditEvent.of("LOAN_SETTLED").entity("loan", loan.getPublicId()).before(Map.of("status", before)));
        }
        return before;
    }

    static void apply(Loan loan, List<LoanInstallment> schedule, Instant now) {
        boolean paid = schedule.stream().allMatch(i -> i.getStatus() == LoanInstallment.Status.PAID);
        boolean overdue = schedule.stream().anyMatch(i -> i.getStatus() == LoanInstallment.Status.OVERDUE);
        if (paid) {
            LoanStateMachine.target(loan.getStatus(), Action.SETTLE).ifPresent(s -> loan.apply(Action.SETTLE, now));
        } else if (overdue && loan.getStatus() == Status.DISBURSED) {
            loan.apply(Action.MARK_OVERDUE, now);
        } else if (!overdue && loan.getStatus() == Status.OVERDUE) {
            loan.apply(Action.CLEAR_OVERDUE, now);
        }
    }
}
