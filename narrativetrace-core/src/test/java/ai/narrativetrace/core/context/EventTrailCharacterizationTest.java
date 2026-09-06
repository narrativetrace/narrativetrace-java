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
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.TraceTreeBuilder;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Characterization tests for identified issues in the Event Trail implementation. */
class EventTrailCharacterizationTest {

  private static final MethodSignature SIG_A =
      new MethodSignature("ServiceA", "methodA", List.of());
  private static final MethodSignature SIG_B =
      new MethodSignature("ServiceB", "methodB", List.of());

  private static final int ITERATIONS = 50_000;

  /** Characterizes the concurrency vulnerability in ThreadLocalNarrativeContext. */
  @Test
  void characterizeConcurrencyVulnerability() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var executor = Executors.newFixedThreadPool(1);
    var startSignal = new CountDownLatch(1);

    SpanId asyncSpanId = context.enterMethod(SIG_A);
    context.detachFrame(asyncSpanId);

    var future =
        executor.submit(
            () -> {
              startSignal.await();
              hammerAsyncExits(context, asyncSpanId);
              return null;
            });

    startSignal.countDown();
    hammerSyncCalls(context);

    future.get(5, TimeUnit.SECONDS);
    executor.shutdownNow();
  }

  private static void hammerAsyncExits(ThreadLocalNarrativeContext ctx, SpanId spanId) {
    for (int i = 0; i < ITERATIONS; i++) {
      ctx.exitMethodWithReturn("\"result\"", spanId);
      ctx.detachFrame(spanId);
    }
  }

  private static void hammerSyncCalls(ThreadLocalNarrativeContext ctx) {
    for (int i = 0; i < ITERATIONS; i++) {
      SpanId h = ctx.enterMethod(SIG_B);
      ctx.exitMethodWithReturn("\"ok\"", h);
    }
  }

  /** Characterizes how the ERRORS level discards the parent context of a failed node. */
  @Test
  void characterizeErrorsLevelContextLoss() {
    SpanContext scA = TestSpanContext.create();
    SpanContext scB = TestSpanContext.childOf(scA);
    List<TraceEvent> events =
        List.of(
            new TraceEvent.EnterEvent(scA, 1000L, SIG_A), // Service (Success)
            new TraceEvent.EnterEvent(scB, 1100L, SIG_B), // Repository (Throws)
            new TraceEvent.ExitEvent(
                scB, 1200L, new TraceOutcome.Threw(new RuntimeException("boom")), null),
            new TraceEvent.ExitEvent(scA, 1300L, new TraceOutcome.Returned("\"ok\""), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.ERRORS);

    // retainErrorPaths preserves the ancestor chain: ServiceA → ServiceB (error)
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("methodA");
    assertThat(tree.roots().get(0).children()).hasSize(1);
    assertThat(tree.roots().get(0).children().get(0).signature().methodName()).isEqualTo("methodB");
  }

  /** Characterizes the inconsistency between Scoped parent (Proxy) and Active Stack (Agent). */
  @Test
  void characterizeAgentParentageLoss() {
    var context = new ThreadLocalNarrativeContext();

    SpanId outerSpanId = context.enterMethod(SIG_A);
    context.runScoped(
        outerSpanId,
        () -> {
          context.detachFrame(outerSpanId);
          context.enterMethod(SIG_B);
          // inner's parent should be outerSpanId (from runScoped), verified via events
          var events = context.events();
          var outerEnter =
              events.stream()
                  .filter(e -> e instanceof TraceEvent.EnterEvent)
                  .map(e -> (TraceEvent.EnterEvent) e)
                  .filter(e -> e.signature().equals(SIG_A))
                  .findFirst()
                  .orElseThrow();
          var innerEnter =
              events.stream()
                  .filter(e -> e instanceof TraceEvent.EnterEvent)
                  .map(e -> (TraceEvent.EnterEvent) e)
                  .filter(e -> e.signature().equals(SIG_B))
                  .findFirst()
                  .orElseThrow();
          assertThat(innerEnter.spanContext().parentSpanId())
              .isEqualTo(outerEnter.spanContext().spanId());
          return null;
        });
  }
}
