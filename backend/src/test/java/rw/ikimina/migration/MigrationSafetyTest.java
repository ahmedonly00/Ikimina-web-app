package rw.ikimina.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Hard Rule H3: migrations are forward-only and additive. Runs in every build, so a
 * destructive migration fails locally before it ever reaches CI.
 *
 * <p>The fixture tests are the canary: they prove the check catches each banned
 * statement and does not trip over comments, strings or protective triggers.
 */
class MigrationSafetyTest {

    /** Maven runs tests from the module directory. */
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Path UNSAFE = Path.of("src/test/resources/migration-fixtures/unsafe");
    private static final Path SAFE = Path.of("src/test/resources/migration-fixtures/safe");

    @Test
    void productionMigrationsAreSafe() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            assertThat(files.count()).as("migrations found").isPositive();
        }
        List<MigrationSafetyCheck.Violation> violations = MigrationSafetyCheck.checkDirectory(MIGRATIONS);
        assertThat(violations).as("destructive or unsafe migration statements").isEmpty();
    }

    @TestFactory
    Stream<DynamicTest> everyUnsafeFixtureIsRejectedForTheExpectedReason() throws IOException {
        List<Path> fixtures;
        try (Stream<Path> files = Files.list(UNSAFE)) {
            fixtures = files.sorted().toList();
        }
        assertThat(fixtures).hasSizeGreaterThanOrEqualTo(MigrationSafetyCheck.BANNED.size());
        return fixtures.stream().map(fixture -> DynamicTest.dynamicTest(fixture.getFileName().toString(), () -> {
            String expected = expectedRule(fixture);
            assertThat(MigrationSafetyCheck.checkFile(fixture))
                    .extracting(MigrationSafetyCheck.Violation::rule)
                    .contains(expected);
        }));
    }

    @Test
    void everyBannedRuleHasAnUnsafeFixture() throws IOException {
        List<String> covered;
        try (Stream<Path> files = Files.list(UNSAFE)) {
            covered = files.map(MigrationSafetyTest::expectedRule).toList();
        }
        assertThat(covered).containsAll(MigrationSafetyCheck.BANNED.keySet());
    }

    @Test
    void safeFixturesPass() {
        assertThat(MigrationSafetyCheck.checkDirectory(SAFE)).isEmpty();
    }

    @Test
    void reportsTheLineOfTheOffendingStatement() {
        List<MigrationSafetyCheck.Violation> violations = MigrationSafetyCheck.checkSql("V9__x.sql",
                "-- header\nCREATE TABLE a (id INT);\n\nDELETE FROM a;\n");
        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.line()).isEqualTo(4);
            assertThat(v.excerpt()).isEqualTo("DELETE FROM a;");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"V1__init.sql", "V12__add_loan_products.sql", "V3__x2.sql"})
    void acceptsVersionedMigrationNames(String name) {
        assertThat(MigrationSafetyCheck.FILE_NAME.matcher(name).matches()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"R__views.sql", "U2__undo.sql", "V0__zero.sql", "V1_single_underscore.sql",
            "v1__lowercase_v.sql", "V1__Mixed_Case.sql", "V1__trailing_.sql", "V1__x.SQL", "V1.1__dotted.sql"})
    void rejectsOtherNames(String name) {
        assertThat(MigrationSafetyCheck.FILE_NAME.matcher(name).matches()).isFalse();
    }

    private static String expectedRule(Path fixture) {
        try {
            String firstLine = Files.readAllLines(fixture, StandardCharsets.UTF_8).getFirst();
            assertThat(firstLine).as("fixture %s declares '-- expect: <rule>'", fixture).startsWith("-- expect: ");
            return firstLine.substring("-- expect: ".length()).trim();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
