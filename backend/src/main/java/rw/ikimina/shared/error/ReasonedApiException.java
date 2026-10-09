package rw.ikimina.shared.error;

import java.util.List;
import java.util.Objects;

/**
 * A refusal with several reasons, each shown to the user (spec 9.2: "return clear, localised
 * reasons"). Rendered like any {@link ApiException}, plus a {@code reasons} array of
 * {@code {code, message}}; each message is localised under {@code reason.<code in lower case>}.
 */
public class ReasonedApiException extends ApiException {

    private static final long serialVersionUID = 1L;

    /** One reason: a stable code a client may branch on, and the arguments of its message. */
    public record Reason(String code, Object... args) {
        public Reason {
            Objects.requireNonNull(code, "code");
            args = args.clone();
        }

        @Override
        public Object[] args() {
            return args.clone();
        }
    }

    private final transient List<Reason> reasons;

    public ReasonedApiException(ErrorCode code, List<Reason> reasons) {
        super(code);
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("a reasoned refusal needs at least one reason");
        }
        this.reasons = List.copyOf(reasons);
    }

    public List<Reason> reasons() {
        return reasons;
    }
}
