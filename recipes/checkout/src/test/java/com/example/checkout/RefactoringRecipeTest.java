/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/RefactoringRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Proves documentation/recipes/review-a-refactoring-did-behaviour-change.md: the committed baseline
 * still matches the shipped checkout, and the refactoring that drops the release is rejected by a
 * diff that names the dropped call — while the test's own assertions still pass (rule 8, docs as
 * tests).
 *
 * <p><b>@llmNote</b> The approved directory is a FRESH copy under {@code build/}, named by a
 * relative path, for two reasons: an approval mismatch writes a {@code .received.nt} beside the
 * baseline and a verification task may never write where the repository lives, and the rejection
 * message the page embeds quotes that path — an absolute or temporary one would differ on every
 * machine and every run. Each test owns its own copy, so neither can delete the other's evidence.
 */
class RefactoringRecipeTest {

  private static final Path PAGE_OUTPUT =
      Path.of("build/narrativetrace/recipes/review-a-refactoring-did-behaviour-change");

  private static final String SCENARIO = "a_declined_card_saves_no_order";
  private static final String FIXTURE = "RefactoredCheckoutFixture";

  private static final Path COMMITTED_BASELINE =
      Path.of("src/test/narratives/" + FIXTURE + "/" + SCENARIO + ".approved.nt");

  @AfterEach
  void restoreTheShippedCheckout() {
    RefactoredCheckoutFixture.refactoringDroppedTheRelease = false;
  }

  @Test
  void the_committed_baseline_matches_the_shipped_checkout(@TempDir Path outputDir)
      throws IOException {
    var approvedDir = freshApprovedDir("approved-as-shipped");

    var summary = runUnderApproval(outputDir, approvedDir);

    assertThat(summary.getTestsFailedCount()).isZero();
    assertThat(received(approvedDir)).doesNotExist();
  }

  @Test
  void dropping_the_release_is_rejected_by_a_diff_that_names_it(@TempDir Path outputDir)
      throws IOException {
    var approvedDir = freshApprovedDir("approved-after-the-refactoring");
    RefactoredCheckoutFixture.refactoringDroppedTheRelease = true;

    var summary = runUnderApproval(outputDir, approvedDir);

    assertThat(summary.getTestsFailedCount()).isEqualTo(1);
    var rejection = summary.getFailures().get(0).getException().getMessage();
    assertThat(rejection).contains("InventoryService.release");
    assertThat(received(approvedDir)).exists();
    Files.writeString(PAGE_OUTPUT.resolve("approval-rejection.txt"), rejection.strip() + "\n");
  }

  private static TestExecutionSummary runUnderApproval(Path outputDir, Path approvedDir) {
    return RecipeRuns.runUnderApproval(
        DiscoverySelectors.selectClass(RefactoredCheckoutFixture.class), outputDir, approvedDir);
  }

  /** The committed baseline, alone, in a directory of this test's own — emptied first. */
  private static Path freshApprovedDir(String name) throws IOException {
    var dir = PAGE_OUTPUT.resolve(name);
    deleteRecursively(dir);
    var baselineDir = dir.resolve(FIXTURE);
    Files.createDirectories(baselineDir);
    Files.copy(COMMITTED_BASELINE, baselineDir.resolve(COMMITTED_BASELINE.getFileName()));
    return dir;
  }

  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }

  private static Path received(Path approvedDir) {
    return approvedDir.resolve(FIXTURE).resolve(SCENARIO + ".received.nt");
  }
}
