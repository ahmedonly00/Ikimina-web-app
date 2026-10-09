package rw.ikimina.shared.error;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import rw.ikimina.shared.ratelimit.RateLimitedException;

/**
 * Turns every exception that reaches the web layer into an RFC 7807 problem
 * with a stable {@link ErrorCode}. Internals - exception messages, stack traces,
 * SQL - never reach the client; they are logged against the request id instead.
 *
 * <p>Spring Security's entry point and access-denied handler delegate here too
 * (see {@code SecurityConfig}), so 401 and 403 have the same shape as everything else.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemFactory problems;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    public GlobalExceptionHandler(ProblemFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail problem = problems.create(ex.code(), ex.detailArgs());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.code().status());
        if (ex instanceof RateLimitedException limited) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(limited.retryAfterSeconds()));
        }
        return response.body(problem);
    }

    /**
     * Two people changed the same thing at once: an optimistic-lock conflict, or a race
     * the database settled with a unique constraint (e.g. two holders of one office).
     */
    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ProblemDetail> handleConcurrentModification(RuntimeException ex) {
        log.warn("Concurrent modification rejected: {} {}", ex.getClass().getSimpleName(), databaseReason(ex));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problems.create(ErrorCode.CONCURRENT_MODIFICATION));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return unauthenticated();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        // A method-security denial for a caller who never authenticated is a 401, not a 403.
        if (trustResolver.isAnonymous(SecurityContextHolder.getContext().getAuthentication())) {
            return unauthenticated();
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problems.create(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problems.create(ErrorCode.INTERNAL_ERROR));
    }

    /** Framework exceptions (bad JSON, unknown fields, wrong method, ...) all funnel through here. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = codeFor(statusCode);
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("Framework error mapped to 500", ex);
        } else {
            log.debug("Request rejected with {}: {}", statusCode, ex.getMessage());
        }
        ProblemDetail problem = problems.createWithStatus(code, statusCode);
        if (ex instanceof MethodArgumentNotValidException invalid) {
            problem.setProperty("errors", fieldErrors(invalid));
        }
        return createResponseEntity(problem, headers, statusCode, request);
    }

    /**
     * SQL state and the first line of the database's message, which names the violated
     * constraint. The following "Detail:" line can quote row values (phone numbers, amounts),
     * so it is never logged.
     */
    private static String databaseReason(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql) {
                String message = sql.getMessage() == null ? "" : sql.getMessage().lines().findFirst().orElse("");
                return "[" + sql.getSQLState() + "] " + message;
            }
        }
        return "";
    }

    private ResponseEntity<ProblemDetail> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problems.create(ErrorCode.UNAUTHENTICATED));
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> status.is4xxClientError() ? ErrorCode.VALIDATION_FAILED : ErrorCode.INTERNAL_ERROR;
        };
    }

    /**
     * Field and constraint name only: stable identifiers a client can map to its
     * own localised text. The validator's default message is English-only.
     */
    private static List<Map<String, String>> fieldErrors(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "constraint", error.getCode() == null ? "Invalid" : error.getCode()))
                .toList();
    }
}
