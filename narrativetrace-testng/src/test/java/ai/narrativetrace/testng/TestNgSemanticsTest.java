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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where TestNG's lifecycle differs from JUnit's, the listener still gives each test exactly its own
 * calls: set-up belongs to the test it prepares, tear-down never reaches the store, an expected
 * exception is a pass, and a skipped test writes nothing.
 */
class TestNgSemanticsTest {

  @Test
  void beforeMethodCallsBelongToTheTest(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, LifecycleFixture.class);

    assertThat(TestNgRuns.trace(outputDir, "LifecycleFixture", "order_is_placed"))
        .contains("FixtureService.prepare")
        .contains("OrderService.placeOrder");
  }

  @Test
  void afterMethodCallsNeverReachTheStore(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, LifecycleFixture.class);

    assertThat(TestNgRuns.trace(outputDir, "LifecycleFixture", "order_is_placed"))
        .doesNotContain("FixtureService.clean");
    assertThat(storedEvents()).as("events a tear-down traced, left in the shared store").isEmpty();
  }

  @Test
  void anExpectedExceptionCountsAsPassed(@TempDir Path outputDir) throws Exception {
    var run = TestNgRuns.run(outputDir, ExpectedExceptionFixture.class);

    assertThat(run.failed()).isFalse();
    assertThat(run.err()).doesNotContain("narrativetrace: trace of");
    assertThat(Files.readString(outputDir.resolve("manifest.json")))
        .contains("\"outcome\": \"passed\"")
        .doesNotContain("\"outcome\": \"failed\"");
  }

  @Test
  void aSkippedTestWritesNothing(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, SkippedFixture.class);

    assertThat(TestNgRuns.listing(outputDir))
        .contains("traces/SkippedFixture/payment_is_taken.md")
        .noneMatch(file -> file.contains("receipt_is_sent"));
    assertThat(Files.readString(outputDir.resolve("manifest.json")))
        .doesNotContain("receiptIsSent");
  }

  @Test
  void aTestSkippedByAFailingBeforeMethodWritesNothingAndLeavesNothing(@TempDir Path outputDir)
      throws Exception {
    TestNgRuns.run(outputDir, BrokenSetupFixture.class);

    assertThat(TestNgRuns.listing(outputDir)).noneMatch(file -> file.contains("order_is_stored"));
    assertThat(storedEvents()).isEmpty();
  }

  /** What the shared store holds once the ring is drained into it — a stuck event cannot hide. */
  private static java.util.List<ai.narrativetrace.api.event.TraceEvent> storedEvents() {
    var pipeline = SharedPipeline.shared().pipeline();
    pipeline.flush();
    return pipeline.events();
  }
}
