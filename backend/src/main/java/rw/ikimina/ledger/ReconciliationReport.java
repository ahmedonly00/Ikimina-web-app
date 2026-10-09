package rw.ikimina.ledger;

import java.util.List;

/**
 * Result of reconciling the ledger (spec 7.1 #2): stored balances against the sum of the lines,
 * and every journal against double entry.
 */
public record ReconciliationReport(List<GroupResult> groups) {

    /**
     * @param mismatchedAccountIds accounts whose stored balance differs from the sum of their lines
     * @param unbalancedJournalIds journals with fewer than two lines or debits not equal to credits
     */
    public record GroupResult(long groupId, int accountsChecked, List<Long> mismatchedAccountIds, List<Long> unbalancedJournalIds,
                              String error) {
        public boolean clean() {
            return error == null && mismatchedAccountIds.isEmpty() && unbalancedJournalIds.isEmpty();
        }
    }

    public boolean clean() {
        return groups.stream().allMatch(GroupResult::clean);
    }

    public List<GroupResult> problems() {
        return groups.stream().filter(group -> !group.clean()).toList();
    }
}
