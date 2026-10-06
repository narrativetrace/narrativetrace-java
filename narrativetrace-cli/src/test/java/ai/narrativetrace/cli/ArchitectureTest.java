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
package ai.narrativetrace.cli;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The {@code narrativetrace} launcher: argument parsing over the {@code narrativetrace-tooling}
 * library, which carries the doctor itself. It must never link against the runtime it diagnoses,
 * and it takes no third-party dependency of its own — {@code narrativetrace-tooling} is the only
 * thing it links against, and that library is zero-dependency in turn. Mirrors {@code
 * narrativetrace-api/src/test/java/ai/narrativetrace/api/ArchitectureTest.java}.
 */
@AnalyzeClasses(
    packages = "ai.narrativetrace.cli",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule cli_never_depends_on_the_runtime =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.cli..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("ai.narrativetrace.core..")
          .as(
              "The narrativetrace launcher reads a project's build file, source tree, and rendered"
                  + " output as text — it must never link against the runtime it diagnoses");

  @ArchTest
  static final ArchRule cli_carries_no_third_party_dependency =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.cli..")
          .should()
          .dependOnClassesThat()
          .resideOutsideOfPackages(
              "ai.narrativetrace.cli..", "ai.narrativetrace.tooling..", "java..", "javax..")
          .as(
              "narrativetrace-cli declares exactly one dependency, the zero-dependency"
                  + " narrativetrace-tooling library it launches");

  @ArchTest
  static final ArchRule no_pro_only_concerns_leak_in =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.cli..")
          .should()
          .resideInAnyPackage("..aggregate..", "..mcp..", "..audit..", "..policy..", "..uplift..")
          .as("Pro-only concerns must never appear as subpackages of the free CLI");
}
