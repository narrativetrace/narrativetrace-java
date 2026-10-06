/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * A catalogue entry names commands and check ids as data — it must never link against the runtime
 * or the entry points it describes, and, like {@code narrativetrace-api}/{@code
 * narrativetrace-tooling}, it takes zero dependencies of its own.
 */
@AnalyzeClasses(
    packages = "ai.narrativetrace.skills",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule skills_never_depend_on_the_runtime_or_an_entry_point =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.skills..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "ai.narrativetrace.core..", "ai.narrativetrace.cli..", "ai.narrativetrace.tooling..")
          .as(
              "A catalogue entry names commands and check ids as data — it must never link"
                  + " against the runtime or the entry points it describes");

  @ArchTest
  static final ArchRule skills_carries_no_third_party_dependency =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.skills..")
          .should()
          .dependOnClassesThat()
          .resideOutsideOfPackages("ai.narrativetrace.skills..", "java..", "javax..")
          .as(
              "narrativetrace-skills-catalogue declares zero dependencies, the same contract"
                  + " narrativetrace-api and narrativetrace-tooling hold");

  @ArchTest
  static final ArchRule no_pro_only_concerns_leak_in =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.skills..")
          .should()
          .resideInAnyPackage("..aggregate..", "..mcp..", "..audit..", "..policy..", "..uplift..")
          .as("Pro-only concerns must never appear as subpackages of the free skills catalogue");
}
