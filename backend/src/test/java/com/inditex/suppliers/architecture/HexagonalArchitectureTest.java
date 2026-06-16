package com.inditex.suppliers.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Compile-time invariant: prevents accidental dependency-rule violations as the
 * codebase grows. Without this, a junior PR could quietly import
 * {@code jakarta.persistence.Entity} into a domain aggregate and break the hexagon.
 */
class HexagonalArchitectureTest {

    private static final String BASE = "com.inditex.suppliers";
    private static final String DOMAIN       = BASE + ".domain..";
    private static final String APPLICATION  = BASE + ".application..";
    private static final String INFRA        = BASE + ".infrastructure..";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages(BASE);
    }

    @Test
    void layered_dependencies_respect_the_hexagon() {
        Architectures.layeredArchitecture().consideringAllDependencies()
                .layer("Domain").definedBy(DOMAIN)
                .layer("Application").definedBy(APPLICATION)
                .layer("Infrastructure").definedBy(INFRA)
                // Outbound dependencies:
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
                .check(classes);
    }

    @Test
    void domain_must_not_depend_on_spring() {
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .because("the domain is framework-agnostic")
                .check(classes);
    }

    @Test
    void domain_must_not_depend_on_jpa() {
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.springframework.data..")
                .because("the domain is persistence-agnostic")
                .check(classes);
    }

    @Test
    void domain_must_not_depend_on_web_or_json() {
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..",
                        "org.springframework.web..",
                        "com.fasterxml.jackson..")
                .check(classes);
    }

    @Test
    void application_must_not_depend_on_web_or_persistence_internals() {
        noClasses().that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "jakarta.servlet..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.springframework.data..")
                .because("application layer talks to infrastructure only through output ports")
                .check(classes);
    }

    @Test
    void application_must_not_depend_on_infrastructure() {
        noClasses().that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat().resideInAPackage(INFRA)
                .check(classes);
    }

    @Test
    void domain_aggregates_must_not_expose_setters() {
        // Aggregates evolve through intention-revealing methods (accept, refuse,
        // ban, ...). A setter would break the encapsulation that protects invariants.
        noClasses().that().resideInAPackage(BASE + ".domain.model..")
                .should().haveSimpleNameStartingWith("Set")
                .orShould().haveSimpleNameContaining("Setter")
                .check(classes);

        methods().that().areDeclaredInClassesThat().resideInAPackage(BASE + ".domain.model..")
                .and().arePublic()
                .should().haveNameNotMatching("set[A-Z].*")
                .because("domain aggregates must evolve via intention-revealing methods, not setters")
                .check(classes);
    }

    @Test
    void domain_must_not_use_jpa_or_validation_annotations() {
        // The aggregate stays POJO. Bean Validation + JPA live exclusively on
        // the infrastructure boundary (REST DTOs and JPA entities respectively).
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.validation..",
                        "jakarta.persistence.."
                )
                .check(classes);
    }

    @Test
    void domain_exceptions_extend_DomainException() {
        // Forces a uniform translation in GlobalExceptionHandler: a single
        // hierarchy means one switch on the exception type, not N catches.
        classes().that().resideInAPackage(BASE + ".domain.exception..")
                .and().haveSimpleNameEndingWith("Exception")
                .and().areNotInterfaces()
                .and().doNotHaveSimpleName("DomainException")
                .should().beAssignableTo(
                        com.inditex.suppliers.domain.exception.DomainException.class)
                .check(classes);
    }

    @Test
    void rest_dtos_must_not_leak_into_domain_or_application() {
        // The HTTP wire shape is a presentation concern. Letting it leak into
        // domain would couple the model to JSON quirks (e.g. naming, jackson
        // annotations).
        noClasses().that().resideInAnyPackage(DOMAIN, APPLICATION)
                .should().dependOnClassesThat().resideInAPackage(
                        BASE + ".infrastructure.rest.dto..")
                .check(classes);
    }

    @Test
    void services_must_be_annotated_with_Service_and_live_in_application() {
        // Spring stereotypes pinned to the application layer keep wiring obvious
        // and avoid accidental @Service beans in infrastructure (which would
        // pull config concerns into the use cases).
        classes().that().haveSimpleNameEndingWith("Service")
                .and().resideOutsideOfPackages("..infrastructure..")
                .and().areNotInterfaces()
                .should().resideInAPackage(BASE + ".application.service..")
                .check(classes);
    }
}
