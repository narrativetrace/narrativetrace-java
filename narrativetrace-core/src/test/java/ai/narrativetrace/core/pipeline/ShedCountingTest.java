/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the buffer sheds must be counted, or the loud-shedding footer can never fire.
 *
 * <p>INTENT: {@code TraceLoss.droppedEvents} has always been documented as "events the bounded
 * buffer shed under load", but until 2026-08-31 the only thing that incremented it was a {@code
 * Flow} subscriber falling behind — a mode the default topology cannot even reach, because it has
 * no subscribers. The one loss mode the default wiring <em>does</em> produce, the ring overwriting
 * unconsumed slots, went uncounted. These tests pin all three modes.
 */
class ShedCountingTest {

  @Test
  void overwritingAnUnconsumedSlotIsCounted() {
    var buffer = new BoundedEventBuffer(4);

    for (int i = 0; i < 10; i++) {
      buffer.put(event());
    }

    var drained = new ArrayList<TraceEvent>();
    buffer.drain(drained::add);

    assertThat(drained).hasSize(4);
    assertThat(buffer.overwrittenCount()).isEqualTo(6);
  }

  @Test
  void aBufferWithRoomToSpareCountsNothing() {
    var buffer = new BoundedEventBuffer(8);

    for (int i = 0; i < 8; i++) {
      buffer.put(event());
    }
    buffer.drain(e -> {});

    assertThat(buffer.overwrittenCount()).isZero();
  }

  @Test
  void pollCountsTheOverwriteItSkipsPastJustAsDrainDoes() {
    var buffer = new BoundedEventBuffer(2);

    for (int i = 0; i < 7; i++) {
      buffer.put(event());
    }

    assertThat(buffer.poll()).isNotNull();
    assertThat(buffer.overwrittenCount()).isEqualTo(5);
  }

  @Test
  void overwritesAccumulateAcrossDrains() {
    var buffer = new BoundedEventBuffer(2);

    for (int i = 0; i < 5; i++) {
      buffer.put(event());
    }
    buffer.drain(e -> {});
    for (int i = 0; i < 6; i++) {
      buffer.put(event());
    }
    buffer.drain(e -> {});

    assertThat(buffer.overwrittenCount()).isEqualTo(7);
  }

  @Test
  void drainReportsHowManyEventsItDelivered() {
    var buffer = new BoundedEventBuffer(8);

    buffer.put(event());
    buffer.put(event());

    assertThat(buffer.drain(e -> {})).isEqualTo(2);
    assertThat(buffer.drain(e -> {})).isZero();
  }

  @Test
  void aDefaultTopologyReportsItsOverwritesAsDroppedEvents() {
    try (var consumer = new BufferedEventConsumer(4, false)) {
      for (int i = 0; i < 10; i++) {
        consumer.accept(event());
      }
      consumer.flush();

      assertThat(consumer.droppedCount()).isEqualTo(6);
      assertThat(consumer.events()).hasSize(4);
    }
  }

  @Test
  void aPipelineSurfacesTheConsumersDroppedCount() {
    try (var pipeline = new DualPathPipeline(null, new BufferedEventConsumer(4, false))) {
      for (int i = 0; i < 10; i++) {
        pipeline.publish(event());
      }
      pipeline.flush();

      assertThat(pipeline.droppedEventCount()).isEqualTo(6);
    }
  }

  @Test
  void aPipelineThatCannotShedReportsZero() {
    try (var pipeline = DualPathPipeline.narrationOnly(e -> {})) {
      pipeline.publish(event());

      assertThat(pipeline.droppedEventCount()).isZero();
    }
  }

  @Test
  void theAdaptiveDrainCountsWhatItDiscardsAboveTheSheddingThreshold() {
    try (var consumer = new BufferedEventConsumer(16, false)) {
      // 13 of 16 slots is 81% — above SHEDDING_THRESHOLD, below EMERGENCY_THRESHOLD.
      for (int i = 0; i < 13; i++) {
        consumer.accept(event());
      }

      consumer.drainCycle();

      assertThat(consumer.droppedCount()).isEqualTo(13);
      assertThat(consumer.events()).isEmpty();
    }
  }

  @Test
  void theAdaptiveDrainCountsWhatItDiscardsInEmergencyMode() {
    try (var consumer = new BufferedEventConsumer(16, false)) {
      for (int i = 0; i < 16; i++) {
        consumer.accept(event());
      }

      consumer.drainCycle();

      assertThat(consumer.droppedCount()).isEqualTo(16);
    }
  }

  @Test
  void aDrainBelowTheSheddingThresholdStoresRatherThanSheds() {
    try (var consumer = new BufferedEventConsumer(16, false)) {
      consumer.accept(event());

      consumer.drainCycle();

      assertThat(consumer.droppedCount()).isZero();
      assertThat(consumer.events()).hasSize(1);
    }
  }

  private static TraceEvent event() {
    var spanContext =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    return new TraceEvent.EnterEvent(
        spanContext, System.nanoTime(), new MethodSignature("S", "m", List.of()));
  }
}
