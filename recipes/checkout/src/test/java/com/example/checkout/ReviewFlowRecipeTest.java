/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/ReviewFlowRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;

/**
 * Proves documentation/recipes/review-a-complex-flow-before-changing-it.md: the same checkout,
 * captured once at DETAIL and once at SUMMARY, writes the artifacts that page embeds — and the
 * value-free one carries no values (rule 8, docs as tests).
 */
class ReviewFlowRecipeTest {

  private static final Path PAGE_OUTPUT =
      Path.of("build/narrativetrace/recipes/review-a-complex-flow-before-changing-it");

  private static final String SCENARIO = "a_customer_buys_two_keyboards";

  @Test
  void detail_carries_the_values_and_summary_carries_the_shape() throws IOException {
    var detail = capture("detail", null);
    var summary = capture("summary", "SUMMARY");

    assertThat(detail).contains("quantity: `2`", "amountCents: `7998`", "[REDACTED]");
    assertThat(summary).doesNotContain("amountCents: `7998`");
  }

  @Test
  void the_structural_trace_of_the_same_run_carries_no_values() throws IOException {
    capture("detail", null);

    var structural =
        Files.readString(
            PAGE_OUTPUT.resolve("detail/structural/CheckoutReviewFixture/" + SCENARIO + ".nt"));

    assertThat(structural)
        .contains("OrderService.placeOrder(customerId, sku, quantity, cardNumber)");
    assertThat(structural).doesNotContain("C-1234", "SKU-KB", "7998", "REDACTED");
  }

  /** Runs the fixture into its own directory, at [level] (null leaves the default, DETAIL). */
  private static String capture(String directory, String level) throws IOException {
    var outputDir = PAGE_OUTPUT.resolve(directory);
    var previous = System.getProperty("narrativetrace.level");
    setOrClear(level);
    try {
      var run =
          RecipeRuns.run(DiscoverySelectors.selectClass(CheckoutReviewFixture.class), outputDir);
      assertThat(run.getTestsFailedCount()).isZero();
    } finally {
      setOrClear(previous);
    }
    return Files.readString(outputDir.resolve("traces/CheckoutReviewFixture/" + SCENARIO + ".md"));
  }

  private static void setOrClear(String level) {
    if (level == null) {
      System.clearProperty("narrativetrace.level");
    } else {
      System.setProperty("narrativetrace.level", level);
    }
  }
}
