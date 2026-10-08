package rw.ikimina.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.List;
import java.util.Set;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.Repository;

/**
 * The project's architecture rules (spec 4.1, 7.1, 20.1, 20.2, Hard Rules H1 and H2).
 *
 * <p>Each rule is a factory so that {@code ArchitectureTest} can apply it to the
 * production code and {@code ArchitectureRulesCanaryTest} can prove, against
 * deliberately bad fixtures, that it really detects what it claims to.
 */
public final class ArchitectureRules {

    /** Floating-point types: never for money, rates or balances (H1). Banned outright, so there is nothing to argue about. */
    private static final Set<String> FLOATING_POINT = Set.of("double", "float", "java.lang.Double", "java.lang.Float");

    /** Conversions that smuggle binary floating point into or out of BigDecimal. */
    private static final Set<String> FLOATING_POINT_CONVERSIONS = Set.of(
            "java.math.BigDecimal.<init>(double)",
            "java.math.BigDecimal.<init>(double, java.math.MathContext)",
            "java.math.BigDecimal.valueOf(double)",
            "java.math.BigDecimal.doubleValue()",
            "java.math.BigDecimal.floatValue()");

    /** Reading the wall clock directly instead of the injected Clock (spec 20.2). */
    private static final Set<String> WALL_CLOCK_READS = wallClockReads();

    private ArchitectureRules() {
    }

    public static ArchRule noFloatingPointFields() {
        return noFields().should(haveFloatingPointType())
                .because("money, rates and balances are BigDecimal only (Hard Rule H1)");
    }

    public static ArchRule noFloatingPointInSignatures() {
        return codeUnits().should(notUseFloatingPointInSignature())
                .because("money, rates and balances are BigDecimal only (Hard Rule H1)");
    }

    public static ArchRule noFloatingPointBigDecimalConversions() {
        return noClasses().should(callAnyOf(FLOATING_POINT_CONVERSIONS, "convert between BigDecimal and binary floating point"))
                .because("new BigDecimal(0.1) is 0.1000000000000000055511151231257827... (Hard Rule H1)");
    }

    /** @param clockOwner the single class allowed to create the system clock */
    public static ArchRule noWallClockReads(Class<?> clockOwner) {
        return noClasses().that().doNotHaveFullyQualifiedName(clockOwner.getName())
                .should(callAnyOf(WALL_CLOCK_READS, "read the wall clock"))
                .because("time comes from the injected java.time.Clock so tests can control it (spec 20.2)");
    }

    /**
     * Lombok's annotations are source-retention and vanish at compile time, so the
     * real enforcement is the maven-enforcer ban that keeps Lombok off the classpath.
     * This rule catches the parts that do reach bytecode (e.g. {@code lombok.Generated}).
     */
    public static ArchRule noLombok() {
        return noClasses().should().dependOnClassesThat().resideInAPackage("lombok..")
                .because("Lombok is banned (Hard Rule H2)");
    }

    public static ArchRule noFieldInjection() {
        return noFields().should().beAnnotatedWith(Autowired.class)
                .orShould().beAnnotatedWith(Value.class)
                .orShould().beAnnotatedWith("jakarta.inject.Inject")
                .because("dependencies are constructor-injected so they are explicit and final");
    }

    /**
     * A module's {@code internal} package (entities, repositories, implementation) is
     * reachable only from inside that module; other modules use its public API
     * (spec 4.1). This is also what keeps every class but the ledger's own away from
     * the ledger tables (spec 7.1).
     *
     * @param root the package whose direct sub-packages are modules, e.g. {@code rw.ikimina}
     */
    public static ArchRule moduleInternalsAreEncapsulated(String root) {
        return classes().that().resideInAPackage(root + ".*.internal..")
                .should(onlyBeAccessedFromTheirOwnModule(root))
                .allowEmptyShould(true)
                .because("modules talk through public service interfaces, never each other's internals (spec 4.1)");
    }

    public static ArchRule sharedDependsOnNoModule(String root) {
        return noClasses().that().resideInAPackage(root + ".shared..")
                .should().dependOnClassesThat(resideInAPackage(root + "..").and(not(resideInAPackage(root + ".shared.."))))
                .because("shared building blocks sit beneath every module");
    }

    public static ArchRule modulesAreFreeOfCycles(String root) {
        return slices().matching(root + ".(*)..").should().beFreeOfCycles()
                .allowEmptyShould(true);
    }

    public static ArchRule repositoriesAreModuleInternal() {
        return classes().that().areAssignableTo(Repository.class)
                .should().resideInAPackage("..internal..")
                .allowEmptyShould(true)
                .because("a repository is an implementation detail of its module (spec 4.1)");
    }

    public static List<ArchRule> all(String root, Class<?> clockOwner) {
        return List.of(
                noFloatingPointFields(),
                noFloatingPointInSignatures(),
                noFloatingPointBigDecimalConversions(),
                noWallClockReads(clockOwner),
                noLombok(),
                noFieldInjection(),
                moduleInternalsAreEncapsulated(root),
                sharedDependsOnNoModule(root),
                modulesAreFreeOfCycles(root),
                repositoriesAreModuleInternal());
    }

    private static ArchCondition<com.tngtech.archunit.core.domain.JavaField> haveFloatingPointType() {
        return new ArchCondition<>("have a floating-point type") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaField field, ConditionEvents events) {
                boolean floating = FLOATING_POINT.contains(field.getRawType().getName());
                events.add(new SimpleConditionEvent(field, floating, field.getFullName() + " is " + field.getRawType().getName()));
            }
        };
    }

    private static ArchCondition<JavaCodeUnit> notUseFloatingPointInSignature() {
        return new ArchCondition<>("not take or return floating-point types") {
            @Override
            public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                if (FLOATING_POINT.contains(codeUnit.getRawReturnType().getName())) {
                    events.add(SimpleConditionEvent.violated(codeUnit, codeUnit.getFullName() + " returns a floating-point type"));
                }
                for (JavaClass parameter : codeUnit.getRawParameterTypes()) {
                    if (FLOATING_POINT.contains(parameter.getName())) {
                        events.add(SimpleConditionEvent.violated(codeUnit, codeUnit.getFullName() + " takes a floating-point parameter"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> callAnyOf(Set<String> targets, String description) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
                    String target = access.getTarget().getFullName();
                    if (targets.contains(target)) {
                        events.add(SimpleConditionEvent.satisfied(access, access.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> onlyBeAccessedFromTheirOwnModule(String root) {
        return new ArchCondition<>("only be accessed from their own module") {
            @Override
            public void check(JavaClass target, ConditionEvents events) {
                String module = moduleOf(root, target);
                for (Dependency dependency : target.getDirectDependenciesToSelf()) {
                    if (!module.equals(moduleOf(root, dependency.getOriginClass()))) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /** The first package segment below {@code root}, or "" for classes outside any module. */
    static String moduleOf(String root, JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(root + ".")) {
            return "";
        }
        String rest = packageName.substring(root.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static Set<String> wallClockReads() {
        Set<String> reads = new java.util.HashSet<>(Set.of(
                "java.time.Instant.now()",
                "java.time.Clock.systemUTC()",
                "java.time.Clock.systemDefaultZone()",
                "java.time.Clock.system(java.time.ZoneId)",
                "java.lang.System.currentTimeMillis()",
                "java.util.Date.<init>()",
                "java.util.Calendar.getInstance()"));
        for (String type : List.of("LocalDate", "LocalDateTime", "LocalTime", "ZonedDateTime", "OffsetDateTime",
                "OffsetTime", "Year", "YearMonth", "MonthDay")) {
            reads.add("java.time." + type + ".now()");
            reads.add("java.time." + type + ".now(java.time.ZoneId)");
        }
        return Set.copyOf(reads);
    }
}
