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
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class BoundedEventBufferPropertyTest {

  @Property
  void capacityIsAlwaysPowerOfTwo(@ForAll @IntRange(min = 1, max = 1024) int requested) {
    var buffer = new BoundedEventBuffer(requested);
    int capacity = buffer.capacity();
    assertThat(Integer.bitCount(capacity)).isEqualTo(1);
    assertThat(capacity).isGreaterThanOrEqualTo(requested);
  }

  @Property
  void sizeNeverExceedsCapacity(
      @ForAll @IntRange(min = 1, max = 64) int capacity,
      @ForAll @IntRange(min = 0, max = 200) int eventCount) {
    var buffer = new BoundedEventBuffer(capacity);
    for (int i = 0; i < eventCount; i++) {
      buffer.put(event());
    }
    assertThat(buffer.size()).isLessThanOrEqualTo(buffer.capacity());
  }

  @Property
  void putNeverCrashes(
      @ForAll @IntRange(min = 1, max = 128) int capacity,
      @ForAll @IntRange(min = 0, max = 500) int eventCount) {
    var buffer = new BoundedEventBuffer(capacity);
    for (int i = 0; i < eventCount; i++) {
      buffer.put(event());
    }
    // reaching here without exception is the property
    assertThat(buffer.capacity()).isPositive();
  }

  @Property
  void drainReturnsEventsInFifoOrder(@ForAll @IntRange(min = 1, max = 64) int capacity) {
    var buffer = new BoundedEventBuffer(capacity);
    int count = Math.min(capacity, 50);
    var spanIds = new ArrayList<SpanId>();
    for (int i = 0; i < count; i++) {
      SpanContext sc = TestSpanContext.create();
      spanIds.add(sc.spanId());
      buffer.put(
          new TraceEvent.EnterEvent(
              sc, System.nanoTime(), new MethodSignature("C", "m", List.of())));
    }
    var drained = new ArrayList<TraceEvent>();
    buffer.drain(drained::add);
    for (int i = 0; i < drained.size(); i++) {
      assertThat(((TraceEvent.EnterEvent) drained.get(i)).spanContext().spanId())
          .isEqualTo(spanIds.get(i));
    }
  }

  @Property
  void drainedCountMatchesPutCountWhenUnderCapacity(
      @ForAll @IntRange(min = 4, max = 128) int capacity) {
    var buffer = new BoundedEventBuffer(capacity);
    int actualCapacity = buffer.capacity();
    int count = actualCapacity / 2;
    for (int i = 0; i < count; i++) {
      buffer.put(event());
    }
    var drained = new ArrayList<TraceEvent>();
    buffer.drain(drained::add);
    assertThat(drained).hasSize(count);
  }

  @Property
  void bufferIsEmptyAfterDrain(
      @ForAll @IntRange(min = 1, max = 64) int capacity,
      @ForAll @IntRange(min = 1, max = 100) int eventCount) {
    var buffer = new BoundedEventBuffer(capacity);
    for (int i = 0; i < eventCount; i++) {
      buffer.put(event());
    }
    buffer.drain(e -> {});
    assertThat(buffer.isEmpty()).isTrue();
    assertThat(buffer.size()).isZero();
  }

  @Property
  void pollReturnsSameOrderAsDrain(@ForAll @IntRange(min = 4, max = 64) int capacity) {
    var buffer1 = new BoundedEventBuffer(capacity);
    var buffer2 = new BoundedEventBuffer(capacity);
    int actualCapacity = buffer1.capacity();
    int count = actualCapacity / 2;
    for (int i = 0; i < count; i++) {
      SpanContext sc = TestSpanContext.create();
      var e =
          new TraceEvent.EnterEvent(
              sc, System.nanoTime(), new MethodSignature("C", "m", List.of()));
      buffer1.put(e);
      buffer2.put(e);
    }
    var drained = new ArrayList<TraceEvent>();
    buffer1.drain(drained::add);
    var polled = new ArrayList<TraceEvent>();
    TraceEvent e = buffer2.poll();
    while (e != null) {
      polled.add(e);
      e = buffer2.poll();
    }
    assertThat(polled).hasSize(drained.size());
    for (int i = 0; i < polled.size(); i++) {
      assertThat(((TraceEvent.EnterEvent) polled.get(i)).spanContext().spanId())
          .isEqualTo(((TraceEvent.EnterEvent) drained.get(i)).spanContext().spanId());
    }
  }

  @Property
  void overflowDropsOldestEvents(@ForAll @IntRange(min = 2, max = 32) int capacity) {
    var buffer = new BoundedEventBuffer(capacity);
    int actualCapacity = buffer.capacity();
    int total = actualCapacity * 2;
    var allEvents = new ArrayList<TraceEvent.EnterEvent>();
    for (int i = 0; i < total; i++) {
      var e =
          new TraceEvent.EnterEvent(
              TestSpanContext.create(),
              System.nanoTime(),
              new MethodSignature("C", "m", List.of()));
      allEvents.add(e);
      buffer.put(e);
    }
    var drained = new ArrayList<TraceEvent>();
    buffer.drain(drained::add);
    assertThat(drained).hasSizeLessThanOrEqualTo(actualCapacity);
    // The first drained event should be from the later half of all produced events
    SpanId firstSpanId = ((TraceEvent.EnterEvent) drained.get(0)).spanContext().spanId();
    int firstIndex = -1;
    for (int i = 0; i < allEvents.size(); i++) {
      if (allEvents.get(i).spanContext().spanId().equals(firstSpanId)) {
        firstIndex = i;
        break;
      }
    }
    assertThat(firstIndex).isGreaterThanOrEqualTo(total - actualCapacity);
  }

  @Property(tries = 20)
  void concurrentProducersNeverLoseEventsWithoutOverflow(
      @ForAll @IntRange(min = 2, max = 8) int producerCount) throws Exception {
    int eventsPerProducer = 50;
    int total = producerCount * eventsPerProducer;
    var buffer = new BoundedEventBuffer(total * 2);
    runProducers(buffer, producerCount, eventsPerProducer);
    var drained = new ArrayList<TraceEvent>();
    buffer.drain(drained::add);
    assertThat(drained).hasSize(total);
    var spanIds = new HashSet<SpanId>();
    for (TraceEvent e : drained) {
      spanIds.add(((TraceEvent.EnterEvent) e).spanContext().spanId());
    }
    assertThat(spanIds).hasSize(total);
  }

  @Property(tries = 20)
  void concurrentProduceAndConsumeNeverLosesEventsWithoutOverflow(
      @ForAll @IntRange(min = 2, max = 6) int producerCount) throws Exception {
    int eventsPerProducer = 100;
    int total = producerCount * eventsPerProducer;
    var buffer = new BoundedEventBuffer(total * 2);
    var consumed = new CopyOnWriteArrayList<TraceEvent>();
    var done = new AtomicInteger();
    var consumer = startConsumer(buffer, consumed, done);
    runProducers(buffer, producerCount, eventsPerProducer);
    done.set(1);
    consumer.join(5000);
    var remaining = new ArrayList<TraceEvent>();
    buffer.drain(remaining::add);
    assertThat(consumed.size() + remaining.size()).isEqualTo(total);
  }

  private static void runProducers(
      BoundedEventBuffer buffer, int producerCount, int eventsPerProducer) throws Exception {
    var latch = new CountDownLatch(1);
    var threads = new Thread[producerCount];
    for (int t = 0; t < producerCount; t++) {
      threads[t] =
          new Thread(
              () -> {
                awaitQuietly(latch);
                for (int i = 0; i < eventsPerProducer; i++) {
                  buffer.put(event());
                }
              });
      threads[t].start();
    }
    latch.countDown();
    for (Thread t : threads) {
      t.join();
    }
  }

  private static Thread startConsumer(
      BoundedEventBuffer buffer, List<TraceEvent> consumed, AtomicInteger done) {
    var consumer =
        new Thread(
            () -> {
              while (done.get() == 0 || !buffer.isEmpty()) {
                TraceEvent e = buffer.poll();
                if (e != null) {
                  consumed.add(e);
                }
              }
            });
    consumer.start();
    return consumer;
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static TraceEvent.EnterEvent event() {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(), System.nanoTime(), new MethodSignature("C", "m", List.of()));
  }
}
