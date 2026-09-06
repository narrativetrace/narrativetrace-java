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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Approval against a run that is known to be incomplete. Equality is the wrong test there: the
 * best-effort path drops non-deterministically, so a missing branch says nothing about behaviour.
 * The run is instead required to be a subsequence of the baseline — absences tolerated, anything
 * added, renamed or reordered still fails — and its artifact is deliberately not promotable.
 */
class LossyApprovalTest {

  private static final TraceLoss LOSSY = new TraceLoss(0, 3, 12);
  private static final TraceLoss CLEAN = TraceLoss.none();

  @Test
  void aLossyRunMissingABranchPassesAndSaysSo(@TempDir Path dir) throws Exception {
    var approved =
        baselineWith(dir, "- OrderService.placeOrder() → value", "  - Notifier.send() → value");

    var note = NarrativeApproval.verify(oneCallTrace(), "trip settles", approved, LOSSY);

    assertThat(note)
        .contains("consistent with baseline")
        .contains("3 async scopes")
        .doesNotContain("unchanged");
  }

  @Test
  void aLossyRunThatAddsACallStillFails(@TempDir Path dir) throws Exception {
    var approved = baselineWith(dir, "- OrderService.placeOrder() → value");

    assertThatThrownBy(
            () -> NarrativeApproval.verify(twoCallTrace(), "trip settles", approved, LOSSY))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Narrative changed");
  }

  @Test
  void aLossyRunDoesNotLeaveAPromotableReceivedFile(@TempDir Path dir) throws Exception {
    var approved = baselineWith(dir, "- OrderService.placeOrder() → value");

    assertThatThrownBy(
            () -> NarrativeApproval.verify(twoCallTrace(), "trip settles", approved, LOSSY))
        .isInstanceOf(AssertionError.class);

    assertThat(dir.resolve("OrderTest/trip_settles.received.nt")).doesNotExist();
    assertThat(dir.resolve("OrderTest/trip_settles.incomplete.nt")).exists();
  }

  @Test
  void theWithheldArtifactIsNamedInTheFailure(@TempDir Path dir) throws Exception {
    var approved = baselineWith(dir, "- OrderService.placeOrder() → value");

    assertThatThrownBy(
            () -> NarrativeApproval.verify(twoCallTrace(), "trip settles", approved, LOSSY))
        .hasMessageContaining("incomplete.nt")
        .hasMessageContaining("not promotable");
  }

  @Test
  void aMissingBaselineOnALossyRunWithholdsTheReceivedFile(@TempDir Path dir) {
    var approved = dir.resolve("OrderTest/trip_settles.approved.nt");

    assertThatThrownBy(
            () -> NarrativeApproval.verify(oneCallTrace(), "trip settles", approved, LOSSY))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("No approved narrative")
        .hasMessageContaining("incomplete");

    assertThat(dir.resolve("OrderTest/trip_settles.received.nt")).doesNotExist();
  }

  @Test
  void promotionIgnoresIncompleteArtifacts(@TempDir Path dir) throws Exception {
    Files.createDirectories(dir.resolve("OrderTest"));
    Files.writeString(dir.resolve("OrderTest/a.received.nt"), "scenario: a\n");
    Files.writeString(dir.resolve("OrderTest/b.incomplete.nt"), "scenario: b\n");

    var promoted = NarrativeApproval.promoteReceived(dir);

    assertThat(promoted).hasSize(1);
    assertThat(dir.resolve("OrderTest/a.approved.nt")).exists();
    assertThat(dir.resolve("OrderTest/b.approved.nt")).doesNotExist();
  }

  @Test
  void acleanRunIsUnaffectedByTheNewParameter(@TempDir Path dir) throws Exception {
    var approved = baselineWith(dir, "- OrderService.placeOrder() → value");

    var note = NarrativeApproval.verify(oneCallTrace(), "trip settles", approved, CLEAN);

    assertThat(note).isEmpty();
  }

  @Test
  void acleanRunMissingABranchStillFails(@TempDir Path dir) throws Exception {
    var approved =
        baselineWith(dir, "- OrderService.placeOrder() → value", "  - Notifier.send() → value");

    assertThatThrownBy(
            () -> NarrativeApproval.verify(oneCallTrace(), "trip settles", approved, CLEAN))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Narrative changed");
  }

  private static Path baselineWith(Path dir, String... callLines) throws IOException {
    var approved = dir.resolve("OrderTest/trip_settles.approved.nt");
    Files.createDirectories(approved.getParent());
    Files.writeString(approved, "scenario: trip settles\n\n" + String.join("\n", callLines) + "\n");
    return approved;
  }

  private static TraceTree oneCallTrace() {
    return new DefaultTraceTree(List.of(call("OrderService", "placeOrder", List.of())));
  }

  private static TraceTree twoCallTrace() {
    return new DefaultTraceTree(
        List.of(call("OrderService", "placeOrder", List.of(call("Payment", "charge", List.of())))));
  }

  private static TraceNode call(String className, String method, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature(className, method, List.of()),
        children,
        new TraceOutcome.Returned("value"));
  }
}
