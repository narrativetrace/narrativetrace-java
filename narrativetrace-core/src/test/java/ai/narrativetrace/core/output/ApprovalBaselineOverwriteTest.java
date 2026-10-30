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

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADV-2026-10-28-1 — the committed baseline is the contract, and nothing but {@code
 * promoteReceived} may write it: {@code NarrativeApproval.verify} derives the received artifact's
 * path from the baseline's own file name by {@link String#replace}, so a baseline whose name does
 * not carry {@code .approved.nt} leaves the derivation a no-op and the received path IS the
 * baseline path. The comparison then writes over — or deletes — the artifact it is comparing
 * against.
 *
 * <p>INTENT: {@code verify(TraceTree, String, Path)} is public API and takes the baseline as a bare
 * {@code Path}; nothing in it requires that path to have come from {@link
 * NarrativeApproval#approvedFile}. The class's own contract says a run's artifact is "deliberately
 * not promotable" and that "one short run must never become the committed contract" — promotion is
 * a separate, reviewed step. With an off-scheme name both halves of that promise are lost in one
 * call: a mismatching run silently re-baselines itself, and a matching run deletes the contract it
 * just satisfied.
 *
 * <p><b>@llmNote</b> Both tests are about the derivation, not about the caller's taste in names.
 * The house rule is a guard clause at the entry of a public method — "validate inputs early, before
 * doing work, never past the point where the invalid input could be masked"; here the invalid input
 * is masked by a {@code replace} that cannot fail, and the masking is destructive. The declared
 * result or the declared exception, never a third thing: deleting the baseline is a third thing.
 */
class ApprovalBaselineOverwriteTest {

  private static final String RENDERED =
      "scenario: trip settles\n\n#1 - Service.doWork() → value\n";

  @Test
  @DisplayName("ADV-2026-10-28-1: a matching run must never delete the baseline it matched")
  void aPassingComparisonAgainstAnOffSchemeBaselineDeletesTheCommittedContract(@TempDir Path dir)
      throws Exception {
    var baseline = dir.resolve("OrderTest/trip_settles.nt");
    Files.createDirectories(baseline.getParent());
    Files.writeString(baseline, RENDERED);

    assertThatThrownBy(
            () ->
                NarrativeApproval.verify(
                    traceWithOneCall(), "trip settles", baseline, TraceLoss.none()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(".approved.nt");

    // The guard refuses before any comparison, so a baseline it would otherwise have matched is
    // never at risk of the delete a matching comparison performs on its received sibling.
    assertThat(Files.readString(baseline)).isEqualTo(RENDERED);
  }

  @Test
  @DisplayName("ADV-2026-10-28-1: a failing comparison must never re-baseline itself")
  void aMismatchAgainstAnOffSchemeBaselineOverwritesTheContractItFailed(@TempDir Path dir)
      throws Exception {
    var baseline = dir.resolve("OrderTest/trip_settles.nt");
    Files.createDirectories(baseline.getParent());
    var committed = "scenario: trip settles\n\n- Service.doWork() → value\n  - Ledger.record()\n";
    Files.writeString(baseline, committed);

    assertThatThrownBy(() -> NarrativeApproval.verify(traceWithOneCall(), "trip settles", baseline))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(".approved.nt");

    // The run that was rejected must not have become the contract: a second, identical run would
    // now pass against a baseline nobody reviewed.
    assertThat(Files.readString(baseline)).isEqualTo(committed);
  }

  private static TraceTree traceWithOneCall() {
    var node =
        new TraceNode(
            new MethodSignature("Service", "doWork", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    return new DefaultTraceTree(List.of(node));
  }
}
