/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

@AnalyzeClasses(
    packages = "ai.narrativetrace.testng",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  /**
   * The ownership rule, the TestNG third: within this module only {@link SharedPipeline} — built
   * once per test JVM, closed by its shutdown hook — calls {@code PipelineBootstrap.createDefault}
   * or {@code build}. Every pipeline has one owner that closes it, and the listener inherits the
   * runtime's default dual-path topology rather than choosing a strategy of its own.
   */
  @ArchTest
  static final ArchRule only_the_shared_pipeline_builds_the_default_pipeline =
      noClasses()
          .that(not(name("ai.narrativetrace.testng.SharedPipeline")))
          .should(callPipelineBootstrapDefaultConstruction())
          .as(
              "Only SharedPipeline may build the default pipeline; every pipeline must have one "
                  + "owner that closes it");

  private static ArchCondition<JavaClass> callPipelineBootstrapDefaultConstruction() {
    return new ArchCondition<>("call PipelineBootstrap.createDefault or .build") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (var member : item.getCodeUnits()) {
          for (JavaCall<?> call : member.getCallsFromSelf()) {
            var target = call.getTarget();
            if (target
                    .getOwner()
                    .isEquivalentTo(ai.narrativetrace.core.pipeline.PipelineBootstrap.class)
                && (target.getName().equals("createDefault") || target.getName().equals("build"))) {
              events.add(
                  SimpleConditionEvent.violated(
                      call, call.getOrigin().getFullName() + " calls " + target.getFullName()));
            }
          }
        }
      }
    };
  }
}
