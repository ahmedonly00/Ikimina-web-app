package rw.ikimina.support;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import rw.ikimina.notifications.internal.FakeSmsProvider;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for API-level integration tests: the whole application on real PostgreSQL (with the
 * owner/app role split and RLS), a movable clock, and the fake SMS gateway to read codes from.
 * Every subclass shares one Spring context and one database; tests isolate themselves by
 * creating their own users and groups.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "app_it");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {

        /** Starts at the real current time so database defaults (now()) and the app clock roughly agree. */
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Clock.systemUTC().instant());
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected FakeSmsProvider sms;

    private Api api;

    protected Api api() {
        if (api == null) {
            api = new Api(mvc, json, sms);
        }
        return api;
    }

    protected Instant now() {
        return clock.instant();
    }
}
