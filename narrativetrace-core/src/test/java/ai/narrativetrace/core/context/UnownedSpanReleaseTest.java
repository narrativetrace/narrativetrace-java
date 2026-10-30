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
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext.TraceStack;
import ai.narrativetrace.core.pipeline.BufferedEventConsumer;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * B-211: spans that no stack ever captures or resets are released, and handed-over work is captured
 * exactly as before.
 *
 * <p>INTENT: The review of 2026-10-27 measured an unbounded leak on 0.5.3 — a thread nobody resets
 * (an unwrapped pool worker, a raw thread, the agent on a batch job) kept every span it traced in
 * the context's span map and its own known-span set, and once anything flushed, every event in the
 * store; a pooled thread kept one trace id for life. The shapes here are that review's scratch
 * experiments ({@code AgentOnly}, {@code ProdLeak}, {@code E1}–{@code E3}) made bounded and fast:
 * the unowned-retention limit is lowered through the package-private constructor, so a few thousand
 * iterations cross it many times over, and every assertion is a constant bound, never a timing.
 *
 * <p><b>@llmNote</b> Same package as the production code: retention is state a caller cannot see,
 * so the assertions read the {@code retainedSpanCount()} and {@code knownSpanCount()} seams beside
 * the pipeline's own event list.
 */
class UnownedSpanReleaseTest {

  /** The unowned-retention limit these tests run under: small, so it is crossed many times. */
  private static final int LIMIT = 64;

  /** Iterations of unowned work: thirty times the limit's worth of spans and more. */
  private static final int ITERATIONS = 2_000;

  /**
   * What "bounded" means here: the limit, plus the latest root of each live thread (never evicted
   * so a later capture on that thread sees it whole), with room to spare — and two orders of
   * magnitude below what the unfixed code retains after {@link #ITERATIONS}.
   */
  private static final int BOUND = 4 * LIMIT;

  private DualPathPipeline pipeline;
  private ThreadLocalNarrativeContext context;
  private ExecutorService pool;

  @BeforeEach
  void setUp() {
    pipeline = new DualPathPipeline(null, new BufferedEventConsumer(1 << 16, false));
    context =
        new ThreadLocalNarrativeContext(
            new NarrativeTraceConfig(TracingLevel.DETAIL), pipeline, null, LIMIT);
    pool = Executors.newSingleThreadExecutor(r -> new Thread(r, "app-pool"));
  }

  @AfterEach
  void tearDown() {
    pool.shutdownNow();
    context.reset();
    pipeline.close();
  }

  // ------------------------------------------------------------------------- the leak, bounded

  @Test
  void agentOnlyShape_aThreadNobodyCapturesKeepsABoundedNumberOfSpans() {
    for (int i = 0; i < ITERATIONS; i++) {
      call("Svc", "work");
    }

    assertThat(context.retainedSpanCount())
        .as("span contexts are released when their span exits")
        .isLessThanOrEqualTo(BOUND);
    assertThat(context.knownSpanCount())
        .as("the thread's own known-span set stays bounded without any capture or reset")
        .isLessThanOrEqualTo(BOUND);
  }

  @Test
  void prodLeakShape_unwrappedPoolAndRawThreadWorkLeavesABoundedStore() throws Exception {
    for (int i = 1; i <= ITERATIONS; i++) {
      context.reset();
      call("Request", "handle");
      pool.submit(() -> call("Pool", "work")).get(10, TimeUnit.SECONDS);
      if (i % 2 == 0) {
        runOnRawThread(() -> call("Raw", "work"));
      }
      var tree = context.captureTrace();
      assertThat(names(tree))
          .as("each request's capture is exactly its own work, as before")
          .containsExactlyInAnyOrder("Request.handle", "Request.inner");
      context.reset();
    }
    pipeline.flush();

    assertThat(distinctStoredSpans())
        .as("unhanded-over events no longer accumulate in the store")
        .isLessThanOrEqualTo(BOUND);
    assertThat(context.retainedSpanCount()).isLessThanOrEqualTo(BOUND);
    assertThat(pool.submit(context::knownSpanCount).get(10, TimeUnit.SECONDS))
        .as("the pool thread's own known-span set stays bounded")
        .isLessThanOrEqualTo(BOUND);
  }

  @Test
  void twoConsecutiveRootsOnAnUnownedPooledThreadAreTwoTraces() throws Exception {
    pool.submit(() -> call("Pool", "first")).get(10, TimeUnit.SECONDS);
    pool.submit(() -> call("Pool", "second")).get(10, TimeUnit.SECONDS);
    pipeline.flush();

    var roots = rootEntersOn("app-pool");
    assertThat(roots).hasSize(2);
    assertThat(roots.get(0).spanContext().traceId())
        .as("a pooled thread nobody hands over must not correlate unrelated work")
        .isNotEqualTo(roots.get(1).spanContext().traceId());
    assertThat(roots.get(1).spanContext().storyId())
        .as("and its next story is its next root's, not the first one it ever ran")
        .isEqualTo("Pool.second");
  }

  @Test
  void askingForTheTraceIdBetweenRootsContinuesThatTrace() throws Exception {
    pool.submit(() -> call("Pool", "first")).get(10, TimeUnit.SECONDS);
    var asked = pool.submit(context::traceId).get(10, TimeUnit.SECONDS);
    pool.submit(() -> call("Pool", "second")).get(10, TimeUnit.SECONDS);
    pipeline.flush();

    var roots = rootEntersOn("app-pool");
    assertThat(asked)
        .as("the id asked for is the trace the thread is in, and the next root continues it")
        .isEqualTo(roots.get(0).spanContext().traceId())
        .isEqualTo(roots.get(1).spanContext().traceId());
  }

  /**
   * B-217 (pinning, not a fix): a pooled thread's next UNRELATED task gets the previous task's
   * trace id when its first act is {@code traceId()} rather than a traced call. This is {@link
   * #askingForTheTraceIdBetweenRootsContinuesThatTrace()} read from the next task's side rather
   * than the same task's: the core cannot tell "the work at hand asking for its own identity" (the
   * supported handover/outbound case) from "the next unrelated task happened to ask first" — both
   * look identical from a stack that nobody claimed. No fix; see the two tests below for the
   * supported ways to avoid it.
   */
  @Test
  void aPooledTaskThatReadsItsTraceIdBeforeAnyCallContinuesTheTaskBeforeIt() throws Exception {
    pool.submit(() -> call("Pool", "first")).get(10, TimeUnit.SECONDS);
    pipeline.flush();
    var firstRootId = rootEntersOn("app-pool").get(0).spanContext().traceId();

    var secondTraceId = pool.submit(context::traceId).get(10, TimeUnit.SECONDS);

    assertThat(secondTraceId)
        .as(
            "an unrelated task that only reads its id before tracing anything inherits the task"
                + " before it")
        .isEqualTo(firstRootId);
  }

  /**
   * B-217: unlike {@link #aPooledTaskThatReadsItsTraceIdBeforeAnyCallContinuesTheTaskBeforeIt},
   * {@code outboundTraceparent()} hands back nothing when no span is open — and when it hands back
   * nothing, there is no identity to stay consistent with, so the next root starts its own trace
   * instead of continuing the one before it.
   */
  @Test
  void anOutboundHeaderThatReturnsNothingDoesNotContinueTheTaskBeforeIt() throws Exception {
    pool.submit(() -> call("Pool", "first")).get(10, TimeUnit.SECONDS);
    var outbound = pool.submit(context::outboundTraceparent).get(10, TimeUnit.SECONDS);
    pool.submit(() -> call("Pool", "second")).get(10, TimeUnit.SECONDS);
    pipeline.flush();

    var roots = rootEntersOn("app-pool");
    assertThat(outbound).as("no span was open between roots, nothing to hand over").isNull();
    assertThat(roots.get(1).spanContext().traceId())
        .as("an outbound header that returned nothing must not continue the task before it")
        .isNotEqualTo(roots.get(0).spanContext().traceId());
  }

  /**
   * B-217, supported case: a span open on an unowned pooled thread still has an identity to hand
   * over, and {@code outboundTraceparent()} returns the current trace rather than starting a fresh
   * one underneath it.
   */
  @Test
  void outboundHeaderOnAnUnownedPooledThreadReturnsTheOpenSpansTrace() throws Exception {
    pool.submit(() -> call("Pool", "first")).get(10, TimeUnit.SECONDS);
    pipeline.flush();
    var firstRootId = rootEntersOn("app-pool").get(0).spanContext().traceId();

    var outbound =
        pool.submit(
                () -> {
                  context.enterMethod(signature("Pool", "second"));
                  try {
                    return context.outboundTraceparent();
                  } finally {
                    context.exitMethodWithReturn("\"done\"");
                  }
                })
            .get(10, TimeUnit.SECONDS);

    assertThat(outbound)
        .as("a span open on the unowned pooled thread still has an identity to hand over")
        .isNotNull();
    assertThat(outbound.traceId())
        .as("the new root started its own trace, independent of the task before it")
        .isNotEqualTo(firstRootId);
  }

  /**
   * B-217, supported answer (i): work handed over through a snapshot runs on a scope the pool
   * thread's own unowned base stack never contributes to, so the stale trace armed by the task
   * before it is irrelevant — the handed-over work carries whichever trace the snapshot was taken
   * from, never the pool's leftover one.
   */
  @Test
  void handedOverThroughASnapshotGivesThePooledTaskAFreshIdentity() throws Exception {
    pool.submit(() -> call("Pool", "first")).get(10, TimeUnit.SECONDS);
    pipeline.flush();
    var firstRootId = rootEntersOn("app-pool").get(0).spanContext().traceId();

    context.reset();
    var requestId = context.traceId();
    var handedOver = context.snapshot();
    var handedOverId = new AtomicReference<TraceId>();
    pool.submit(handedOver.wrap(() -> handedOverId.set(context.traceId())))
        .get(10, TimeUnit.SECONDS);

    assertThat(handedOverId.get())
        .as("handed-over work carries the handing-over thread's trace, not the pool's stale one")
        .isEqualTo(requestId)
        .isNotEqualTo(firstRootId);
  }

  /**
   * B-217, supported answer (ii): a task that resets its thread's state before reading its identity
   * claims the thread ({@link NarrativeContext#reset()}'s doc, and the integrations' own pattern)
   * and so starts over with no trace, instead of inheriting the one the last unowned root left
   * armed.
   */
  @Test
  void resettingAtTaskStartGivesThePooledTaskAFreshIdentity() throws Exception {
    pool.submit(() -> call("Pool", "first")).get(10, TimeUnit.SECONDS);
    pipeline.flush();
    var firstRootId = rootEntersOn("app-pool").get(0).spanContext().traceId();

    var secondTraceId =
        pool.submit(
                () -> {
                  context.reset();
                  return context.traceId();
                })
            .get(10, TimeUnit.SECONDS);

    assertThat(secondTraceId)
        .as("a task that resets before reading its id never inherits the task before it")
        .isNotEqualTo(firstRootId);
  }

  @Test
  void evictedSpansAreCountedOnTheirOwnStackAndNowhereElse() throws Exception {
    var roots = 10 * LIMIT;
    for (int i = 0; i < roots; i++) {
      call("Svc", "work");
    }
    var bystander =
        pool.submit(
                () -> {
                  context.reset();
                  call("Bystander", "work");
                  return context.captureTrace();
                })
            .get(10, TimeUnit.SECONDS);

    var tree = context.captureTrace();

    var missing = 2L * roots - names(tree).size();
    assertThat(missing).as("the oldest unowned roots were released").isPositive();
    assertThat(tree.loss().refusedSpans())
        .as("and what this thread can no longer report is counted here, exactly")
        .isEqualTo(missing);
    assertThat(bystander.loss().refusedSpans() + bystander.loss().discardedSpans())
        .as("another thread's narrative lost nothing and is told nothing")
        .isZero();
  }

  // --------------------------------------------------- three paths B-211 missed (B-238)

  /**
   * B-238 defect 1: a proxy-style detach on thread A, completed by thread B, must still seal A's
   * root — not wait for a next root on A that, on an all-async thread, never comes.
   */
  @Test
  void asyncCompletionOnAnotherThreadStillSealsTheDetachingThreadsRoot() throws Exception {
    var asyncA = Executors.newSingleThreadExecutor(r -> new Thread(r, "async-a"));
    try {
      for (int i = 0; i < ITERATIONS; i++) {
        SpanId spanId =
            asyncA
                .submit(
                    () -> {
                      var id = context.enterMethod(signature("Async", "work"));
                      context.enterMethod(signature("Async", "inner"));
                      context.exitMethodWithReturn("\"done\"");
                      context.detachFrame(id);
                      return id;
                    })
                .get(10, TimeUnit.SECONDS);
        // Completes on a different thread than the one that detached, as a future's completion
        // callback would.
        context.exitMethodWithReturn("\"done\"", spanId);
      }
      pipeline.flush();

      assertThat(asyncA.submit(context::knownSpanCount).get(10, TimeUnit.SECONDS))
          .as(
              "the detaching thread's own known-span set stays bounded once completion runs"
                  + " elsewhere")
          .isLessThanOrEqualTo(BOUND);
      assertThat(distinctStoredSpans())
          .as("the store stays bounded for a thread whose roots are all async")
          .isLessThanOrEqualTo(BOUND);
      assertThat(context.retainedSpanCount()).isLessThanOrEqualTo(BOUND);

      var roots = rootEntersOn("async-a");
      assertThat(roots.size())
          .as("the oldest roots were released; the most recent ones are still in the store")
          .isGreaterThanOrEqualTo(2);
      assertThat(roots.get(1).spanContext().traceId())
          .as("the detaching thread's next root gets a fresh trace once the previous one completed")
          .isNotEqualTo(roots.get(0).spanContext().traceId());
    } finally {
      asyncA.shutdownNow();
    }
  }

  /**
   * B-238 defect 2: a childless {@code emitTraceNode} root on an unowned thread must seal like any
   * other root — {@code exitEmittedNode} used to only detach, never queue for release.
   */
  @Test
  void emittedChildlessRootsOnAnUnownedThreadStayBounded() {
    for (int i = 0; i < ITERATIONS; i++) {
      var node =
          new TraceNode(
              signature("Emitted", "work"), List.of(), new TraceOutcome.Returned("\"done\""));
      context.emitTraceNode(node, null);
    }
    pipeline.flush();

    assertThat(context.knownSpanCount())
        .as("an emitted root must seal, not just detach, on an unowned thread")
        .isLessThanOrEqualTo(BOUND);
    assertThat(distinctStoredSpans())
        .as("the store stays bounded for emitted roots too")
        .isLessThanOrEqualTo(BOUND);
  }

  /**
   * B-238 defect 3: {@code reset()} must forget the closed stack's own known/adopted sets, not
   * merely the shared maps — otherwise the stack stays reachable (and its sets populated) through
   * the unowned-release queue until a later sweep happens to iterate past its segments.
   */
  @Test
  void resetForgetsTheClosedStacksKnownAndAdoptedSpans() {
    for (int i = 0; i < 3; i++) {
      call("Svc", "work");
    }
    var closedStack = currentStackOf(context);

    context.reset();

    assertThat(closedStack.knownSpanIds())
        .as("reset must forget the closed stack's own known set, the one the queue keeps reachable")
        .isEmpty();
  }

  private static TraceStack currentStackOf(ThreadLocalNarrativeContext context) {
    var stack = context.swapStack(new TraceStack());
    context.swapStack(stack);
    return stack;
  }

  // --------------------------------------------------------------- owned work, unchanged

  @Test
  void aClaimedThreadKeepsOneTraceAndLosesNothingPastTheLimit() {
    context.reset();
    var roots = 5 * LIMIT;
    for (int i = 0; i < roots; i++) {
      call("Svc", "work");
    }

    var tree = context.captureTrace();

    assertThat(tree.roots()).hasSize(roots);
    assertThat(tree.roots().stream().map(r -> r.spanContext().traceId()).distinct())
        .as("a thread its owner resets is one trace between resets, as before")
        .hasSize(1);
    assertThat(tree.loss().refusedSpans()).isZero();
  }

  @Test
  void aCaptureBeforeAnyClaimStillSeesEverythingUnderTheLimit() {
    call("Svc", "first");
    call("Svc", "second");

    assertThat(names(context.captureTrace()))
        .containsExactlyInAnyOrder("Svc.first", "Svc.inner", "Svc.second", "Svc.inner");
  }

  @Test
  void theLatestRootOfALiveThreadIsNeverReleasedEvenWhenLargerThanTheLimit() throws Exception {
    var root = context.enterMethod(signature("Big", "root"));
    for (int i = 0; i < 3 * LIMIT; i++) {
      context.enterMethod(signature("Big", "child"));
      context.exitMethodWithReturn("\"ok\"");
    }
    context.exitMethodWithReturn("\"ok\"", root);
    for (int i = 0; i < 10 * LIMIT; i++) {
      pool.submit(() -> call("Noise", "work")).get(10, TimeUnit.SECONDS);
    }

    var tree = context.captureTrace();

    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).children()).hasSize(3 * LIMIT);
    assertThat(tree.loss().refusedSpans()).isZero();
  }

  @Test
  void handedOverWorkIsCapturedExactlyAsBeforeUnderUnownedNoise() throws Exception {
    var noise = Executors.newFixedThreadPool(2, r -> new Thread(r, "noise"));
    var releaseLive = new CountDownLatch(1);
    try {
      flood(noise, 10 * LIMIT);
      context.reset();
      var root = context.enterMethod(signature("Test", "main"));
      forkAndMerge("Fork", "a", "b");
      flood(noise, 10 * LIMIT);
      runOnRawThread(context.snapshot().wrap(() -> call("Snap", "w")));
      var liveThread = startLiveChild(releaseLive);
      flood(noise, 10 * LIMIT);
      context.exitMethodWithReturn("\"ok\"", root);

      var tree = context.captureTrace();
      releaseLive.countDown();
      liveThread.join(10_000);

      assertThat(names(tree))
          .containsExactlyInAnyOrder(
              "Test.main",
              "Fork.a",
              "Fork.inner",
              "Fork.b",
              "Fork.inner",
              "Snap.w",
              "Snap.inner",
              "Live.w",
              "Live.inner");
      assertThat(tree.loss().refusedSpans() + tree.loss().discardedSpans()).isZero();
    } finally {
      releaseLive.countDown();
      noise.shutdownNow();
    }
  }

  @Test
  void concurrentClaimedThreadsSharingAPoolCaptureOnlyTheirOwnWork() throws Exception {
    var threads = 4;
    var shared = Executors.newFixedThreadPool(threads, r -> new Thread(r, "shared"));
    var barrier = new CyclicBarrier(threads);
    var failures = new CopyOnWriteArrayList<String>();
    var runners = new ArrayList<Thread>();
    try {
      for (int t = 0; t < threads; t++) {
        var tag = "P" + t;
        var runner = new Thread(() -> runRounds(tag, shared, barrier, failures), "runner-" + tag);
        runners.add(runner);
        runner.start();
      }
      for (var runner : runners) {
        runner.join(60_000);
      }
      assertThat(failures).as("every capture holds exactly its own handed-over work").isEmpty();
    } finally {
      shared.shutdownNow();
    }
  }

  /**
   * One claimed thread of the parallel shape (the review's E3): per round, its own root, a fork and
   * a snapshot task on the shared pool (handed over), an unwrapped task on the same pool and a raw
   * thread (not handed over); its capture must hold exactly the handed-over work.
   */
  private void runRounds(
      String tag, ExecutorService shared, CyclicBarrier barrier, List<String> failures) {
    var expected =
        List.of(tag + ".main", tag + ".fork", tag + ".inner", tag + ".snap", tag + ".inner");
    try {
      for (int round = 0; round < 20; round++) {
        context.reset();
        barrier.await(10, TimeUnit.SECONDS);
        var captured = oneRound(tag, shared);
        if (!sameMultiset(captured, expected)) {
          failures.add(tag + " round " + round + ": " + captured);
        }
      }
      context.reset();
    } catch (Exception e) {
      failures.add(tag + ": " + e);
    }
  }

  private List<String> oneRound(String tag, ExecutorService shared) throws Exception {
    var root = context.enterMethod(signature(tag, "main"));
    var group = ForkGroup.create(context);
    var fork = shared.submit(group.wrap((Runnable) () -> call(tag, "fork")));
    var snap = shared.submit(context.snapshot().wrap(() -> call(tag, "snap")));
    var unwrapped = shared.submit(() -> call(tag, "unwrapped"));
    fork.get(10, TimeUnit.SECONDS);
    snap.get(10, TimeUnit.SECONDS);
    unwrapped.get(10, TimeUnit.SECONDS);
    runOnRawThread(() -> call(tag, "raw"));
    group.merge();
    context.exitMethodWithReturn("\"ok\"", root);
    return names(context.captureTrace());
  }

  private static boolean sameMultiset(List<String> actual, List<String> expected) {
    var sortedActual = new ArrayList<>(actual);
    var sortedExpected = new ArrayList<>(expected);
    sortedActual.sort(null);
    sortedExpected.sort(null);
    return sortedActual.equals(sortedExpected);
  }

  /** Two ForkGroup members on a two-thread executor, merged under the current span. */
  private void forkAndMerge(String type, String first, String second) throws Exception {
    var forkers = Executors.newFixedThreadPool(2);
    try {
      var group = ForkGroup.create(context);
      var a = forkers.submit(group.wrap((Runnable) () -> call(type, first)));
      var b = forkers.submit(group.wrap((Runnable) () -> call(type, second)));
      a.get(10, TimeUnit.SECONDS);
      b.get(10, TimeUnit.SECONDS);
      group.merge();
    } finally {
      forkers.shutdownNow();
    }
  }

  /** A snapshot-activated worker that has finished its calls and is still inside its scope. */
  private Thread startLiveChild(CountDownLatch release) throws InterruptedException {
    var live = context.snapshot();
    var entered = new CountDownLatch(1);
    var thread =
        new Thread(
            () -> {
              try (var scope = live.activate()) {
                call("Live", "w");
                entered.countDown();
                release.await(10, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            },
            "live-child");
    thread.start();
    assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
    return thread;
  }

  // ------------------------------------------------------------------------------- helpers

  /** One root with one child: the two-span shape every review experiment used. */
  private void call(String type, String method) {
    context.enterMethod(signature(type, method));
    context.enterMethod(signature(type, "inner"));
    context.exitMethodWithReturn("\"done\"");
    context.exitMethodWithReturn("\"done\"");
  }

  private void flood(ExecutorService executor, int roots) throws Exception {
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < roots; i++) {
      futures.add(executor.submit(() -> call("Noise", "work")));
    }
    for (var future : futures) {
      future.get(10, TimeUnit.SECONDS);
    }
  }

  private static void runOnRawThread(Runnable task) throws InterruptedException {
    var thread = new Thread(task, "raw");
    thread.start();
    thread.join(10_000);
  }

  private List<TraceEvent.EnterEvent> rootEntersOn(String threadName) {
    return pipeline.events().stream()
        .filter(TraceEvent.EnterEvent.class::isInstance)
        .map(TraceEvent.EnterEvent.class::cast)
        .filter(e -> e.thread() != null && threadName.equals(e.thread().threadName()))
        .filter(e -> e.spanContext().parentSpanId() == null)
        .toList();
  }

  private int distinctStoredSpans() {
    Set<SpanId> ids =
        pipeline.events().stream()
            .map(TraceEvent::spanIdOf)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
    return ids.size();
  }

  private static List<String> names(TraceTree tree) {
    var names = new ArrayList<String>();
    for (var root : tree.roots()) {
      collect(root, names);
    }
    return names;
  }

  private static void collect(TraceNode node, List<String> names) {
    names.add(node.signature().className() + "." + node.signature().methodName());
    for (var child : node.children()) {
      collect(child, names);
    }
  }

  private static MethodSignature signature(String type, String method) {
    return new MethodSignature(type, method, List.of());
  }
}
