/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BufferedEventConsumerTest {

  private BufferedEventConsumer consumer;

  @AfterEach
  void tearDown() {
    if (consumer != null) {
      consumer.close();
    }
  }

  @Test
  void acceptedEventAppearsInStore() throws InterruptedException {
    consumer = new BufferedEventConsumer(4);
    var event = enterEvent("Foo", "bar");

    consumer.accept(event);
    awaitEvents(consumer, 1);

    assertThat(consumer.events()).containsExactly(event);
  }

  @Test
  void closeStopsConsumerThread() throws InterruptedException {
    consumer = new BufferedEventConsumer(4);

    consumer.close();
    Thread.sleep(50);

    assertThat(consumer.consumerAlive()).isFalse();
  }

  @Test
  void closeDrainsRemainingEvents() throws InterruptedException {
    consumer = new BufferedEventConsumer(4);
    var e1 = enterEvent("A", "a");
    var e2 = enterEvent("B", "b");

    consumer.accept(e1);
    consumer.accept(e2);
    awaitEvents(consumer, 2);
    consumer.close();

    assertThat(consumer.events()).containsExactly(e1, e2);
  }

  @Test
  void multipleEventsFromDifferentThreads() throws InterruptedException {
    consumer = new BufferedEventConsumer(64);
    int count = 20;
    var threads = new Thread[count];

    for (int i = 0; i < count; i++) {
      threads[i] = new Thread(() -> consumer.accept(enterEvent("T", "m")));
      threads[i].start();
    }
    for (Thread t : threads) {
      t.join();
    }
    awaitEvents(consumer, count);

    assertThat(consumer.events()).hasSize(count);
  }

  @Test
  void sheddingModeDoesNotAddToEventList() {
    consumer = new BufferedEventConsumer(16, false);
    for (int i = 0; i < 14; i++) {
      consumer.accept(enterEvent("Hot", "method"));
    }

    consumer.drainCycle();

    assertThat(consumer.events()).isEmpty();
  }

  @Test
  void transitionsFromSheddingToNormalAfterDrain() {
    consumer = new BufferedEventConsumer(16, false);
    // First: fill to shedding level (>70%)
    for (int i = 0; i < 14; i++) {
      consumer.accept(enterEvent("Hot", "method"));
    }
    consumer.drainCycle(); // shedding drains and discards all
    assertThat(consumer.events()).isEmpty();

    // Now queue is empty — add one event, should process normally
    var normalEvent = enterEvent("Normal", "work");
    consumer.accept(normalEvent);
    consumer.drainCycle();

    assertThat(consumer.events()).containsExactly(normalEvent);
  }

  @Test
  void belowSheddingThresholdProcessesNormally() {
    consumer = new BufferedEventConsumer(16, false);
    // 10 of 16 = 62.5% < 70% — normal mode
    for (int i = 0; i < 10; i++) {
      consumer.accept(enterEvent("Svc", "work"));
    }

    // Drain all 10 events one by one (normal mode polls one per cycle)
    for (int i = 0; i < 10; i++) {
      consumer.drainCycle();
    }

    assertThat(consumer.events()).hasSize(10);
  }

  @Test
  void normalModeAddsToEventList() {
    consumer = new BufferedEventConsumer(16, false);
    var event = enterEvent("Svc", "run");
    consumer.accept(event);

    consumer.drainCycle();

    assertThat(consumer.events()).containsExactly(event);
  }

  @Test
  void emergencyModeDiscardsEverything() {
    consumer = new BufferedEventConsumer(16, false);
    // Fill 16 of 16 slots (100% > 95%)
    for (int i = 0; i < 16; i++) {
      consumer.accept(enterEvent("Hot", "method"));
    }

    consumer.drainCycle();

    assertThat(consumer.events()).isEmpty();
  }

  @Test
  void defaultCapacityIsSixtyFiveThousandFiveHundredAndThirtySixSlots() {
    consumer = new BufferedEventConsumer(BufferedEventConsumer.DEFAULT_CAPACITY, false);

    assertThat(consumer.bufferCapacity()).isEqualTo(65_536);
  }

  /**
   * The ring never grows, so its size is decided once — and a size it cannot address must be
   * refused loudly at construction rather than silently rounded. {@code Integer.MAX_VALUE} is the
   * case that used to throw {@code NegativeArraySizeException} from inside the power-of-two
   * rounding; a negative used to be rounded up to a two-slot ring.
   */
  @ParameterizedTest
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE, (1 << 30) + 1, Integer.MAX_VALUE})
  void refusesACapacityTheRingCannotAddress(int capacity) {
    assertThatThrownBy(() -> new BufferedEventConsumer(capacity, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("capacity must be between 1 and 1073741824, but was " + capacity);
  }

  @Test
  void theSmallestAcceptedCapacityStillYieldsAUsableRing() {
    consumer = new BufferedEventConsumer(1, false);

    assertThat(consumer.bufferCapacity()).isEqualTo(2);
  }

  @Test
  void defaultConstructorUsesDefaultCapacity() {
    consumer = new BufferedEventConsumer();
    var event = enterEvent("A", "a");

    consumer.accept(event);
    consumer.close();

    assertThat(consumer.events()).contains(event);
  }

  @Test
  void closeRestoresInterruptFlag() throws InterruptedException {
    consumer = new BufferedEventConsumer(4, false);
    Thread closer =
        new Thread(
            () -> {
              Thread.currentThread().interrupt();
              consumer.close();
            });
    closer.start();
    closer.join(2000);

    assertThat(closer.isInterrupted()).isTrue();
  }

  @Test
  void closeImmediatelyAfterAcceptStillDrains() {
    consumer = new BufferedEventConsumer(4);
    var event = enterEvent("A", "a");

    consumer.accept(event);
    consumer.close();

    assertThat(consumer.events()).contains(event);
  }

  @Test
  void subscriberReceivesEventsInNormalMode() throws Exception {
    consumer = new BufferedEventConsumer(16);
    var subscriber = new EventStoreSubscriber();
    consumer.subscribe(subscriber);

    consumer.accept(enterEvent("Svc", "run"));
    subscriber.awaitEvents(1).get(2, java.util.concurrent.TimeUnit.SECONDS);

    assertThat(subscriber.events()).hasSize(1);
    assertThat(subscriber.events().get(0)).isInstanceOf(TraceEvent.EnterEvent.class);
  }

  @Test
  void closeSignalsOnCompleteToSubscribers() throws Exception {
    consumer = new BufferedEventConsumer(16);
    var subscriber = new EventStoreSubscriber();
    consumer.subscribe(subscriber);

    consumer.accept(enterEvent("Svc", "run"));
    consumer.close();
    subscriber.awaitComplete().get(2, java.util.concurrent.TimeUnit.SECONDS);

    assertThat(subscriber.events()).hasSize(1);
  }

  @Test
  void subscriberDoesNotReceiveEventsDuringShedding() {
    consumer = new BufferedEventConsumer(16, false);
    var subscriber = new EventStoreSubscriber();
    consumer.subscribe(subscriber);
    // Fill to shedding level (>70%)
    for (int i = 0; i < 14; i++) {
      consumer.accept(enterEvent("Hot", "method"));
    }

    consumer.drainCycle();

    assertThat(subscriber.events()).isEmpty();
  }

  @Test
  void multipleSubscribersEachReceiveEvent() throws Exception {
    consumer = new BufferedEventConsumer(16);
    var sub1 = new EventStoreSubscriber();
    var sub2 = new EventStoreSubscriber();
    consumer.subscribe(sub1);
    consumer.subscribe(sub2);

    consumer.accept(enterEvent("Svc", "run"));
    sub1.awaitEvents(1).get(2, java.util.concurrent.TimeUnit.SECONDS);
    sub2.awaitEvents(1).get(2, java.util.concurrent.TimeUnit.SECONDS);

    assertThat(sub1.events()).hasSize(1);
    assertThat(sub2.events()).hasSize(1);
  }

  @Test
  void clearRemovesAllStoredEvents() {
    consumer = new BufferedEventConsumer(16, false);
    consumer.accept(enterEvent("A", "a"));
    consumer.flush();
    assertThat(consumer.events()).hasSize(1);

    consumer.clear();

    assertThat(consumer.events()).isEmpty();
  }

  @Test
  void flushDrainsQueuedEventsToStore() {
    consumer = new BufferedEventConsumer(16, false);
    consumer.accept(enterEvent("A", "a"));
    consumer.accept(enterEvent("B", "b"));

    consumer.flush();

    assertThat(consumer.events()).hasSize(2);
  }

  @Test
  void droppedCountIsZeroInitially() {
    consumer = new BufferedEventConsumer(16, false);

    assertThat(consumer.droppedCount()).isZero();
  }

  @Test
  void subscriberDoesNotReceiveEventsDuringEmergency() {
    consumer = new BufferedEventConsumer(16, false);
    var subscriber = new EventStoreSubscriber();
    consumer.subscribe(subscriber);
    // Fill to emergency level (>90%)
    for (int i = 0; i < 16; i++) {
      consumer.accept(enterEvent("Hot", "method"));
    }

    consumer.drainCycle();

    assertThat(consumer.events()).isEmpty();
    assertThat(subscriber.events()).isEmpty();
  }

  private static void awaitEvents(BufferedEventConsumer consumer, int count)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + 2000;
    while (consumer.events().size() < count && System.currentTimeMillis() < deadline) {
      Thread.sleep(5);
    }
  }

  @Test
  void drainCycleUpdatesLastActivityNanos() {
    consumer = new BufferedEventConsumer(4, false);

    consumer.drainCycle();

    assertThat(consumer.lastActivityNanos()).isGreaterThan(0);
  }

  @Test
  void staleSinceMillisReturnsTimeSinceLastActivity() throws InterruptedException {
    consumer = new BufferedEventConsumer(4, false);
    consumer.drainCycle();

    Thread.sleep(50);

    assertThat(consumer.staleSinceMillis()).isGreaterThanOrEqualTo(40);
  }

  @Test
  void staleSinceMillisReturnsZeroBeforeFirstDrainCycle() {
    consumer = new BufferedEventConsumer(4, false);

    assertThat(consumer.staleSinceMillis()).isZero();
  }

  @Test
  void heartbeatUpdatesOnEveryDrainCycleIncludingEmpty() {
    consumer = new BufferedEventConsumer(4, false);

    consumer.drainCycle();
    long first = consumer.lastActivityNanos();
    consumer.drainCycle();
    long second = consumer.lastActivityNanos();

    assertThat(second).isGreaterThan(first);
  }

  @Test
  void closeIsIdempotent() {
    consumer = new BufferedEventConsumer(4);

    consumer.close();
    consumer.close();

    assertThat(consumer.consumerAlive()).isFalse();
  }

  @Test
  void closeIsIdempotentWhenCalledConcurrently() throws InterruptedException {
    consumer = new BufferedEventConsumer(4);
    int threadCount = 10;
    var threads = new Thread[threadCount];
    for (int i = 0; i < threadCount; i++) {
      threads[i] = new Thread(consumer::close);
      threads[i].start();
    }
    for (Thread t : threads) {
      t.join(2000);
    }

    assertThat(consumer.consumerAlive()).isFalse();
  }

  @Test
  void shutdownHookDrainsRemainingEvents() {
    consumer = new BufferedEventConsumer(16, false);
    consumer.accept(enterEvent("A", "a"));
    consumer.accept(enterEvent("B", "b"));

    consumer.shutdownHookAction();

    assertThat(consumer.events()).hasSize(2);
  }

  @Test
  void shutdownHookIsNoOpAfterExplicitClose() {
    consumer = new BufferedEventConsumer(16, false);
    consumer.accept(enterEvent("A", "a"));

    consumer.close();
    consumer.shutdownHookAction();

    assertThat(consumer.events()).hasSize(1);
  }

  @Test
  void watchdogStaleCallbackDoesNotThrow() {
    consumer = new BufferedEventConsumer(4, false);

    consumer.onWatchdogStale();

    assertThat(consumer.staleSinceMillis()).isZero();
  }

  @Test
  void closeRestoresInterruptFlagWithActiveConsumer() throws InterruptedException {
    consumer = new BufferedEventConsumer(4);
    awaitDrainThread();
    Thread closer =
        new Thread(
            () -> {
              Thread.currentThread().interrupt();
              consumer.close();
            });
    closer.start();
    closer.join(3000);

    assertThat(closer.isInterrupted()).isTrue();
  }

  @Test
  void consumerStartsWatchdogAutomatically() throws InterruptedException {
    consumer = new BufferedEventConsumer(4);
    Thread.sleep(50);

    Thread watchdogThread = findThread("narrative-trace-watchdog");

    assertThat(watchdogThread).isNotNull();
    assertThat(watchdogThread.isDaemon()).isTrue();
  }

  @Test
  void watchdogIsClosedWhenConsumerCloses() throws InterruptedException {
    consumer = new BufferedEventConsumer(4);
    Thread.sleep(50);
    assertThat(findThread("narrative-trace-watchdog")).isNotNull();

    consumer.close();
    Thread.sleep(100);

    assertThat(findThread("narrative-trace-watchdog")).isNull();
  }

  @Test
  void drainLoopStopsWhenThreadIsInterrupted() throws InterruptedException {
    consumer = new BufferedEventConsumer(4);
    Thread drainThread = findThread("narrative-trace-consumer");
    assertThat(drainThread).isNotNull();
    assertThat(drainThread.isAlive()).isTrue();

    drainThread.interrupt();
    drainThread.join(2000);

    assertThat(drainThread.isAlive()).isFalse();
  }

  @Test
  void lastActivityNanosIsZeroBeforeFirstDrainCycle() {
    consumer = new BufferedEventConsumer(4, false);

    assertThat(consumer.lastActivityNanos()).isZero();
  }

  @Test
  void drainLoopStopsOnInterruptEvenWithPendingEvents() throws InterruptedException {
    consumer = new BufferedEventConsumer(64);
    for (int i = 0; i < 10; i++) {
      consumer.accept(enterEvent("Svc", "work"));
    }

    Thread drainThread = findThread("narrative-trace-consumer");
    assertThat(drainThread).isNotNull();
    drainThread.interrupt();
    drainThread.join(2000);

    assertThat(drainThread.isAlive()).isFalse();
  }

  private static void awaitDrainThread() throws InterruptedException {
    long deadline = System.currentTimeMillis() + 2000;
    while (findThread("narrative-trace-consumer") == null
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(5);
    }
  }

  private static Thread findThread(String name) {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(t -> t.getName().equals(name))
        .findFirst()
        .orElse(null);
  }

  // ------------------------------------------------------ removing a span that is still in flight

  /**
   * The supplemental bug hunt's S4 residue, deterministically: a drain stops at the first slot a
   * producer has claimed but not written, so a request ending under concurrent publishing removes
   * spans whose events are still in the ring. Draining them afterwards used to put them in the
   * store, where nothing would ever clear them again — 62 events survived 2,000 discarded workers.
   */
  @Test
  void aSpanRemovedWhileItsEventsAreStillInTheRingNeverReachesTheStore() {
    consumer = new BufferedEventConsumer(16, false);
    var spanContext = TestSpanContext.create();
    consumer.accept(enterEvent(spanContext, "Worker", "late"));

    consumer.removeSpans(Set.of(spanContext.spanId()));
    consumer.flush();

    assertThat(consumer.events())
        .as("an event that arrives after its request ended is not a new trace")
        .isEmpty();
  }

  /**
   * What a capture asks before it treats the store as the record. An event sitting in the ring is
   * "published but not arrived", and a caller that cannot tell the two apart writes a story with a
   * hole in it.
   */
  @Test
  void anEventStillInTheRingMeansTheConsumerIsNotDrained() {
    consumer = new BufferedEventConsumer(16, false);

    assertThat(consumer.drained()).as("nothing published yet").isTrue();
    consumer.accept(enterEvent("Worker", "publishing"));
    assertThat(consumer.drained()).as("published, not yet drained").isFalse();

    consumer.flush();

    assertThat(consumer.drained()).isTrue();
  }

  /**
   * The refusal is surgical: one request ending must not take a concurrent one's events with it.
   */
  @Test
  void refusingOneSpanDoesNotRefuseTheEventsOfAnother() {
    consumer = new BufferedEventConsumer(16, false);
    var ending = TestSpanContext.create();
    var concurrent = TestSpanContext.create();
    consumer.accept(enterEvent(ending, "Worker", "late"));
    var survivor = enterEvent(concurrent, "Other", "inFlight");
    consumer.accept(survivor);

    consumer.removeSpans(Set.of(ending.spanId()));
    consumer.flush();

    assertThat(consumer.events()).containsExactly(survivor);
  }

  /**
   * The other half of that mechanism: the refusal is remembered only while it can matter. Once a
   * drain sees an empty ring, every event published before the removal has arrived and been
   * filtered, so the span ids are dropped rather than accumulated for the life of the process.
   *
   * <p><b>@llmNote</b> Asserted through the only observable consequence — a same-span event
   * published <em>after</em> the ring drained is stored again. That is memory hygiene showing
   * through, not a promise about resurrection: span ids are unique, so nothing in the library can
   * publish under a span id whose request has ended.
   */
  @Test
  void aSpanRefusalIsForgottenOnceTheRingHasDrained() {
    consumer = new BufferedEventConsumer(16, false);
    var spanContext = TestSpanContext.create();
    consumer.accept(enterEvent(spanContext, "Worker", "late"));
    consumer.removeSpans(Set.of(spanContext.spanId()));
    consumer.flush();

    var afterwards = enterEvent(spanContext, "Worker", "again");
    consumer.accept(afterwards);
    consumer.flush();

    assertThat(consumer.events()).containsExactly(afterwards);
  }

  /**
   * The symptom: {@code captureTrace()} on a pipeline that was already closed reached {@code
   * SubmissionPublisher.offer} on a closed publisher, which answers with {@link
   * IllegalStateException} — and nothing on the flush path wraps it, so an ended subscription
   * became the application's exception.
   */
  @Test
  void aFlushAfterCloseIsNotTheCallersException() {
    consumer = new BufferedEventConsumer(4, false);
    consumer.close();
    var late = enterEvent("Late", "call");

    consumer.accept(late);
    consumer.flush();

    assertThat(consumer.events()).containsExactly(late);
  }

  /** The half of that path a fix must not take with it: close still fans its last drain out. */
  @Test
  void theFinalDrainOnCloseStillReachesSubscribers() throws Exception {
    consumer = new BufferedEventConsumer(4, false);
    var subscriber = new EventStoreSubscriber();
    consumer.subscribe(subscriber);
    consumer.accept(enterEvent("Svc", "run"));

    consumer.close();
    subscriber.awaitComplete().get(2, java.util.concurrent.TimeUnit.SECONDS);

    assertThat(subscriber.events()).hasSize(1);
  }

  /**
   * The ring is single-consumer, and {@code close()} used to drain it without the monitor every
   * other drain holds — so a request capturing while the pipeline shut down could walk the ring
   * alongside the closing thread, read the same index, and record one call twice.
   *
   * <p><b>@edgeCase</b> Repeated, because the window is a few instructions wide. The jcstress
   * scenario of the same name hits it in roughly one sample in seven; this hits it often enough to
   * keep the gate honest on every commit, and is deterministic once the drains are serialised.
   */
  @Test
  void aCloseRacingACaptureDoesNotRecordACallTwice() throws InterruptedException {
    for (int attempt = 0; attempt < 200; attempt++) {
      assertNoDuplicateUnderCloseRacingFlush();
    }
  }

  private void assertNoDuplicateUnderCloseRacingFlush() throws InterruptedException {
    consumer = new BufferedEventConsumer(8, false);
    var first = enterEvent("A", "a");
    var second = enterEvent("B", "b");
    consumer.accept(first);
    consumer.accept(second);
    var start = new CountDownLatch(1);
    var flusher = raceThread(start, consumer::flush);
    var closer = raceThread(start, consumer::close);

    start.countDown();
    flusher.join(2000);
    closer.join(2000);
    consumer.flush();

    assertThat(consumer.events()).containsExactly(first, second);
  }

  private static Thread raceThread(CountDownLatch start, Runnable action) {
    var thread =
        new Thread(
            () -> {
              try {
                start.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
              action.run();
            });
    thread.start();
    return thread;
  }

  private static TraceEvent.EnterEvent enterEvent(
      ai.narrativetrace.api.event.SpanContext spanContext, String className, String methodName) {
    return new TraceEvent.EnterEvent(
        spanContext, System.nanoTime(), new MethodSignature(className, methodName, List.of()));
  }

  private static TraceEvent.EnterEvent enterEvent(String className, String methodName) {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(),
        System.nanoTime(),
        new MethodSignature(className, methodName, List.of()));
  }
}
