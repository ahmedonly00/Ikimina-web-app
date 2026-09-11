package com.example.ikimina.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    /** Paged so a long-lived group's history cannot exhaust memory. */
    Page<LedgerEntry> findByGroupIdOrderByOccurredOnDescIdDesc(Long groupId, Pageable pageable);

    Page<LedgerEntry> findByMemberIdOrderByOccurredOnDescIdDesc(Long memberId, Pageable pageable);

    List<LedgerEntry> findBySourceTypeAndSourceId(String sourceType, Long sourceId);

    /** True once a reversing entry exists for the given entry. */
    boolean existsByReversalOf(Long entryId);

    /**
     * Fund balance for a group: credits minus debits. Derived on demand rather
     * than stored, so it can always be explained by the underlying rows.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN e.direction = com.example.ikimina.ledger.LedgerDirection.CREDIT "
            + "THEN e.amount ELSE -e.amount END), 0) "
            + "FROM LedgerEntry e WHERE e.groupId = :groupId")
    BigDecimal balanceForGroup(@Param("groupId") Long groupId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.direction = com.example.ikimina.ledger.LedgerDirection.CREDIT "
            + "THEN e.amount ELSE -e.amount END), 0) "
            + "FROM LedgerEntry e WHERE e.memberId = :memberId")
    BigDecimal balanceForMember(@Param("memberId") Long memberId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.direction = com.example.ikimina.ledger.LedgerDirection.CREDIT "
            + "THEN e.amount ELSE -e.amount END), 0) "
            + "FROM LedgerEntry e WHERE e.groupId = :groupId "
            + "AND e.occurredOn BETWEEN :from AND :to")
    BigDecimal balanceForGroupBetween(@Param("groupId") Long groupId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /**
     * Credits that originated from a mobile-money notification, for a group and
     * window. Compared against what providers reported; a difference means the
     * books and the money have diverged.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN e.direction = com.example.ikimina.ledger.LedgerDirection.CREDIT "
            + "THEN e.amount ELSE -e.amount END), 0) "
            + "FROM LedgerEntry e WHERE e.groupId = :groupId AND e.sourceType = 'MOBILE_MONEY' "
            + "AND e.occurredOn BETWEEN :from AND :to")
    BigDecimal mobileMoneyTotalForGroupBetween(@Param("groupId") Long groupId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /**
     * Per-member totals for a group, used to reconcile the fund balance against
     * the sum of member balances.
     */
    @Query("SELECT e.memberId, COALESCE(SUM(CASE WHEN e.direction = com.example.ikimina.ledger.LedgerDirection.CREDIT "
            + "THEN e.amount ELSE -e.amount END), 0) "
            + "FROM LedgerEntry e WHERE e.groupId = :groupId AND e.memberId IS NOT NULL "
            + "GROUP BY e.memberId")
    List<Object[]> memberBalancesForGroup(@Param("groupId") Long groupId);
}
