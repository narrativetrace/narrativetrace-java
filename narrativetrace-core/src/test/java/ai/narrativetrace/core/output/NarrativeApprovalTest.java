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
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Approval mode for the structural narrative (TODO item 14, ApprovalTests-style): a committed
 * {@code *.approved.nt} baseline is the contract, a run whose structure differs fails with a
 * readable diff and leaves a {@code *.received.nt} for review, and approving is promoting the
 * received file. Free-tier mechanism only — no semantics beyond byte comparison.
 */
class NarrativeApprovalTest {

  @Test
  void missingApprovedBaselineFailsAndWritesReceived(@TempDir Path dir) {
    var approved = dir.resolve("OrderTest/trip_settles.approved.nt");
    var trace = traceWithOneCall();

    assertThatThrownBy(() -> NarrativeApproval.verify(trace, "trip settles", approved))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("No approved narrative for scenario \"trip settles\"")
        .hasMessageContaining(dir.resolve("OrderTest/trip_settles.received.nt").toString())
        .hasMessageContaining("approveNarratives");

    var received = dir.resolve("OrderTest/trip_settles.received.nt");
    assertThat(received).exists();
    assertThat(contentOf(received))
        .isEqualTo("scenario: trip settles\n\n- Service.doWork() → value\n");
  }

  @Test
  void matchingBaselinePassesAndClearsAnyStaleReceivedFile(@TempDir Path dir) throws Exception {
    var approved = dir.resolve("OrderTest/trip_settles.approved.nt");
    Files.createDirectories(approved.getParent());
    Files.writeString(approved, "scenario: trip settles\n\n- Service.doWork() → value\n");
    var received = dir.resolve("OrderTest/trip_settles.received.nt");
    Files.writeString(received, "stale from an earlier mismatch");

    NarrativeApproval.verify(traceWithOneCall(), "trip settles", approved);

    assertThat(received).doesNotExist();
    assertThat(approved).exists();
  }

  @Test
  void changedStructureFailsWithTheDiffAndWritesReceived(@TempDir Path dir) throws Exception {
    var approved = dir.resolve("OrderTest/trip_settles.approved.nt");
    Files.createDirectories(approved.getParent());
    Files.writeString(
        approved, "scenario: trip settles\n\n- Service.doWork() → value\n  - Ledger.record()\n");

    assertThatThrownBy(() -> NarrativeApproval.verify(traceWithOneCall(), "trip settles", approved))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Narrative changed against the approved baseline")
        .hasMessageContaining("-1 call Ledger.record")
        .hasMessageContaining("-  - Ledger.record()")
        .hasMessageContaining("approveNarratives");

    var received = dir.resolve("OrderTest/trip_settles.received.nt");
    assertThat(received).exists();
    assertThat(contentOf(received))
        .isEqualTo("scenario: trip settles\n\n- Service.doWork() → value\n");
    assertThat(contentOf(approved)).contains("Ledger.record");
  }

  @Test
  void promoteReceivedTurnsEveryReceivedFileIntoTheApprovedBaseline(@TempDir Path dir)
      throws Exception {
    var orderDir = Files.createDirectories(dir.resolve("OrderTest"));
    Files.writeString(orderDir.resolve("trip_settles.approved.nt"), "old baseline\n");
    Files.writeString(orderDir.resolve("trip_settles.received.nt"), "new structure\n");
    var billingDir = Files.createDirectories(dir.resolve("BillingTest"));
    Files.writeString(billingDir.resolve("invoice_paid.received.nt"), "first structure\n");

    var promoted = NarrativeApproval.promoteReceived(dir);

    assertThat(promoted)
        .containsExactlyInAnyOrder(
            orderDir.resolve("trip_settles.approved.nt"),
            billingDir.resolve("invoice_paid.approved.nt"));
    assertThat(contentOf(orderDir.resolve("trip_settles.approved.nt")))
        .isEqualTo("new structure\n");
    assertThat(contentOf(billingDir.resolve("invoice_paid.approved.nt")))
        .isEqualTo("first structure\n");
    assertThat(orderDir.resolve("trip_settles.received.nt")).doesNotExist();
    assertThat(billingDir.resolve("invoice_paid.received.nt")).doesNotExist();
  }

  @Test
  void promoteReceivedOnAMissingDirectoryPromotesNothing(@TempDir Path dir) throws Exception {
    assertThat(NarrativeApproval.promoteReceived(dir.resolve("no-narratives-here"))).isEmpty();
  }

  @Test
  void approvedFileMapsClassAndMethodLikeTheTraceArtifacts() {
    var file =
        NarrativeApproval.approvedFile(
            Path.of("src/test/narratives"), "com.example.OrderTest", "customerPlacesOrder");

    assertThat(file)
        .isEqualTo(Path.of("src/test/narratives/OrderTest/customer_places_order.approved.nt"));
  }

  /**
   * Regression: the baseline tree sliced the simple name out itself and resolved it verbatim, so a
   * name whose last dot is followed by a separator put an approved baseline outside the directory
   * the caller nominated. It shares the trace tree's rule now. Found by the security suite's
   * hostile-name corpus.
   */
  @Test
  void aClassNameThatResolvesToAnAbsolutePathKeepsTheBaselineInsideTheApprovedDirectory() {
    var approvedDir = Path.of("src/test/narratives");

    var file = NarrativeApproval.approvedFile(approvedDir, "..\u002f..\u002ftmp\u002fevil", "runs");

    assertThat(file.normalize()).startsWithRaw(approvedDir);
    assertThat(file).isEqualTo(Path.of("src/test/narratives/_tmp_evil/runs.approved.nt"));
  }

  private static String contentOf(Path file) {
    try {
      return Files.readString(file);
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
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
