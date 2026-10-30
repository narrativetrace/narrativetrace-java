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

  /** A pipe in a name would start a new column; the cell escape is what keeps the row intact. */
  @Test
  void aPipeInATestNameIsEscapedSoTheRowKeepsItsColumns() {
    var index = RunIndex.render(List.of(failing("f|1", "boom")));

    assertThat(index).contains("| OrderTest.f\\|1 | failed |");
  }

  /**
   * A code span cannot carry a backslash escape, so a pipe in a path is percent-encoded instead.
   */
  @Test
  void aPipeInATracePathIsPercentEncodedInsideItsCodeSpan() {
    var index =
        RunIndex.render(
            List.of(row("f1", "failed", "boom", Map.of("trace", "traces/OrderTest/f|1.md"))));

    assertThat(index).contains("`traces/OrderTest/f%7C1.md`");
  }

  /** The failure message is the one cell that can carry prose; its line breaks are collapsed. */
  @Test
  void aNewlineInAFailureMessageIsCollapsedIntoTheOneRow() {
    var index = RunIndex.render(List.of(failing("f1", "expected: 2\nbut was: 3")));

    assertThat(index).contains("| expected: 2 but was: 3 |");
    assertThat(index.lines().filter(line -> line.startsWith("| OrderTest.f1")).count())
        .isEqualTo(1);
  }

  /** The cap is on the message, not under it: a message of exactly the limit is left whole. */
  @Test
  void aMessageOfExactlyTheLimitIsNotCutAndOneCharacterMoreIs() {
    var atLimit = RunIndex.render(List.of(failing("f1", "x".repeat(200))));
    var pastLimit = RunIndex.render(List.of(failing("f1", "x".repeat(201))));

    assertThat(atLimit).contains("| " + "x".repeat(200) + " |").doesNotContain("…");
    assertThat(pastLimit).contains("| " + "x".repeat(200) + "… |");
  }

  /** A failing row the integration recorded no message for renders the cell empty, not "null". */
  @Test
  void aFailingRowWithNoMessageRendersAnEmptyErrorCell() {
    var index =
        RunIndex.render(
            List.of(row("f1", "failed", null, Map.of("trace", "traces/OrderTest/f1.md"))));

    assertThat(index).contains("| `traces/OrderTest/f1.md` |  |").doesNotContain("null");
  }

  /** The listing is what is on disk: a row whose format wrote no trace file says so. */
  @Test
  void aRowWithNoTraceFileSaysSoInsteadOfLinkingOne() {
    var index = RunIndex.render(List.of(row("p1", "passed", null, Map.of())));

    assertThat(index).contains("| OrderTest.p1 | passed | (no trace file) |");
  }
}
