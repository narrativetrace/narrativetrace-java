/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.maven;

/**
 * Trivial implementation used by the Maven example test.
 *
 * <p>INTENT: Keep the traced behavior obvious so the reader can concentrate on how {@code pom.xml}
 * and the JUnit 5 extension capture and render the interaction, not on what this method does.
 */
public class DefaultGreetingService implements GreetingService {

  @Override
  public String greet(String name) {
    return "Hello, " + name + "!";
  }
}
