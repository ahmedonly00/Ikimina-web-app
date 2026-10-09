package rw.ikimina.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import rw.ikimina.shared.web.RequestIdFilter;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.Api.User;
import rw.ikimina.support.IntegrationTest;

/**
 * Every error leaves the API as RFC 7807 with a stable code and a localised message
 * (spec 17), exercised through the full application against {@link ErrorProbeController}.
 */
@ActiveProfiles("error-probe")
class ErrorHandlingIT extends IntegrationTest {

    private static final String BASE = "/test-support/errors";

    private User user;

    @BeforeEach
    void signIn() {
        user = api().register("Error Prober");
    }

    private Response get(String path, String... headers) {
        var request = MockMvcRequestBuilders.get(BASE + path).header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken());
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return api().perform(request);
    }

    private Response postJson(String body) {
        return api().call(HttpMethod.POST, BASE + "/echo", user.accessToken(), body, null);
    }

    private static void isProblem(Response response, int status, ErrorCode code) {
        response.expect(status, code.name());
        assertThat(response.raw().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.body().get("status").asInt()).isEqualTo(status);
        assertThat(response.text("title")).isNotBlank();
        assertThat(response.text("detail")).isNotBlank();
        assertThat(response.text("requestId")).isNotBlank();
    }

    @Test
    void businessRuleFailureCarriesItsCodeAndEnglishText() {
        Response response = get("/business-rule", RequestIdFilter.HEADER, "trace-123");
        isProblem(response, 422, ErrorCode.INSUFFICIENT_GROUP_FUNDS);
        assertThat(response.text("title")).isEqualTo("Not enough group funds");
        assertThat(response.text("requestId")).isEqualTo("trace-123");
        assertThat(response.raw().getHeader(RequestIdFilter.HEADER)).isEqualTo("trace-123");
    }

    @Test
    void messagesFollowAcceptLanguageForKinyarwanda() {
        Response response = get("/business-rule", HttpHeaders.ACCEPT_LANGUAGE, "rw-RW,rw;q=0.9");
        assertThat(response.code()).isEqualTo("INSUFFICIENT_GROUP_FUNDS");
        assertThat(response.text("title")).startsWith("[rw-todo]");
    }

    @Test
    void unsupportedLanguagesFallBackToEnglish() {
        assertThat(get("/business-rule", HttpHeaders.ACCEPT_LANGUAGE, "fr").text("title")).isEqualTo("Not enough group funds");
    }

    @Test
    void validRequestRoundTripsMoneyAsAString() {
        Response response = postJson("{\"amount\":\"150000.5\",\"reference\":\"MP2410.1234\"}").expect(200);
        assertThat(response.text("amount")).isEqualTo("150000.50");
    }

    @Test
    void unknownJsonFieldIsRejected() {
        isProblem(postJson("{\"amount\":\"10\",\"reference\":\"x\",\"approvedBy\":1}"), 400, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void moneySentAsAJsonNumberIsRejected() {
        isProblem(postJson("{\"amount\":0.1,\"reference\":\"x\"}"), 400, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void moneyWithSubFrancPrecisionIsRejected() {
        isProblem(postJson("{\"amount\":\"10.005\",\"reference\":\"x\"}"), 400, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void malformedJsonIsRejected() {
        isProblem(postJson("{\"amount\":"), 400, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void beanValidationFailuresListFieldAndConstraint() {
        Response response = postJson("{\"reference\":\"\"}");
        isProblem(response, 400, ErrorCode.VALIDATION_FAILED);
        Map<String, String> constraints = new HashMap<>();
        response.body().get("errors").forEach(error -> constraints.put(error.get("field").asString(), error.get("constraint").asString()));
        assertThat(constraints).containsEntry("amount", "NotNull").containsEntry("reference", "NotBlank");
    }

    @Test
    void unexpectedFailureDoesNotLeakInternals() throws Exception {
        Response response = get("/bug");
        isProblem(response, 500, ErrorCode.INTERNAL_ERROR);
        String body = response.raw().getContentAsString();
        assertThat(body).doesNotContain("db-internal", "ikimina_app", "IllegalStateException");
    }

    @Test
    void wrongMethodIsReported() {
        isProblem(api().call(HttpMethod.PUT, BASE + "/echo", user.accessToken(), null, null), 405, ErrorCode.METHOD_NOT_ALLOWED);
    }

    @Test
    void wrongContentTypeIsReported() {
        isProblem(api().perform(MockMvcRequestBuilders.post(BASE + "/echo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken())
                .contentType(MediaType.TEXT_PLAIN).content("x")), 415, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void accessDeniedForASignedInUserIsForbidden() {
        isProblem(get("/denied"), 403, ErrorCode.FORBIDDEN);
    }

    @Test
    void anonymousCallerGetsUnauthenticated() {
        Response response = api().perform(MockMvcRequestBuilders.get(BASE + "/business-rule"));
        isProblem(response, 401, ErrorCode.UNAUTHENTICATED);
        assertThat(response.raw().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }
}
