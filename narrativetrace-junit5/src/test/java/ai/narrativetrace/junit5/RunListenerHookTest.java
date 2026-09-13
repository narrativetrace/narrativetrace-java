/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.spi.RunListener;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The run's own boundary hook (2026-09-13 ruling, item 2): every discovered {@link RunListener} is
 * notified of this run's identity, isolated per listener — mirrors {@link
 * ReportContributorHookTest} for the equivalent report-contributor seam.
 */
class RunListenerHookTest {

  /** Declared in {@code META-INF/services}; records what the hook hands it. */
  public static class RecordingRunListener implements RunListener {
    static final List<String> STARTED_NAMES = new ArrayList<>();
    static int ended;

    @Override
    public void runStarted(String runId, String runName) {
      STARTED_NAMES.add(runName);
    }

    @Override
    public void runEnded() {
      ended++;
    }
  }

  /** Also declared; proves one failing listener does not suppress its neighbours or the run. */
  public static class ThrowingRunListener implements RunListener {
    static int startedInvocations;
    static int endedInvocations;

    @Override
    public void runStarted(String runId, String runName) {
      startedInvocations++;
      throw new IllegalStateException("deliberately broken run listener");
    }

    @Override
    public void runEnded() {
      endedInvocations++;
      throw new IllegalStateException("deliberately broken run listener");
    }
  }

  @BeforeEach
  void resetFixtures() {
    RecordingRunListener.STARTED_NAMES.clear();
    RecordingRunListener.ended = 0;
    ThrowingRunListener.startedInvocations = 0;
    ThrowingRunListener.endedInvocations = 0;
  }

  @Test
  void notifyRunStartedHandsTheRunIdentityToEveryDiscoveredListener() {
    var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();

    accumulator.notifyRunStarted();

    assertThat(RecordingRunListener.STARTED_NAMES)
        .containsExactly(accumulator.runIdentity().name());
  }

  @Test
  void aListenerThatThrowsOnRunStartedIsSkippedWithoutFailingTheOthers() {
    new NarrativeTraceExtension.GlobalTraceAccumulator().notifyRunStarted();

    assertThat(ThrowingRunListener.startedInvocations).isOne();
    assertThat(RecordingRunListener.STARTED_NAMES).hasSize(1);
  }

  @Test
  void notifyRunEndedReachesEveryDiscoveredListener() {
    new NarrativeTraceExtension.GlobalTraceAccumulator().notifyRunEnded();

    assertThat(RecordingRunListener.ended).isOne();
  }

  @Test
  void aListenerThatThrowsOnRunEndedIsSkippedWithoutFailingTheOthers() {
    new NarrativeTraceExtension.GlobalTraceAccumulator().notifyRunEnded();

    assertThat(ThrowingRunListener.endedInvocations).isOne();
    assertThat(RecordingRunListener.ended).isOne();
  }
}
