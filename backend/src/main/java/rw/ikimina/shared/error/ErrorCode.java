package rw.ikimina.shared.error;

import java.util.Locale;

import org.springframework.http.HttpStatus;

/**
 * Stable, client-facing error codes (spec 17.10). A client branches on {@code code};
 * the human-readable title and detail come from the i18n bundle under
 * {@code error.<code in lower case>.title|detail}.
 *
 * <p>Codes are part of the API contract: add new ones freely, never rename or
 * repurpose an existing one.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    SUBSCRIPTION_SUSPENDED(HttpStatus.FORBIDDEN),
    PLAN_LIMIT_REACHED(HttpStatus.FORBIDDEN),
    INSUFFICIENT_GROUP_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT),
    LOAN_INVALID_TRANSITION(HttpStatus.CONFLICT),
    SELF_APPROVAL_FORBIDDEN(HttpStatus.FORBIDDEN),
    DUPLICATE_APPROVAL(HttpStatus.CONFLICT),
    DUPLICATE_EXTERNAL_REF(HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    PERIOD_CLOSED(HttpStatus.CONFLICT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public String titleKey() {
        return "error." + name().toLowerCase(Locale.ROOT) + ".title";
    }

    public String detailKey() {
        return "error." + name().toLowerCase(Locale.ROOT) + ".detail";
    }
}
