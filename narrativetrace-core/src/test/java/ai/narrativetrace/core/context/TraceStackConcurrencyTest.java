/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TraceStackConcurrencyTest {

  private static final MethodSignature SIG_A =
      new MethodSignature("ServiceA", "methodA", List.of());
  private static final MethodSignature SIG_B =
      new MethodSignature("ServiceB", "methodB", List.of());
  private static final int ITERATIONS = 500;

  private final ThreadLocalNarrativeContext context = new ThreadLocalNarrativeContext();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void concurrentAppendsFromTwoThreadsProduceCompleteTrace() throws Exception {
    SpanId[] asyncSpanIds = createDetachedSpanIds();
    var readySignal = new CountDownLatch(1);

    Future<?> future = submitCallbackCompletions(asyncSpanIds, readySignal);

    readySignal.countDown();
    appendOwnerThreadEvents();

    future.get(5, TimeUnit.SECONDS);

    // All enter+exit pairs from both threads produce complete trace trees
    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(ITERATIONS + ITERATIONS);
  }

  private SpanId[] createDetachedSpanIds() {
    SpanId[] spanIds = new SpanId[ITERATIONS];
    for (int i = 0; i < ITERATIONS; i++) {
      spanIds[i] = context.enterMethod(SIG_A);
      context.detachFrame(spanIds[i]);
    }
    return spanIds;
  }

  private Future<?> submitCallbackCompletions(SpanId[] asyncSpanIds, CountDownLatch readySignal) {
    return executor.submit(
        () -> {
          readySignal.await();
          for (SpanId spanId : asyncSpanIds) {
            context.exitMethodWithReturn("\"async\"", spanId);
          }
          return null;
        });
  }

  private void appendOwnerThreadEvents() {
    for (int i = 0; i < ITERATIONS; i++) {
      SpanId h = context.enterMethod(SIG_B);
      context.exitMethodWithReturn("\"ok\"", h);
    }
  }
}
