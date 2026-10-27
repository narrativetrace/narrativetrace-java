/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Gradle DSL block for Java-agent mode settings.
 *
 * <p>INTENT: Restrict agent instrumentation to specific package prefixes instead of instrumenting
 * everything on the test JVM classpath. {@code exclude} is list 2 of the agent's two exclusion
 * lists (see the installation guide's "Exclude packages" section) — configurable, package-named,
 * additive to {@code packages}; leaving it unset means the agent's shipped default applies, the
 * same as omitting {@code exclude=} from the agent argument directly.
 */
public abstract class AgentExtension {

  public abstract ListProperty<String> getPackages();

  public abstract ListProperty<String> getExclude();

  /**
   * The agent's opt-in deep mode: {@code "internal"} also weaves the private methods of the classes
   * under test, so a test that asks for it ({@code @DeepTrace}, or {@code narrativetrace.depth})
   * records them. Unset means the agent's default, public methods only.
   */
  public abstract Property<String> getDepth();

  /** The agent's cap on deep spans per scope; unset means the agent's default. */
  public abstract Property<Integer> getMaxInternalSpans();
}
