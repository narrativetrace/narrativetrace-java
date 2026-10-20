/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

@AnalyzeClasses(
    packages = "ai.narrativetrace.agent",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule agent_does_not_depend_on_core_output_export_or_tree =
      noClasses()
          .that()
          .resideInAPackage("..agent..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..output..", "..export..", "..tree..")
          .as(
              "Agent captures events — it should not depend on"
                  + " output, export, or tree packages");

  @ArchTest
  static final ArchRule agent_does_not_depend_on_sibling_modules =
      noClasses()
          .that()
          .resideInAPackage("..agent..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..proxy..",
              "..spring..",
              "..servlet..",
              "..micrometer..",
              "..opentelemetry..",
              "..junit4..",
              "..junit5..",
              "..diagrams..",
              "..clarity..",
              "..gradle..")
          .as("Agent module should only depend on core (and optionally slf4j)");

  @ArchTest
  static final ArchRule asm_confined_to_bytecode_classes =
      noClasses()
          .that()
          .haveSimpleNameStartingWith("Agent")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.objectweb.asm..")
          .as(
              "ASM should only be used by bytecode transformation classes,"
                  + " not by AgentRuntime or AgentConfig");

  @ArchTest
  static final ArchRule agent_does_not_depend_on_frameworks =
      noClasses()
          .that()
          .resideInAPackage("..agent..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta..",
              "javax.servlet..",
              "io.micrometer..",
              "io.opentelemetry..")
          .as("Agent module should be framework-agnostic");

  @ArchTest
  static final ArchRule agent_runtime_and_config_should_be_final =
      classes()
          .that()
          .haveSimpleNameStartingWith("Agent")
          .should()
          .haveModifier(JavaModifier.FINAL)
          .as("AgentRuntime and AgentConfig are called from injected bytecode, not subclassed");

  /**
   * §176 ownership rule, the agent half: within this module the only caller of {@code
   * PipelineBootstrap.createDefault}/{@code build} is {@link AgentRuntime#initialize}, the premain
   * lifecycle owner. See {@code ai.narrativetrace.core.ArchitectureTest} for core's half of the
   * same rule.
   */
  @ArchTest
  static final ArchRule only_agentRuntime_builds_the_default_pipeline =
      noClasses()
          .that(not(name("ai.narrativetrace.agent.AgentRuntime")))
          .should(callsPipelineBootstrapDefaultConstruction())
          .as(
              "Only AgentRuntime may build the default pipeline; every pipeline must have one "
                  + "owner that closes it");

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
