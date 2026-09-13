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
package ai.narrativetrace.skills;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * A catalogue entry names commands and check ids as data — it must never link against the runtime
 * or the CLI it describes, and, like {@code narrativetrace-api}/{@code narrativetrace-cli}, it
 * takes zero dependencies of its own.
 */
@AnalyzeClasses(
    packages = "ai.narrativetrace.skills",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule skills_never_depend_on_the_runtime_or_the_cli =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.skills..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("ai.narrativetrace.core..", "ai.narrativetrace.cli..")
          .as(
              "A catalogue entry names commands and check ids as data — it must never link"
                  + " against the runtime or the CLI it describes");

  @ArchTest
  static final ArchRule skills_carries_no_third_party_dependency =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.skills..")
          .should()
          .dependOnClassesThat()
          .resideOutsideOfPackages("ai.narrativetrace.skills..", "java..", "javax..")
          .as(
              "narrativetrace-skills declares zero dependencies, the same contract"
                  + " narrativetrace-api and narrativetrace-cli hold");

  @ArchTest
  static final ArchRule no_pro_only_concerns_leak_in =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.skills..")
          .should()
          .resideInAnyPackage("..aggregate..", "..mcp..", "..audit..", "..policy..", "..uplift..")
          .as("Pro-only concerns must never appear as subpackages of the free skills catalogue");
}
