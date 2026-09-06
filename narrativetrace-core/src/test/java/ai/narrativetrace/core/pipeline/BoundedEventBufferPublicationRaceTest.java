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
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Regression guard for the publication-ordering race in BoundedEventBuffer.
 *
 * <p>Before per-slot sequence numbers, producerIndex advanced (slot claimed) before the event was
 * written to the array. A concurrent consumer could read a null slot, advance consumerIndex, and
 * permanently skip the event. This fired on 94% of runs.
 *
 * <p>The fix uses per-slot sequence numbers with release/acquire semantics so the consumer only
 * advances past slots whose sequence confirms publication.
 */
class BoundedEventBufferPublicationRaceTest {

  /**
   * Producers and a consumer run concurrently on a tiny buffer. If the consumer ever reads null
   * from a slot it believed was occupied (because producerIndex advanced), it means the publication
   * race fired: the slot was claimed but not yet written.
   *
   * <p>We detect this by comparing: (events produced) vs (non-null events consumed + events still
   * in buffer). Any gap means events were permanently lost to the race — not to overflow.
   */
  @RepeatedTest(500)
  void concurrentProduceAndConsumeNeverLosesEvents() throws InterruptedException {
    int capacity = 1024;
    var buffer = new BoundedEventBuffer(capacity);
    int producerCount = 4;
    int eventsPerProducer = 64;
    int totalProduced = producerCount * eventsPerProducer;

    var consumed = new ArrayList<TraceEvent>();
    var done = new AtomicInteger();
    Thread consumer = startPollingConsumer(buffer, consumed, done);
    runProducers(buffer, producerCount, eventsPerProducer);
    done.set(1);
    consumer.join(2000);

    var remaining = new ArrayList<TraceEvent>();
    buffer.drain(remaining::add);
    assertThat(consumed.size() + remaining.size())
        .as("No events lost (no overflow, capacity=%d)", capacity)
        .isEqualTo(totalProduced);
  }

  /** Same as above but the consumer uses drain() — exercises the break-on-unpublished path. */
  @RepeatedTest(500)
  void concurrentProduceAndDrainNeverLosesEvents() throws InterruptedException {
    int capacity = 1024;
    var buffer = new BoundedEventBuffer(capacity);
    int producerCount = 4;
    int eventsPerProducer = 64;
    int totalProduced = producerCount * eventsPerProducer;

    var consumed = new ArrayList<TraceEvent>();
    var done = new AtomicInteger();
    Thread consumer = startDrainingConsumer(buffer, consumed, done);
    runProducers(buffer, producerCount, eventsPerProducer);
    done.set(1);
    consumer.join(2000);

    var remaining = new ArrayList<TraceEvent>();
    buffer.drain(remaining::add);
    assertThat(consumed.size() + remaining.size())
        .as("No events lost (no overflow, capacity=%d)", capacity)
        .isEqualTo(totalProduced);
  }

  private static Thread startPollingConsumer(
      BoundedEventBuffer buffer, List<TraceEvent> consumed, AtomicInteger done) {
    var thread =
        new Thread(
            () -> {
              while (done.get() == 0 || !buffer.isEmpty()) {
                TraceEvent event = buffer.poll();
                if (event != null) {
                  consumed.add(event);
                }
              }
            });
    thread.start();
    return thread;
  }

  private static Thread startDrainingConsumer(
      BoundedEventBuffer buffer, List<TraceEvent> consumed, AtomicInteger done) {
    var thread =
        new Thread(
            () -> {
              while (done.get() == 0 || !buffer.isEmpty()) {
                buffer.drain(consumed::add);
              }
            });
    thread.start();
    return thread;
  }

  private static void runProducers(BoundedEventBuffer buffer, int count, int eventsPerProducer)
      throws InterruptedException {
    Thread[] producers = new Thread[count];
    for (int t = 0; t < count; t++) {
      producers[t] =
          new Thread(
              () -> {
                for (int i = 0; i < eventsPerProducer; i++) {
                  buffer.put(enterEvent("P", "m"));
                }
              });
      producers[t].start();
    }
    for (Thread p : producers) {
      p.join();
    }
  }

  private static TraceEvent.EnterEvent enterEvent(String className, String methodName) {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(),
        System.nanoTime(),
        new MethodSignature(className, methodName, List.of()));
  }
}
