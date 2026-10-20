/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/CostRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;

/**
 * Proves documentation/recipes/trace-in-production-without-the-cost.md: the SAME declined-payment
 * run, captured once at {@code DETAIL} and once at {@code ERRORS}, writes the two artifacts that
 * page embeds — and what {@code ERRORS} drops is every call that returned normally (rule 8, docs as
 * tests).
 *
 * <p><b>@llmNote</b> The scenario is {@link DeclinedPaymentFixture}'s fixed reproduction, reused
 * rather than rewritten: this page's subject is the level dial, not another flow, and a reader
 * comparing the two levels should be comparing one run's renderings. Only the passing method is
 * selected — the fixture's other test fails by design for
 * documentation/recipes/help-me-debug-and-fix-this-bug.md.
 */
class CostRecipeTest {

  private static final Path PAGE_OUTPUT =
      Path.of("build/narrativetrace/recipes/trace-in-production-without-the-cost");

  private static final String SCENARIO = "the_stock_is_released_when_the_card_is_declined";

  @Test
  void errors_keeps_the_failed_run_whole_and_writes_nothing_for_a_successful_one()
      throws IOException {
    var errors = capture("errors", "ERRORS");

    assertThat(errors)
        .contains(
            "OrderService.placeOrder",
            "InventoryService.reserve",
            "PaymentService.charge",
            "InventoryService.release");
    // The control: the same path IS written when the level keeps successful runs, so the
    // absence below is the level's doing and not a path that was never right.
    assertThat(successfulRunAt(null)).exists();
    assertThat(successfulRunAt("ERRORS")).doesNotExist();
  }

  @Test
  void detail_carries_the_values_errors_suppresses() throws IOException {
    assertThat(capture("detail", null)).contains("customerId: `\"C-1234\"`");
    assertThat(capture("errors", "ERRORS")).contains("customerId: …").doesNotContain("\"C-1234\"");
  }

  @Test
  void both_levels_still_redact_the_card_number() throws IOException {
    assertThat(capture("detail", null)).contains("[REDACTED]").doesNotContain("4000000000000002");
    assertThat(capture("errors", "ERRORS"))
        .contains("[REDACTED]")
        .doesNotContain("4000000000000002");
  }

  /**
   * The successful checkout at [level]: at ERRORS nothing failed, so there is no trace to write.
   */
  private static Path successfulRunAt(String level) {
    var outputDir = PAGE_OUTPUT.resolve(level == null ? "detail-successful" : "errors-successful");
    var previous = System.getProperty("narrativetrace.level");
    setOrClear(level);
    try {
      var run =
          RecipeRuns.run(DiscoverySelectors.selectClass(CheckoutReviewFixture.class), outputDir);
      assertThat(run.getTestsFailedCount()).isZero();
    } finally {
      setOrClear(previous);
    }
    return outputDir.resolve("traces/CheckoutReviewFixture/a_customer_buys_two_keyboards.md");
  }

  /** Runs the reproduction into its own directory, at [level] (null leaves the default, DETAIL). */
  private static String capture(String directory, String level) throws IOException {
    var outputDir = PAGE_OUTPUT.resolve(directory);
    var previous = System.getProperty("narrativetrace.level");
    setOrClear(level);
    try {
      var run =
          RecipeRuns.run(
              DiscoverySelectors.selectMethod(
                  DeclinedPaymentFixture.class, SCENARIO, NarrativeContext.class.getName()),
              outputDir);
      assertThat(run.getTestsFailedCount()).isZero();
    } finally {
      setOrClear(previous);
    }
    return Files.readString(outputDir.resolve("traces/DeclinedPaymentFixture/" + SCENARIO + ".md"));
  }

  private static void setOrClear(String level) {
    if (level == null) {
      System.clearProperty("narrativetrace.level");
    } else {
      System.setProperty("narrativetrace.level", level);
    }
  }
}
