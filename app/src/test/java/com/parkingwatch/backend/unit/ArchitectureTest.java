package com.parkingwatch.backend.unit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

/** Reglas de arquitectura del backend verificadas en cada build (RNF-4.1, RNF-4.2). */
@AnalyzeClasses(
    packages = "com.parkingwatch.backend",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule controllersDoNotUseRepositories =
      noClasses()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Repository");

  @ArchTest
  static final ArchRule rulesEngineIsIndependentOfTheWebLayer =
      noClasses()
          .that()
          .resideInAnyPackage("..rules..", "..report..", "..zone..", "..model..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.web.servlet..", "jakarta.servlet..");

  @ArchTest
  static final ArchRule theDomainPublishesRealtimeEventsWithoutKnowingTheTransport =
      noClasses()
          .that()
          .resideInAnyPackage("..rules..", "..report..", "..zone..", "..model..", "..ingestion..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.messaging..", "org.springframework.web.socket..");

  @ArchTest
  static final ArchRule onlyTheStorageAdapterTouchesTheDisk =
      noClasses()
          .that()
          .resideOutsideOfPackages("..storage..", "..config..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.nio.file.Files");
}
