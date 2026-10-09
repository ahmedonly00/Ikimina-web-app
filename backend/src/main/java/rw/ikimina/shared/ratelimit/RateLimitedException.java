package rw.ikimina.shared.ratelimit;

import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;

/** A rate limit was exceeded; rendered as 429 with {@code Retry-After} (spec 16.5). */
public class RateLimitedException extends ApiException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
