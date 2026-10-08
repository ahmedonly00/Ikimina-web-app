package rw.ikimina.shared.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import rw.ikimina.shared.i18n.LocaleConfig;
import rw.ikimina.shared.money.MoneyJsonConfig;
import rw.ikimina.shared.security.SecurityConfig;
import rw.ikimina.shared.web.RequestIdFilter;

/** Every error leaves the API as RFC 7807 with a stable code and a localised message (spec 17). */
@WebMvcTest(ErrorProbeController.class)
@ActiveProfiles("error-probe")
@Import({SecurityConfig.class, ProblemFactory.class, LocaleConfig.class, MoneyJsonConfig.class})
@WithMockUser
class ErrorHandlingTest {

    private static final String BASE = "/test-support/errors";

    @Autowired
    private MockMvc mvc;

    private ResultActions postJson(String body) throws Exception {
        return mvc.perform(post(BASE + "/echo").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static void isProblem(ResultActions result, int status, ErrorCode code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void businessRuleFailureCarriesItsCodeAndEnglishText() throws Exception {
        ResultActions result = mvc.perform(get(BASE + "/business-rule").header(RequestIdFilter.HEADER, "trace-123"));
        isProblem(result, 422, ErrorCode.INSUFFICIENT_GROUP_FUNDS);
        result.andExpect(jsonPath("$.title").value("Not enough group funds"))
                .andExpect(jsonPath("$.requestId").value("trace-123"))
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-123"));
    }

    @Test
    void messagesFollowAcceptLanguageForKinyarwanda() throws Exception {
        mvc.perform(get(BASE + "/business-rule").header(HttpHeaders.ACCEPT_LANGUAGE, "rw-RW,rw;q=0.9"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_GROUP_FUNDS"))
                .andExpect(jsonPath("$.title").value(startsWith("[rw-todo]")));
    }

    @Test
    void unsupportedLanguagesFallBackToEnglish() throws Exception {
        mvc.perform(get(BASE + "/business-rule").header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(jsonPath("$.title").value("Not enough group funds"));
    }

    @Test
    void validRequestRoundTripsMoneyAsAString() throws Exception {
        postJson("{\"amount\":\"150000.5\",\"reference\":\"MP2410.1234\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("150000.50"));
    }

    @Test
    void unknownJsonFieldIsRejected() throws Exception {
        isProblem(postJson("{\"amount\":\"10\",\"reference\":\"x\",\"approvedBy\":1}"), 400, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void moneySentAsAJsonNumberIsRejected() throws Exception {
        isProblem(postJson("{\"amount\":0.1,\"reference\":\"x\"}"), 400, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void moneyWithSubFrancPrecisionIsRejected() throws Exception {
        isProblem(postJson("{\"amount\":\"10.005\",\"reference\":\"x\"}"), 400, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        isProblem(postJson("{\"amount\":"), 400, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void beanValidationFailuresListFieldAndConstraint() throws Exception {
        ResultActions result = postJson("{\"reference\":\"\"}");
        isProblem(result, 400, ErrorCode.VALIDATION_FAILED);
        result.andExpect(jsonPath("$.errors[?(@.field == 'amount')].constraint").value("NotNull"))
                .andExpect(jsonPath("$.errors[?(@.field == 'reference')].constraint").value("NotBlank"));
    }

    @Test
    void unexpectedFailureDoesNotLeakInternals() throws Exception {
        ResultActions result = mvc.perform(get(BASE + "/bug"));
        isProblem(result, 500, ErrorCode.INTERNAL_ERROR);
        result.andExpect(content().string(not(containsString("db-internal"))))
                .andExpect(content().string(not(containsString("ikimina_app"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void wrongMethodIsReported() throws Exception {
        isProblem(mvc.perform(put(BASE + "/echo")), 405, ErrorCode.METHOD_NOT_ALLOWED);
    }

    @Test
    void wrongContentTypeIsReported() throws Exception {
        isProblem(mvc.perform(post(BASE + "/echo").contentType(MediaType.TEXT_PLAIN).content("x")),
                415, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void accessDeniedForASignedInUserIsForbidden() throws Exception {
        isProblem(mvc.perform(get(BASE + "/denied")), 403, ErrorCode.FORBIDDEN);
    }

    @Test
    @WithAnonymousUser
    void anonymousCallerGetsUnauthenticated() throws Exception {
        ResultActions result = mvc.perform(get(BASE + "/business-rule"));
        isProblem(result, 401, ErrorCode.UNAUTHENTICATED);
        result.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }
}
