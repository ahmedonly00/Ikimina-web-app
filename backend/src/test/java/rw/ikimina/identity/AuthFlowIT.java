package rw.ikimina.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import rw.ikimina.support.Api;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.Api.User;
import rw.ikimina.support.IntegrationTest;
import rw.ikimina.support.PostgresTestDatabase;
import rw.ikimina.support.TestPasswords;

/** Spec 16.1 / 17.1: registration with OTP, sign-in, rotating refresh tokens, reset, tampering. */
class AuthFlowIT extends IntegrationTest {

    private static final String STRONG_PASSWORD = TestPasswords.strong();

    @Nested
    class Registration {

        @Test
        void verifiedPhoneCreatesTheAccountAndSignsIn() {
            User user = api().register("Mukamana Alice");

            Response me = api().get("/api/v1/me", user).expect(200);
            assertThat(me.text("phone")).isEqualTo(user.phone());
            assertThat(me.text("fullName")).isEqualTo("Mukamana Alice");
            assertThat(me.text("platformRole")).isEqualTo("USER");
            assertThat(me.body().has("passwordHash")).isFalse();
        }

        @Test
        void refreshCookieIsHttpOnlySecureStrictAndScopedToAuth() {
            String phone = Api.newPhone();
            String ip = Api.randomIp();
            api().startRegistration(phone, "Uwase Grace", STRONG_PASSWORD, ip).expect(202);
            Response verified = api().call(HttpMethod.POST, "/api/v1/auth/verify-phone", null,
                    Map.of("phone", phone, "otp", api().lastOtp(phone)), ip).expect(200);

            String setCookie = verified.raw().getHeader("Set-Cookie");
            assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
            assertThat(verified.text("tokenType")).isEqualTo("Bearer");
            assertThat(verified.body().get("expiresIn").asLong()).isEqualTo(900);
        }

        @Test
        void noAccountExistsUntilThePhoneIsVerified() {
            String phone = Api.newPhone();
            api().startRegistration(phone, "Pending Person", STRONG_PASSWORD, null).expect(202);
            api().login(phone, STRONG_PASSWORD, null).expect(401, "INVALID_CREDENTIALS");
        }

        @Test
        void registeringAnExistingNumberLooksTheSameButSendsNothing() {
            User existing = api().register("Existing Member");
            int smsBefore = api().smsCount(existing.phone());

            api().startRegistration(existing.phone(), "Impostor", STRONG_PASSWORD, null).expect(202);

            assertThat(api().smsCount(existing.phone())).isEqualTo(smsBefore);
            api().login(existing.phone(), existing.password(), null).expect(200);
        }

        @Test
        void acceptsTheWaysPeopleTypeRwandanNumbers() {
            String phone = Api.newPhone();                       // +2507XXXXXXXX
            String local = "0" + phone.substring(4, 7) + " " + phone.substring(7, 10) + " " + phone.substring(10);
            api().startRegistration(local, "Local Format", STRONG_PASSWORD, null).expect(202);
            assertThat(api().smsCount(phone)).isEqualTo(1);
        }

        @Test
        void rejectsWeakPasswordsAndBadInput() {
            String phone = Api.newPhone();
            api().startRegistration(phone, "Weak", "password", null).expect(400, "PASSWORD_TOO_WEAK");
            api().startRegistration(phone, "Weak", "short1", null).expect(400, "PASSWORD_TOO_WEAK");
            api().startRegistration(phone, "Weak", "my" + phone.substring(4) + "pw", null).expect(400, "PASSWORD_TOO_WEAK");
            api().startRegistration("+1 555 0100", "Foreign", STRONG_PASSWORD, null).expect(400, "INVALID_PHONE_NUMBER");
            api().call(HttpMethod.POST, "/api/v1/auth/register", null, Map.of("phone", phone, "fullName", "No Terms",
                    "password", STRONG_PASSWORD, "locale", "en", "acceptTerms", false), null).expect(400, "VALIDATION_FAILED");
            api().call(HttpMethod.POST, "/api/v1/auth/register", null, Map.of("phone", phone, "fullName", "Sneaky",
                    "password", STRONG_PASSWORD, "locale", "en", "acceptTerms", true, "platformRole", "PLATFORM_ADMIN"), null)
                    .expect(400, "VALIDATION_FAILED");
        }
    }

    @Nested
    class OneTimeCodes {

        private Response verify(String phone, String otp) {
            return api().call(HttpMethod.POST, "/api/v1/auth/verify-phone", null, Map.of("phone", phone, "otp", otp), null);
        }

        @Test
        void aCodeDiesAfterFiveWrongAttemptsEvenIfTheSixthIsRight() {
            String phone = Api.newPhone();
            api().startRegistration(phone, "Guessing", STRONG_PASSWORD, null).expect(202);
            String code = api().lastOtp(phone);
            String wrong = code.equals("000000") ? "111111" : "000000";
            for (int i = 0; i < 5; i++) {
                verify(phone, wrong).expect(400, "OTP_INVALID");
            }
            verify(phone, code).expect(400, "OTP_INVALID");
        }

        @Test
        void aCodeExpiresAfterFiveMinutes() {
            String phone = Api.newPhone();
            api().startRegistration(phone, "Slow", STRONG_PASSWORD, null).expect(202);
            String code = api().lastOtp(phone);
            clock.advance(Duration.ofMinutes(5).plusSeconds(1));
            verify(phone, code).expect(400, "OTP_EXPIRED");
        }

        @Test
        void requestingANewCodeInvalidatesTheOldOne() {
            String phone = Api.newPhone();
            api().startRegistration(phone, "Twice", STRONG_PASSWORD, null).expect(202);
            String first = api().lastOtp(phone);
            api().startRegistration(phone, "Twice", STRONG_PASSWORD, null).expect(202);
            String second = api().lastOtp(phone);
            if (!first.equals(second)) {
                verify(phone, first).expect(400, "OTP_INVALID");
            }
            verify(phone, second).expect(200);
        }

        @Test
        void aCodeCanBeUsedOnlyOnce() {
            String phone = Api.newPhone();
            api().startRegistration(phone, "Once", STRONG_PASSWORD, null).expect(202);
            String code = api().lastOtp(phone);
            verify(phone, code).expect(200);
            verify(phone, code).expect(400, "OTP_INVALID");
        }
    }

    @Nested
    class SignIn {

        @Test
        void unknownNumberAndWrongPasswordFailIdentically() {
            User user = api().register("Real Account");
            Response wrongPassword = api().login(user.phone(), "Not-the-password-123", null).expect(401, "INVALID_CREDENTIALS");
            Response unknown = api().login(Api.newPhone(), "Not-the-password-123", null).expect(401, "INVALID_CREDENTIALS");
            assertThat(unknown.text("title")).isEqualTo(wrongPassword.text("title"));
            assertThat(unknown.text("detail")).isEqualTo(wrongPassword.text("detail"));
        }

        @Test
        void fiveFailuresLockTheAccountWithAProgressiveDelay() {
            User user = api().register("Forgetful");
            for (int i = 0; i < 5; i++) {
                api().login(user.phone(), "Wrong-password-" + i, Api.randomIp()).expect(401);
            }
            // Locked: even the right password fails, and indistinguishably.
            api().login(user.phone(), user.password(), Api.randomIp()).expect(401, "INVALID_CREDENTIALS");

            clock.advance(Duration.ofMinutes(1).plusSeconds(1));
            api().login(user.phone(), user.password(), Api.randomIp()).expect(200);
        }

        @Test
        void eachSignInIsAudited() throws SQLException {
            User user = api().register("Audited");
            api().login(user.phone(), "Wrong-password-x", null).expect(401);
            api().login(user.phone(), user.password(), null).expect(200);
            assertThat(platformAuditActions(user)).contains("USER_REGISTERED", "LOGIN_FAILED", "LOGIN_SUCCEEDED");
        }
    }

    @Nested
    class RefreshTokens {

        @Test
        void refreshRotatesTheToken() {
            User user = api().register("Rotating");
            Response refreshed = api().refresh(user.refreshToken(), user.ip()).expect(200);
            String next = refreshed.cookie(Api.REFRESH_COOKIE).getValue();
            assertThat(next).isNotEqualTo(user.refreshToken());
            api().get("/api/v1/me", user.withAccessToken(refreshed.text("accessToken"))).expect(200);
        }

        @Test
        void reusingARotatedTokenRevokesTheWholeFamily() throws SQLException {
            User user = api().register("Stolen Cookie");
            String stolen = user.refreshToken();
            String legit = api().refresh(stolen, user.ip()).expect(200).cookie(Api.REFRESH_COOKIE).getValue();

            Response reuse = api().refresh(stolen, Api.randomIp()).expect(401, "INVALID_REFRESH_TOKEN");
            assertThat(reuse.raw().getHeader("Set-Cookie")).contains("Max-Age=0");
            // The legitimate holder's newer token is gone too: the attacker's copy cannot outlive it.
            api().refresh(legit, user.ip()).expect(401, "INVALID_REFRESH_TOKEN");
            assertThat(platformAuditActions(user)).contains("REFRESH_TOKEN_REUSE_DETECTED");
        }

        @Test
        void refreshRequiresTheAntiCsrfHeader() {
            User user = api().register("No Header");
            api().perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                            .cookie(new Cookie(Api.REFRESH_COOKIE, user.refreshToken())))
                    .expect(403, "CSRF_CHECK_FAILED");
        }

        @Test
        void expiredRefreshTokenIsRejected() {
            User user = api().register("Thirty Days");
            clock.advance(Duration.ofDays(30).plusSeconds(1));
            api().refresh(user.refreshToken(), user.ip()).expect(401, "INVALID_REFRESH_TOKEN");
        }

        @Test
        void logoutEndsTheSession() {
            User user = api().register("Leaving");
            api().perform(MockMvcRequestBuilders.post("/api/v1/auth/logout")
                    .header("X-Ikimina-Csrf", "1")
                    .cookie(new Cookie(Api.REFRESH_COOKIE, user.refreshToken()))).expect(204);
            api().refresh(user.refreshToken(), user.ip()).expect(401, "INVALID_REFRESH_TOKEN");
        }
    }

    @Nested
    class PasswordReset {

        @Test
        void resetSignsOutEverySessionIncludingLiveAccessTokens() {
            User user = api().register("Reset Me");
            clock.advance(Duration.ofSeconds(2));   // the old token's iat must be strictly before the change
            api().post("/api/v1/auth/password/forgot", null, Map.of("phone", user.phone())).expect(202);
            String newPassword = "Gushya-" + UUID.randomUUID();
            api().post("/api/v1/auth/password/reset", null,
                    Map.of("phone", user.phone(), "otp", api().lastOtp(user.phone()), "newPassword", newPassword)).expect(204);

            api().get("/api/v1/me", user).expect(401, "UNAUTHENTICATED");
            api().refresh(user.refreshToken(), user.ip()).expect(401, "INVALID_REFRESH_TOKEN");
            api().login(user.phone(), user.password(), null).expect(401);
            api().login(user.phone(), newPassword, null).expect(200);
        }

        @Test
        void forgotPasswordForAnUnknownNumberLooksTheSame() {
            String phone = Api.newPhone();
            api().post("/api/v1/auth/password/forgot", null, Map.of("phone", phone)).expect(202);
            assertThat(api().smsCount(phone)).isZero();
        }
    }

    @Nested
    class AccessTokens {

        @Test
        void missingOrGarbageTokenIsUnauthenticated() {
            api().get("/api/v1/me", null).expect(401, "UNAUTHENTICATED");
            User user = api().register("Garbage");
            api().get("/api/v1/me", user.withAccessToken("not-a-jwt")).expect(401, "UNAUTHENTICATED");
        }

        @Test
        void tamperedPayloadIsRejected() {
            User user = api().register("Tamperer");
            String[] parts = user.accessToken().split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                    .replace("\"USER\"", "\"PLATFORM_ADMIN\"");
            String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
            api().get("/api/v1/me", user.withAccessToken(forged)).expect(401, "UNAUTHENTICATED");
        }

        @Test
        void unsignedAlgNoneTokenIsRejected() {
            User user = api().register("Alg None");
            String[] parts = user.accessToken().split("\\.");
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
            api().get("/api/v1/me", user.withAccessToken(header + "." + parts[1] + ".")).expect(401, "UNAUTHENTICATED");
        }

        @Test
        void accessTokensExpireAfterFifteenMinutes() {
            User user = api().register("Fifteen Minutes");
            clock.advance(Duration.ofMinutes(15).plusSeconds(31));   // 30 s clock-skew allowance
            api().get("/api/v1/me", user).expect(401, "UNAUTHENTICATED");
        }

        @Test
        void reauthenticationNeedsTheRightPassword() {
            User user = api().register("Step Up");
            api().post("/api/v1/auth/reauth", user, Map.of("password", "Wrong-password-1")).expect(401, "INVALID_CREDENTIALS");
            assertThat(api().reauthenticate(user).accessToken()).isNotEqualTo(user.accessToken());
        }
    }

    @Nested
    class Profile {

        @Test
        void aPersonCanChangeTheirNameAndLanguageOnly() {
            User user = api().register("Old Name");
            Response updated = api().patch("/api/v1/me", user, Map.of("fullName", "New Name", "locale", "rw")).expect(200);
            assertThat(updated.text("fullName")).isEqualTo("New Name");
            assertThat(updated.text("locale")).isEqualTo("rw");

            api().patch("/api/v1/me", user, Map.of("platformRole", "PLATFORM_ADMIN")).expect(400, "VALIDATION_FAILED");
            api().patch("/api/v1/me", user, Map.of("locale", "fr")).expect(400, "VALIDATION_FAILED");
        }
    }

    /** Platform-chain audit actions about this user, read as the owner (outside RLS). */
    private static List<String> platformAuditActions(User user) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it");
             PreparedStatement statement = owner.prepareStatement("""
                     SELECT a.action FROM audit_logs a JOIN users u ON u.id = a.actor_user_id
                     WHERE a.group_id IS NULL AND u.phone = ? ORDER BY a.id""")) {
            statement.setString(1, user.phone());
            List<String> actions = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    actions.add(rows.getString(1));
                }
            }
            return actions;
        }
    }
}
