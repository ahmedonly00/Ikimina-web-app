package rw.ikimina.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rw.ikimina.support.PostgresTestDatabase;
import rw.ikimina.support.TestMigrations;

/**
 * Spec 5.3: the application's database role must not be a superuser and must not
 * own the tables, or row-level security would not apply to it. It gets only the
 * privileges each migration grants it.
 */
class DatabaseRolesIT {

    private static final String DATABASE = "roles_it";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @BeforeAll
    static void migrate() {
        TestMigrations.flyway(DATABASE).migrate();
    }

    @Test
    void appRoleHasNoElevatedAttributes() throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner(DATABASE);
             Statement statement = owner.createStatement();
             ResultSet role = statement.executeQuery("""
                     SELECT rolsuper, rolbypassrls, rolcreaterole, rolcreatedb, rolreplication
                     FROM pg_roles WHERE rolname = 'ikimina_app'""")) {
            assertThat(role.next()).isTrue();
            assertThat(role.getBoolean("rolsuper")).as("superuser").isFalse();
            assertThat(role.getBoolean("rolbypassrls")).as("bypasses RLS").isFalse();
            assertThat(role.getBoolean("rolcreaterole")).as("creates roles").isFalse();
            assertThat(role.getBoolean("rolcreatedb")).as("creates databases").isFalse();
            assertThat(role.getBoolean("rolreplication")).as("replication").isFalse();
        }
    }

    @Test
    void ownerOwnsEveryObjectInTheSchemaAndTheAppOwnsNothing() throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner(DATABASE);
             Statement statement = owner.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT c.relname, pg_get_userbyid(c.relowner) AS owner
                     FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE n.nspname = 'ikimina'""")) {
            int objects = 0;
            while (rows.next()) {
                objects++;
                assertThat(rows.getString("owner")).as(rows.getString("relname")).isEqualTo(PostgresTestDatabase.OWNER);
            }
            assertThat(objects).as("objects in schema ikimina").isPositive();
        }
    }

    @Test
    void appCannotCreateObjects() throws SQLException {
        try (Connection app = PostgresTestDatabase.connectAsApp(DATABASE); Statement statement = app.createStatement()) {
            assertDenied(statement, "CREATE TABLE ikimina.sneaky (id INT)");
            assertDenied(statement, "CREATE TABLE public.sneaky (id INT)");
        }
    }

    @Test
    void appCannotTouchTheMigrationHistory() throws SQLException {
        try (Connection app = PostgresTestDatabase.connectAsApp(DATABASE); Statement statement = app.createStatement()) {
            assertDenied(statement, "SELECT * FROM flyway_schema_history");
            assertDenied(statement, "DELETE FROM flyway_schema_history");
        }
    }

    @Test
    void appHasExactlyTheShedlockPrivilegesItNeeds() throws SQLException {
        try (Connection app = PostgresTestDatabase.connectAsApp(DATABASE); Statement statement = app.createStatement()) {
            statement.execute("INSERT INTO shedlock (name, lock_until, locked_at, locked_by) "
                    + "VALUES ('roles-it', now(), now(), 'test')");
            statement.execute("UPDATE shedlock SET locked_by = 'test-2' WHERE name = 'roles-it'");
            try (ResultSet rows = statement.executeQuery("SELECT locked_by FROM shedlock WHERE name = 'roles-it'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("test-2");
            }
            assertDenied(statement, "DELETE FROM shedlock");
            assertDenied(statement, "TRUNCATE shedlock");
        }
    }

    private static void assertDenied(Statement statement, String sql) {
        assertThatThrownBy(() -> statement.execute(sql))
                .as(sql)
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }
}
