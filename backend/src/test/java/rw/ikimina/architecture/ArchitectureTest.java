package rw.ikimina.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import rw.ikimina.IkiminaApplication;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.time.ClockConfig;

/** Applies every architecture rule to the production code. Any violation fails the build. */
class ArchitectureTest {

    private static final String ROOT = "rw.ikimina";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @TestFactory
    Stream<DynamicTest> productionCodeObeysTheArchitectureRules() {
        return ArchitectureRules.all(ROOT, ClockConfig.class).stream()
                .map(rule -> DynamicTest.dynamicTest(rule.getDescription(), () -> rule.check(PRODUCTION)));
    }

    /** Guards against the rules passing vacuously because the import found nothing (e.g. after a package rename). */
    @Test
    void importSeesTheProductionCode() {
        assertThat(PRODUCTION.contain(IkiminaApplication.class)).isTrue();
        assertThat(PRODUCTION.contain(Money.class)).isTrue();
        assertThat(PRODUCTION.stream().noneMatch(c -> c.getName().contains("archfixtures"))).isTrue();
    }
}
