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
 * everything on the test JVM classpath.
 */
public abstract class AgentExtension {

  public abstract ListProperty<String> getPackages();
}
