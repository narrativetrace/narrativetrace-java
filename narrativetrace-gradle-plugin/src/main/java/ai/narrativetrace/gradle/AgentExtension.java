/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import org.gradle.api.provider.ListProperty;

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
}
