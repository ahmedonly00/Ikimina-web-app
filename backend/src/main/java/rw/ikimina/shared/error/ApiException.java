package rw.ikimina.shared.error;

import java.util.Arrays;

/**
 * A business-rule failure to report to the client as an RFC 7807 problem.
 *
 * <p>The exception carries an {@link ErrorCode} and the arguments for its localised
 * detail message, never a user-facing sentence: text is resolved per request
 * locale from the i18n bundle (Hard Rule H10). The Java message is for logs only.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final transient Object[] detailArgs;

    public ApiException(ErrorCode code, Object... detailArgs) {
        super(code.name());
        this.code = code;
        this.detailArgs = detailArgs.clone();
    }

    public ErrorCode code() {
        return code;
    }

    public Object[] detailArgs() {
        return detailArgs.clone();
    }

    @Override
    public String toString() {
        return "ApiException[" + code + ", args=" + Arrays.toString(detailArgs) + "]";
    }
}
