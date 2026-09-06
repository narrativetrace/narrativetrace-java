/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.SpanId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Same-package test for the adoption ledger itself: the cap is a boundary condition an end-to-end
 * test can only reach with 10,000 real spans, so the semantics are pinned here.
 */
class TraceStackAdoptionTest {

  @Test
  void adoptsABatchThatFitsAndSaysSo() {
    var stack = new ThreadLocalNarrativeContext.TraceStack(5);

    assertThat(stack.adopt(spanIds(3)))
        .as("the caller acts on the answer: a false means the caller must discard the batch")
        .isTrue();
  }

  /**
   * A closed stack belongs to a request that has ended, so it takes nothing more — and says so, so
   * the worker discards rather than hands over into a stack nobody will capture from.
   *
   * <p><b>@edgeCase</b> Not counted as a refusal: the request ended, it did not overflow, and the
   * context counts that as a discard instead.
   */
  @Test
  void aClosedStackRefusesAdoptionWithoutCountingItAsAnOverflow() {
    var stack = new ThreadLocalNarrativeContext.TraceStack(5);
    stack.close();

    assertThat(stack.adopt(spanIds(2))).isFalse();
    assertThat(stack.adoptedSpanIds()).isEmpty();
    assertThat(stack.refusedScopeCount()).isZero();
    assertThat(stack.refusedSpanCount()).isZero();
  }

  @Test
  void aRefusedBatchSaysSoToo() {
    var stack = new ThreadLocalNarrativeContext.TraceStack(2);

    assertThat(stack.adopt(spanIds(3))).isFalse();
  }

  @Test
  void adoptsABatchThatFits() {
    var stack = new ThreadLocalNarrativeContext.TraceStack(5);

    stack.adopt(spanIds(3));

    assertThat(stack.adoptedSpanIds()).hasSize(3);
    assertThat(stack.refusedScopeCount()).isZero();
    assertThat(stack.refusedSpanCount()).isZero();
  }

  @Test
  void adoptsUpToTheCapExactly() {
    var stack = new ThreadLocalNarrativeContext.TraceStack(5);

    stack.adopt(spanIds(5));

    assertThat(stack.adoptedSpanIds()).hasSize(5);
    assertThat(stack.refusedScopeCount()).isZero();
  }

  @Test
  void refusesAWholeBatchThatWouldOverflow() {
    var stack = new ThreadLocalNarrativeContext.TraceStack(5);
    stack.adopt(spanIds(3));

    stack.adopt(spanIds(3));

    // All three, or none — never a fragment that orphans a child from its parent.
    assertThat(stack.adoptedSpanIds()).hasSize(3);
    assertThat(stack.refusedScopeCount()).isOne();
    assertThat(stack.refusedSpanCount()).isEqualTo(3);
  }

  @Test
  void refusesABatchLargerThanTheCapOutright() {
    var stack = new ThreadLocalNarrativeContext.TraceStack(5);

    stack.adopt(spanIds(9));

    assertThat(stack.adoptedSpanIds()).isEmpty();
    assertThat(stack.refusedScopeCount()).isOne();
    assertThat(stack.refusedSpanCount()).isEqualTo(9);
  }

  @Test
  void aSmallerBatchStillFitsAfterARefusal() {
    var stack = new ThreadLocalNarrativeContext.TraceStack(5);
    stack.adopt(spanIds(3));
    stack.adopt(spanIds(3));

    stack.adopt(spanIds(2));

    assertThat(stack.adoptedSpanIds()).hasSize(5);
    assertThat(stack.refusedScopeCount()).isOne();
    assertThat(stack.refusedSpanCount()).isEqualTo(3);
  }

  @Test
  void anEmptyBatchIsNeitherAdoptedNorRefused() {
    var stack = new ThreadLocalNarrativeContext.TraceStack(5);

    stack.adopt(Set.of());

    assertThat(stack.adoptedSpanIds()).isEmpty();
    assertThat(stack.refusedScopeCount()).isZero();
  }

  @Test
  void countsEveryRefusedScopeSeparately() {
    var stack = new ThreadLocalNarrativeContext.TraceStack(2);

    stack.adopt(spanIds(2));
    stack.adopt(spanIds(1));
    stack.adopt(spanIds(4));

    assertThat(stack.refusedScopeCount()).isEqualTo(2);
    assertThat(stack.refusedSpanCount()).isEqualTo(5);
  }

  @Test
  void concurrentWorkersNeverExceedTheCapNorLoseACount() throws Exception {
    var stack = new ThreadLocalNarrativeContext.TraceStack(50);
    int workers = 16;
    var start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(workers);
    try {
      for (int i = 0; i < workers; i++) {
        pool.submit(
            () -> {
              start.await();
              stack.adopt(spanIds(10));
              return null;
            });
      }
      start.countDown();
      pool.shutdown();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    // 16 batches of 10 against a cap of 50: five fit, the rest are refused whole.
    assertThat(stack.adoptedSpanIds()).hasSize(50);
    assertThat(stack.refusedScopeCount()).isEqualTo(11);
    assertThat(stack.refusedSpanCount()).isEqualTo(110);
  }

  @Test
  void aStackThatNeverAdoptsReportsNothing() {
    var stack = new ThreadLocalNarrativeContext.TraceStack(5);

    assertThat(stack.adoptedSpanIds()).isEmpty();
    assertThat(stack.refusedScopeCount()).isZero();
    assertThat(stack.refusedSpanCount()).isZero();
  }

  private static Set<SpanId> spanIds(int count) {
    var ids = new LinkedHashSet<SpanId>();
    for (int i = 0; i < count; i++) {
      ids.add(SpanId.generate());
    }
    return ids;
  }
}
