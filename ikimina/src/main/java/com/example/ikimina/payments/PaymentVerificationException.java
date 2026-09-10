package com.example.ikimina.payments;

/**
 * A notification could not be proven authentic.
 *
 * Deliberately carries no detail to the caller: an attacker probing a webhook
 * must not learn whether the signature, the timestamp, or the payload shape was
 * what rejected them. The reason is logged server-side instead.
 */
public class PaymentVerificationException extends Exception {

    public PaymentVerificationException(String message) {
        super(message);
    }

    public PaymentVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}