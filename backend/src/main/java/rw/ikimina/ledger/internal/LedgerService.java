package rw.ikimina.ledger.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.ledger.AccountRef;
import rw.ikimina.ledger.AccountType;
import rw.ikimina.ledger.Direction;
import rw.ikimina.ledger.JournalRequest;
import rw.ikimina.ledger.JournalReversed;
import rw.ikimina.ledger.JournalSource;
import rw.ikimina.ledger.JournalType;
import rw.ikimina.ledger.Ledger;
import rw.ikimina.ledger.PostedJournal;
import rw.ikimina.ledger.StatementEntry;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.security.CurrentUser;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * The only writer of the ledger tables (spec 7.1 #5).
 *
 * <p>Posting, in order:
 * <ol>
 *   <li>validate - at least two lines, every amount rounded to a whole franc at this boundary
 *       (spec 4.3) and positive, debits equal credits;</li>
 *   <li>claim the idempotency key with {@code INSERT ... ON CONFLICT DO NOTHING}: a concurrent
 *       retry waits for the first and then sees its journal, instead of failing (H7);</li>
 *   <li>create accounts on first use, write the lines;</li>
 *   <li>lock the touched balance rows in account-id order ({@code FOR UPDATE}) - a fixed order,
 *       so two postings can never deadlock - and apply the deltas with a version check (spec 7.3).</li>
 * </ol>
 * A deferred database trigger re-checks every journal's balance at commit.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class LedgerService implements Ledger {

    private static final String CLAIM_KEY = """
            INSERT INTO ledger_journals (group_id, journal_type, idempotency_key, request_hash, business_date, description,
                                         source, external_ref, member_id, reverses_journal_id, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (group_id, idempotency_key) DO NOTHING
            RETURNING id, public_id""";

    private static final String ENSURE_ACCOUNT = """
            INSERT INTO ledger_accounts (group_id, account_type, membership_id, bucket_id, loan_id, normal_side)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (group_id, account_type, COALESCE(membership_id, 0), COALESCE(bucket_id, 0), COALESCE(loan_id, 0))
            DO NOTHING""";

    private static final String FIND_ACCOUNT = """
            SELECT id FROM ledger_accounts
            WHERE group_id = ? AND account_type = ?
              AND COALESCE(membership_id, 0) = ? AND COALESCE(bucket_id, 0) = ? AND COALESCE(loan_id, 0) = ?""";

    private record ExistingJournal(long id, UUID publicId, String type, LocalDate businessDate, String requestHash) {
    }

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    LedgerService(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Override
    public PostedJournal post(JournalRequest request) {
        return write(request, null);
    }

    @Override
    public PostedJournal reverse(long journalId, String reason) {
        long groupId = groupId();
        ExistingJournal original = jdbc.query(
                        "SELECT id, public_id, journal_type, business_date, request_hash FROM ledger_journals WHERE group_id = ? AND id = ?",
                        (rs, n) -> new ExistingJournal(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3),
                                rs.getDate(4).toLocalDate(), rs.getString(5)),
                        groupId, journalId)
                .stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (JournalType.REVERSAL.name().equals(original.type())) {
            throw new ApiException(ErrorCode.JOURNAL_NOT_REVERSIBLE);
        }
        Integer alreadyReversed = jdbc.queryForObject(
                "SELECT count(*) FROM ledger_journals WHERE group_id = ? AND reverses_journal_id = ?", Integer.class, groupId, journalId);
        if (alreadyReversed != null && alreadyReversed > 0) {
            throw new ApiException(ErrorCode.JOURNAL_ALREADY_REVERSED);
        }

        record Row(AccountRef account, Direction direction, Money amount) {
        }
        List<Row> rows = jdbc.query("""
                        SELECT a.account_type, a.membership_id, a.bucket_id, a.loan_id, l.direction, l.amount
                        FROM ledger_lines l JOIN ledger_accounts a ON a.id = l.account_id
                        WHERE l.group_id = ? AND l.journal_id = ? ORDER BY l.id""",
                (rs, n) -> new Row(new AccountRef(AccountType.valueOf(rs.getString(1)), (Long) rs.getObject(2),
                        (Long) rs.getObject(3), (Long) rs.getObject(4)),
                        Direction.valueOf(rs.getString(5)), Money.of(rs.getBigDecimal(6))),
                groupId, journalId);
        List<JournalRequest.Line> mirrored = rows.stream()
                .map(row -> new JournalRequest.Line(row.account(), row.direction().opposite(), row.amount()))
                .toList();

        JournalRequest reversal = new JournalRequest(JournalType.REVERSAL, "reversal:" + original.publicId(),
                sha256("reversal:" + original.requestHash()), original.businessDate(), reason, JournalSource.MANUAL, null, null, mirrored);
        PostedJournal posted = write(reversal, journalId);
        events.publishEvent(new JournalReversed(groupId, journalId, JournalType.valueOf(original.type()), posted.id()));
        return posted;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<PostedJournal> findJournal(UUID publicId) {
        return jdbc.query("SELECT id, public_id, journal_type, business_date FROM ledger_journals WHERE group_id = ? AND public_id = ?",
                        (rs, n) -> new PostedJournal(rs.getLong(1), rs.getObject(2, UUID.class), JournalType.valueOf(rs.getString(3)),
                                rs.getDate(4).toLocalDate(), false),
                        groupId(), publicId)
                .stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Map<Long, UUID> publicIds(Collection<Long> journalIds) {
        Map<Long, UUID> ids = new HashMap<>();
        if (journalIds.isEmpty()) {
            return ids;
        }
        jdbc.query("SELECT id, public_id FROM ledger_journals WHERE group_id = ? AND id = ANY(?)",
                rs -> {
                    ids.put(rs.getLong(1), rs.getObject(2, UUID.class));
                },
                groupId(), journalIds.toArray(Long[]::new));
        return ids;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Money balance(AccountRef account) {
        List<BigDecimal> found = jdbc.query("""
                        SELECT b.balance FROM ledger_balances b JOIN ledger_accounts a ON a.id = b.account_id
                        WHERE a.group_id = ? AND a.account_type = ?
                          AND COALESCE(a.membership_id, 0) = ? AND COALESCE(a.bucket_id, 0) = ? AND COALESCE(a.loan_id, 0) = ?""",
                (rs, n) -> rs.getBigDecimal(1),
                groupId(), account.type().name(), zero(account.membershipId()), zero(account.bucketId()), zero(account.loanId()));
        return found.isEmpty() ? Money.ZERO : Money.of(found.getFirst());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Map<Long, Money> memberSavingsByBucket(long membershipId) {
        Map<Long, Money> balances = new TreeMap<>();
        jdbc.query("""
                        SELECT a.bucket_id, b.balance FROM ledger_accounts a JOIN ledger_balances b ON b.account_id = a.id
                        WHERE a.group_id = ? AND a.account_type = 'MEMBER_SAVINGS' AND a.membership_id = ?""",
                rs -> {
                    balances.put(rs.getLong(1), Money.of(rs.getBigDecimal(2)));
                },
                groupId(), membershipId);
        return balances;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Money balanceBefore(AccountRef account, LocalDate date) {
        BigDecimal sum = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(CASE WHEN l.direction = a.normal_side THEN l.amount ELSE -l.amount END), 0)
                        FROM ledger_lines l
                        JOIN ledger_accounts a ON a.id = l.account_id
                        JOIN ledger_journals j ON j.id = l.journal_id
                        WHERE a.group_id = ? AND a.account_type = ?
                          AND COALESCE(a.membership_id, 0) = ? AND COALESCE(a.bucket_id, 0) = ? AND COALESCE(a.loan_id, 0) = ?
                          AND j.business_date < ?""",
                BigDecimal.class,
                groupId(), account.type().name(), zero(account.membershipId()), zero(account.bucketId()), zero(account.loanId()),
                Date.valueOf(date));
        return Money.of(sum == null ? BigDecimal.ZERO : sum);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<StatementEntry> entries(AccountRef account, LocalDate from, LocalDate to) {
        Money running = balanceBefore(account, from);
        record Raw(UUID journalId, LocalDate date, JournalType type, String description, String externalRef,
                   Direction direction, Direction normalSide, Money amount) {
        }
        List<Raw> raws = jdbc.query("""
                        SELECT j.public_id, j.business_date, j.journal_type, j.description, j.external_ref,
                               l.direction, a.normal_side, l.amount
                        FROM ledger_lines l
                        JOIN ledger_accounts a ON a.id = l.account_id
                        JOIN ledger_journals j ON j.id = l.journal_id
                        WHERE a.group_id = ? AND a.account_type = ?
                          AND COALESCE(a.membership_id, 0) = ? AND COALESCE(a.bucket_id, 0) = ? AND COALESCE(a.loan_id, 0) = ?
                          AND j.business_date BETWEEN ? AND ?
                        ORDER BY j.business_date, j.id, l.id""",
                (rs, n) -> new Raw(rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate(), JournalType.valueOf(rs.getString(3)),
                        rs.getString(4), rs.getString(5), Direction.valueOf(rs.getString(6)), Direction.valueOf(rs.getString(7)),
                        Money.of(rs.getBigDecimal(8))),
                groupId(), account.type().name(), zero(account.membershipId()), zero(account.bucketId()), zero(account.loanId()),
                Date.valueOf(from), Date.valueOf(to));
        List<StatementEntry> entries = new ArrayList<>(raws.size());
        for (Raw raw : raws) {
            running = raw.direction() == raw.normalSide() ? running.plus(raw.amount()) : running.minus(raw.amount());
            entries.add(new StatementEntry(raw.journalId(), raw.date(), raw.type(), raw.description(), raw.externalRef(),
                    raw.direction(), raw.amount(), running));
        }
        return entries;
    }

    private PostedJournal write(JournalRequest request, Long reversesJournalId) {
        long groupId = groupId();
        validate(request);
        long createdBy = CurrentUser.require().id();

        List<Map<String, Object>> claimed = jdbc.queryForList(CLAIM_KEY,
                groupId, request.type().name(), request.idempotencyKey(), request.requestHash(), Date.valueOf(request.businessDate()),
                request.description(), request.source().name(), request.externalRef(), request.memberId(), reversesJournalId, createdBy);
        if (claimed.isEmpty()) {
            return replay(groupId, request);
        }
        long journalId = ((Number) claimed.getFirst().get("id")).longValue();
        UUID publicId = (UUID) claimed.getFirst().get("public_id");

        Map<Long, BigDecimal> deltas = new TreeMap<>();   // ordered by account id: the lock order
        for (JournalRequest.Line line : request.lines()) {
            long accountId = ensureAccount(groupId, line.account());
            BigDecimal amount = line.amount().toPostingAmount();
            jdbc.update("INSERT INTO ledger_lines (journal_id, group_id, account_id, direction, amount) VALUES (?, ?, ?, ?, ?)",
                    journalId, groupId, accountId, line.direction().name(), amount);
            BigDecimal signed = line.direction() == line.account().type().normalSide() ? amount : amount.negate();
            deltas.merge(accountId, signed, BigDecimal::add);
        }
        applyBalances(groupId, journalId, deltas);
        return new PostedJournal(journalId, publicId, request.type(), request.businessDate(), false);
    }

    /** Rounds to whole francs (spec 4.3) and checks the journal balances before anything is written. */
    private static void validate(JournalRequest request) {
        if (request.lines().size() < 2) {
            throw new IllegalArgumentException("A journal needs at least two lines");
        }
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (JournalRequest.Line line : request.lines()) {
            BigDecimal amount = line.amount().toPostingAmount();
            if (amount.signum() <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
            if (line.direction() == Direction.DEBIT) {
                debits = debits.add(amount);
            } else {
                credits = credits.add(amount);
            }
        }
        if (debits.compareTo(credits) != 0) {
            throw new IllegalArgumentException("Unbalanced journal: debits " + debits + " vs credits " + credits);
        }
    }

    private PostedJournal replay(long groupId, JournalRequest request) {
        ExistingJournal existing = jdbc.queryForObject("""
                        SELECT id, public_id, journal_type, business_date, request_hash
                        FROM ledger_journals WHERE group_id = ? AND idempotency_key = ?""",
                (rs, n) -> new ExistingJournal(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getDate(4).toLocalDate(), rs.getString(5)),
                groupId, request.idempotencyKey());
        if (existing == null || !existing.requestHash().equals(request.requestHash())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return new PostedJournal(existing.id(), existing.publicId(), JournalType.valueOf(existing.type()),
                existing.businessDate(), true);
    }

    private long ensureAccount(long groupId, AccountRef ref) {
        jdbc.update(ENSURE_ACCOUNT, groupId, ref.type().name(), ref.membershipId(), ref.bucketId(), ref.loanId(),
                ref.type().normalSide().name());
        Long id = jdbc.queryForObject(FIND_ACCOUNT, Long.class, groupId, ref.type().name(),
                zero(ref.membershipId()), zero(ref.bucketId()), zero(ref.loanId()));
        if (id == null) {
            throw new IllegalStateException("ledger account was not created");
        }
        jdbc.update("INSERT INTO ledger_balances (account_id, group_id) VALUES (?, ?) ON CONFLICT (account_id) DO NOTHING", id, groupId);
        return id;
    }

    private void applyBalances(long groupId, long journalId, Map<Long, BigDecimal> deltas) {
        Map<Long, Long> versions = new HashMap<>();
        for (Long accountId : deltas.keySet()) {   // TreeMap: ascending id, the global lock order
            Long version = jdbc.queryForObject(
                    "SELECT version FROM ledger_balances WHERE account_id = ? AND group_id = ? FOR UPDATE",
                    Long.class, accountId, groupId);
            versions.put(accountId, version);
        }
        for (Map.Entry<Long, BigDecimal> delta : deltas.entrySet()) {
            int updated = jdbc.update("""
                            UPDATE ledger_balances SET balance = balance + ?, last_journal_id = ?, version = version + 1
                            WHERE account_id = ? AND group_id = ? AND version = ?""",
                    delta.getValue(), journalId, delta.getKey(), groupId, versions.get(delta.getKey()));
            if (updated != 1) {
                throw new IllegalStateException("ledger balance " + delta.getKey() + " changed under its lock");
            }
        }
    }

    private static long groupId() {
        return TenantContext.requireGroup().groupId();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long zero(Long id) {
        return id == null ? 0L : id;
    }
}
