package rw.ikimina.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rw.ikimina.archfixtures.bad.FieldInjected;
import rw.ikimina.archfixtures.bad.FloatingPointConversion;
import rw.ikimina.archfixtures.bad.FloatingPointField;
import rw.ikimina.archfixtures.bad.FloatingPointSignature;
import rw.ikimina.archfixtures.bad.ReadsWallClock;
import rw.ikimina.archfixtures.good.WellBehaved;
import rw.ikimina.shared.time.ClockConfig;

/**
 * Proves each architecture rule fails on code that breaks it and passes on code
 * that does not. A rule that can never fail would make ArchitectureTest a no-op.
 *
 * <p>Lombok has no canary here: it is kept off the classpath entirely by the
 * maven-enforcer ban, so a Lombok-using fixture could not even compile.
 */
class ArchitectureRulesCanaryTest {

    private static final String BAD_MODULES = "rw.ikimina.archfixtures.modules";
    private static final String CLEAN_MODULES = "rw.ikimina.archfixtures.cleanmodules";

    private static final ClassFileImporter IMPORTER = new ClassFileImporter();

    private static JavaClasses classes(Class<?>... types) {
        return IMPORTER.importClasses(types);
    }

    private static void assertViolates(ArchRule rule, JavaClasses classes, String expectedFragment) {
        EvaluationResult result = rule.evaluate(classes);
        assertThat(result.hasViolation()).as("%s should be violated", rule.getDescription()).isTrue();
        assertThat(result.getFailureReport().toString()).contains(expectedFragment);
    }

    private static void assertSatisfied(ArchRule rule, JavaClasses classes) {
        EvaluationResult result = rule.evaluate(classes);
        assertThat(result.hasViolation()).as(result.getFailureReport().toString()).isFalse();
    }

    @Nested
    class FloatingPoint {

        @Test
        void fieldIsDetected() {
            assertViolates(ArchitectureRules.noFloatingPointFields(), classes(FloatingPointField.class), "balance");
        }

        @Test
        void signatureIsDetected() {
            assertViolates(ArchitectureRules.noFloatingPointInSignatures(), classes(FloatingPointSignature.class), "interestRate");
        }

        @Test
        void bigDecimalConversionsAreDetected() {
            assertViolates(ArchitectureRules.noFloatingPointBigDecimalConversions(),
                    classes(FloatingPointConversion.class), "BigDecimal.valueOf(double)");
            assertViolates(ArchitectureRules.noFloatingPointBigDecimalConversions(),
                    classes(FloatingPointConversion.class), "BigDecimal.<init>(double)");
        }

        @Test
        void exactDecimalCodeIsAccepted() {
            JavaClasses good = classes(WellBehaved.class);
            assertSatisfied(ArchitectureRules.noFloatingPointFields(), good);
            assertSatisfied(ArchitectureRules.noFloatingPointInSignatures(), good);
            assertSatisfied(ArchitectureRules.noFloatingPointBigDecimalConversions(), good);
        }
    }

    @Nested
    class WallClock {

        @Test
        void directReadsAreDetected() {
            ArchRule rule = ArchitectureRules.noWallClockReads(ClockConfig.class);
            assertViolates(rule, classes(ReadsWallClock.class), "LocalDate.now()");
            assertViolates(rule, classes(ReadsWallClock.class), "Instant.now()");
        }

        @Test
        void injectedClockIsAccepted() {
            assertSatisfied(ArchitectureRules.noWallClockReads(ClockConfig.class), classes(WellBehaved.class));
        }

        @Test
        void theDesignatedClockOwnerIsExempt() {
            // ClockConfig calls Clock.system(ZoneId); imported alongside a class the rule does check.
            assertSatisfied(ArchitectureRules.noWallClockReads(ClockConfig.class), classes(ClockConfig.class, WellBehaved.class));
        }
    }

    @Nested
    class Injection {

        @Test
        void fieldInjectionIsDetected() {
            assertViolates(ArchitectureRules.noFieldInjection(), classes(FieldInjected.class), "dependency");
        }

        @Test
        void constructorInjectionIsAccepted() {
            assertSatisfied(ArchitectureRules.noFieldInjection(), classes(WellBehaved.class));
        }
    }

    @Nested
    class Modules {

        private final JavaClasses bad = IMPORTER.importPackages(BAD_MODULES);
        private final JavaClasses clean = IMPORTER.importPackages(CLEAN_MODULES);

        @Test
        void reachingIntoAnotherModulesInternalsIsDetected() {
            assertViolates(ArchitectureRules.moduleInternalsAreEncapsulated(BAD_MODULES), bad, "BetaService");
        }

        @Test
        void usingYourOwnInternalsIsAccepted() {
            EvaluationResult result = ArchitectureRules.moduleInternalsAreEncapsulated(BAD_MODULES).evaluate(bad);
            assertThat(result.getFailureReport().toString()).doesNotContain("AlphaApi");
            assertSatisfied(ArchitectureRules.moduleInternalsAreEncapsulated(CLEAN_MODULES), clean);
        }

        @Test
        void sharedDependingOnAModuleIsDetected() {
            assertViolates(ArchitectureRules.sharedDependsOnNoModule(BAD_MODULES), bad, "SharedUtil");
            assertSatisfied(ArchitectureRules.sharedDependsOnNoModule(CLEAN_MODULES), clean);
        }

        @Test
        void cyclesBetweenModulesAreDetected() {
            assertViolates(ArchitectureRules.modulesAreFreeOfCycles(BAD_MODULES), bad, "Cycle");
            assertSatisfied(ArchitectureRules.modulesAreFreeOfCycles(CLEAN_MODULES), clean);
        }

        @Test
        void repositoryOutsideInternalIsDetected() {
            assertViolates(ArchitectureRules.repositoriesAreModuleInternal(), bad, "BetaRepository");
            assertSatisfied(ArchitectureRules.repositoriesAreModuleInternal(), clean);
        }
    }
}
