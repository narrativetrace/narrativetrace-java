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
package ai.narrativetrace.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The rules that make this jar a contract rather than a second copy of the runtime.
 *
 * <p><b>@llmNote</b> The direction rule is the load-bearing one: the API may never reach into the
 * runtime. A single such reference would put `ai.narrativetrace.core` on the classpath of everyone
 * who compiles against the contract, which is exactly what publishing the two separately exists to
 * prevent — and, after Wave R, would also cross a licence boundary.
 */
@AnalyzeClasses(
    packages = "ai.narrativetrace.api",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule api_never_depends_on_the_runtime =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("ai.narrativetrace.core..")
          .as("The API contract must never reach into the runtime that implements it");

  @ArchTest
  static final ArchRule api_carries_no_third_party_dependency =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.api..")
          .should()
          .dependOnClassesThat()
          .resideOutsideOfPackages("ai.narrativetrace.api..", "java..", "javax..")
          .as(
              "The API jar declares zero dependencies; every one it took would be one a consumer"
                  + " of the contract could not refuse");

  @ArchTest
  static final ArchRule event_model_is_a_pure_data_layer =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.api.event..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "ai.narrativetrace.api.export..",
              "ai.narrativetrace.api.render..",
              "ai.narrativetrace.api.spi..",
              "ai.narrativetrace.api.tree..")
          .as(
              "Event model should be a pure data layer that nothing else in the contract pulls it"
                  + " into");

  @ArchTest
  static final ArchRule annotation_package_is_self_contained =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.api.annotation..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "ai.narrativetrace.api.event..",
              "ai.narrativetrace.api.export..",
              "ai.narrativetrace.api.render..",
              "ai.narrativetrace.api.spi..",
              "ai.narrativetrace.api.tree..",
              "ai.narrativetrace.api.config..")
          .as("Annotations are written in user source and must drag nothing else in with them");

  @ArchTest
  static final ArchRule config_package_is_a_leaf =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.api.config..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "ai.narrativetrace.api.event..",
              "ai.narrativetrace.api.export..",
              "ai.narrativetrace.api.render..",
              "ai.narrativetrace.api.spi..",
              "ai.narrativetrace.api.tree..")
          .as("The capture-level vocabulary is a leaf of the contract");

  @ArchTest
  static final ArchRule no_package_cycles =
      slices()
          .matching("ai.narrativetrace.api.(*)..")
          .should()
          .beFreeOfCycles()
          .as("API packages should have no circular dependencies");

  @ArchTest
  static final ArchRule no_runtime_concerns_leak_into_the_contract =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.api..")
          .should()
          .resideInAnyPackage(
              "..context..",
              "..pipeline..",
              "..output..",
              "..template..",
              "..aggregate..",
              "..mcp..",
              "..audit..",
              "..policy..",
              "..uplift..")
          .as("Runtime and Pro-tier concerns must never appear as subpackages of the API");
}
