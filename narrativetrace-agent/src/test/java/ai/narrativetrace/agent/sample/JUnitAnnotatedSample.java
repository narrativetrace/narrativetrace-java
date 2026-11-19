/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

import org.junit.jupiter.api.Test;

/**
 * A class whose class file declares a JUnit 5 test method, for the content rule that recognises
 * test code when the code source says nothing. Abstract so the build's own test discovery never
 * runs it.
 */
public abstract class JUnitAnnotatedSample {

  @Test
  void checksSomething() {
    helper();
  }

  abstract int seed();

  public int helper() {
    return seed();
  }
}
