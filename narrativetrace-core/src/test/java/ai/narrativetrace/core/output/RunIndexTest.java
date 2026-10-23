/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunIndexTest {

  private static ScenarioManifest.Entry row(
      String method, String outcome, String message, Map<String, String> artifacts) {
    return new ScenarioManifest.Entry(
        method,
        ArtifactIdentity.ofMethod("com.example.OrderTest", method),
        artifacts,
        outcome,
        message);
  }

  private static ScenarioManifest.Entry failing(String method, String message) {
    return row(method, "failed", message, Map.of("trace", "traces/OrderTest/" + method + ".md"));
  }

  private static ScenarioManifest.Entry passing(String method) {
    return row(method, "passed", null, Map.of("trace", "traces/OrderTest/" + method + ".md"));
  }

  @Test
  void failingRowsComeFirstEachGroupInExecutionOrder() {
    var index =
        RunIndex.render(
            List.of(passing("p1"), failing("f1", "boom"), passing("p2"), failing("f2", "bang")));

    assertThat(index.indexOf("OrderTest.f1")).isLessThan(index.indexOf("OrderTest.f2"));
    assertThat(index.indexOf("OrderTest.f2")).isLessThan(index.indexOf("## Passing"));
    assertThat(index.indexOf("## Passing")).isLessThan(index.indexOf("OrderTest.p1"));
    assertThat(index.indexOf("OrderTest.p1")).isLessThan(index.indexOf("OrderTest.p2"));
  }

  @Test
  void aRunWithNoFailureSaysSoUnderTheFailingHeading() {
    var index = RunIndex.render(List.of(passing("p1")));

    assertThat(index).contains("## Failing\n\nNo test failed in this run.\n");
  }

  @Test
  void aRowWithNoRecordedVerdictIsListedAsPassingWithAnUnknownOutcome() {
    var index =
        RunIndex.render(
            List.of(
                new ScenarioManifest.Entry(
                    "x", ArtifactIdentity.ofMethod("T", "x"), Map.of("trace", "traces/T/x.md"))));

    assertThat(index).contains("| T.x | unknown | `traces/T/x.md` |");
  }

  @Test
  void aLongMessageIsCutToOneShortLine() {
    var index = RunIndex.render(List.of(failing("f1", "x".repeat(500))));

    var line = index.lines().filter(l -> l.contains("OrderTest.f1")).findFirst().orElseThrow();
    assertThat(line.length()).isLessThan(400);
    assertThat(line).contains("…");
  }

  @Test
  void writesNothingForAnEmptyRunAndAnAtomicFileOtherwise(@TempDir Path dir) throws Exception {
    RunIndex.write(List.of(), dir);
    assertThat(dir.resolve("INDEX.md")).doesNotExist();

    RunIndex.write(List.of(failing("f1", "boom")), dir);
    RunIndex.write(List.of(failing("f1", "boom"), passing("p1")), dir);

    assertThat(Files.readString(dir.resolve("INDEX.md"))).contains("OrderTest.p1");
    try (var files = Files.list(dir)) {
      assertThat(files).hasSize(1);
    }
  }
}
