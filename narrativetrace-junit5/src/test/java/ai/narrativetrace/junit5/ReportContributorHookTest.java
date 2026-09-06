/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.spi.NamedTrace;
import ai.narrativetrace.api.spi.ReportContributor;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.clarity.DomainVocabulary;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The suite-end seam: a contributor declared on the classpath receives every accumulated trace,
 * under the scenario names it was captured with, pointed at the run's output directory.
 */
class ReportContributorHookTest {

  @TempDir Path outputDir;

  /** Declared in {@code META-INF/services}; records what the hook hands it. */
  public static class RecordingContributor implements ReportContributor {
    static final List<NamedTrace> RECEIVED = new java.util.ArrayList<>();
    static Path lastOutputDir;

    @Override
    public void contribute(List<NamedTrace> traces, Path directory) {
      RECEIVED.addAll(traces);
      lastOutputDir = directory;
    }
  }

  /** Also declared; proves one failing contributor does not suppress its neighbours or the run. */
  public static class ThrowingContributor implements ReportContributor {
    static int invocations;

    @Override
    public void contribute(List<NamedTrace> traces, Path directory) {
      invocations++;
      throw new IllegalStateException("deliberately broken contributor");
    }
  }

  @BeforeEach
  void resetFixtures() {
    RecordingContributor.RECEIVED.clear();
    ThrowingContributor.invocations = 0;
  }

  @Test
  void handsEveryAccumulatedTraceToDiscoveredContributors() {
    var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();
    accumulator.contribute(
        tracesNamed("places an order"),
        List.of(),
        outputDir,
        GlossaryHarvestStep.disabled(),
        DomainVocabulary.empty());

    accumulator.close();

    assertThat(RecordingContributor.RECEIVED)
        .singleElement()
        .satisfies(
            named -> {
              assertThat(named.name()).isEqualTo("places an order");
              assertThat(named.tree().roots()).hasSize(1);
            });
    assertThat(RecordingContributor.lastOutputDir).isEqualTo(outputDir);
  }

  @Test
  void aContributorThatThrowsIsSkippedWithoutFailingTheRunOrItsNeighbours() {
    var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();
    accumulator.contribute(
        tracesNamed("survives"),
        List.of(),
        outputDir,
        GlossaryHarvestStep.disabled(),
        DomainVocabulary.empty());

    accumulator.close();

    assertThat(ThrowingContributor.invocations).isOne();
    assertThat(RecordingContributor.RECEIVED).hasSize(1);
  }

  @Test
  void contributorsAreNotCalledWhenTheSuiteCapturedNothing() {
    var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();

    accumulator.close();

    assertThat(RecordingContributor.RECEIVED).isEmpty();
  }

  @Test
  void discoveryKillSwitchSuppressesContributorsWithoutAffectingTheRun() {
    System.setProperty("narrativetrace.discovery", "off");
    try {
      var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();
      accumulator.contribute(
          tracesNamed("ignored"),
          List.of(),
          outputDir,
          GlossaryHarvestStep.disabled(),
          DomainVocabulary.empty());

      accumulator.close();

      assertThat(RecordingContributor.RECEIVED).isEmpty();
      assertThat(ThrowingContributor.invocations).isZero();
    } finally {
      System.clearProperty("narrativetrace.discovery");
    }
  }

  /**
   * A scenario with no usable name cannot be handed over as a {@link NamedTrace}. The run must
   * still finish and still write its own artifacts — contributors are additive, never load-bearing.
   */
  @Test
  void anUnusableScenarioNameSkipsContributorsInsteadOfFailingTheRun() {
    var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();
    accumulator.contribute(
        tracesNamed("   "),
        List.of(),
        outputDir,
        GlossaryHarvestStep.disabled(),
        DomainVocabulary.empty());

    accumulator.close();

    assertThat(RecordingContributor.RECEIVED).isEmpty();
    assertThat(ThrowingContributor.invocations).isZero();
  }

  private static Map<String, TraceTree> tracesNamed(String scenario) {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig());
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn("\"ok\"");
    var traces = new LinkedHashMap<String, TraceTree>();
    traces.put(scenario, context.captureTrace());
    return traces;
  }
}
