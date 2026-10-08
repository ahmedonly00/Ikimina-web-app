package rw.ikimina.support;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * One PostgreSQL container per test JVM, prepared exactly like the real thing: the
 * same image family, and the same {@code docker/postgres/sql/roles.sql} that
 * docker-compose runs, so tests see the owner/app role split and its privileges.
 *
 * <p>Each test class asks for its own database by name, so classes that need a
 * pristine, unmigrated schema do not depend on test ordering.
 */
public final class PostgresTestDatabase {

    /** Spec 4.2: PostgreSQL 16+. Tests run on the minimum supported major version. */
    public static final String IMAGE = "postgres:16-alpine";

    public static final String OWNER = "ikimina_owner";
    public static final String APP = "ikimina_app";
    public static final String OWNER_PASSWORD = "owner-test-only";
    public static final String APP_PASSWORD = "app-test-only";
    public static final String SCHEMA = "ikimina";

    private static final String ROLES_SQL_IN_CONTAINER = "/opt/ikimina/sql/roles.sql";
    private static final Pattern DATABASE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,40}");

    private static final PostgreSQLContainer CONTAINER = start();
    private static final Set<String> PREPARED = ConcurrentHashMap.newKeySet();

    private PostgresTestDatabase() {
    }

    private static PostgreSQLContainer start() {
        PostgreSQLContainer container = new PostgreSQLContainer(IMAGE)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(Path.of("docker/postgres/sql/roles.sql")), ROLES_SQL_IN_CONTAINER);
        container.start();
        return container;
    }

    /** Creates (once) a database with the Ikimina roles and schema, but no migrations applied. */
    public static synchronized String prepare(String databaseName) {
        if (!DATABASE_NAME.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Unsafe database name: " + databaseName);
        }
        if (PREPARED.add(databaseName)) {
            try (Connection connection = superuserConnection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + databaseName);
            } catch (SQLException e) {
                throw new IllegalStateException("Could not create test database " + databaseName, e);
            }
            runRolesScript(databaseName);
        }
        return jdbcUrl(databaseName);
    }

    public static String jdbcUrl(String databaseName) {
        return "jdbc:postgresql://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                + "/" + databaseName + "?currentSchema=" + SCHEMA;
    }

    public static Connection connectAs(String databaseName, String user, String password) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(databaseName), user, password);
    }

    public static Connection connectAsOwner(String databaseName) throws SQLException {
        return connectAs(databaseName, OWNER, OWNER_PASSWORD);
    }

    public static Connection connectAsApp(String databaseName) throws SQLException {
        return connectAs(databaseName, APP, APP_PASSWORD);
    }

    /** Flyway placeholders the migrations expect, as the application sets them. */
    public static Map<String, String> flywayPlaceholders() {
        return Map.of("appRole", APP);
    }

    /** Points a Spring Boot test at {@code databaseName}: app role for the pool, owner role for Flyway. */
    public static void register(DynamicPropertyRegistry registry, String databaseName) {
        String url = prepare(databaseName);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> APP);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.user", () -> OWNER);
        registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
    }

    private static Connection superuserConnection() throws SQLException {
        return DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
    }

    private static void runRolesScript(String databaseName) {
        try {
            Container.ExecResult result = CONTAINER.execInContainer(
                    "psql", "-v", "ON_ERROR_STOP=1",
                    "-U", CONTAINER.getUsername(), "-d", databaseName,
                    "-v", "owner_password=" + OWNER_PASSWORD,
                    "-v", "app_password=" + APP_PASSWORD,
                    "-f", ROLES_SQL_IN_CONTAINER);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("roles.sql failed on " + databaseName + ": " + result.getStderr());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
