/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.render.StructuralTraceRenderer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Approval-testing over the structural narrative (ADR-002, TODO item 14): committed {@code
 * *.approved.nt} baselines are the behavioral contract, and a run whose structure differs fails
 * with a readable diff.
 *
 * <p>INTENT: The ApprovalTests convention applied to the value-free {@code .nt} artifact — a
 * mismatch writes the current render as {@code *.received.nt} beside the baseline for review, and
 * approving is promoting the received file over the approved one. Byte comparison only: the
 * renderer is deterministic, so byte-identical is behaviorally identical. Anything smarter
 * (semantic diff, review workflow) is deliberately out of scope here.
 */
public final class NarrativeApproval {

  private NarrativeApproval() {}

  /**
   * Verifies the scenario's structure against its committed baseline.
   *
   * @throws AssertionError if no baseline exists (the current render is written as the received
   *     file to review and approve) or if the structure differs from the baseline (received file
   *     written, message carries the readable diff)
   * @throws IOException if the baseline or received file cannot be read or written
   */
  public static void verify(TraceTree trace, String scenario, Path approvedFile)
      throws IOException {
    verify(trace, scenario, approvedFile, TraceLoss.none());
  }

  /**
   * Verifies a scenario whose run may have been incomplete.
   *
   * <p>INTENT: The best-effort path drops events under load and refuses async scopes above the
   * adoption cap, non-deterministically — so on a lossy run a missing branch is evidence about the
   * machine, not about the code, and byte equality would report behaviour change that did not
   * happen. Such a run is instead required to be a <em>subsequence</em> of the baseline: absences
   * are tolerated and named, while anything added, renamed or reordered still fails.
   *
   * <p>The artifact of a lossy run is written as {@code *.incomplete.nt}, which {@link
   * #promoteReceived(Path)} does not promote. One short run must never become the committed
   * contract — every later complete run would then read as having *added* calls.
   *
   * @return a note to surface for a lossy pass ("consistent with baseline, …"), empty when the run
   *     was clean and matched exactly. A lossy pass is weaker evidence than an exact match and must
   *     not be reported as "unchanged".
   * @throws AssertionError when the baseline is missing, or the structure changed in a way loss
   *     cannot explain
   */
  public static String verify(TraceTree trace, String scenario, Path approvedFile, TraceLoss loss)
      throws IOException {
    var current = new StructuralTraceRenderer().renderDocument(trace, scenario);
    var receivedFile = loss.any() ? incompleteSibling(approvedFile) : receivedSibling(approvedFile);
    if (!Files.exists(approvedFile)) {
      new TraceFileWriter().write(current, receivedFile);
      throw new AssertionError(
          "No approved narrative for scenario \""
              + scenario
              + "\".\n"
              + (loss.any()
                  ? "This run was incomplete ("
                      + describe(loss)
                      + "), so its structure was written to "
                      + receivedFile
                      + " and is deliberately not promotable — rerun to record a baseline."
                  : "Received: "
                      + receivedFile
                      + "\nReview it and approve via the approveNarratives task (or rename it to "
                      + approvedFile.getFileName()
                      + ")."));
    }
    var baseline = Files.readString(approvedFile);
    if (loss.any()) {
      return verifyLossy(baseline, current, receivedFile, loss);
    }
    var delta = StructuralDelta.between(baseline, current);
    if (!delta.unchanged()) {
      new TraceFileWriter().write(current, receivedFile);
      throw new AssertionError(
          "Narrative changed against the approved baseline ("
              + delta.summary()
              + "):\n"
              + delta.diff()
              + "Received: "
              + receivedFile
              + "\nIf this change is intended, approve it via the approveNarratives task.");
    }
    Files.deleteIfExists(receivedFile);
    return "";
  }

  /**
   * Subsequence containment: the run may be short, but everything in it must be in the baseline.
   */
  private static String verifyLossy(
      String baseline, String current, Path incompleteFile, TraceLoss loss) throws IOException {
    if (LineDiff.isSubsequence(baseline, current)) {
      Files.deleteIfExists(incompleteFile);
      return "consistent with baseline, but this run was incomplete (" + describe(loss) + ")";
    }
    new TraceFileWriter().write(current, incompleteFile);
    throw new AssertionError(
        "Narrative changed against the approved baseline in a way loss cannot explain:\n"
            + LineDiff.unified(baseline, current)
            + "This run was also incomplete ("
            + describe(loss)
            + "), so its structure was written to "
            + incompleteFile
            + " and is not promotable — fix or rerun, then approve a complete run.");
  }

  /** Human phrasing of what a run lost, for messages that must not read as behaviour change. */
  private static String describe(TraceLoss loss) {
    var parts = new java.util.ArrayList<String>();
    if (loss.droppedEvents() > 0) {
      parts.add(loss.droppedEvents() + " events dropped");
    }
    if (loss.refusedScopes() > 0) {
      parts.add(loss.refusedScopes() + " async scopes not adopted");
    }
    return String.join(", ", parts);
  }

  /**
   * The committed baseline's location for one test: {@code
   * <approvedDir>/<SimpleClassName>/<method_slug>.approved.nt} — the same class-directory and slug
   * rules as every other per-test artifact, so baseline and build artifact line up by name.
   */
  public static Path approvedFile(Path approvedDir, String testClassName, String testMethodName) {
    return OutputDirectoryResolver.classDirectory(approvedDir, testClassName)
        .resolve(OutputDirectoryResolver.toFileSlug(testMethodName) + ".approved.nt");
  }

  /** Where a lossy run's structure goes: readable, comparable by hand, and never promotable. */
  private static Path incompleteSibling(Path approvedFile) {
    var name = approvedFile.getFileName().toString().replace(".approved.nt", ".incomplete.nt");
    return approvedFile.resolveSibling(name);
  }

  private static Path receivedSibling(Path approvedFile) {
    var name = approvedFile.getFileName().toString().replace(".approved.nt", ".received.nt");
    return approvedFile.resolveSibling(name);
  }

  /**
   * Promotes every {@code *.received.nt} under the root to its {@code *.approved.nt} baseline — the
   * whole of "approving": reviewed received files become the new contract. Backs the Gradle {@code
   * approveNarratives} task and works standalone for non-Gradle builds.
   *
   * @return the approved files written, in no guaranteed order; empty when there is nothing to
   *     promote or the root does not exist yet
   */
  public static List<Path> promoteReceived(Path root) throws IOException {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    List<Path> received;
    try (var files = Files.walk(root)) {
      received = files.filter(f -> f.getFileName().toString().endsWith(".received.nt")).toList();
    }
    var promoted = new ArrayList<Path>();
    for (var file : received) {
      var approved =
          file.resolveSibling(
              file.getFileName().toString().replace(".received.nt", ".approved.nt"));
      Files.move(file, approved, StandardCopyOption.REPLACE_EXISTING);
      promoted.add(approved);
    }
    return promoted;
  }
}
