/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The tooling library diagnoses a project from the outside: it must never link against the runtime
 * it inspects, and — like {@code narrativetrace-api} — it takes zero dependencies of its own.
 *
 * <p>It is also the library BOTH free entry points embed: the {@code narrativetrace} CLI and the
 * Gradle plugin each depend on it, and neither depends on the other. A reference from here back
 * into {@code ai.narrativetrace.cli} or {@code ai.narrativetrace.gradle} would close that triangle
 * into a cycle and make one entry point's release the other's problem.
 */
@AnalyzeClasses(
    packages = "ai.narrativetrace.tooling",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule tooling_never_depends_on_the_runtime =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.tooling..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("ai.narrativetrace.core..")
          .as(
              "The tooling library reads a project's build file, source tree, and rendered output"
                  + " as text — it must never link against the runtime it diagnoses");

  @ArchTest
  static final ArchRule tooling_never_depends_on_an_entry_point =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.tooling..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("ai.narrativetrace.cli..", "ai.narrativetrace.gradle..")
          .as(
              "The tooling library is embedded by the CLI and by the Gradle plugin — it must never"
                  + " reach back into either entry point");

  @ArchTest
  static final ArchRule tooling_carries_no_third_party_dependency =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.tooling..")
          .should()
          .dependOnClassesThat()
          .resideOutsideOfPackages("ai.narrativetrace.tooling..", "java..", "javax..")
          .as(
              "narrativetrace-tooling declares zero dependencies, the same contract"
                  + " narrativetrace-api holds");

  @ArchTest
  static final ArchRule no_pro_only_concerns_leak_in =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.tooling..")
          .should()
          .resideInAnyPackage("..aggregate..", "..mcp..", "..audit..", "..policy..", "..uplift..")
          .as("Pro-only concerns must never appear as subpackages of the free tooling library");
}
