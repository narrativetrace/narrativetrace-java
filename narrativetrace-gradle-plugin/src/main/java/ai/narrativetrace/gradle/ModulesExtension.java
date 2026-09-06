/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import org.gradle.api.provider.Property;

/**
 * Gradle DSL block for toggling optional NarrativeTrace integration modules.
 *
 * <p>INTENT: Use this nested block to add bridge modules only when the build actually needs them.
 */
public abstract class ModulesExtension {

  public abstract Property<Boolean> getSlf4j();

  public abstract Property<Boolean> getMicrometer();

  public abstract Property<Boolean> getServlet();

  public abstract Property<Boolean> getSpringWeb();

  public abstract Property<Boolean> getOpentelemetry();

  public abstract Property<Boolean> getMicronaut();

  public abstract Property<Boolean> getMicronautHttp();
}
