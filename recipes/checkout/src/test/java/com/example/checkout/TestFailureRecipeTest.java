/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/TestFailureRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;

/**
 * Proves documentation/recipes/why-did-this-test-fail.md: the page's assertion message and the
 * narrative beside it come from one run of a test that really failed (rule 8, docs as tests).
 *
 * <p><b>@llmNote</b> The assertion message is written to a file because it belongs to the RUNNER,
 * not to a trace artifact — it is the one thing on that page the extension does not write. Both
 * files land under {@code build/}, never in the source tree.
 */
class TestFailureRecipeTest {

  private static final Path PAGE_OUTPUT =
      Path.of("build/narrativetrace/recipes/why-did-this-test-fail");

  private static final String SCENARIO = "the_customer_gets_an_order_id_for_their_keyboards";

  @Test
  void the_failing_run_reports_the_assertion_and_writes_its_own_narrative() throws IOException {
    var summary =
        RecipeRuns.run(DiscoverySelectors.selectClass(StaleExpectationFixture.class), PAGE_OUTPUT);

    assertThat(summary.getTestsFailedCount()).isEqualTo(1);
    var message = summary.getFailures().get(0).getException().getMessage();
    Files.writeString(PAGE_OUTPUT.resolve("assertion-failure.txt"), message.strip() + "\n");

    var narrative =
        Files.readString(PAGE_OUTPUT.resolve("traces/StaleExpectationFixture/" + SCENARIO + ".md"));
    assertThat(message).contains("ORD-C-1234-SKU-KB-1", "ORD-C-1234-SKU-KB-2");
    assertThat(narrative).contains("**Result:** FAILED");
    assertThat(narrative).contains("quantity: `2`");
    assertThat(narrative).contains("amountCents: `7998`");
  }
}
