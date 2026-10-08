package rw.ikimina;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.time.BusinessTime;
import rw.ikimina.support.PostgresTestDatabase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 0 acceptance: the empty application boots against real PostgreSQL, applies
 * its migrations as the owner role, runs as the app role, and serves health and the
 * OpenAPI contract. The OpenAPI document is written to target/openapi/ for CI.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"springdoc.api-docs.enabled=true"})
class ApplicationBootIT {

    private static final Path OPENAPI_OUTPUT = Path.of("target/openapi/openapi.json");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "app_boot_it");
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper json;

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private Clock clock;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void healthIsUpIncludingTheDatabase() throws Exception {
        HttpResponse<String> health = get("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(json.readTree(health.body()).get("status").asString()).isEqualTo("UP");
        // Anonymous callers see the status only, never component details.
        assertThat(health.body()).doesNotContain("db", "PostgreSQL");

        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
    }

    @Test
    void servesTheOpenApiContract() throws Exception {
        HttpResponse<String> docs = get("/v3/api-docs");
        assertThat(docs.statusCode()).isEqualTo(200);
        JsonNode openApi = json.readTree(docs.body());
        assertThat(openApi.get("openapi").asString()).startsWith("3.");
        assertThat(openApi.get("info").get("title").asString()).isEqualTo("Ikimina API");

        Files.createDirectories(OPENAPI_OUTPUT.getParent());
        Files.writeString(OPENAPI_OUTPUT, json.writerWithDefaultPrettyPrinter().writeValueAsString(openApi));
    }

    @Test
    void everythingElseIsClosedWithAProblemDocument() throws Exception {
        HttpResponse<String> response = get("/api/v1/groups", "X-Request-Id", "boot-it-1");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.get("code").asString()).isEqualTo("UNAUTHENTICATED");
        assertThat(problem.get("requestId").asString()).isEqualTo("boot-it-1");
        assertThat(response.headers().firstValue("X-Request-Id")).hasValue("boot-it-1");
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
    }

    @Test
    void schedulerLockWorksWithTheAppRolesPrivileges() {
        Optional<SimpleLock> lock = lockProvider.lock(new LockConfiguration(
                Instant.now(clock), "phase0-boot-it", Duration.ofSeconds(30), Duration.ZERO));
        assertThat(lock).isPresent();
        Optional<SimpleLock> second = lockProvider.lock(new LockConfiguration(
                Instant.now(clock), "phase0-boot-it", Duration.ofSeconds(30), Duration.ZERO));
        assertThat(second).as("the same job cannot be locked twice").isEmpty();
        lock.get().unlock();
    }

    @Test
    void applicationWiringFollowsTheConventions() {
        assertThat(clock.getZone()).isEqualTo(BusinessTime.ZONE);
        assertThat(json.writeValueAsString(Money.ofWholeRwf(150_000))).isEqualTo("\"150000.00\"");
    }
}
