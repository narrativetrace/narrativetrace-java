/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Reads what the {@code testngSample} task wrote when Gradle's OWN TestNG runner ran the sample
 * suite with nothing but the dependency on the classpath — the adopter's whole wiring, proven under
 * the runner they actually use rather than a hand-built {@code TestNG} instance.
 */
class GradleTestNgSampleTest {

  private static final Path OUTPUT = Path.of(System.getProperty("narrativetrace.testngSampleDir"));

  @Test
  void gradlesTestNgRunnerWroteOneTracePerTestInvocation() throws Exception {
    assertThat(TestNgRuns.listing(OUTPUT))
        .contains(
            "traces/SampleCheckoutTest/cart_is_priced.md",
            "traces/SampleCheckoutTest/coupon_is_applied-001.md",
            "traces/SampleCheckoutTest/coupon_is_applied-002.md",
            "traces/SampleCheckoutTest/payment_is_captured.md",
            "manifest.json",
            "INDEX.md");
  }

  @Test
  void theIndexListsTheFailingTestFirst() throws Exception {
    var index = Files.readString(OUTPUT.resolve("INDEX.md"));

    var failing = index.indexOf("## Failing");
    var passing = index.indexOf("## Passing");
    assertThat(failing).isPositive().isLessThan(passing);
    assertThat(index.substring(failing, passing))
        .contains("SampleCheckoutTest.paymentIsCaptured | failed |")
        .contains("the payment was not captured");
  }

  @Test
  void theFailingTestNamedItsTraceOnStandardError() throws Exception {
    var results = OUTPUT.resolveSibling("test-results/testngSample");
    String xml;
    try (Stream<Path> files = Files.list(results)) {
      var report =
          files
              .filter(f -> f.getFileName().toString().endsWith(".xml"))
              .filter(f -> f.getFileName().toString().contains("SampleCheckoutTest"))
              .findFirst()
              .orElseThrow();
      xml = Files.readString(report);
    }

    assertThat(xml)
        .contains(
            "narrativetrace: trace of SampleCheckoutTest.paymentIsCaptured → "
                + OUTPUT.resolve("traces/SampleCheckoutTest/payment_is_captured.md"));
  }
}
