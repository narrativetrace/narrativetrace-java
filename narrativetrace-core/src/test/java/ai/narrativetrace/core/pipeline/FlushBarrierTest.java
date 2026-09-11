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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@code flush()} is a barrier for events published before it — bounded, never silent.
 *
 * <p>INTENT: A ring drain stops at the first slot a producer has claimed and not yet written, and
 * the thread inside that two-instruction window can be descheduled for a whole scheduling quantum.
 * A flush that returns then has silently omitted every event behind the stalled slot — including
 * the flushing thread's own, which is how a fork child's whole trace vanished from its collect
 * ({@code AsyncLifecycleContractTest} lost 5–6 of 64 children on a loaded host). The barrier
 * contract fixed here: everything published before {@code flush()} was called is in the store when
 * it returns, or the wait's exhaustion is observable via {@code drained()}.
 *
 * <p>Uses the package-private claim seam in {@link BoundedEventBuffer} to hold a producer between
 * claim and write, which makes the descheduled-producer window schedulable.
 */
class FlushBarrierTest {

  private static final String STALLED_PRODUCER = "flush-barrier-stalled-producer";

  private final CountDownLatch stalledClaim = new CountDownLatch(1);
  private final CountDownLatch releaseStalledWrite = new CountDownLatch(1);
  private Thread stalledProducer;

  @AfterEach
  void tearDown() throws Exception {
    releaseStalledWrite.countDown();
    if (stalledProducer != null) {
      stalledProducer.join(TimeUnit.SECONDS.toMillis(5));
    }
  }

  /**
   * The defect scenario: the caller's own events sit behind another producer's stalled claim, and a
   * flush that returns around the stall has silently omitted them. The discriminator is
   * deterministic in both directions — a flush that gives up early <em>completes</em> while the
   * stall is still held, which the liveness probe catches; a flush that waits cannot complete
   * before the release, because nothing else can consume the claimed slot.
   *
   * <p><b>@llmNote</b> The 200 ms probe is an observation window, not an eventual assertion: the
   * correct behaviour makes early completion logically impossible (the 5 s budget cannot lapse
   * inside it), so the probe never fails a correct implementation by scheduling luck.
   */
  @Test
  void flushDeliversTheCallersEventsBehindAnotherProducersStalledClaim() throws Exception {
    var buffer = new BoundedEventBuffer(16, this::stallDesignatedProducer);
    var consumer = new BufferedEventConsumer(buffer, false, TimeUnit.SECONDS.toNanos(5));
    var stalled = enterEvent("Stalled", "claimed");
    var own = enterEvent("Caller", "own");
    stalledProducer = startStalledProducer(buffer, stalled);
    stalledClaim.await();
    buffer.put(own);
    var flusher = new Thread(consumer::flush, "flush-barrier-flusher");

    flusher.start();
    flusher.join(200);
    assertThat(flusher.isAlive())
        .as("a flush waits out a claim made before it instead of returning around it")
        .isTrue();

    releaseStalledWrite.countDown();
    flusher.join(TimeUnit.SECONDS.toMillis(5));

    assertThat(flusher.isAlive()).as("the flush completes once the stalled write lands").isFalse();
    assertThat(consumer.events())
        .as("everything published before the flush is in the store when it returns")
        .containsExactly(stalled, own);
    assertThat(consumer.drained()).isTrue();
  }

  /**
   * The wait is a deadline, not a promise: a producer that never writes must not hang the flush.
   * Giving up leaves {@code drained()} false — the caller can tell — and the next flush after the
   * write lands delivers everything in order.
   */
  @Test
  void flushGivesUpBoundedlyOnAClaimNothingIsWritingAndRecoversAfterTheWrite() throws Exception {
    var buffer = new BoundedEventBuffer(16, this::stallDesignatedProducer);
    var consumer = new BufferedEventConsumer(buffer, false, TimeUnit.MILLISECONDS.toNanos(2));
    var stalled = enterEvent("Stalled", "claimed");
    var own = enterEvent("Caller", "own");
    stalledProducer = startStalledProducer(buffer, stalled);
    stalledClaim.await();
    buffer.put(own);

    consumer.flush();

    assertThat(consumer.events()).as("nothing behind the stalled claim is reachable").isEmpty();
    assertThat(consumer.drained()).as("the exhausted wait is observable, never silent").isFalse();

    releaseStalledWrite.countDown();
    stalledProducer.join();
    consumer.flush();

    assertThat(consumer.events()).containsExactly(stalled, own);
    assertThat(consumer.drained()).isTrue();
  }

  /** Claim seam: holds only the designated producer thread, between its claim and its write. */
  private void stallDesignatedProducer() {
    if (STALLED_PRODUCER.equals(Thread.currentThread().getName())) {
      stalledClaim.countDown();
      awaitUninterruptibly(releaseStalledWrite);
    }
  }

  private static Thread startStalledProducer(BoundedEventBuffer buffer, TraceEvent event) {
    var producer = new Thread(() -> buffer.put(event), STALLED_PRODUCER);
    producer.start();
    return producer;
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    boolean interrupted = false;
    try {
      while (true) {
        try {
          latch.await();
          return;
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static TraceEvent.EnterEvent enterEvent(String className, String methodName) {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(),
        System.nanoTime(),
        new MethodSignature(className, methodName, List.of()));
  }
}
