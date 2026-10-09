package rw.ikimina.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import rw.ikimina.notifications.internal.FakeSmsProvider;
import rw.ikimina.shared.security.AuthHeaders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Drives the HTTP API the way a client would, for integration tests. */
public final class Api {

    public static final String REFRESH_COOKIE = "ikimina_refresh";

    private static final AtomicLong NEXT_PHONE = new AtomicLong(ThreadLocalRandom.current().nextLong(10_000_000L, 50_000_000L));
    private static final Pattern OTP = Pattern.compile("code is ([0-9]{6})");

    /** A response with its body parsed (null when empty). */
    public record Response(int status, JsonNode body, MockHttpServletResponse raw) {

        public String code() {
            return body == null || body.get("code") == null ? null : body.get("code").asString();
        }

        public String text(String field) {
            return body.get(field).asString();
        }

        public Cookie cookie(String name) {
            return raw.getCookie(name);
        }

        public Response expect(int expectedStatus) {
            assertThat(status).as("HTTP status; body: %s", body).isEqualTo(expectedStatus);
            return this;
        }

        public Response expect(int expectedStatus, String expectedCode) {
            expect(expectedStatus);
            assertThat(code()).as("problem code; body: %s", body).isEqualTo(expectedCode);
            return this;
        }
    }

    /** Someone who has registered and is signed in. {@code refreshToken} is the cookie value. */
    public record User(String phone, String password, String fullName, String accessToken, String refreshToken, String ip) {

        public User withAccessToken(String token) {
            return new User(phone, password, fullName, token, refreshToken, ip);
        }

        public User withRefreshToken(String token) {
            return new User(phone, password, fullName, accessToken, token, ip);
        }
    }

    private final MockMvc mvc;
    private final JsonMapper json;
    private final FakeSmsProvider sms;

    Api(MockMvc mvc, JsonMapper json, FakeSmsProvider sms) {
        this.mvc = mvc;
        this.json = json;
        this.sms = sms;
    }

    // --- raw calls -------------------------------------------------------------------------

    public Response call(HttpMethod method, String path, String accessToken, Object body, String ip) {
        return call(method, path, accessToken, body, ip, Map.of());
    }

    public Response call(HttpMethod method, String path, String accessToken, Object body, String ip, Map<String, String> headers) {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, path)
                .with(r -> {
                    r.setRemoteAddr(ip == null ? randomIp() : ip);
                    return r;
                });
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        headers.forEach(request::header);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
        }
        return perform(request);
    }

    public Response perform(MockHttpServletRequestBuilder request) {
        try {
            MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
            String content = response.getContentAsString();
            JsonNode body = content.isEmpty() ? null : json.readTree(content);
            return new Response(response.getStatus(), body, response);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Response get(String path, User as) {
        return call(HttpMethod.GET, path, as == null ? null : as.accessToken(), null, as == null ? null : as.ip());
    }

    public Response post(String path, User as, Object body) {
        return call(HttpMethod.POST, path, as == null ? null : as.accessToken(), body, as == null ? null : as.ip());
    }

    public Response put(String path, User as, Object body) {
        return call(HttpMethod.PUT, path, as == null ? null : as.accessToken(), body, as == null ? null : as.ip());
    }

    public Response patch(String path, User as, Object body) {
        return call(HttpMethod.PATCH, path, as == null ? null : as.accessToken(), body, as == null ? null : as.ip());
    }

    /** Records a contribution the way the app does, with a fresh Idempotency-Key unless one is given. */
    public Response contribute(String groupId, User as, Map<String, Object> body, String idempotencyKey) {
        return call(HttpMethod.POST, "/api/v1/groups/" + groupId + "/contributions", as.accessToken(), body, as.ip(),
                Map.of("Idempotency-Key", idempotencyKey == null ? "key-" + java.util.UUID.randomUUID() : idempotencyKey));
    }

    /** POST /auth/refresh with the cookie and the anti-CSRF header, as the web app sends it. */
    public Response refresh(String refreshToken, String ip) {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                .header(AuthHeaders.CSRF_HEADER, "1")
                .with(r -> {
                    r.setRemoteAddr(ip == null ? randomIp() : ip);
                    return r;
                });
        if (refreshToken != null) {
            request.cookie(new Cookie(REFRESH_COOKIE, refreshToken));
        }
        return perform(request);
    }

    // --- flows -----------------------------------------------------------------------------

    public static String newPhone() {
        return "+2507" + NEXT_PHONE.incrementAndGet();
    }

    public static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    public Response startRegistration(String phone, String fullName, String password, String ip) {
        return call(HttpMethod.POST, "/api/v1/auth/register", null,
                new LinkedHashMap<>(Map.of("phone", phone, "fullName", fullName,
                        "password", password, "locale", "en", "acceptTerms", true)), ip);
    }

    /** The newest one-time code texted to {@code phone}. */
    public String lastOtp(String phone) {
        List<FakeSmsProvider.SentSms> sent = sms.sentTo(phone);
        assertThat(sent).as("SMS sent to %s", phone).isNotEmpty();
        Matcher matcher = OTP.matcher(sent.getFirst().text());
        assertThat(matcher.find()).as("SMS contains a code: %s", sent.getFirst().text()).isTrue();
        return matcher.group(1);
    }

    public int smsCount(String phone) {
        return sms.sentTo(phone).size();
    }

    /** Registers a new person with a fresh phone and signs them in. */
    public User register(String fullName) {
        String phone = newPhone();
        String password = TestPasswords.strong();
        String ip = randomIp();
        startRegistration(phone, fullName, password, ip).expect(202);
        Response verified = call(HttpMethod.POST, "/api/v1/auth/verify-phone", null,
                Map.of("phone", phone, "otp", lastOtp(phone)), ip).expect(200);
        return new User(phone, password, fullName, verified.text("accessToken"),
                verified.cookie(REFRESH_COOKIE).getValue(), ip);
    }

    public Response login(String phone, String password, String ip) {
        return call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("phone", phone, "password", password), ip);
    }

    /** Re-enters the password and returns the user with a step-up access token. */
    public User reauthenticate(User user) {
        Response response = post("/api/v1/auth/reauth", user, Map.of("password", user.password())).expect(200);
        return user.withAccessToken(response.text("accessToken"));
    }
}
