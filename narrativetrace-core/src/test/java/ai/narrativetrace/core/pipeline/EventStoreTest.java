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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EventStoreTest {

  @Test
  void startsEmpty() {
    var store = new EventStore();

    assertThat(store.events()).isEmpty();
  }

  @Test
  void addStoresEvent() {
    var store = new EventStore();
    var event = enterEvent("Foo", "bar");

    store.add(event);

    assertThat(store.events()).containsExactly(event);
  }

  @Test
  void addPreservesInsertionOrder() {
    var store = new EventStore();
    var e1 = enterEvent("A", "a");
    var e2 = enterEvent("B", "b");

    store.add(e1);
    store.add(e2);

    assertThat(store.events()).containsExactly(e1, e2);
  }

  @Test
  void eventsReturnsUnmodifiableList() {
    var store = new EventStore();
    store.add(enterEvent("Foo", "bar"));

    assertThat(store.events()).isUnmodifiable();
  }

  @Test
  void clearRemovesAllEvents() {
    var store = new EventStore();
    store.add(enterEvent("Foo", "bar"));
    store.add(enterEvent("Baz", "qux"));

    store.clear();

    assertThat(store.events()).isEmpty();
  }

  @Test
  void eventsSnapshotIsNotAffectedBySubsequentAdd() {
    var store = new EventStore();
    store.add(enterEvent("A", "a"));

    var snapshot = store.events();
    store.add(enterEvent("B", "b"));

    assertThat(snapshot).hasSize(1);
  }

  @Test
  @SuppressWarnings("PMD.DoNotUseThreads")
  void concurrentAddAndReadDoNotCorruptTheStore() throws InterruptedException {
    // The daemon consumer thread add()s while request threads call events(); a plain
    // ArrayList throws ConcurrentModificationException / corrupts under this interleaving.
    var store = new EventStore();
    var error = new AtomicReference<Throwable>();
    var start = new CountDownLatch(1);
    var writer = new Thread(() -> writeEvents(store, 5_000, start, error));
    var reader = new Thread(() -> readEvents(store, 3_000, start, error));

    writer.start();
    reader.start();
    start.countDown();
    writer.join(10_000);
    reader.join(10_000);

    assertThat(error.get()).isNull();
    assertThat(store.events()).hasSize(5_000);
  }

  private static void writeEvents(
      EventStore store, int count, CountDownLatch start, AtomicReference<Throwable> error) {
    try {
      start.await();
      for (int i = 0; i < count; i++) {
        store.add(enterEvent("W", "m" + i));
      }
    } catch (Throwable t) { // NOPMD capture any failure for the assertion
      error.compareAndSet(null, t);
    }
  }

  private static void readEvents(
      EventStore store, int count, CountDownLatch start, AtomicReference<Throwable> error) {
    try {
      start.await();
      for (int i = 0; i < count; i++) {
        store.events();
      }
    } catch (Throwable t) { // NOPMD capture any failure for the assertion
      error.compareAndSet(null, t);
    }
  }

  private static TraceEvent.EnterEvent enterEvent(String className, String methodName) {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(),
        System.nanoTime(),
        new MethodSignature(className, methodName, List.of()));
  }
}
