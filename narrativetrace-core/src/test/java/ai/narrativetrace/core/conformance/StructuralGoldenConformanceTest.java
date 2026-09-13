/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.render.StructuralTraceRenderer;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the cross-platform golden {@code .nt} artifact — a FairSplit/trip-settlement scenario,
 * "negative expense is rejected before touching the ledger" — from committed Java source.
 *
 * <p>INTENT: Every other NarrativeTrace runtime (.NET, Swift, TypeScript, Python) pins its
 * structural renderer against this exact byte sequence as {@code java-negative-expense.nt}, but
 * until this test existed the Java tree carried no source that produced it — the golden's
 * provenance was orphaned. This test builds the identical tree by hand, using the real {@link
 * TraceNode}/{@link TraceTree} model the same way {@code StructuralTraceRendererTest} does, and
 * asserts the committed resource is both what the ports pin <em>and</em> what {@link
 * StructuralTraceRenderer} actually produces today.
 *
 * <p><b>@llmNote</b> Java is the family's source of truth for this artifact: a change here that
 * alters the rendered bytes must fail this test first, and every port then re-pins its own copy
 * from the (reviewed, intentional) new bytes — never the reverse. Do not "fix" a failure here by
 * editing the committed resource to match a renderer change without also updating {@code
 * documentation/structural-trace-format.md} and every port's fixture in the same change.
 */
class StructuralGoldenConformanceTest {

  private static final String GOLDEN_RESOURCE = "structural-golden/negative-expense.nt";

  /** The exception the FairSplit dogfood scenario throws; named to match every port's mirror. */
  private static final class InvalidExpenseException extends RuntimeException {
    InvalidExpenseException(String message) {
      super(message);
    }
  }

  private static TraceNode call(
      String className, String methodName, List<String> parameters, TraceOutcome outcome) {
    return call(className, methodName, parameters, outcome, List.of());
  }

  private static TraceNode call(
      String className,
      String methodName,
      List<String> parameters,
      TraceOutcome outcome,
      List<TraceNode> children) {
    var captures = parameters.stream().map(name -> new ParameterCapture(name, "", false)).toList();
    return new TraceNode(new MethodSignature(className, methodName, captures), children, outcome);
  }

  private static TraceTree fairSplitNegativeExpense() {
    var failure =
        new TraceOutcome.Threw(new InvalidExpenseException("amount -12.00 EUR is not positive"));
    return new DefaultTraceTree(
        List.of(
            call(
                "TripSettlementService",
                "recordExpense",
                List.of("tripName", "expense"),
                failure,
                List.of(call("ExpenseValidator", "ensureValid", List.of("expense"), failure))),
            call(
                "TripSettlementService",
                "settleTrip",
                List.of("tripName"),
                new TraceOutcome.Returned("SettlementPlan[transfers=0]"),
                List.of(
                    call(
                        "TripLedger",
                        "expensesOf",
                        List.of("tripName"),
                        new TraceOutcome.Returned("[]")),
                    call(
                        "BalanceCalculator",
                        "computeBalances",
                        List.of("expenses"),
                        new TraceOutcome.Returned("{}")),
                    call(
                        "SettlementPlanner",
                        "planTransfers",
                        List.of("balances"),
                        new TraceOutcome.Returned("[]"))))));
  }

  private static byte[] golden() throws IOException {
    try (InputStream stream =
        StructuralGoldenConformanceTest.class
            .getClassLoader()
            .getResourceAsStream(GOLDEN_RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException(
            "Golden resource not found on classpath: " + GOLDEN_RESOURCE);
      }
      return stream.readAllBytes();
    }
  }

  @Test
  void rendererReproducesTheGoldenArtifactByteForByte() throws IOException {
    var golden = golden();
    var rendered =
        new StructuralTraceRenderer()
            .renderDocument(
                fairSplitNegativeExpense(),
                "Negative expense is rejected before touching the ledger")
            .getBytes(StandardCharsets.UTF_8);

    // Two assertions on purpose: the string comparison fails with a readable line-by-line diff
    // when the renderer drifts; the byte comparison is what actually guards the artifact's
    // contract — encoding, line endings and all — so a renderer change that alters the bytes
    // fails here first, and every port then re-pins its own fixture from Java, never the reverse.
    assertThat(new String(rendered, StandardCharsets.UTF_8))
        .isEqualTo(new String(golden, StandardCharsets.UTF_8));
    assertThat(rendered).isEqualTo(golden);
  }
}
