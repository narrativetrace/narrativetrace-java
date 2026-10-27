/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.config.TraceDepth;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An approval baseline declares the depth it was cut at, and a run is only judged against a
 * baseline of the same depth: a deep baseline and a shallow run never compare as a silent pass.
 */
class DeepApprovalTest {

  private static final String DEEP_BASELINE =
      "scenario: prices\ndepth: internal\n\n#1 - Pricing.total() → value\n";
  private static final String SHALLOW_BASELINE =
      "scenario: prices\n\n#1 - Pricing.total() → value\n";

  private static TraceTree publicOnlyTrace() {
    return new DefaultTraceTree(
        List.of(
            new TraceNode(
                new MethodSignature("Pricing", "total", List.of()),
                List.of(),
                new TraceOutcome.Returned("225"),
                1_000_000L)));
  }

  private static Path baseline(Path dir, String content) throws Exception {
    var approved = dir.resolve("PricingTest/prices.approved.nt");
    Files.createDirectories(approved.getParent());
    Files.writeString(approved, content);
    return approved;
  }

  @Test
  void aDeepBaselineAndAShallowRunWithIdenticalLinesIsAMismatchNotAPass(@TempDir Path dir)
      throws Exception {
    var approved = baseline(dir, DEEP_BASELINE);

    assertThatThrownBy(
            () ->
                NarrativeApproval.verify(
                    publicOnlyTrace(),
                    "prices",
                    approved,
                    ai.narrativetrace.api.event.TraceLoss.none(),
                    TraceDepth.PUBLIC))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Depth mismatch")
        .hasMessageContaining("baseline was recorded at depth internal")
        .hasMessageContaining("this run at depth public");
    assertThat(dir.resolve("PricingTest/prices.received.nt")).exists();
  }

  @Test
  void aShallowBaselineAndADeepRunIsAMismatchToo(@TempDir Path dir) throws Exception {
    var approved = baseline(dir, SHALLOW_BASELINE);

    assertThatThrownBy(
            () ->
                NarrativeApproval.verify(
                    publicOnlyTrace(),
                    "prices",
                    approved,
                    ai.narrativetrace.api.event.TraceLoss.none(),
                    TraceDepth.INTERNAL))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Depth mismatch")
        .hasMessageContaining("baseline was recorded at depth public")
        .hasMessageContaining("this run at depth internal");
  }

  @Test
  void aDeepBaselineAndADeepRunPass(@TempDir Path dir) throws Exception {
    var approved = baseline(dir, DEEP_BASELINE);

    var note =
        NarrativeApproval.verify(
            publicOnlyTrace(),
            "prices",
            approved,
            ai.narrativetrace.api.event.TraceLoss.none(),
            TraceDepth.INTERNAL);

    assertThat(note).isEmpty();
  }

  @Test
  void aBaselineWithoutADepthLineIsPublicAndTheDefaultVerifyIsUnchanged(@TempDir Path dir)
      throws Exception {
    var approved = baseline(dir, SHALLOW_BASELINE);

    assertThat(NarrativeApproval.verify(publicOnlyTrace(), "prices", approved, TraceDepth.PUBLIC))
        .isEmpty();
    NarrativeApproval.verify(publicOnlyTrace(), "prices", approved);
  }

  @Test
  void aWrittenTraceFileSaysItsDepthAndTheDeepSpansItDropped(@TempDir Path dir) throws Exception {
    var identity = ArtifactIdentity.ofMethod("com.example.PricingTest", "prices");
    TraceTestSupport.writeTraceFile(
        identity,
        "prices",
        publicOnlyTrace(),
        false,
        dir,
        new PrintStream(new java.io.ByteArrayOutputStream()),
        "markdown",
        t -> "sequenceDiagram",
        t -> "sequenceDiagram",
        true,
        null,
        TraceDepth.INTERNAL,
        12);

    var lines = Files.readAllLines(dir.resolve("traces/PricingTest/prices.md"));
    var open = lines.indexOf("<!--");
    var header = lines.subList(open, lines.indexOf("-->") + 1);
    assertThat(String.join("\n", header))
        .contains("Depth: internal")
        .contains("12 deep spans were dropped");
    assertThat(header).hasSizeLessThanOrEqualTo(10);
    assertThat(Files.readString(dir.resolve("structural/PricingTest/prices.nt")))
        .startsWith("scenario: Prices\ndepth: internal\n");
  }
}
