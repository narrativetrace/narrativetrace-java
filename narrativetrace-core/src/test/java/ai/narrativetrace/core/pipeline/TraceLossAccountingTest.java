/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a reader is told about loss is what actually happened.
 *
 * <p>INTENT: The 2026-09-01 bug hunt published 64 events into a 16-slot ring, captured 16, and read
 * {@code TraceLoss[droppedEvents=0]}. {@link ShedCountingTest} pins each mechanism at the buffer
 * and consumer level; this pins the number a user actually sees — {@link
 * ThreadLocalNarrativeContext#traceLoss()} — against the exact repro shape, end to end through a
 * context, because a short trace that reports no loss is indistinguishable from a complete one.
 */
class TraceLossAccountingTest {

  private static final int CAPACITY = 16;
  private static final int PAIRS = 32;

  @Test
  void aTraceThatOverflowedItsRingSaysHowMuchItLost() {
    try (var pipeline = new DualPathPipeline(null, new BufferedEventConsumer(CAPACITY, false))) {
      var context =
          new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL), pipeline);
      publishPairs(context, PAIRS);

      context.captureTrace();

      assertThat(context.traceLoss().droppedEvents())
          .as("%d events published into %d slots", PAIRS * 2, CAPACITY)
          .isEqualTo(PAIRS * 2L - CAPACITY);
      assertThat(pipeline.events()).hasSize(CAPACITY);
      context.reset();
    }
  }

  @Test
  void aTraceThatFitsInItsRingReportsNoLoss() {
    try (var pipeline = new DualPathPipeline(null, new BufferedEventConsumer(CAPACITY, false))) {
      var context =
          new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL), pipeline);
      publishPairs(context, CAPACITY / 2);

      context.captureTrace();

      assertThat(context.traceLoss().droppedEvents()).isZero();
      assertThat(context.traceLoss().any()).isFalse();
      context.reset();
    }
  }

  private static void publishPairs(ThreadLocalNarrativeContext context, int pairs) {
    for (int i = 0; i < pairs; i++) {
      context.enterMethod(new MethodSignature("OrderService", "call" + i, List.of()));
      context.exitMethodWithReturn("\"ok\"");
    }
  }
}
