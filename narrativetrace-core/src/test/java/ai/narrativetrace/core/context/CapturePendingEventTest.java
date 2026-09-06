/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.pipeline.EventPipeline;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A capture waits for events that are published but not yet drained.
 *
 * <p>INTENT: The ring drain stops at the first slot a producer has claimed and not yet written, so
 * under concurrent publishing a single {@code flush()} can leave the calling thread's own events
 * outstanding — and the capture taken then omits them silently, which is the one thing a
 * best-effort path may not do. Measured with the fork helper: collecting 512 workers on 8 threads
 * lost 17 and 21 of them in two runs out of eight, with the deficit exactly the number of
 * collections that saw no events at all.
 *
 * <p><b>@llmNote</b> The race is real but not schedulable, so the pipeline here makes it
 * deterministic: each {@code flush()} delivers exactly one pending event, which is what a drain
 * that stops early does. One flush is not enough to see a completed call; the capture must ask
 * whether the pipeline is drained and flush again.
 */
class CapturePendingEventTest {

  /**
   * A pipeline whose flush delivers one event at a time, like a drain stopping at a claimed slot.
   */
  private static final class OneAtATimePipeline implements EventPipeline {
    private final Deque<TraceEvent> pending = new ArrayDeque<>();
    private final List<TraceEvent> visible = new ArrayList<>();
    private int flushes;

    @Override
    public void publish(TraceEvent event) {
      pending.add(event);
    }

    @Override
    public void flush() {
      flushes++;
      if (!pending.isEmpty()) {
        visible.add(pending.poll());
      }
    }

    @Override
    public boolean drained() {
      return pending.isEmpty();
    }

    @Override
    public List<TraceEvent> events() {
      return List.copyOf(visible);
    }

    @Override
    public void close() {
      pending.clear();
      visible.clear();
    }
  }

  @Test
  void aCallWhoseExitIsStillInFlightIsCapturedComplete() {
    var pipeline = new OneAtATimePipeline();
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
    var span = context.enterMethod(new MethodSignature("Service", "call", List.of()));
    context.exitMethodWithReturn("\"ok\"", span);

    var tree = context.captureTrace();

    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome())
        .as("one flush sees only the enter; the capture must wait for the exit it published")
        .isInstanceOf(TraceOutcome.Returned.class);
    assertThat(pipeline.flushes).isGreaterThan(1);
  }

  @Test
  void aCaptureWithNothingInFlightFlushesExactlyOnce() {
    var pipeline = new OneAtATimePipeline();
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);

    context.captureTrace();

    assertThat(pipeline.flushes)
        .as("the common case — a drained pipeline — pays nothing for the wait")
        .isOne();
  }
}
