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
import org.junit.jupiter.api.Test;

class BoundedEventBufferTest {

  @Test
  void pollOnEmptyBufferReturnsNull() {
    var buffer = new BoundedEventBuffer(4);

    assertThat(buffer.poll()).isNull();
  }

  @Test
  void putAndPollReturnsSameEvent() {
    var buffer = new BoundedEventBuffer(4);
    var event = enterEvent("Foo", "bar");

    buffer.put(event);

    assertThat(buffer.poll()).isSameAs(event);
  }

  @Test
  void overflowDropsOldest() {
    var buffer = new BoundedEventBuffer(2);
    var e1 = enterEvent("A", "a");
    var e2 = enterEvent("B", "b");
    var e3 = enterEvent("C", "c");

    buffer.put(e1);
    buffer.put(e2);
    buffer.put(e3); // overwrites e1

    assertThat(buffer.poll()).isSameAs(e2);
    assertThat(buffer.poll()).isSameAs(e3);
    assertThat(buffer.poll()).isNull();
  }

  @Test
  void fifoOrder() {
    var buffer = new BoundedEventBuffer(4);
    var e1 = enterEvent("A", "a");
    var e2 = enterEvent("B", "b");
    var e3 = enterEvent("C", "c");

    buffer.put(e1);
    buffer.put(e2);
    buffer.put(e3);

    assertThat(buffer.poll()).isSameAs(e1);
    assertThat(buffer.poll()).isSameAs(e2);
    assertThat(buffer.poll()).isSameAs(e3);
    assertThat(buffer.poll()).isNull();
  }

  @Test
  void sizeNeverExceedsCapacityAfterOverflow() {
    var buffer = new BoundedEventBuffer(2);
    for (int i = 0; i < 10; i++) {
      buffer.put(enterEvent("X", "x"));
    }
    assertThat(buffer.size()).isEqualTo(2);
  }

  @Test
  void drainConsumesAllEvents() {
    var buffer = new BoundedEventBuffer(4);
    var e1 = enterEvent("A", "a");
    var e2 = enterEvent("B", "b");
    buffer.put(e1);
    buffer.put(e2);

    var drained = new ArrayList<TraceEvent>();
    buffer.drain(drained::add);

    assertThat(drained).containsExactly(e1, e2);
    assertThat(buffer.isEmpty()).isTrue();
  }

  @Test
  void sizeReflectsCurrentCount() {
    var buffer = new BoundedEventBuffer(4);

    assertThat(buffer.size()).isZero();

    buffer.put(enterEvent("A", "a"));
    assertThat(buffer.size()).isEqualTo(1);

    buffer.put(enterEvent("B", "b"));
    assertThat(buffer.size()).isEqualTo(2);

    buffer.poll();
    assertThat(buffer.size()).isEqualTo(1);
  }

  @Test
  void capacityRoundsToPowerOfTwo() {
    assertThat(new BoundedEventBuffer(3).capacity()).isEqualTo(4);
    assertThat(new BoundedEventBuffer(4).capacity()).isEqualTo(4);
    assertThat(new BoundedEventBuffer(5).capacity()).isEqualTo(8);
    assertThat(new BoundedEventBuffer(1).capacity()).isEqualTo(2);
  }

  @Test
  void concurrentProducersDoNotCrash() throws InterruptedException {
    var buffer = new BoundedEventBuffer(64);
    int threadCount = 8;
    int eventsPerThread = 100;
    var threads = new Thread[threadCount];

    for (int t = 0; t < threadCount; t++) {
      threads[t] =
          new Thread(
              () -> {
                for (int i = 0; i < eventsPerThread; i++) {
                  buffer.put(enterEvent("T", "m"));
                }
              });
      threads[t].start();
    }
    for (Thread thread : threads) {
      thread.join();
    }

    var drained = new ArrayList<TraceEvent>();
    buffer.drain(drained::add);
    assertThat(drained).isNotEmpty();
    assertThat(drained.size()).isLessThanOrEqualTo(64);
  }

  /**
   * The flush-barrier predicate pair: the cursor is the claim horizon at the moment it is read, and
   * {@code consumedPast} answers whether the consumer has accounted for everything below it.
   */
  @Test
  void cursorMarksTheClaimHorizonAndConsumedPastTracksTheDrain() {
    var buffer = new BoundedEventBuffer(4);
    assertThat(buffer.consumedPast(buffer.cursor()))
        .as("an untouched buffer has nothing outstanding")
        .isTrue();

    buffer.put(enterEvent("A", "a"));
    buffer.put(enterEvent("B", "b"));
    long cursor = buffer.cursor();

    assertThat(buffer.consumedPast(cursor)).as("published but not yet drained").isFalse();
    buffer.drain(e -> {});
    assertThat(buffer.consumedPast(cursor)).as("the drain accounted for both").isTrue();
  }

  /**
   * A lapped slot is accounted for by the overwrite counter, not by delivery — and it still counts
   * as consumed, or a barrier waiting on the cursor would wait for an event that no longer exists.
   */
  @Test
  void consumedPastCountsOverwrittenSlotsAsAccountedFor() {
    var buffer = new BoundedEventBuffer(2);
    for (int i = 0; i < 4; i++) {
      buffer.put(enterEvent("T", "m" + i));
    }
    long cursor = buffer.cursor();

    var drained = new ArrayList<TraceEvent>();
    buffer.drain(drained::add);

    assertThat(buffer.consumedPast(cursor)).isTrue();
    assertThat(drained).hasSize(2);
    assertThat(buffer.overwrittenCount())
        .as("the lapped half is counted, never silent")
        .isEqualTo(2);
  }

  private static TraceEvent.EnterEvent enterEvent(String className, String methodName) {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(),
        System.nanoTime(),
        new MethodSignature(className, methodName, List.of()));
  }
}
