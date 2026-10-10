package rw.ikimina.ledger.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.ledger.JournalType;
import rw.ikimina.ledger.Ledger;
import rw.ikimina.ledger.PostedJournal;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * Two-step reversal of a ledger entry (owner decision, Phase 2; Hard Rule H9): one officer asks,
 * giving a reason; a different officer approves, which posts the reversing journal (spec 7.1 #3),
 * or rejects. Nothing in the ledger changes until approval.
 */
@Service
class ReversalService {

    /**
     * Journals whose owning module undoes its own records when they are reversed (a JournalReversed
     * listener). A loan disbursement is not among them: a mistaken loan is cancelled before payout.
     */
    private static final Set<JournalType> REVERSIBLE = EnumSet.of(JournalType.CONTRIBUTION, JournalType.LOAN_REPAYMENT);

    record ReversalView(UUID requestId, UUID journalId, JournalType journalType, String reason, String status,
                        UUID requestedBy, UUID decidedBy, String decisionReason, UUID reversalJournalId,
                        Instant createdAt, Instant decidedAt) {
    }

    private static final String VIEW = """
            SELECT r.public_id, j.public_id AS journal_public_id, j.journal_type, r.reason, r.status,
                   req.public_id AS requested_by, dec.public_id AS decided_by, r.decision_reason,
                   rev.public_id AS reversal_public_id, r.created_at, r.decided_at
            FROM ledger_reversal_requests r
            JOIN ledger_journals j ON j.id = r.journal_id
            JOIN group_memberships req ON req.id = r.requested_by
            LEFT JOIN group_memberships dec ON dec.id = r.decided_by
            LEFT JOIN ledger_journals rev ON rev.id = r.reversal_journal_id
            WHERE r.group_id = ?""";

    private final JdbcTemplate jdbc;
    private final Ledger ledger;
    private final AuditService audit;
    private final Clock clock;

    ReversalService(JdbcTemplate jdbc, Ledger ledger, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    ReversalView request(UUID journalPublicId, String reason) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        PostedJournal journal = ledger.findJournal(journalPublicId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!REVERSIBLE.contains(journal.type())) {
            throw new ApiException(ErrorCode.JOURNAL_NOT_REVERSIBLE);
        }
        Integer reversed = jdbc.queryForObject("SELECT count(*) FROM ledger_journals WHERE group_id = ? AND reverses_journal_id = ?",
                Integer.class, scope.groupId(), journal.id());
        if (reversed != null && reversed > 0) {
            throw new ApiException(ErrorCode.JOURNAL_ALREADY_REVERSED);
        }
        UUID requestId;
        try {
            requestId = jdbc.queryForObject("""
                            INSERT INTO ledger_reversal_requests (group_id, journal_id, reason, requested_by, created_at)
                            VALUES (?, ?, ?, ?, ?) RETURNING public_id""",
                    UUID.class, scope.groupId(), journal.id(), reason.trim(), scope.membershipId(), now());
        } catch (DuplicateKeyException pending) {
            throw new ApiException(ErrorCode.JOURNAL_ALREADY_REVERSED);
        }
        audit.record(AuditEvent.of("REVERSAL_REQUESTED").entity("reversal_request", requestId)
                .after(Map.of("journal", journal.publicId(), "type", journal.type())).reason(reason.trim()));
        return find(scope.groupId(), requestId);
    }

    @Transactional
    ReversalView approve(UUID requestId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Pending pending = lockPending(scope.groupId(), requestId);
        if (pending.requestedBy() == scope.membershipId()) {
            throw new ApiException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }
        PostedJournal reversal = ledger.reverse(pending.journalId(), pending.reason());
        jdbc.update("""
                        UPDATE ledger_reversal_requests
                        SET status = 'APPROVED', decided_by = ?, reversal_journal_id = ?, decided_at = ?, version = version + 1
                        WHERE id = ?""",
                scope.membershipId(), reversal.id(), now(), pending.id());
        audit.record(AuditEvent.of("REVERSAL_APPROVED").entity("reversal_request", requestId)
                .after(Map.of("reversalJournal", reversal.publicId())));
        return find(scope.groupId(), requestId);
    }

    @Transactional
    ReversalView reject(UUID requestId, String reason) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Pending pending = lockPending(scope.groupId(), requestId);
        jdbc.update("""
                        UPDATE ledger_reversal_requests
                        SET status = 'REJECTED', decided_by = ?, decision_reason = ?, decided_at = ?, version = version + 1
                        WHERE id = ?""",
                scope.membershipId(), reason.trim(), now(), pending.id());
        audit.record(AuditEvent.of("REVERSAL_REJECTED").entity("reversal_request", requestId).reason(reason.trim()));
        return find(scope.groupId(), requestId);
    }

    @Transactional(readOnly = true)
    List<ReversalView> pending() {
        long groupId = TenantContext.requireGroup().groupId();
        return jdbc.query(VIEW + " AND r.status = 'PENDING' ORDER BY r.created_at", ReversalService::view, groupId);
    }

    private record Pending(long id, long journalId, long requestedBy, String reason) {
    }

    private Pending lockPending(long groupId, UUID requestId) {
        List<Pending> found = jdbc.query("""
                        SELECT id, journal_id, requested_by, reason, status FROM ledger_reversal_requests
                        WHERE group_id = ? AND public_id = ? FOR UPDATE""",
                (rs, n) -> "PENDING".equals(rs.getString(5))
                        ? new Pending(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4))
                        : null,
                groupId, requestId);
        if (found.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (found.getFirst() == null) {
            throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION);   // already decided
        }
        return found.getFirst();
    }

    private ReversalView find(long groupId, UUID requestId) {
        return jdbc.query(VIEW + " AND r.public_id = ?", ReversalService::view, groupId, requestId).getFirst();
    }

    private static ReversalView view(ResultSet rs, int n) throws SQLException {
        Timestamp decided = rs.getTimestamp("decided_at");
        return new ReversalView(rs.getObject("public_id", UUID.class), rs.getObject("journal_public_id", UUID.class),
                JournalType.valueOf(rs.getString("journal_type")), rs.getString("reason"), rs.getString("status"),
                rs.getObject("requested_by", UUID.class), rs.getObject("decided_by", UUID.class), rs.getString("decision_reason"),
                rs.getObject("reversal_public_id", UUID.class), rs.getTimestamp("created_at").toInstant(),
                decided == null ? null : decided.toInstant());
    }

    private OffsetDateTime now() {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }
}
