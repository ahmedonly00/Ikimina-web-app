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
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // Phase 1: identity
    INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST),
    /** Wrong phone, wrong password, locked or disabled account: deliberately indistinguishable (spec 16.1). */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
    OTP_INVALID(HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST),
    PASSWORD_TOO_WEAK(HttpStatus.BAD_REQUEST),
    TERMS_NOT_ACCEPTED(HttpStatus.BAD_REQUEST),
    REAUTHENTICATION_REQUIRED(HttpStatus.FORBIDDEN),
    CSRF_CHECK_FAILED(HttpStatus.FORBIDDEN),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),

    // Phase 1: groups
    INVITATION_INVALID(HttpStatus.BAD_REQUEST),
    ALREADY_MEMBER(HttpStatus.CONFLICT),
    OFFICE_OCCUPIED(HttpStatus.CONFLICT),
    OFFICE_TRANSFER_REQUIRED(HttpStatus.CONFLICT),
    SELF_MODIFICATION_FORBIDDEN(HttpStatus.FORBIDDEN),
    SETTINGS_CHANGE_STALE(HttpStatus.CONFLICT),

    // Phase 2: ledger and savings
    JOURNAL_NOT_REVERSIBLE(HttpStatus.CONFLICT),
    JOURNAL_ALREADY_REVERSED(HttpStatus.CONFLICT),
    MEMBER_NOT_ACTIVE(HttpStatus.CONFLICT),
    BUCKET_NOT_ACTIVE(HttpStatus.CONFLICT),
    BUCKET_CHANGE_STALE(HttpStatus.CONFLICT),

    // Phase 3: loans
    /** Carries the reasons, each localised under {@code reason.<code>} (spec 9.2). */
    LOAN_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_CONTENT),
    LOAN_APPROVER_NOT_ALLOWED(HttpStatus.FORBIDDEN),
    LOAN_DISBURSER_MUST_DIFFER(HttpStatus.FORBIDDEN),
    LOAN_OVERPAYMENT(HttpStatus.UNPROCESSABLE_CONTENT),
    LOAN_PRODUCT_NOT_ACTIVE(HttpStatus.CONFLICT),
    LOAN_PRODUCT_CHANGE_STALE(HttpStatus.CONFLICT),
    LOAN_TERMS_UNSUPPORTED(HttpStatus.BAD_REQUEST),

    // Phase 3c: withdrawals
    /** Carries the reasons, each localised under {@code reason.<code>}. */
    WITHDRAWAL_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT),
    WITHDRAWAL_INVALID_STATE(HttpStatus.CONFLICT),
    WITHDRAWAL_NOT_YET_DUE(HttpStatus.CONFLICT);

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
