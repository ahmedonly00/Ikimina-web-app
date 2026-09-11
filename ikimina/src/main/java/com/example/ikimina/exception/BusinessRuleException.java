package com.example.ikimina.exception;

/**
 * A domain rule rejected the request (e.g. a group already has an active
 * cycle). Mapped to HTTP 400 with the message shown to the caller, so messages
 * must stay user-facing and free of internal detail.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
