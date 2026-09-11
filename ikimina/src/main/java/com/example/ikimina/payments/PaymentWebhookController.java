package com.example.ikimina.payments;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Receives provider payment notifications.
 *
 * Unauthenticated by necessity - a provider cannot hold a user token - so the
 * signature is the authentication. Three consequences shape this class:
 *
 *  1. The body is taken as a String, not a parsed DTO. Signatures cover exact
 *     bytes, so Spring must not deserialise and re-serialise before verifying.
 *  2. Responses are deliberately uninformative. An attacker probing the
 *     endpoint learns only accepted or rejected, never why.
 *  3. A 2xx is returned for anything successfully recorded, including a payment
 *     we could not match to a member. Providers retry on non-2xx, and retrying
 *     will not fix an unmatched payment - a human will.
 */
@RestController
@RequestMapping("/api/webhooks/payments")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final Map<String, PaymentProvider> providers = new HashMap<>();
    private final PaymentIngestionService ingestionService;

    public PaymentWebhookController(List<PaymentProvider> providerBeans,
                                    PaymentIngestionService ingestionService) {
        for (PaymentProvider provider : providerBeans) {
            providers.put(provider.name().toLowerCase(Locale.ROOT), provider);
        }
        this.ingestionService = ingestionService;
    }

    @PostMapping("/{providerName}")
    public ResponseEntity<Map<String, String>> receive(
            @PathVariable String providerName,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest request) {

        PaymentProvider provider = providers.get(providerName.toLowerCase(Locale.ROOT));
        if (provider == null) {
            // Same shape as a rejected signature: do not confirm which
            // providers are configured.
            log.warn("Payment webhook for unknown provider '{}'", providerName);
            return rejected();
        }
        if (rawBody == null || rawBody.isBlank()) {
            return rejected();
        }

        PaymentNotification notification;
        try {
            notification = provider.verifyAndParse(rawBody, headersOf(request));
        } catch (PaymentVerificationException ex) {
            // The reason stays server-side.
            log.warn("Rejected {} webhook: {}", providerName, ex.getMessage());
            return rejected();
        }

        InboundPaymentEvent event = ingestionService.ingest(provider.name(), notification);

        // Acknowledge receipt. The provider's job is done even when ours is not.
        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "eventId", String.valueOf(event.getId()),
                "processing", event.getProcessingStatus().name()));
    }

    private ResponseEntity<Map<String, String>> rejected() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "rejected"));
    }

    private Map<String, String> headersOf(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        var names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
        }
        return Collections.unmodifiableMap(headers);
    }
}