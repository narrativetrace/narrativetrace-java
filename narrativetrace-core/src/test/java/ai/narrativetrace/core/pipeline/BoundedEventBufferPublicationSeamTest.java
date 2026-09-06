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
import org.junit.jupiter.api.Test;

/**
 * Deterministic proof that per-slot sequences prevent the claim-before-store race.
 *
 * <p>Uses the package-private test seam in {@link BoundedEventBuffer} to pause a producer between
 * slot claim (producerIndex incremented) and event publication (sequence set). This makes the race
 * window infinitely wide so the consumer is guaranteed to observe it.
 */
class BoundedEventBufferPublicationSeamTest {

  @Test
  void pollReturnsNullWhileSlotClaimedButUnpublished() throws Exception {
    var claimed = new CountDownLatch(1);
    var releasePublish = new CountDownLatch(1);

    var buffer =
        new BoundedEventBuffer(
            4,
            () -> {
              claimed.countDown();
              awaitUninterruptibly(releasePublish);
            });

    var event = enterEvent("Svc", "run");
    var producer = new Thread(() -> buffer.put(event));
    producer.start();
    claimed.await();

    // producerIndex has advanced, but event + sequence write are blocked
    TraceEvent beforePublish = buffer.poll();

    releasePublish.countDown();
    producer.join();

    // Now the event is fully published
    TraceEvent afterPublish = buffer.poll();

    assertThat(beforePublish).as("poll before publish must return null").isNull();
    assertThat(afterPublish).as("poll after publish must return the event").isSameAs(event);
  }

  @Test
  void drainSkipsUnpublishedSlotAndResumesLater() throws Exception {
    var claimed = new CountDownLatch(1);
    var releasePublish = new CountDownLatch(1);

    var buffer =
        new BoundedEventBuffer(
            4,
            () -> {
              claimed.countDown();
              awaitUninterruptibly(releasePublish);
            });

    var event = enterEvent("Svc", "run");
    var producer = new Thread(() -> buffer.put(event));
    producer.start();
    claimed.await();

    // drain while slot is claimed but unpublished — should collect nothing
    var drainedBefore = new java.util.ArrayList<TraceEvent>();
    buffer.drain(drainedBefore::add);

    releasePublish.countDown();
    producer.join();

    // drain after publish — should collect the event
    var drainedAfter = new java.util.ArrayList<TraceEvent>();
    buffer.drain(drainedAfter::add);

    assertThat(drainedBefore).as("drain before publish must be empty").isEmpty();
    assertThat(drainedAfter)
        .as("drain after publish must contain the event")
        .containsExactly(event);
  }

  /**
   * The other half of the same protocol, from the consumer's side: the sequence check proves the
   * producer <em>had</em> finished writing the slot, not that it still holds that event. A producer
   * that laps the ring between the check and the read replaces the event under the consumer, which
   * then delivers a later generation's event under this index — and delivers it a second time when
   * the index catches up, while the event that belonged here is lost and uncounted.
   *
   * <p>jcstress observed it at 123 samples in 2.2 million ({@code DrainRacingPublishTest}); the
   * consumer seam makes the window infinitely wide.
   */
  @Test
  void aSlotOverwrittenBetweenTheCheckAndTheReadIsShedNotDelivered() throws Exception {
    var atSlotRead = new CountDownLatch(1);
    var releaseRead = new CountDownLatch(1);
    var buffer = bufferPausingAtSlotRead(atSlotRead, releaseRead);
    buffer.put(enterEvent("Svc", "first"));
    var firstPass = new java.util.ArrayList<TraceEvent>();
    var consumer = new Thread(() -> buffer.drain(firstPass::add));

    consumer.start();
    assertThat(atSlotRead.await(5, java.util.concurrent.TimeUnit.SECONDS))
        .as("the consumer seam must fire between the sequence check and the slot read")
        .isTrue();
    var later = lapTheRing(buffer);
    releaseRead.countDown();
    consumer.join(2000);

    assertThat(firstPass).as("an overwritten slot must not be delivered at all").isEmpty();
    assertThat(buffer.overwrittenCount()).as("and it must be counted as shed").isEqualTo(1);
    var secondPass = new java.util.ArrayList<TraceEvent>();
    buffer.drain(secondPass::add);
    assertThat(secondPass)
        .as("the surviving events arrive once each, in order")
        .containsExactlyElementsOf(later);
    assertThat(buffer.overwrittenCount())
        .as("and stepping over the lapped slot left the consumer where it belongs")
        .isEqualTo(1);
  }

  /**
   * The same window on the other consumer entry point. {@code poll} answers {@code null} — its
   * contract's "nothing available right now" — rather than a wrong event, and the loss is counted.
   *
   * <p><b>@edgeCase</b> The two memory fences this protocol needs cannot be pinned by any
   * deterministic test: they emit no instruction on a strongly ordered CPU and only constrain the
   * compiler, so removing either leaves every example-based test green. {@code
   * DrainRacingPublishTest} is the only thing that sees them, and only across tens of millions of
   * samples.
   */
  @Test
  void aPollWhoseSlotWasOverwrittenAnswersNullAndCountsTheLoss() throws Exception {
    var atSlotRead = new CountDownLatch(1);
    var releaseRead = new CountDownLatch(1);
    var buffer = bufferPausingAtSlotRead(atSlotRead, releaseRead);
    buffer.put(enterEvent("Svc", "first"));
    var polled = new java.util.concurrent.atomic.AtomicReference<TraceEvent>();
    var consumer = new Thread(() -> polled.set(buffer.poll()));

    consumer.start();
    assertThat(atSlotRead.await(5, java.util.concurrent.TimeUnit.SECONDS))
        .as("the consumer seam must fire between the sequence check and the slot read")
        .isTrue();
    var later = lapTheRing(buffer);
    releaseRead.countDown();
    consumer.join(2000);

    assertThat(polled.get()).as("an overwritten slot must not be polled").isNull();
    assertThat(buffer.overwrittenCount()).as("it is counted as shed").isEqualTo(1);
    assertThat(buffer.poll())
        .as("and the ring resumes at the next generation")
        .isSameAs(later.get(0));
    assertThat(buffer.poll()).isSameAs(later.get(1));
  }

  /**
   * A two-slot buffer whose consumer blocks after accepting a slot's sequence, before reading it.
   */
  private static BoundedEventBuffer bufferPausingAtSlotRead(
      CountDownLatch reached, CountDownLatch release) {
    return new BoundedEventBuffer(
        2,
        () -> {},
        () -> {
          reached.countDown();
          awaitUninterruptibly(release);
        });
  }

  /** Publishes two more events, wrapping the two-slot ring onto the slot being read. */
  private static List<TraceEvent> lapTheRing(BoundedEventBuffer buffer) {
    List<TraceEvent> later = List.of(enterEvent("Svc", "second"), enterEvent("Svc", "third"));
    later.forEach(buffer::put);
    return later;
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    boolean interrupted = false;
    while (true) {
      try {
        latch.await();
        break;
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private static TraceEvent.EnterEvent enterEvent(String className, String methodName) {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(),
        System.nanoTime(),
        new MethodSignature(className, methodName, List.of()));
  }
}
