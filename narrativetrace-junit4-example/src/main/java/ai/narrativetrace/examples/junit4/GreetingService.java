/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.junit4;

/**
 * Minimal service used by the JUnit 4 example tests.
 *
 * <p>This interface is intentionally tiny so the example can focus on rule wiring and trace output
 * instead of on domain complexity.
 */
public interface GreetingService {

  /** Returns a greeting for one display name. */
  String greet(String name);
}
