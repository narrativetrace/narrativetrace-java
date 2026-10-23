/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.spi.NamedTrace;
import ai.narrativetrace.api.spi.ReportContributor;
import ai.narrativetrace.api.spi.RunListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The run's boundary hooks under TestNG: every discovered {@link RunListener} hears the run's
 * identity on each test thread and its end once; every discovered {@link ReportContributor}
 * receives the run's traces — each isolated, so a broken one never fails the run.
 */
class RunHooksTest {

  /** Declared in {@code META-INF/services}; records what the hook hands it. */
  public static class RecordingRunListener implements RunListener {
    static final List<String> STARTED_NAMES = new CopyOnWriteArrayList<>();
    static final AtomicInteger ENDED = new AtomicInteger();

    @Override
    public void runStarted(String runId, String runName) {
      STARTED_NAMES.add(runName);
    }

    @Override
    public void runEnded() {
      ENDED.incrementAndGet();
    }
  }

  /** Also declared; proves one failing listener does not suppress its neighbours or the run. */
  public static class ThrowingRunListener implements RunListener {
    static final AtomicInteger STARTED = new AtomicInteger();

    @Override
    public void runStarted(String runId, String runName) {
      STARTED.incrementAndGet();
      throw new IllegalStateException("deliberately broken run listener");
    }

    @Override
    public void runEnded() {
      throw new IllegalStateException("deliberately broken run listener");
    }
  }

  /** Declared in {@code META-INF/services}; records what the hook hands it. */
  public static class RecordingContributor implements ReportContributor {
    static final List<NamedTrace> RECEIVED = new CopyOnWriteArrayList<>();
    static final java.util.concurrent.atomic.AtomicReference<Path> LAST_OUTPUT_DIR =
        new java.util.concurrent.atomic.AtomicReference<>();

    @Override
    public void contribute(List<NamedTrace> traces, Path directory) {
      RECEIVED.addAll(traces);
      LAST_OUTPUT_DIR.set(directory);
    }
  }

  /** Also declared; proves one failing contributor does not suppress its neighbours or the run. */
  public static class ThrowingContributor implements ReportContributor {
    static final AtomicInteger INVOCATIONS = new AtomicInteger();

    @Override
    public void contribute(List<NamedTrace> traces, Path directory) {
      INVOCATIONS.incrementAndGet();
      throw new IllegalStateException("deliberately broken contributor");
    }
  }

  @BeforeEach
  void resetRecorders() {
    RecordingRunListener.STARTED_NAMES.clear();
    RecordingRunListener.ENDED.set(0);
    ThrowingRunListener.STARTED.set(0);
    RecordingContributor.RECEIVED.clear();
    RecordingContributor.LAST_OUTPUT_DIR.set(Path.of("unset"));
    ThrowingContributor.INVOCATIONS.set(0);
  }

  @Test
  void runListenersHearStartAndEnd(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(RecordingRunListener.STARTED_NAMES).hasSize(3);
    assertThat(RecordingRunListener.STARTED_NAMES.stream().distinct()).hasSize(1);
    assertThat(RecordingRunListener.ENDED).hasValue(1);
  }

  @Test
  void aThrowingRunListenerIsSkipped(@TempDir Path outputDir) throws Exception {
    var run = TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(ThrowingRunListener.STARTED).hasValue(3);
    assertThat(run.failed()).isFalse();
    assertThat(run.err()).contains("failed on runStarted and was skipped");
    assertThat(run.err()).contains("failed on runEnded and was skipped");
  }

  @Test
  void reportContributorsReceiveEveryTrace(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(RecordingContributor.RECEIVED)
        .extracting(NamedTrace::name)
        .containsExactlyInAnyOrder("Customer places order", "Customer cancels order");
    assertThat(RecordingContributor.LAST_OUTPUT_DIR.get()).isEqualTo(outputDir);
  }

  @Test
  void aThrowingContributorIsSkipped(@TempDir Path outputDir) throws Exception {
    var run = TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(ThrowingContributor.INVOCATIONS).hasValue(1);
    assertThat(RecordingContributor.RECEIVED).hasSize(2);
    assertThat(run.err()).contains("failed and was skipped");
    assertThat(run.failed()).isFalse();
  }

  @Test
  void theRunNameIsTheSameInFrontmatterFooterAndManifest(@TempDir Path outputDir) throws Exception {
    var run = TestNgRuns.run(outputDir, OrderFixture.class);

    var name = runName(run.out());
    assertThat(TestNgRuns.trace(outputDir, "OrderFixture", "customer_places_order"))
        .contains("run: " + name);
    assertThat(Files.readString(outputDir.resolve("manifest.json"))).contains(name);
  }

  @Test
  void twoRunsGetDifferentRunNames(@TempDir Path one, @TempDir Path two) throws Exception {
    var first = runName(TestNgRuns.run(one, OrderFixture.class).out());
    var second = runName(TestNgRuns.run(two, OrderFixture.class).out());

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void theDiscoveryKillSwitchSuppressesContributorsWithoutAffectingTheRun(@TempDir Path outputDir)
      throws Exception {
    var run =
        TestNgRuns.run(
            outputDir, Map.of("narrativetrace.discovery", "off"), testng -> {}, OrderFixture.class);

    assertThat(RecordingContributor.RECEIVED).isEmpty();
    assertThat(ThrowingContributor.INVOCATIONS).hasValue(0);
    assertThat(outputDir.resolve("manifest.json")).exists();
    assertThat(run.failed()).isFalse();
  }

  private static String runName(String console) {
    return console
        .lines()
        .map(String::strip)
        .filter(line -> line.startsWith("run: "))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no run line in:\n" + console))
        .substring("run: ".length());
  }
}
