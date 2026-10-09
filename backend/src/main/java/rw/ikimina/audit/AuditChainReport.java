package rw.ikimina.audit;

import java.util.List;

/**
 * Result of verifying every audit chain.
 *
 * @param chains one entry per chain (group id, or 0 for the platform chain)
 */
public record AuditChainReport(List<ChainResult> chains) {

    /**
     * @param firstBrokenAuditId id of the first row whose hash or link does not verify; null when intact
     * @param problem            what was wrong; null when intact
     */
    public record ChainResult(long chainKey, int rowsChecked, Long firstBrokenAuditId, String problem) {
        public boolean intact() {
            return problem == null;
        }
    }

    public boolean allIntact() {
        return chains.stream().allMatch(ChainResult::intact);
    }

    public List<ChainResult> broken() {
        return chains.stream().filter(chain -> !chain.intact()).toList();
    }
}
