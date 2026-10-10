package rw.ikimina.savings.internal;

import java.time.Clock;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.ledger.JournalReversed;
import rw.ikimina.ledger.JournalReversing;
import rw.ikimina.ledger.JournalType;

/**
 * When a withdrawal payout is reversed (two-step, like a contribution), the money is back in the
 * member's savings: the savings transaction and the withdrawal are marked reversed in the same
 * transaction as the reversing journal. The withdrawal row is locked on {@link JournalReversing},
 * before the ledger locks any balance - the order its payout uses.
 */
@Component
class WithdrawalReversalListener {

    private final SavingsTransactionRepository transactions;
    private final SavingsWithdrawalRepository withdrawals;
    private final AuditService audit;
    private final Clock clock;

    WithdrawalReversalListener(SavingsTransactionRepository transactions, SavingsWithdrawalRepository withdrawals, AuditService audit,
                               Clock clock) {
        this.transactions = transactions;
        this.withdrawals = withdrawals;
        this.audit = audit;
        this.clock = clock;
    }

    @EventListener
    void beforeReversal(JournalReversing event) {
        if (event.originalType() != JournalType.WITHDRAWAL) {
            return;
        }
        transactions.findByGroupIdAndJournalId(event.groupId(), event.journalId())
                .ifPresent(txn -> withdrawals.findByGroupIdAndTransactionIdForUpdate(event.groupId(), txn.getId()));
    }

    @EventListener
    void onReversal(JournalReversed event) {
        if (event.originalType() != JournalType.WITHDRAWAL) {
            return;
        }
        transactions.findByGroupIdAndJournalId(event.groupId(), event.journalId()).ifPresent(txn -> {
            txn.markReversed(clock.instant());
            withdrawals.findByGroupIdAndTransactionIdForUpdate(event.groupId(), txn.getId()).ifPresent(w -> {
                w.markReversed(clock.instant());
                audit.record(AuditEvent.of("WITHDRAWAL_REVERSED").entity("savings_withdrawal", w.getPublicId())
                        .after(Map.of("amount", w.getAmount())));
            });
            transactions.flush();
            withdrawals.flush();
        });
    }
}
