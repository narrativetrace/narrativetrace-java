/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.pipeline.BufferedEventConsumer;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The release gate for the async end-of-life contract: when a request ends, everything it could
 * report ends with it.
 *
 * <p>INTENT: The supplemental bug hunt's {@code AsyncHelperDiscardProbe} measured 512 adopted
 * worker spans and 1,024 events surviving a successful parent {@code reset()}, in three separate
 * async shapes. This file is that probe's shape as a regression suite, kept as one block in the
 * gate output so the four shapes cannot drift apart: ordinary snapshot adoption, late completion
 * after reset, {@link ForkGroup} and {@link FireAndForgetGroup}.
 *
 * <p><b>@llmNote</b> Same package as the production code on purpose: retention is state a caller
 * cannot see, so the assertions read {@code retainedSpanCount()} — a package-private seam — beside
 * the pipeline's own public event list. Visibility <em>while the request is live</em> is a
 * different contract, tested in {@code LiveSnapshotScopeVisibilityTest}; what this file pins is
 * end-of-life.
 */
class AsyncLifecycleContractTest {

  /** Workers per scenario. Plural enough to catch a partial sweep, small enough to stay fast. */
  private static final int WORKERS = 64;

  private static final int THREADS = 4;

  private BufferedEventConsumer consumer;
  private DualPathPipeline pipeline;
  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    consumer = new BufferedEventConsumer(1 << 16, false);
    pipeline = new DualPathPipeline(null, consumer);
    context =
        new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL), pipeline);
  }

  @AfterEach
  void tearDown() {
    context.reset();
    pipeline.close();
  }

  // ------------------------------------------------------------------ S3: adoption before reset

  @Test
  void adoptedWorkerSpansAndTheirEventsDoNotSurviveTheParentReset() throws Exception {
    var root = context.enterMethod(signature("Request", "handle"));
    var snapshot = context.snapshot();
    runOnWorkers(
        () -> {
          try (var scope = snapshot.activate()) {
            var child = context.enterMethod(signature("Worker", "adopted"));
            context.exitMethodWithReturn("\"ok\"", child);
          }
        });
    context.exitMethodWithReturn("\"ok\"", root);
    pipeline.flush();

    assertThat(context.captureTrace().roots())
        .as("the worker calls were adopted, so the request reported them")
        .hasSize(1);
    assertThat(context.retainedSpanCount()).isEqualTo(WORKERS + 1);

    context.reset();
    pipeline.flush();

    assertThat(context.retainedSpanCount())
        .as("owned and adopted spans alike end with the request")
        .isZero();
    assertThat(pipeline.events()).as("and so do their events").isEmpty();
  }

  // ------------------------------------------------------------- S4: completion after the reset

  @Test
  void aWorkerThatFinishesAfterTheParentResetIsDiscardedAndCounted() throws Exception {
    var root = context.enterMethod(signature("Request", "handle"));
    var snapshot = context.snapshot();
    context.exitMethodWithReturn("\"ok\"", root);
    context.reset();

    runOnWorkers(
        () -> {
          try (var scope = snapshot.activate()) {
            var child = context.enterMethod(signature("Worker", "late"));
            context.exitMethodWithReturn("\"ok\"", child);
          }
        });
    pipeline.flush();

    assertThat(context.retainedSpanCount())
        .as("work no live request can report is not retained")
        .isZero();
    assertThat(pipeline.events()).as("and neither are its events").isEmpty();
    assertThat(context.traceLoss().discardedSpans())
        .as("discarding is loss, and loss is counted")
        .isEqualTo(WORKERS);
  }

  @Test
  void anAdoptionTheCapRefusesLeavesNothingBehindEither() throws Exception {
    context.swapStack(new ThreadLocalNarrativeContext.TraceStack(1));
    var root = context.enterMethod(signature("Request", "handle"));
    var snapshot = context.snapshot();

    runOnWorkers(
        () -> {
          try (var scope = snapshot.activate()) {
            var child = context.enterMethod(signature("Worker", "adopted"));
            context.exitMethodWithReturn("\"ok\"", child);
          }
        });
    context.exitMethodWithReturn("\"ok\"", root);
    pipeline.flush();

    assertThat(context.traceLoss().refusedSpans())
        .as("one batch fits under the cap of one; the rest are refused")
        .isEqualTo(WORKERS - 1L);
    assertThat(context.retainedSpanCount())
        .as("a refused batch is missing from the narrative, so it is not kept in memory")
        .isEqualTo(2);
  }

  // ------------------------------------------------------------- S5: the helpers own their copies

  @Test
  void forkedWorkerSpansAreGoneOnceTheirRootsHaveBeenCollected() throws Exception {
    var root = context.enterMethod(signature("Request", "forkJoin"));
    var group = ForkGroup.create(context);
    runOnWorkers(
        group.wrap(
            (Runnable)
                () -> {
                  var child = context.enterMethod(signature("Worker", "forked"));
                  context.exitMethodWithReturn("\"ok\"", child);
                }));
    group.merge();
    context.exitMethodWithReturn("\"ok\"", root);
    pipeline.flush();

    assertThat(context.captureTrace().roots().get(0).children())
        .as("the merged narrative is the deliverable, and it is unchanged")
        .hasSize(WORKERS);
    assertThat(context.retainedSpanCount())
        .as("the request's own spans: the root and one re-emitted node per worker, no raw ones")
        .isEqualTo(WORKERS + 1);

    context.reset();
    pipeline.flush();

    assertThat(context.retainedSpanCount()).isZero();
    assertThat(spanCarryingEvents()).as("no worker residue survives the request").isEmpty();
  }

  @Test
  void fireAndForgetWorkerSpansAreGoneOnceTheirRootsHaveBeenCollected() throws Exception {
    var root = context.enterMethod(signature("Request", "fireAndForget"));
    var group = FireAndForgetGroup.create(context, "Launcher");
    runOnWorkers(
        () ->
            group
                .wrap(
                    () -> {
                      var child = context.enterMethod(signature("Worker", "background"));
                      context.exitMethodWithReturn("\"ok\"", child);
                      return null;
                    })
                .get());
    context.exitMethodWithReturn("\"ok\"", root);
    pipeline.flush();

    assertThat(group.childRoots())
        .as("the copied child roots are the deliverable, and they are unchanged")
        .hasSize(WORKERS);
    assertThat(context.retainedSpanCount())
        .as("the request's own spans: the root and the launcher node, no raw worker ones")
        .isEqualTo(2);

    context.reset();
    pipeline.flush();

    assertThat(context.retainedSpanCount()).isZero();
    assertThat(spanCarryingEvents()).as("no worker residue survives the request").isEmpty();
  }

  /**
   * The helpers call this from a wrapped task, and a task can run on a thread that never traced —
   * an empty scope, or one whose level suppressed everything. Discarding nothing must not build a
   * stack to discard, and must not fail.
   */
  @Test
  void discardingTheLocalTraceOfAThreadThatNeverTracedDoesNothing() {
    context.discardLocalTrace();

    assertThat(context.captureTrace().roots()).isEmpty();
    assertThat(context.retainedSpanCount()).isZero();
  }

  /**
   * Retained events that belong to a span.
   *
   * <p><b>@edgeCase</b> The group lifecycle events ({@code fork created}, {@code merge}, {@code
   * fire-and-forget launched}) carry no span id, so nothing keyed by span can clear them; they are
   * excluded here rather than silently asserted away. Their retention is a separate, smaller
   * question than the one this file answers.
   */
  private List<ai.narrativetrace.api.event.TraceEvent> spanCarryingEvents() {
    return pipeline.events().stream()
        .filter(event -> ai.narrativetrace.api.event.TraceEvent.spanIdOf(event) != null)
        .toList();
  }

  private static MethodSignature signature(String type, String method) {
    return new MethodSignature(type, method, List.of());
  }

  /** Runs one task per worker and waits for all of them, so nothing is in flight at the assert. */
  private static void runOnWorkers(Runnable task) throws InterruptedException {
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    var finished = new CountDownLatch(WORKERS);
    try {
      for (int i = 0; i < WORKERS; i++) {
        executor.execute(
            () -> {
              try {
                task.run();
              } finally {
                finished.countDown();
              }
            });
      }
      assertThat(finished.await(30, TimeUnit.SECONDS)).as("workers finished").isTrue();
    } finally {
      executor.shutdownNow();
    }
  }
}
