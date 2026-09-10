package com.example.ikimina.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Reference adapter for a generic HMAC-signed JSON webhook.
 *
 * This exists so the ingestion pipeline is complete and testable without
 * committing to a specific provider's contract. It expects:
 *
 * <pre>
 *   X-Signature: hex(HMAC-SHA256(timestamp + "." + rawBody, secret))
 *   X-Timestamp: epoch seconds
 *
 *   { "eventId": "...", "reference": "...", "amount": "5000.00",
 *     "currency": "RWF", "msisdn": "+2507...", "groupId": 1,
 *     "memberId": 2, "status": "SUCCESS", "occurredAt": "2026-09-10T10:00:00Z" }
 * </pre>
 *
 * A real MTN MoMo, Airtel Money or Paypack adapter replaces only this class:
 * it maps that provider's field names and signature scheme into
 * {@link PaymentNotification}. Everything downstream is unchanged.
 */
@Component
public class GenericHmacPaymentProvider implements PaymentProvider {

    public static final String NAME = "generic-hmac";

    private static final String SIGNATURE_HEADER = "x-signature";
    private static final String TIMESTAMP_HEADER = "x-timestamp";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Empty by default, and an empty secret makes verification refuse every
     * request - so an unconfigured provider is closed, not open.
     */
    @Value("${ikimina.payments.generic-hmac.secret:}")
    private String secret;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PaymentNotification verifyAndParse(String rawBody, Map<String, String> headers)
            throws PaymentVerificationException {

        String timestampHeader = headers.get(TIMESTAMP_HEADER);
        if (timestampHeader == null || timestampHeader.isBlank()) {
            throw new PaymentVerificationException("Missing timestamp header");
        }

        Instant timestamp;
        try {
            timestamp = Instant.ofEpochSecond(Long.parseLong(timestampHeader.trim()));
        } catch (NumberFormatException ex) {
            throw new PaymentVerificationException("Timestamp is not epoch seconds");
        }
        WebhookSignatureVerifier.verifyTimestamp(timestamp, WebhookSignatureVerifier.DEFAULT_TOLERANCE);

        // The timestamp is inside the signed payload, so it cannot be altered
        // independently of the body.
        WebhookSignatureVerifier.verify(
                timestampHeader.trim() + "." + rawBody,
                headers.get(SIGNATURE_HEADER),
                secret);

        JsonNode node;
        try {
            node = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new PaymentVerificationException("Body is not valid JSON", ex);
        }

        String eventId = text(node, "eventId");
        if (eventId == null) {
            throw new PaymentVerificationException("Missing eventId");
        }

        BigDecimal amount;
        try {
            // Read as text, not double: parsing money through a floating point
            // type reintroduces exactly the drift Phase 2 removed.
            String raw = text(node, "amount");
            amount = raw == null ? null : new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new PaymentVerificationException("Amount is not a decimal number");
        }

        Instant occurredAt = timestamp;
        String occurredAtText = text(node, "occurredAt");
        if (occurredAtText != null) {
            try {
                occurredAt = Instant.parse(occurredAtText);
            } catch (Exception ignored) {
                // Fall back to the signed timestamp rather than rejecting a
                // payment over a formatting difference.
                occurredAt = timestamp;
            }
        }

        String status = text(node, "status");
        boolean successful = status != null
                && ("SUCCESS".equalsIgnoreCase(status) || "SUCCESSFUL".equalsIgnoreCase(status)
                    || "COMPLETED".equalsIgnoreCase(status));

        return new PaymentNotification(
                eventId,
                text(node, "reference"),
                amount,
                text(node, "currency"),
                text(node, "msisdn"),
                node.hasNonNull("groupId") ? node.get("groupId").asLong() : null,
                node.hasNonNull("memberId") ? node.get("memberId").asLong() : null,
                occurredAt,
                successful,
                rawBody);
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}