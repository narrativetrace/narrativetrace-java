/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.maven;

/**
 * Minimal service used by the Maven example test.
 *
 * <p>This interface is intentionally tiny so the example can focus on {@code pom.xml} wiring and
 * trace output instead of on domain complexity — it carries no NarrativeTrace reference of any
 * kind; tracing is added at the test boundary via {@code NarrativeTraceProxy.trace(...)}.
 */
public interface GreetingService {

  /** Returns a greeting for one display name. */
  String greet(String name);
}
