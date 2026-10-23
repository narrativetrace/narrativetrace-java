/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunPointersTest {

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
  void theFooterListsFailingTracesByAbsolutePathInExecutionOrderThenTheIndex(@TempDir Path dir) {
    var bytes = new ByteArrayOutputStream();

    RunPointers.printFailingFirst(
        List.of(failing("f1", "boom"), passing("p1"), failing("f2", "bang")),
        dir,
        new PrintStream(bytes, true, StandardCharsets.UTF_8));

    assertThat(bytes.toString(StandardCharsets.UTF_8).lines().toList())
        .containsExactly(
            "narrativetrace: trace of OrderTest.f1 → "
                + dir.toAbsolutePath().normalize().resolve("traces/OrderTest/f1.md"),
            "narrativetrace: trace of OrderTest.f2 → "
                + dir.toAbsolutePath().normalize().resolve("traces/OrderTest/f2.md"),
            "narrativetrace: index of this run → "
                + dir.toAbsolutePath().normalize().resolve("INDEX.md"));
  }

  @Test
  void theFooterIsSilentWhenNothingFailed(@TempDir Path dir) {
    var bytes = new ByteArrayOutputStream();

    RunPointers.printFailingFirst(List.of(passing("p1")), dir, new PrintStream(bytes));

    assertThat(bytes.size()).isZero();
  }

  @Test
  void aFailingRowWithNoTraceFileHasNoPointer(@TempDir Path dir) {
    assertThat(RunPointers.pointerLine(row("f1", "failed", "boom", Map.of()), dir)).isEmpty();
  }

  @Test
  void anInvocationIsLabelledWithItsNumber() {
    var identity = ArtifactIdentity.ofInvocation("com.example.CatalogTest", "finds", 2, "TENT");

    assertThat(RunPointers.label(identity)).isEqualTo("CatalogTest.finds[#2]");
  }

  @Test
  void theManifestRowsCarryOutcomeAndMessageOnlyWhenKnown() {
    var manifest =
        ScenarioManifest.render(
            List.of(
                failing("f1", "bo\"om"),
                passing("p1"),
                new ScenarioManifest.Entry("x", ArtifactIdentity.ofMethod("T", "x"), Map.of())));

    assertThat(manifest).contains("\"outcome\": \"failed\",\n      \"message\": \"bo\\\"om\"");
    assertThat(manifest.lines().filter(l -> l.contains("\"outcome\"")).count()).isEqualTo(2);
    assertThat(manifest.lines().filter(l -> l.contains("\"message\"")).count()).isOne();
  }
}
