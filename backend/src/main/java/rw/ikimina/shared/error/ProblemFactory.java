package rw.ikimina.shared.error;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import rw.ikimina.shared.web.RequestIdFilter;

/**
 * Builds the RFC 7807 body for every error the API returns (spec 17):
 * a stable {@code code}, a localised {@code title} and {@code detail}, and the
 * {@code requestId} that ties the response to the server logs.
 */
@Component
public class ProblemFactory {

    private final MessageSource messages;

    public ProblemFactory(MessageSource messages) {
        this.messages = messages;
    }

    /** Each reason as {@code {code, message}}, the message localised under {@code reason.<code>}. */
    public List<Map<String, String>> reasons(List<ReasonedApiException.Reason> reasons) {
        Locale locale = LocaleContextHolder.getLocale();
        return reasons.stream()
                .map(r -> Map.of("code", r.code(),
                        "message", messages.getMessage("reason." + r.code().toLowerCase(Locale.ROOT), r.args(), locale)))
                .toList();
    }

    public ProblemDetail create(ErrorCode code, Object... detailArgs) {
        return createWithStatus(code, code.status(), detailArgs);
    }

    /**
     * For framework errors whose HTTP status is more specific than the code's
     * default (e.g. 406 or 413 reported as {@link ErrorCode#VALIDATION_FAILED}).
     */
    public ProblemDetail createWithStatus(ErrorCode code, HttpStatusCode status, Object... detailArgs) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(messages.getMessage(code.titleKey(), null, locale));
        problem.setDetail(messages.getMessage(code.detailKey(), detailArgs, locale));
        problem.setProperty("code", code.name());
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }
}
