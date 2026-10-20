/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

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
    packages = "ai.narrativetrace.junit5",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  /**
   * §176 ownership rule, the JUnit 5 half: within this module the only callers of {@code
   * PipelineBootstrap.createDefault}/{@code build} are {@link NarrativeTraceExtension} itself (the
   * deprecated-style {@code newContext} test utility) and its nested {@code SuiteContextResource} —
   * the one lifecycle owner, built once per test JVM and closed with {@code
   * GlobalTraceAccumulator}. See {@code ai.narrativetrace.core.ArchitectureTest} for core's half of
   * the same rule.
   */
  @ArchTest
  static final ArchRule only_the_suite_context_resource_builds_the_default_pipeline =
      noClasses()
          .that(not(name("ai.narrativetrace.junit5.NarrativeTraceExtension")))
          .and(not(name("ai.narrativetrace.junit5.NarrativeTraceExtension$SuiteContextResource")))
          .should(callsPipelineBootstrapDefaultConstruction())
          .as(
              "Only NarrativeTraceExtension and its SuiteContextResource may build the default "
                  + "pipeline; every pipeline must have one owner that closes it");

  private static ArchCondition<JavaClass> callsPipelineBootstrapDefaultConstruction() {
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
