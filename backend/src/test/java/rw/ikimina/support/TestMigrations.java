package rw.ikimina.support;

import org.flywaydb.core.Flyway;

/** Flyway configured the way the application configures it (see application.yml), for tests that drive it directly. */
public final class TestMigrations {

    private TestMigrations() {
    }

    public static Flyway flyway(String databaseName) {
        return Flyway.configure()
                .dataSource(PostgresTestDatabase.prepare(databaseName),
                        PostgresTestDatabase.OWNER, PostgresTestDatabase.OWNER_PASSWORD)
                .schemas(PostgresTestDatabase.SCHEMA)
                .defaultSchema(PostgresTestDatabase.SCHEMA)
                .createSchemas(false)
                .locations("classpath:db/migration")
                .placeholders(PostgresTestDatabase.flywayPlaceholders())
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load();
    }
}
