/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "ai.narrativetrace.proxy",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule proxy_does_not_depend_on_core_internals =
      noClasses()
          .that()
          .resideInAPackage("..proxy..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..pipeline..", "..output..", "..tree..", "..export..", "..config..")
          .as(
              "Proxy module should only use core's public API"
                  + " (annotation, context, event, render, template),"
                  + " not internal packages (pipeline, output, tree, export, config)");

  @ArchTest
  static final ArchRule proxy_does_not_depend_on_sibling_modules =
      noClasses()
          .that()
          .resideInAPackage("..proxy..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..spring..",
              "..servlet..",
              "..agent..",
              "..slf4j..",
              "..micrometer..",
              "..opentelemetry..",
              "..junit4..",
              "..junit5..",
              "..diagrams..",
              "..clarity..",
              "..gradle..")
          .as("Proxy module should only depend on core, not on sibling integration modules");

  @ArchTest
  static final ArchRule proxy_classes_should_be_final =
      classes()
          .that()
          .resideInAPackage("..proxy..")
          .and()
          .areNotInterfaces()
          .should()
          .haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
          .as("Proxy classes are utilities and factories, not designed for subclassing");

  @ArchTest
  static final ArchRule proxy_does_not_depend_on_frameworks =
      noClasses()
          .that()
          .resideInAPackage("..proxy..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta..",
              "javax.servlet..",
              "io.micrometer..",
              "io.opentelemetry..",
              "org.slf4j..")
          .as("Proxy module should be framework-agnostic with zero external dependencies");
}
