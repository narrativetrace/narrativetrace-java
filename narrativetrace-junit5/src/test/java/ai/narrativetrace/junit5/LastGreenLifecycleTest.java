/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.output.NarrativeApproval;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * The last-green artifact's lifecycle: it advances only when the test passed <em>and</em> its
 * approval was accepted.
 *
 * <p>Regression for the 2026-09-08 agent evaluation's second finding — a rejected change landed in
 * the generated baseline anyway, so reverting it then reported a removal "since last green" that
 * never happened.
 */
class LastGreenLifecycleTest {

  private static final String STRUCTURAL = "structural/ApprovalDriftFixture/booking_is_stored.nt";

  @AfterEach
  void resetFixture() {
    ApprovalDriftFixture.extraQuery = false;
  }

  @Test
  void anApprovalRejectionLeavesTheLastGreenArtifactWhereItWas(
      @TempDir Path outputDir, @TempDir Path approvedDir) throws IOException {
    var lastGreen = establishBaseline(outputDir, approvedDir);
    assertThat(lastGreen).doesNotContain("availableUnits");

    ApprovalDriftFixture.extraQuery = true;
    var rejected = runFixture(outputDir, approvedDir);

    assertThat(rejected.getTestsFailedCount()).isEqualTo(1);
    assertThat(rejected.getFailures().get(0).getException())
        .hasMessageContaining("Narrative changed against the approved baseline");
    assertThat(Files.readString(outputDir.resolve(STRUCTURAL))).isEqualTo(lastGreen);
  }

  @Test
  void revertingARejectedChangeReportsNoDelta(@TempDir Path outputDir, @TempDir Path approvedDir)
      throws IOException {
    establishBaseline(outputDir, approvedDir);
    ApprovalDriftFixture.extraQuery = true;
    runFixture(outputDir, approvedDir);

    ApprovalDriftFixture.extraQuery = false;
    var console = captureConsole(() -> runFixture(outputDir, approvedDir));

    assertThat(console).contains("Since last green: 1 scenario unchanged");
    assertThat(console).doesNotContain("availableUnits");
  }

  /**
   * A rejected run's artifacts still say what happened — only the <em>baseline</em> is held back.
   * The narrative is the reviewer's evidence for the rejection, and it records the run as failed.
   */
  @Test
  void aRejectedRunStillWritesItsOwnNarrative(@TempDir Path outputDir, @TempDir Path approvedDir)
      throws IOException {
    establishBaseline(outputDir, approvedDir);

    ApprovalDriftFixture.extraQuery = true;
    runFixture(outputDir, approvedDir);

    var narrative =
        Files.readString(outputDir.resolve("traces/ApprovalDriftFixture/booking_is_stored.md"));
    assertThat(narrative).contains("InventoryService.availableUnits");
    assertThat(narrative).contains("**Result:** FAILED");
  }

  /** A run whose test passes and whose approval is accepted advances the baseline, as before. */
  @Test
  void anAcceptedChangeStillAdvancesTheLastGreenArtifact(
      @TempDir Path outputDir, @TempDir Path approvedDir) throws IOException {
    establishBaseline(outputDir, approvedDir);

    ApprovalDriftFixture.extraQuery = true;
    runFixture(outputDir, approvedDir);
    NarrativeApproval.promoteReceived(approvedDir);
    var accepted = runFixture(outputDir, approvedDir);

    assertThat(accepted.getTestsFailedCount()).isZero();
    assertThat(Files.readString(outputDir.resolve(STRUCTURAL))).contains("availableUnits");
  }

  /** Records the baseline the way a consumer does: run, review the received file, promote it. */
  private static String establishBaseline(Path outputDir, Path approvedDir) throws IOException {
    ApprovalDriftFixture.extraQuery = false;
    runFixture(outputDir, approvedDir);
    NarrativeApproval.promoteReceived(approvedDir);
    runFixture(outputDir, approvedDir);
    return Files.readString(outputDir.resolve(STRUCTURAL));
  }

  private static TestExecutionSummary runFixture(Path outputDir, Path approvedDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", outputDir.toString());
    System.setProperty("narrativetrace.approval", "true");
    System.setProperty("narrativetrace.approvedDir", approvedDir.toString());
    try {
      var request =
          LauncherDiscoveryRequestBuilder.request()
              .selectors(DiscoverySelectors.selectClass(ApprovalDriftFixture.class));
      var listener = new SummaryGeneratingListener();
      LauncherFactory.create().execute(request.build(), listener);
      return listener.getSummary();
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
      System.clearProperty("narrativetrace.approval");
      System.clearProperty("narrativetrace.approvedDir");
    }
  }

  private static String captureConsole(Runnable body) {
    var captured = new ByteArrayOutputStream();
    var original = System.out;
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      body.run();
    } finally {
      System.setOut(original);
    }
    return captured.toString(StandardCharsets.UTF_8);
  }
}
