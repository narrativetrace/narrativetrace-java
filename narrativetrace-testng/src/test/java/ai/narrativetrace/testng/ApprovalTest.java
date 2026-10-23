/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.output.NarrativeApproval;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Approval traces ({@code narrativetrace.approval=true}) under TestNG: an unapproved structure
 * fails the test until its {@code .received.nt} is promoted to {@code .approved.nt}, and the
 * last-green artifact advances only on a run that is green in full.
 */
class ApprovalTest {

  private static final String STRUCTURAL = "structural/ApprovalDriftFixture/booking_is_stored.nt";

  @AfterEach
  void resetFixtures() {
    ApprovalDriftFixture.extraQuery = false;
  }

  @Test
  void approvalModeFailsAnUnapprovedScenarioUntilPromoted(
      @TempDir Path outputDir, @TempDir Path approvedDir) throws IOException {
    var unapproved = runApproving(outputDir, approvedDir, OrderFixture.class);

    assertThat(unapproved.failures()).hasSize(2);
    assertThat(unapproved.failureMessages()).allMatch(m -> m.contains("No approved narrative"));
    assertThat(receivedFilesUnder(approvedDir)).hasSize(2);

    NarrativeApproval.promoteReceived(approvedDir);
    var approved = runApproving(outputDir, approvedDir, OrderFixture.class);

    assertThat(approved.failures()).isEmpty();
    assertThat(receivedFilesUnder(approvedDir)).isEmpty();
  }

  @Test
  void aScenarioWithoutTraceIsNeverJudged(@TempDir Path outputDir, @TempDir Path approvedDir)
      throws IOException {
    var run = runApproving(outputDir, approvedDir, OrderFixture.class);

    assertThat(run.failures())
        .extracting(r -> r.getMethod().getMethodName())
        .doesNotContain("quietTest");
  }

  @Test
  void aRejectionLeavesTheLastGreenArtifactWhereItWas(
      @TempDir Path outputDir, @TempDir Path approvedDir) throws IOException {
    var lastGreen = establishBaseline(outputDir, approvedDir);
    assertThat(lastGreen).doesNotContain("availableUnits");

    ApprovalDriftFixture.extraQuery = true;
    var rejected = runApproving(outputDir, approvedDir, ApprovalDriftFixture.class);

    assertThat(rejected.failureMessages())
        .singleElement()
        .asString()
        .contains("Narrative changed against the approved baseline")
        .contains("InventoryService.availableUnits");
    assertThat(Files.readString(outputDir.resolve(STRUCTURAL))).isEqualTo(lastGreen);
  }

  @Test
  void aRejectedRunStillWritesItsOwnNarrativeAndPointsAtIt(
      @TempDir Path outputDir, @TempDir Path approvedDir) throws IOException {
    establishBaseline(outputDir, approvedDir);

    ApprovalDriftFixture.extraQuery = true;
    var rejected = runApproving(outputDir, approvedDir, ApprovalDriftFixture.class);

    var narrative =
        Files.readString(outputDir.resolve("traces/ApprovalDriftFixture/booking_is_stored.md"));
    assertThat(narrative).contains("InventoryService.availableUnits").contains("FAILED");
    assertThat(rejected.err())
        .contains("narrativetrace: trace of ApprovalDriftFixture.bookingIsStored → ");
  }

  @Test
  void anAcceptedChangeAdvancesTheLastGreenArtifact(
      @TempDir Path outputDir, @TempDir Path approvedDir) throws IOException {
    establishBaseline(outputDir, approvedDir);

    ApprovalDriftFixture.extraQuery = true;
    runApproving(outputDir, approvedDir, ApprovalDriftFixture.class);
    NarrativeApproval.promoteReceived(approvedDir);
    var accepted = runApproving(outputDir, approvedDir, ApprovalDriftFixture.class);

    assertThat(accepted.failures()).isEmpty();
    assertThat(Files.readString(outputDir.resolve(STRUCTURAL))).contains("availableUnits");
  }

  @Test
  void anInaccessibleBaselineFailsTheTestNamingIt(@TempDir Path outputDir, @TempDir Path root)
      throws IOException {
    var notADirectory = Files.writeString(root.resolve("narratives"), "a file, not a directory");

    var run = runApproving(outputDir, notADirectory, ApprovalDriftFixture.class);

    assertThat(run.failureMessages())
        .singleElement()
        .asString()
        .contains("Narrative approval could not access the baseline");
  }

  private static String establishBaseline(Path outputDir, Path approvedDir) throws IOException {
    ApprovalDriftFixture.extraQuery = false;
    runApproving(outputDir, approvedDir, ApprovalDriftFixture.class);
    NarrativeApproval.promoteReceived(approvedDir);
    runApproving(outputDir, approvedDir, ApprovalDriftFixture.class);
    return Files.readString(outputDir.resolve(STRUCTURAL));
  }

  private static TestNgRuns.Run runApproving(Path outputDir, Path approvedDir, Class<?> fixture)
      throws IOException {
    return TestNgRuns.run(
        outputDir,
        Map.of(
            "narrativetrace.approval",
            "true",
            "narrativetrace.approvedDir",
            approvedDir.toString()),
        testng -> {},
        fixture);
  }

  private static List<Path> receivedFilesUnder(Path dir) throws IOException {
    try (var files = Files.walk(dir)) {
      return files.filter(f -> f.getFileName().toString().endsWith(".received.nt")).toList();
    }
  }
}
