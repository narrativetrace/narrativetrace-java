/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "ai.narrativetrace.core",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  /**
   * The event model and the annotations now live in {@code narrativetrace-api}, and their purity
   * rules moved with them. What is left to enforce here is the other direction: no annotation type
   * may be declared in the runtime, or a user would have to name the runtime jar to write it.
   */
  @ArchTest
  static final ArchRule core_declares_no_annotation_types =
      noClasses()
          .that()
          .areAnnotations()
          .should()
          .resideInAPackage("ai.narrativetrace.core..")
          .as("Annotations are the authoring surface and belong to the API jar, never the runtime")
          // Zero matches is the passing state: core is supposed to declare no annotation at all.
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule config_package_does_not_depend_on_runtime_packages =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.core.config..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "ai.narrativetrace.core.context..",
              "ai.narrativetrace.core.pipeline..",
              "ai.narrativetrace.core.render..",
              "ai.narrativetrace.core.output..",
              "ai.narrativetrace.core.template..",
              "ai.narrativetrace.core.tree..",
              "ai.narrativetrace.core.export..")
          .as("Config package should be a leaf with no dependencies on other core packages");

  @ArchTest
  static final ArchRule renderers_do_not_depend_on_context_or_pipeline =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.core.render..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "ai.narrativetrace.core.context..",
              "ai.narrativetrace.core.pipeline..",
              "ai.narrativetrace.core.output..",
              "ai.narrativetrace.core.export..")
          .as("Renderers should only consume the tree/event model, not reach into runtime state");

  @ArchTest
  static final ArchRule tree_package_does_not_depend_on_context_or_pipeline =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.core.tree..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "ai.narrativetrace.core.context..",
              "ai.narrativetrace.core.pipeline..",
              "ai.narrativetrace.core.render..",
              "ai.narrativetrace.core.output..",
              "ai.narrativetrace.core.export..")
          .as("Tree builder should only depend on config and event model");

  /**
   * Template resolution is a leaf utility with four deliberate exceptions, all about applying a
   * security invariant once rather than duplicating it: {@code RedactionPolicy}, {@code
   * ValueRenderer}, {@code ScalarTrust} and {@code ControlEscape}.
   *
   * <p>A template can name a redacted member two ways, and each needs one of the first two. Naming
   * a <em>path</em> ({@code @Narrated("charging {card.cvv}")}) needs the rule itself, so the
   * resolver asks {@code RedactionPolicy} — an immutable value type over a string set that depends
   * on nothing. Naming the <em>object</em> ({@code @Narrated("charging {card}")}) needs the
   * renderer, because the object's own {@code toString()} prints its {@code @NotTraced} components
   * in full and only {@code ValueRenderer} knows how to render it without them.
   *
   * <p>The other two close a different hole the same way: a scalar argument (a {@code Number}
   * subclass, an {@code Enum} constant) substituted directly into a template's flat text must not
   * trust that value's own {@code toString()} any more than {@code ValueRenderer} does, so the
   * resolver asks {@code ScalarTrust} which types are JDK-fixed and sanitizes the rest through
   * {@code ControlEscape} — the exact decision {@code ValueRenderer} makes on its own scalar path.
   *
   * <p>Redaction and scalar sanitizing are both cross-cutting security invariants rather than
   * rendering details, so the resolver must apply them. The alternative was a second implementation
   * of each rule inside this package — the defect class TODO 46 exists to close, and the same class
   * a Number subclass's unsanitized {@code toString()} belongs to. Every exception here is a named
   * leaf class, and none of the four depends on {@code template}, so nothing here can become a
   * cycle.
   */
  @ArchTest
  static final ArchRule template_package_is_self_contained =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.core.template..")
          .should()
          .dependOnClassesThat(
              resideInAnyPackage(
                      "ai.narrativetrace.core.context..",
                      "ai.narrativetrace.core.pipeline..",
                      "ai.narrativetrace.core.render..",
                      "ai.narrativetrace.core.output..",
                      "ai.narrativetrace.core.export..",
                      "ai.narrativetrace.core.tree..",
                      "ai.narrativetrace.core.config..")
                  .and(not(name("ai.narrativetrace.core.render.RedactionPolicy")))
                  .and(not(name("ai.narrativetrace.core.render.ValueRenderer")))
                  .and(not(name("ai.narrativetrace.core.render.ScalarTrust")))
                  .and(not(name("ai.narrativetrace.core.render.ControlEscape"))))
          .as("Template parser should be a self-contained utility with no internal dependencies");

  @ArchTest
  static final ArchRule export_package_does_not_depend_on_runtime_packages =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.core.export..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "ai.narrativetrace.core.context..",
              "ai.narrativetrace.core.pipeline..",
              "ai.narrativetrace.core.output..",
              "ai.narrativetrace.core.template..")
          .as("Export package should not depend on context, pipeline, output, or template");

  @ArchTest
  static final ArchRule renderer_implementations_should_be_named_renderer =
      classes()
          .that()
          .implement(ai.narrativetrace.api.render.NarrativeRenderer.class)
          .should()
          .haveSimpleNameEndingWith("Renderer")
          .as("NarrativeRenderer implementations should have names ending with 'Renderer'");

  @ArchTest
  static final ArchRule no_package_cycles =
      slices()
          .matching("ai.narrativetrace.core.(*)..")
          .should()
          .beFreeOfCycles()
          .as("Core packages should have no circular dependencies");

  @ArchTest
  static final ArchRule pipeline_does_not_depend_on_render_or_output =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.core.pipeline..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "ai.narrativetrace.core.render..",
              "ai.narrativetrace.core.output..",
              "ai.narrativetrace.core.export..",
              "ai.narrativetrace.core.template..")
          .as("Pipeline layer should not depend on render, output, export, or template");

  @ArchTest
  static final ArchRule no_aggregation_types_remain_in_core =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.core..")
          .should()
          .haveSimpleNameStartingWith("Aggregate")
          .orShould()
          .haveSimpleNameEndingWith("DurationStats")
          .orShould()
          .haveSimpleName("OutcomeKey")
          .as(
              "Event-stream aggregation types (Aggregate*, *DurationStats, OutcomeKey) belong to "
                  + "the Pro tier, not the free core (Phase 31a)");

  @ArchTest
  static final ArchRule no_pro_only_subpackages_in_core =
      noClasses()
          .that()
          .resideInAPackage("ai.narrativetrace.core..")
          .should()
          .resideInAnyPackage("..aggregate..", "..mcp..", "..audit..", "..policy..", "..uplift..")
          .as(
              "Pro-only concerns (aggregate, mcp, audit, policy, uplift) must not appear as "
                  + "subpackages of ai.narrativetrace.core (Phase 31a/31c, Wave R)");
}
