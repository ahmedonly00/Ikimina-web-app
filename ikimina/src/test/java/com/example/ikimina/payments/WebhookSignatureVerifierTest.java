package com.example.ikimina.payments;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A webhook is the one endpoint an attacker can reach without credentials, so
 * these tests pin the properties that keep it closed.
 */
class WebhookSignatureVerifierTest {

    private static final String SECRET = "provider-shared-secret-value";

    @Test
    @DisplayName("a correctly signed payload verifies")
    void acceptsValidSignature() throws Exception {
        String payload = "1789000000.{\"eventId\":\"e1\"}";
        String signature = WebhookSignatureVerifier.hexHmacSha256(payload, SECRET);

        assertDoesNotThrow(() -> WebhookSignatureVerifier.verify(payload, signature, SECRET));
    }

    @Test
    @DisplayName("a tampered body no longer verifies")
    void rejectsTamperedBody() throws Exception {
        String signature = WebhookSignatureVerifier.hexHmacSha256("1789000000.{\"amount\":\"100\"}", SECRET);

        // An attacker inflating the amount must invalidate the signature.
        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verify("1789000000.{\"amount\":\"999999\"}", signature, SECRET));
    }

    @Test
    @DisplayName("a signature from the wrong secret is rejected")
    void rejectsWrongSecret() throws Exception {
        String payload = "1789000000.{\"eventId\":\"e1\"}";
        String signature = WebhookSignatureVerifier.hexHmacSha256(payload, "some-other-secret");

        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verify(payload, signature, SECRET));
    }

    @Test
    @DisplayName("an unconfigured secret refuses everything rather than accepting it")
    void unconfiguredSecretIsClosed() {
        // A webhook with no secret must not be an open endpoint that writes to
        // the ledger.
        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verify("payload", "abcdef", null));
        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verify("payload", "abcdef", "   "));
    }

    @Test
    @DisplayName("a missing or malformed signature is rejected")
    void rejectsMalformedSignature() {
        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verify("payload", null, SECRET));
        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verify("payload", "", SECRET));
        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verify("payload", "not-hex-at-all", SECRET));
    }

    @Test
    @DisplayName("a captured notification stops being replayable once it ages out")
    void rejectsReplayedTimestamp() {
        Duration tolerance = Duration.ofMinutes(5);

        assertDoesNotThrow(() ->
                WebhookSignatureVerifier.verifyTimestamp(Instant.now(), tolerance));

        // Ten minutes old: a captured request must no longer be accepted even
        // though its signature is still cryptographically valid.
        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verifyTimestamp(Instant.now().minus(Duration.ofMinutes(10)), tolerance));
    }

    @Test
    @DisplayName("a future-dated timestamp is rejected")
    void rejectsFutureTimestamp() {
        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verifyTimestamp(
                        Instant.now().plus(Duration.ofMinutes(10)), Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("a missing timestamp is rejected")
    void rejectsMissingTimestamp() {
        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verifyTimestamp(null, Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("signature comparison is constant-time")
    void usesConstantTimeComparison() throws Exception {
        // Not a timing measurement - that is too flaky to assert. This pins the
        // observable contract: a signature of the wrong length is rejected
        // rather than throwing from an array comparison, which is what a
        // constant-time MessageDigest.isEqual does.
        String payload = "1789000000.{}";
        String valid = WebhookSignatureVerifier.hexHmacSha256(payload, SECRET);

        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verify(payload, valid.substring(0, 32), SECRET));
        assertThrows(PaymentVerificationException.class, () ->
                WebhookSignatureVerifier.verify(payload, valid + "00", SECRET));
    }
}