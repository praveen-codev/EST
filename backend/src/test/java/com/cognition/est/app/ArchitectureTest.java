package com.cognition.est.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Layering rules enforced from day one; the AI engine must stay framework-free so it can be
 * golden-tested.
 */
@AnalyzeClasses(packages = "com.cognition.est")
class ArchitectureTest {

    @ArchTest
    static final ArchRule aiEngineIsFrameworkFree =
            noClasses()
                    .that()
                    .resideInAPackage("..est.ai..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "..est.api..",
                            "..est.calendar..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule devToolsNeverLeakIntoDomain =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("..est.app.dev..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..est.app.dev..")
                    .allowEmptyShould(true);
}
