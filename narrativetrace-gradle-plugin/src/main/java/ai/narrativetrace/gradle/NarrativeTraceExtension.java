/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * Top-level Gradle DSL extension for the NarrativeTrace plugin.
 *
 * <p>INTENT: This is the configuration surface build scripts use to choose mode, scope, output,
 * clarity gates, and optional integration modules.
 */
public abstract class NarrativeTraceExtension {

  public abstract Property<Boolean> getEnabled();

  public abstract Property<String> getMode();

  public abstract Property<String> getTestFramework();

  public abstract Property<String> getScope();

  public abstract Property<String> getFormat();

  public abstract Property<String> getTracingLevel();

  /**
   * Overrides the version used for every managed NarrativeTrace dependency. Unset by default, in
   * which case the plugin uses the version embedded in its own JAR. Set it to consume a different
   * release or a local {@code -SNAPSHOT} (e.g. {@code libraryVersion.set("0.2.0-SNAPSHOT")})
   * without hand-pinning each dependency — the paved path for snapshot dogfooding, where the
   * plugin's embedded version otherwise lags the libraries under development.
   */
  public abstract Property<String> getLibraryVersion();

  /**
   * Whether the test suite harvests the domain glossary (ADR-012). Off by default: harvesting
   * rewrites {@code glossary.json} / {@code glossary.md} at the repository root, outside the build
   * directory, which no build may do unasked.
   */
  public abstract Property<Boolean> getGlossary();

  /**
   * Approval mode for structural narratives (TODO item 14): when {@code true}, a test whose traced
   * structure differs from its committed {@code *.approved.nt} baseline fails with a readable diff,
   * and a {@code *.received.nt} file is left beside the baseline for review. Off by default; accept
   * changes with the {@code approveNarratives} task.
   */
  public abstract Property<Boolean> getApproval();

  /**
   * Directory of committed narrative baselines, {@code <dir>/<TestClass>/<scenario>.approved.nt}.
   * Default: {@code src/test/narratives}.
   */
  public abstract DirectoryProperty getApprovedDir();

  public abstract DirectoryProperty getOutputDir();

  private final ModulesExtension modules;
  private final AgentExtension agent;
  private final ClarityExtension clarity;

  /** Sets up defaults and creates nested extension objects. */
  @Inject
  @SuppressWarnings("PMD.ConstructorCallsOverridableMethod") // Gradle convention pattern
  public NarrativeTraceExtension(ObjectFactory objects) {
    this.modules = objects.newInstance(ModulesExtension.class);
    this.agent = objects.newInstance(AgentExtension.class);
    this.clarity = objects.newInstance(ClarityExtension.class);

    getEnabled().convention(true);
    getMode().convention("proxy");
    getTestFramework().convention("junit5");
    getScope().convention("test");
    getGlossary().convention(false);
    getApproval().convention(false);

    applyModuleDefaults();
    agent.getPackages().convention(java.util.Collections.emptyList());
    applyClarityDefaults();
  }

  /** All integration modules are off by default; each is an explicit opt-in. */
  private void applyModuleDefaults() {
    modules.getSlf4j().convention(false);
    modules.getMicrometer().convention(false);
    modules.getServlet().convention(false);
    modules.getSpringWeb().convention(false);
    modules.getOpentelemetry().convention(false);
    modules.getMicronaut().convention(false);
    modules.getMicronautHttp().convention(false);
  }

  /** Every clarity gate is open by default; thresholds are explicit opt-ins. */
  private void applyClarityDefaults() {
    clarity.getMinScore().convention(0.0);
    clarity.getMaxHighIssues().convention(Integer.MAX_VALUE);
    clarity.getMaxSuiteIssues().convention(Integer.MAX_VALUE);
    clarity.getWarnOnly().convention(false);
  }

  /** Returns the nested module-toggle block. */
  public ModulesExtension getModules() {
    return modules;
  }

  /** Configures optional integration-module toggles. */
  public void modules(Action<? super ModulesExtension> action) {
    action.execute(modules);
  }

  /** Returns the nested Java-agent block. */
  public AgentExtension getAgent() {
    return agent;
  }

  /** Configures Java-agent-specific settings. */
  public void agent(Action<? super AgentExtension> action) {
    action.execute(agent);
  }

  /** Returns the nested clarity-check block. */
  public ClarityExtension getClarity() {
    return clarity;
  }

  /** Configures clarity score thresholds and failure behavior. */
  public void clarity(Action<? super ClarityExtension> action) {
    action.execute(clarity);
  }
}
