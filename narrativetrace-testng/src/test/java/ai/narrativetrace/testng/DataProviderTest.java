/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A method TestNG runs more than once — a data provider's rows, {@code invocationCount},
 * {@code @Factory} instances — owns one artifact per invocation, labelled {@code Class.method[#n]}
 * the way core labels a JUnit parameterized invocation, never by its arguments.
 */
class DataProviderTest {

  @Test
  void eachRowOwnsItsOwnArtifacts(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, DataProviderFixture.class);

    assertThat(TestNgRuns.trace(outputDir, "DataProviderFixture", "equipment_can_be_found-001"))
        .contains("kayak")
        .doesNotContain("tent");
    assertThat(TestNgRuns.trace(outputDir, "DataProviderFixture", "equipment_can_be_found-002"))
        .contains("tent")
        .doesNotContain("kayak");
    assertThat(TestNgRuns.trace(outputDir, "DataProviderFixture", "equipment_can_be_found-003"))
        .contains("stove");
  }

  @Test
  void theFailingRowIsLabelledClassMethodHashN(@TempDir Path outputDir) throws Exception {
    var run = TestNgRuns.run(outputDir, DataProviderFixture.class);

    assertThat(run.err())
        .contains(
            "narrativetrace: trace of DataProviderFixture.equipmentCanBeFound[#3] → "
                + outputDir
                    .toAbsolutePath()
                    .normalize()
                    .resolve("traces/DataProviderFixture/equipment_can_be_found-003.md"));
  }

  @Test
  void theManifestReachesEveryRow(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, DataProviderFixture.class);

    var manifest = Files.readString(outputDir.resolve("manifest.json"));
    for (var n : new String[] {"001", "002", "003"}) {
      assertThat(manifest)
          .contains("traces/DataProviderFixture/equipment_can_be_found-" + n + ".md");
    }
  }

  @Test
  void theStructuralTitleOfARowNamesTheMethodNotItsArgument(@TempDir Path outputDir)
      throws Exception {
    TestNgRuns.run(outputDir, DataProviderFixture.class);

    var structural =
        Files.readString(
            outputDir.resolve("structural/DataProviderFixture/equipment_can_be_found-002.nt"));
    assertThat(structural).contains("Equipment can be found #2").doesNotContain("tent");
  }

  @Test
  void anOrdinaryMethodKeepsItsUndecoratedName(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, DataProviderFixture.class);

    assertThat(outputDir.resolve("traces/DataProviderFixture/ordinary_lookup.md")).exists();
  }

  @Test
  void invocationCountAndFactoryInstancesGetDistinctArtifacts(@TempDir Path outputDir)
      throws Exception {
    TestNgRuns.run(outputDir, InvocationCountFixture.class, FactoryFixture.class);

    assertThat(TestNgRuns.listing(outputDir))
        .contains(
            "traces/InvocationCountFixture/ticket_is_issued-001.md",
            "traces/InvocationCountFixture/ticket_is_issued-002.md",
            "traces/InvocationCountFixture/ticket_is_issued-003.md",
            "traces/FactoryFixture/region_is_served-001.md",
            "traces/FactoryFixture/region_is_served-002.md");
  }
}
