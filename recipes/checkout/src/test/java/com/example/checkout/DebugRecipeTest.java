/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/DebugRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;

/**
 * Proves documentation/recipes/help-me-debug-and-fix-this-bug.md: the page's "before" trace comes
 * from a run that really failed, its "after" trace from the same reproduction passing against the
 * fix, and the committed {@code .approved.nt} still matches the fixed flow (rule 8, docs as tests).
 */
class DebugRecipeTest {

  private static final Path PAGE_OUTPUT =
      Path.of("build/narrativetrace/recipes/help-me-debug-and-fix-this-bug");

  private static final String FIXED = "the_stock_is_released_when_the_card_is_declined";
  private static final String BEFORE_THE_FIX = FIXED + "_before_the_fix";

  private static final Path COMMITTED_BASELINE =
      Path.of("src/test/narratives/DeclinedPaymentFixture/" + FIXED + ".approved.nt");

  @Test
  void the_reproduction_fails_only_against_the_code_before_the_fix() throws IOException {
    var summary =
        RecipeRuns.run(DiscoverySelectors.selectClass(DeclinedPaymentFixture.class), PAGE_OUTPUT);

    assertThat(summary.getTestsSucceededCount()).isEqualTo(1);
    assertThat(summary.getTestsFailedCount()).isEqualTo(1);
    var failure = summary.getFailures().get(0);
    assertThat(failure.getTestIdentifier().getDisplayName()).contains("before_the_fix");
    assertThat(failure.getException()).hasMessageContaining("5");

    assertThat(narrative(BEFORE_THE_FIX)).contains("**Result:** FAILED");
    assertThat(narrative(BEFORE_THE_FIX)).doesNotContain("InventoryService.release");
    assertThat(narrative(FIXED)).contains("InventoryService.release");
  }

  @Test
  void the_committed_baseline_still_pins_the_fixed_flow(
      @TempDir Path outputDir, @TempDir Path approvedDir) throws IOException {
    var baselineDir = approvedDir.resolve("DeclinedPaymentFixture");
    Files.createDirectories(baselineDir);
    Files.copy(COMMITTED_BASELINE, baselineDir.resolve(COMMITTED_BASELINE.getFileName()));

    var summary =
        RecipeRuns.runUnderApproval(
            DiscoverySelectors.selectMethod(
                DeclinedPaymentFixture.class, FIXED, NarrativeContext.class.getName()),
            outputDir,
            approvedDir);

    assertThat(summary.getTestsFailedCount()).isZero();
    assertThat(summary.getTestsSucceededCount()).isEqualTo(1);
    try (Stream<Path> written = Files.walk(baselineDir)) {
      assertThat(written.filter(path -> path.toString().endsWith(".received.nt"))).isEmpty();
    }
  }

  private static String narrative(String scenario) throws IOException {
    return Files.readString(
        PAGE_OUTPUT.resolve("traces/DeclinedPaymentFixture/" + scenario + ".md"));
  }
}
