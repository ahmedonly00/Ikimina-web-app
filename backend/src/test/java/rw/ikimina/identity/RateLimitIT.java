package rw.ikimina.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import rw.ikimina.support.Api;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.IntegrationTest;
import rw.ikimina.support.TestPasswords;

/**
 * Phase 1 acceptance: OTP and login rate limits, demonstrated (spec 16.5). Each limit is
 * exceeded, answered with 429 and Retry-After, and lifted when its window passes.
 */
class RateLimitIT extends IntegrationTest {

    private static final String PASSWORD = TestPasswords.strong();

    @Test
    void codesToOnePhoneAreLimitedToThreePerQuarterHour() {
        String phone = Api.newPhone();
        for (int i = 0; i < 3; i++) {
            api().startRegistration(phone, "Impatient", PASSWORD, Api.randomIp()).expect(202);
        }
        Response limited = api().startRegistration(phone, "Impatient", PASSWORD, Api.randomIp()).expect(429, "RATE_LIMITED");
        assertRetryAfter(limited, 900);
        assertThat(api().smsCount(phone)).as("no SMS beyond the limit").isEqualTo(3);

        clock.advance(Duration.ofMinutes(15));
        api().startRegistration(phone, "Impatient", PASSWORD, Api.randomIp()).expect(202);
    }

    @Test
    void codesFromOneAddressAreLimitedAcrossPhones() {
        String ip = Api.randomIp();
        // Forgot-password draws on the same per-address budget as registration codes.
        for (int i = 0; i < 10; i++) {
            api().call(HttpMethod.POST, "/api/v1/auth/password/forgot", null, Map.of("phone", Api.newPhone()), ip).expect(202);
        }
        assertRetryAfter(api().call(HttpMethod.POST, "/api/v1/auth/password/forgot", null,
                Map.of("phone", Api.newPhone()), ip).expect(429, "RATE_LIMITED"), 900);
    }

    @Test
    void signInAttemptsOnOnePhoneAreLimitedEvenFromManyAddresses() {
        String phone = Api.newPhone();
        for (int i = 0; i < 10; i++) {
            api().login(phone, "Wrong-password-" + i, Api.randomIp()).expect(401, "INVALID_CREDENTIALS");
        }
        assertRetryAfter(api().login(phone, "Wrong-password-x", Api.randomIp()).expect(429, "RATE_LIMITED"), 900);
    }

    @Test
    void signInAttemptsFromOneAddressAreLimitedEvenAcrossPhones() {
        String ip = Api.randomIp();
        for (int i = 0; i < 30; i++) {
            api().login(Api.newPhone(), "Wrong-password-" + i, ip).expect(401, "INVALID_CREDENTIALS");
        }
        assertRetryAfter(api().login(Api.newPhone(), "Wrong-password-x", ip).expect(429, "RATE_LIMITED"), 900);

        clock.advance(Duration.ofMinutes(15));
        api().login(Api.newPhone(), "Wrong-password-y", ip).expect(401, "INVALID_CREDENTIALS");
    }

    @Test
    void codeVerificationIsLimitedPerPhone() {
        String phone = Api.newPhone();
        api().startRegistration(phone, "Guesser", PASSWORD, Api.randomIp()).expect(202);
        for (int i = 0; i < 10; i++) {
            api().call(HttpMethod.POST, "/api/v1/auth/verify-phone", null, Map.of("phone", phone, "otp", "000000"), Api.randomIp());
        }
        api().call(HttpMethod.POST, "/api/v1/auth/verify-phone", null, Map.of("phone", phone, "otp", "000000"), Api.randomIp())
                .expect(429, "RATE_LIMITED");
    }

    private static void assertRetryAfter(Response response, int windowSeconds) {
        String retryAfter = response.raw().getHeader("Retry-After");
        assertThat(retryAfter).as("Retry-After header").isNotNull();
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, windowSeconds);
    }
}
