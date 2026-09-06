/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

class NarrativeTraceClassRuleDuplicateScenariosTest {

  @BeforeEach
  void resetGlobal() {
    NarrativeTraceClassRule.resetGlobalAccumulator();
  }

  @Test
  void suiteLevelArtifactsShouldRetainDuplicateScenarioNamesFromDifferentClasses(
      @TempDir Path tempDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(
                      selectClass(DuplicateScenarioFixtureOne.class),
                      selectClass(DuplicateScenarioFixtureTwo.class))
                  .build());
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var jsonContent = Files.readString(tempDir.resolve("clarity-results.json"));
    assertThat(countOccurrences(jsonContent, "\"name\":\"Customer places order\"")).isEqualTo(2);
  }

  private static int countOccurrences(String haystack, String needle) {
    var count = 0;
    var fromIndex = 0;
    while (true) {
      var index = haystack.indexOf(needle, fromIndex);
      if (index < 0) {
        return count;
      }
      count++;
      fromIndex = index + needle.length();
    }
  }
}
