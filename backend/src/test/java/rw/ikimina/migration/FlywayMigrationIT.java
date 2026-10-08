package rw.ikimina.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import rw.ikimina.support.PostgresTestDatabase;
import rw.ikimina.support.TestMigrations;

/** The migration chain applies cleanly to an empty PostgreSQL, is idempotent, and validates. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywayMigrationIT {

    private static final String DATABASE = "flyway_it";

    private Flyway flyway;
    private MigrateResult firstRun;

    @BeforeAll
    void migrateAnEmptyDatabase() {
        flyway = TestMigrations.flyway(DATABASE);
        firstRun = flyway.migrate();
    }

    @Test
    void appliesEveryMigrationToAnEmptyDatabase() {
        assertThat(firstRun.success).isTrue();
        assertThat(firstRun.migrationsExecuted).isEqualTo(flyway.info().applied().length);
        assertThat(firstRun.migrationsExecuted).isPositive();
        for (MigrationInfo migration : flyway.info().all()) {
            assertThat(migration.getState()).as(migration.getScript()).isEqualTo(MigrationState.SUCCESS);
        }
    }

    @Test
    void reRunningIsANoOp() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void appliedSchemaValidatesAgainstTheMigrationFiles() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void foundationObjectsExistAndBelongToTheOwner() throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner(DATABASE);
             Statement statement = owner.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT pg_get_userbyid(p.proowner) AS owner
                     FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                     WHERE n.nspname = 'ikimina' AND p.proname = 'forbid_mutation'""")) {
            assertThat(rows.next()).as("forbid_mutation() exists").isTrue();
            assertThat(rows.getString("owner")).isEqualTo(PostgresTestDatabase.OWNER);
        }
    }

    @Test
    void forbidMutationBlocksUpdateDeleteAndTruncate() throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner(DATABASE);
             Statement statement = owner.createStatement()) {
            statement.execute("CREATE TEMP TABLE guarded (id INT)");
            statement.execute("CREATE TRIGGER guarded_rows BEFORE UPDATE OR DELETE ON guarded "
                    + "FOR EACH ROW EXECUTE FUNCTION ikimina.forbid_mutation()");
            statement.execute("CREATE TRIGGER guarded_truncate BEFORE TRUNCATE ON guarded "
                    + "FOR EACH STATEMENT EXECUTE FUNCTION ikimina.forbid_mutation()");
            statement.execute("INSERT INTO guarded VALUES (1)");

            assertRejected(statement, "UPDATE guarded SET id = 2");
            assertRejected(statement, "DELETE FROM guarded");
            assertRejected(statement, "TRUNCATE guarded");
            try (ResultSet rows = statement.executeQuery("SELECT id FROM guarded")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }

    private static void assertRejected(Statement statement, String sql) {
        assertThatThrownBy(() -> statement.execute(sql))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("immutable")
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23001"));
    }
}
