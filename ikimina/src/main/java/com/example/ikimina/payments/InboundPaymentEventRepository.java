package com.example.ikimina.payments;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InboundPaymentEventRepository extends JpaRepository<InboundPaymentEvent, Long> {

    Optional<InboundPaymentEvent> findByProviderAndProviderEventId(String provider, String providerEventId);

    Page<InboundPaymentEvent> findByGroupIdOrderByReceivedAtDesc(Long groupId, Pageable pageable);

    Page<InboundPaymentEvent> findByProcessingStatusOrderByReceivedAtDesc(
            InboundPaymentEvent.ProcessingStatus status, Pageable pageable);

    /** Anything a human still needs to look at. */
    List<InboundPaymentEvent> findByProcessingStatusIn(
            List<InboundPaymentEvent.ProcessingStatus> statuses);

    /**
     * Total the provider says settled for a group in a window. Compared against
     * the ledger to detect drift in either direction.
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM InboundPaymentEvent e "
            + "WHERE e.groupId = :groupId AND e.processingStatus = "
            + "com.example.ikimina.payments.InboundPaymentEvent$ProcessingStatus.APPLIED "
            + "AND e.receivedAt BETWEEN :from AND :to")
    BigDecimal appliedTotalForGroup(@Param("groupId") Long groupId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /**
     * Clears the payer phone number for one member, keeping amounts and
     * references. Used when a member exercises erasure: the financial evidence
     * is retained, the personal data is not.
     */
    @Modifying
    @Query("UPDATE InboundPaymentEvent e SET e.payerMsisdn = null WHERE e.memberId = :memberId")
    int scrubPayerMsisdnForMember(@Param("memberId") Long memberId);

    long countByProcessingStatus(InboundPaymentEvent.ProcessingStatus status);
}