package rw.ikimina.identity.internal;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.ikimina.identity.internal.SessionTokens.IssuedSession;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.error.ProblemFactory;
import rw.ikimina.shared.security.AuthHeaders;
import rw.ikimina.shared.security.AuthProperties;

/**
 * Authentication endpoints (spec 17.1). The access token goes in the response body; the
 * refresh token only ever travels in an HttpOnly, Secure, SameSite=Strict cookie scoped to
 * this path, so page scripts cannot read it (spec 16.1).
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    static final String REFRESH_COOKIE = "ikimina_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    record RegisterRequest(@NotBlank @Size(max = 32) String phone,
                           @NotBlank @Size(max = 200) String fullName,
                           @NotBlank @Size(max = 128) String password,
                           @NotBlank @Pattern(regexp = "en|rw") String locale,
                           @AssertTrue boolean acceptTerms) {
    }

    record VerifyPhoneRequest(@NotBlank @Size(max = 32) String phone, @NotBlank @Size(max = 6) String otp) {
    }

    record LoginRequest(@NotBlank @Size(max = 32) String phone, @NotBlank @Size(max = 128) String password) {
    }

    record ForgotPasswordRequest(@NotBlank @Size(max = 32) String phone) {
    }

    record ResetPasswordRequest(@NotBlank @Size(max = 32) String phone,
                                @NotBlank @Size(max = 6) String otp,
                                @NotBlank @Size(max = 128) String newPassword) {
    }

    record ReauthRequest(@NotBlank @Size(max = 128) String password) {
    }

    record TokenResponse(String accessToken, String tokenType, long expiresIn) {
        static TokenResponse bearer(String token, long expiresIn) {
            return new TokenResponse(token, "Bearer", expiresIn);
        }
    }

    private final AuthService auth;
    private final AuthProperties properties;
    private final ProblemFactory problems;

    AuthController(AuthService auth, AuthProperties properties, ProblemFactory problems) {
        this.auth = auth;
        this.properties = properties;
        this.problems = problems;
    }

    /** Always 202, whether or not the number already has an account (no enumeration). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void register(@Valid @RequestBody RegisterRequest request) {
        auth.register(request.phone(), request.fullName(), request.password(), request.locale(), request.acceptTerms());
    }

    @PostMapping("/verify-phone")
    ResponseEntity<TokenResponse> verifyPhone(@Valid @RequestBody VerifyPhoneRequest request) {
        return signedIn(auth.verifyPhone(request.phone(), request.otp()));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return signedIn(auth.login(request.phone(), request.password()));
    }

    @PostMapping("/refresh")
    ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                              @RequestHeader(name = AuthHeaders.CSRF_HEADER, required = false) String csrf) {
        requireCsrfHeader(csrf);
        AuthService.Outcome outcome = auth.refresh(refreshToken);
        if (outcome instanceof AuthService.Failed(ErrorCode reason)) {
            // The session is over either way: report it and expire the cookie in the same response.
            return ResponseEntity.status(reason.status())
                    .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problems.create(reason));
        }
        return signedIn(outcome);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                @RequestHeader(name = AuthHeaders.CSRF_HEADER, required = false) String csrf) {
        requireCsrfHeader(csrf);
        auth.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO)).build();
    }

    /** Always 202, whether or not the number has an account. */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        auth.forgotPassword(request.phone());
    }

    @PostMapping("/password/reset")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        auth.resetPassword(request.phone(), request.otp(), request.newPassword()).ifPresent(reason -> {
            throw new ApiException(reason);
        });
        return ResponseEntity.noContent().build();
    }

    /** Step-up: re-enter the password to get an access token with a fresh auth_time. */
    @PostMapping("/reauth")
    TokenResponse reauthenticate(@Valid @RequestBody ReauthRequest request) {
        String token = auth.reauthenticate(request.password())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        return TokenResponse.bearer(token, auth.accessTokenTtlSeconds());
    }

    private ResponseEntity<TokenResponse> signedIn(AuthService.Outcome outcome) {
        return switch (outcome) {
            case AuthService.Failed failed -> throw new ApiException(failed.reason());
            case AuthService.Succeeded succeeded -> {
                IssuedSession session = succeeded.session();
                yield ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), properties.refreshTokenTtl()))
                        .body(TokenResponse.bearer(session.accessToken(), session.accessTokenExpiresInSeconds()));
            }
        };
    }

    private String refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }

    private static void requireCsrfHeader(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.CSRF_CHECK_FAILED);
        }
    }
}
