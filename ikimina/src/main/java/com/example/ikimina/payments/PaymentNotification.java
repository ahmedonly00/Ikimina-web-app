package com.example.ikimina.payments;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A provider notification, normalised.
 *
 * Each adapter converts its provider's payload into this shape so the rest of
 * the system never learns a provider's field names.
 *
 * @param providerEventId provider's unique id for this notification; the
 *                        idempotency key for the whole pipeline
 * @param providerReference the reference a member would quote to support
 * @param groupId          group the payment belongs to, if the provider carries it
 * @param memberId         member, if the provider carries it; otherwise resolved from msisdn
 * @param payerMsisdn      payer's phone number as the provider reported it
 * @param successful       whether the provider considers the payment settled;
 *                         only settled payments reach the ledger
 */
public record PaymentNotification(
        String providerEventId,
        String providerReference,
        BigDecimal amount,
        String currency,
        String payerMsisdn,
        Long groupId,
        Long memberId,
        Instant occurredAt,
        boolean successful,
        String rawPayload) {
}