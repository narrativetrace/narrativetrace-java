/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.agent;

/**
 * Plain service class with no NarrativeTrace awareness at all: no annotation, no import from this
 * project, no interface. The {@code -javaagent} the {@code agentTest} task attaches (see {@code
 * build.gradle.kts}) is what makes {@link #greet} narrated; nothing here asks for it.
 *
 * <p>INTENT: Contrast with {@code narrativetrace-junit4-example}'s {@code GreetingService} / {@code
 * DefaultGreetingService} pair, which is split into an interface and an implementation because JDK
 * dynamic proxying (the proxy path) requires one. Agent-mode weaving instruments the class file
 * directly at load time, so a single concrete class with no interface is enough.
 */
public class GreetingService {

  /** Returns a greeting for one display name. */
  public String greet(String name) {
    return "Hello, " + name + "!";
  }
}
