package com.example.ikimina.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Shared HMAC verification for provider webhooks.
 *
 * Three properties matter here, and each has been got wrong in the wild:
 *
 *  1. <b>Constant-time comparison.</b> Comparing signatures with
 *     {@code String.equals} leaks, through timing, how many leading bytes were
 *     correct, which is enough to forge a signature byte by byte.
 *     {@link MessageDigest#isEqual} is the constant-time comparison.
 *
 *  2. <b>Replay protection.</b> A valid signature is valid forever unless the
 *     signed payload includes a timestamp and the receiver bounds how old it may
 *     be. Without this, a captured notification can be resent to credit a
 *     contribution again. (Idempotency on the event id also guards this, but
 *     defence should not rest on one control.)
 *
 *  3. <b>Signing the exact bytes.</b> The signature covers the body as sent.
 *     Parsing and re-serialising JSON before verifying changes whitespace and
 *     key order, and the signature will not match - or worse, a lenient
 *     implementation will be tricked into verifying different bytes than it
 *     acts on.
 */
public final class WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * How far out of date a notification may be. Generous enough for provider
     * retries and clock skew, short enough that a captured request stops being
     * useful quickly.
     */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private WebhookSignatureVerifier() {
    }

    /**
     * @param signedPayload exactly what the provider signed, usually
     *                      {@code timestamp + "." + rawBody}
     * @param providedSignature hex-encoded signature from the request header
     */
    public static void verify(String signedPayload,
                              String providedSignature,
                              String secret) throws PaymentVerificationException {
        if (secret == null || secret.isBlank()) {
            // Refuse rather than accept everything: a webhook with no configured
            // secret is an open endpoint that writes to the ledger.
            throw new PaymentVerificationException("No webhook secret configured for this provider");
        }
        if (providedSignature == null || providedSignature.isBlank()) {
            throw new PaymentVerificationException("Missing signature header");
        }

        byte[] expected = hmacSha256(signedPayload, secret);
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(providedSignature.trim().toLowerCase());
        } catch (IllegalArgumentException ex) {
            throw new PaymentVerificationException("Signature is not valid hex");
        }

        if (!MessageDigest.isEqual(expected, provided)) {
            throw new PaymentVerificationException("Signature mismatch");
        }
    }

    /**
     * Rejects notifications that are too old (replay) or implausibly far in the
     * future (clock skew or a forged timestamp).
     */
    public static void verifyTimestamp(Instant timestamp, Duration tolerance)
            throws PaymentVerificationException {
        if (timestamp == null) {
            throw new PaymentVerificationException("Missing timestamp");
        }
        Instant now = Instant.now();
        if (timestamp.isBefore(now.minus(tolerance))) {
            throw new PaymentVerificationException("Timestamp too old; possible replay");
        }
        if (timestamp.isAfter(now.plus(tolerance))) {
            throw new PaymentVerificationException("Timestamp in the future");
        }
    }

    public static byte[] hmacSha256(String payload, String secret) throws PaymentVerificationException {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("HMAC computation failed", ex);
            throw new PaymentVerificationException("Could not compute signature", ex);
        }
    }

    /** Convenience for tests and for providers that document a hex signature. */
    public static String hexHmacSha256(String payload, String secret) throws PaymentVerificationException {
        return HexFormat.of().formatHex(hmacSha256(payload, secret));
    }
}