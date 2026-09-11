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
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ForkGroup;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A fork child the collect could not see is never dropped silently.
 *
 * <p>INTENT: {@code ForkGroup}'s collect step is the last capture a worker scope ever gets — the
 * discard behind it is unconditional — so a child whose events were still stuck behind another
 * producer's claimed-but-unwritten slot used to vanish whole: empty roots, spans tombstoned, no
 * counter touched. The contract pinned here is the family law: the flush barrier makes the loss all
 * but unreachable, and when its bounded wait is exhausted anyway, the dropped child is counted
 * through the refusal channel — an async subtree absent from the tree.
 *
 * <p>Lives in the pipeline package on purpose: it drives the public context API across the package
 * boundary while using {@link BoundedEventBuffer}'s package-private claim seam to make the
 * descheduled-producer window schedulable.
 */
class ForkCollectBarrierTest {

  private static final String STALLED_PRODUCER = "fork-collect-stalled-producer";

  private final CountDownLatch stalledClaim = new CountDownLatch(1);
  private final CountDownLatch releaseStalledWrite = new CountDownLatch(1);
  private Thread stalledProducer;
  private DualPathPipeline pipeline;

  @AfterEach
  void tearDown() throws Exception {
    releaseStalledWrite.countDown();
    if (stalledProducer != null) {
      stalledProducer.join(TimeUnit.SECONDS.toMillis(5));
    }
    if (pipeline != null) {
      pipeline.close();
    }
  }

  /**
   * The counted last resort: the stalled claim is never released while the group runs, the bounded
   * wait is exhausted, and the child is genuinely gone from the merged narrative — loss under
   * pressure is acceptable — but it is counted as a refused scope, and its tombstoned events never
   * resurface. Silent is the one thing this loss may not be.
   */
  @Test
  void aForkChildTheExhaustedDrainCannotSeeIsDroppedButCounted() throws Exception {
    var context = contextOverStallableRing(TimeUnit.MILLISECONDS.toNanos(2));
    var root = context.enterMethod(signature("Request", "forkJoin"));
    var group = ForkGroup.create(context);
    stalledProducer = startStalledProducer();
    stalledClaim.await();
    runOnWorker(
        group.wrap(
            (Runnable)
                () -> {
                  var child = context.enterMethod(signature("Worker", "forked"));
                  context.exitMethodWithReturn("\"ok\"", child);
                }));
    group.merge();
    context.exitMethodWithReturn("\"ok\"", root);

    assertThat(context.captureTrace().roots().get(0).children())
        .as("the child could not be collected — that loss is permitted")
        .isEmpty();
    assertThat(context.traceLoss().refusedScopes())
        .as("but the dropped child is a counted scope, never a silent one")
        .isEqualTo(1);
    assertThat(context.traceLoss().refusedSpans()).isEqualTo(1);

    releaseStalledWrite.countDown();
    stalledProducer.join();
    pipeline.flush();

    assertThat(workerEvents())
        .as("the uncollected child's late events are tombstoned, not resurrected")
        .isEmpty();
  }

  /**
   * The barrier at work: the stalled neighbour resolves while the group is still running, the
   * collect's flush waits it out, and the child arrives in the merged narrative — nothing is
   * counted because nothing was lost.
   */
  @Test
  void aForkChildBehindAStalledClaimThatResolvesIsCollected() throws Exception {
    var context = contextOverStallableRing(TimeUnit.SECONDS.toNanos(5));
    var root = context.enterMethod(signature("Request", "forkJoin"));
    var group = ForkGroup.create(context);
    stalledProducer = startStalledProducer();
    stalledClaim.await();
    var childPublished = new CountDownLatch(1);
    var worker =
        new Thread(
            group.wrap(
                (Runnable)
                    () -> {
                      var child = context.enterMethod(signature("Worker", "forked"));
                      context.exitMethodWithReturn("\"ok\"", child);
                      childPublished.countDown();
                    }));
    worker.start();
    childPublished.await();
    releaseStalledWrite.countDown();
    worker.join();
    group.merge();
    context.exitMethodWithReturn("\"ok\"", root);

    assertThat(context.captureTrace().roots().get(0).children())
        .as("the collect waited the stalled write out and kept the child")
        .hasSize(1);
    assertThat(context.traceLoss().refusedScopes()).isZero();
    assertThat(context.traceLoss().refusedSpans()).isZero();
  }

  /** A clean collect counts nothing — the counter moves only when a child was actually unseen. */
  @Test
  void aCleanForkCollectCountsNothing() throws Exception {
    var consumer = new BufferedEventConsumer(1 << 10, false);
    pipeline = new DualPathPipeline(null, consumer);
    var context =
        new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL), pipeline);
    var root = context.enterMethod(signature("Request", "forkJoin"));
    var group = ForkGroup.create(context);
    runOnWorker(
        group.wrap(
            (Runnable)
                () -> {
                  var child = context.enterMethod(signature("Worker", "forked"));
                  context.exitMethodWithReturn("\"ok\"", child);
                }));
    group.merge();
    context.exitMethodWithReturn("\"ok\"", root);

    assertThat(context.captureTrace().roots().get(0).children()).hasSize(1);
    assertThat(context.traceLoss().refusedScopes()).isZero();
    assertThat(context.traceLoss().refusedSpans()).isZero();
  }

  private ThreadLocalNarrativeContext contextOverStallableRing(long inFlightWaitNanos) {
    var buffer = new BoundedEventBuffer(1 << 10, this::stallDesignatedProducer);
    var consumer = new BufferedEventConsumer(buffer, false, inFlightWaitNanos);
    pipeline = new DualPathPipeline(null, consumer);
    return new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL), pipeline);
  }

  /** Claim seam: holds only the designated producer thread, between its claim and its write. */
  private void stallDesignatedProducer() {
    if (STALLED_PRODUCER.equals(Thread.currentThread().getName())) {
      stalledClaim.countDown();
      awaitUninterruptibly(releaseStalledWrite);
    }
  }

  private Thread startStalledProducer() {
    var producer =
        new Thread(
            () ->
                pipeline.publish(
                    new TraceEvent.EnterEvent(
                        TestSpanContext.create(),
                        System.nanoTime(),
                        signature("Neighbour", "stalled"))),
            STALLED_PRODUCER);
    producer.start();
    return producer;
  }

  private List<TraceEvent> workerEvents() {
    return pipeline.events().stream()
        .filter(
            event ->
                event instanceof TraceEvent.EnterEvent enter
                    && "Worker".equals(enter.signature().className()))
        .toList();
  }

  private static void runOnWorker(Runnable task) throws InterruptedException {
    var worker = new Thread(task, "fork-collect-worker");
    worker.start();
    worker.join(TimeUnit.SECONDS.toMillis(30));
    assertThat(worker.isAlive()).as("worker finished").isFalse();
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

  private static MethodSignature signature(String type, String method) {
    return new MethodSignature(type, method, List.of());
  }
}
