/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The capture level is read as each test starts, so two tests in one class can differ. */
class LevelPerTestTest {

  @Test
  void twoTestsAtDifferentLevelsEachGetTheirOwnLevel(@TempDir Path outputDir) throws Exception {
    System.clearProperty("narrativetrace.level");
    try {
      TestNgRuns.run(outputDir, LevelPerTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.level");
    }

    assertThat(LevelPerTestFixture.firstCapturedParameterValues).isTrue();
    assertThat(LevelPerTestFixture.secondCapturedParameterValues).isFalse();
  }
}
