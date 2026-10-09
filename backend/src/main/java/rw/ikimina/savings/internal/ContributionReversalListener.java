package rw.ikimina.savings.internal;

import java.time.Clock;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.ledger.JournalReversed;
import rw.ikimina.ledger.JournalType;

/**
 * Keeps the savings records in step with the ledger: when a contribution's journal is reversed,
 * the contribution is marked reversed and whatever it paid is owed again - in the same
 * transaction as the reversing journal. Other credit the member has then settles what it can.
 */
@Component
class ContributionReversalListener {

    private final SavingsTransactionRepository transactions;
    private final ObligationAllocator allocator;
    private final AuditService audit;
    private final Clock clock;

    ContributionReversalListener(SavingsTransactionRepository transactions, ObligationAllocator allocator, AuditService audit,
                                 Clock clock) {
        this.transactions = transactions;
        this.allocator = allocator;
        this.audit = audit;
        this.clock = clock;
    }

    @EventListener
    void onReversal(JournalReversed event) {
        if (event.originalType() != JournalType.CONTRIBUTION) {
            return;
        }
        transactions.findByGroupIdAndJournalId(event.groupId(), event.journalId()).ifPresent(txn -> {
            txn.markReversed(clock.instant());
            transactions.flush();
            allocator.undo(event.groupId(), txn);
            allocator.settle(event.groupId(), txn.getMembershipId(), txn.getBucketId(), null);
            audit.record(AuditEvent.of("CONTRIBUTION_REVERSED").entity("savings_transaction", txn.getPublicId())
                    .after(Map.of("amount", txn.getAmount())));
        });
    }
}
