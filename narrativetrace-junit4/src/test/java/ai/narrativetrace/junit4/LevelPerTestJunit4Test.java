/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * §176 build step 1, JUnit 4 half: the class rule's context is shared for the whole class, but the
 * capture level is re-read and reapplied by {@code starting()} on every test — so two tests at
 * different levels in one class each still get their own.
 */
class LevelPerTestJunit4Test {

  @Test
  void twoTestsAtDifferentLevelsInOneClassEachGetTheirOwnLevel() {
    System.clearProperty("narrativetrace.level");
    try {
      runNested(LevelPerTestJunit4Fixture.class);
    } finally {
      System.clearProperty("narrativetrace.level");
    }

    assertThat(LevelPerTestJunit4Fixture.firstCapturedParameterValues)
        .as("the first test ran at the default level, DETAIL")
        .isTrue();
    assertThat(LevelPerTestJunit4Fixture.secondCapturedParameterValues)
        .as("the second test ran at the level the first test set for it, SUMMARY")
        .isFalse();
  }

  private static void runNested(Class<?> fixture) {
    var request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(fixture))
            .build();
    LauncherFactory.create().execute(request);
  }
}
