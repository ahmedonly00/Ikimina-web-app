package com.example.ikimina.payments;

import java.util.Map;

/**
 * Port for a mobile-money provider.
 *
 * Deliberately narrow: verify a notification and normalise it. There is no
 * method to move money, because this platform does not move money - see
 * {@link PackageInfo}.
 *
 * To add MTN MoMo, Airtel Money, or an aggregator such as Paypack, implement
 * this interface and register it as a bean. Nothing else changes: ingestion,
 * idempotency, ledger posting and reconciliation are provider-agnostic.
 */
public interface PaymentProvider {

    /** Value used in the webhook path and stored on each event. */
    String name();

    /**
     * Verifies authenticity and returns the normalised notification.
     *
     * Implementations must reject anything they cannot prove came from the
     * provider. Throwing here means the request never reaches the ledger.
     *
     * @param rawBody verbatim request body - do not re-serialise before
     *                verifying, since signatures cover the exact bytes
     * @param headers request headers, lower-cased keys
     * @throws PaymentVerificationException if the signature, timestamp or shape
     *                                      is not acceptable
     */
    PaymentNotification verifyAndParse(String rawBody, Map<String, String> headers)
            throws PaymentVerificationException;
}