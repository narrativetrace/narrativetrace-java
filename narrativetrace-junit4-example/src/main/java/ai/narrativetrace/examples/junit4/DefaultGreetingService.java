/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.junit4;

/**
 * Trivial implementation used by the JUnit 4 example tests.
 *
 * <p>INTENT: Keep the traced behavior obvious so developers can concentrate on how the JUnit 4
 * rules capture and render the interaction.
 */
public class DefaultGreetingService implements GreetingService {

  @Override
  public String greet(String name) {
    return "Hello, " + name + "!";
  }
}
