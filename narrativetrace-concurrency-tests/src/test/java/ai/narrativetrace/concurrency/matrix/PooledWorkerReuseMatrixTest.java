/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.matrix;

import static ai.narrativetrace.concurrency.support.Requests.SKUS;
import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.concurrency.support.Exported;
import ai.narrativetrace.concurrency.support.Handover;
import ai.narrativetrace.concurrency.support.Requests;
import ai.narrativetrace.concurrency.support.ServletAssembly;
import ai.narrativetrace.concurrency.support.SpringWebAssembly;
import ai.narrativetrace.concurrency.support.TraceFacts;
import ai.narrativetrace.concurrency.support.Verdict;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * A pooled worker reused across requests: one pool thread, two consecutive requests handed over to
 * it. Each request exports only its own work and its own trace id — the thread keeps no trace from
 * the request before.
 */
class PooledWorkerReuseMatrixTest {

  private static final String FIRST = Requests.id(21);
  private static final String SECOND = Requests.id(22);

  @Test
  void servletForkGroup_theSameWorkerServesTwoRequestsEachExportsOnlyItsOwn() throws Exception {
    try (var assembly = ServletAssembly.of(Handover.FORK_GROUP, 1)) {
      assembly.serve(FIRST);
      assembly.serve(SECOND);
      assertSameWorkerDifferentTraces(
          assembly.exporter.of(FIRST), assembly.exporter.of(SECOND), "reserve");
    }
  }

  @Test
  void servletDecoratedExecutor_theSameWorkerServesTwoRequestsEachExportsOnlyItsOwn()
      throws Exception {
    try (var assembly = ServletAssembly.of(Handover.DECORATED_EXECUTOR, 1)) {
      assembly.serve(FIRST);
      assembly.serve(SECOND);
      assertSameWorkerDifferentTraces(
          assembly.exporter.of(FIRST), assembly.exporter.of(SECOND), "reserve");
    }
  }

  @Test
  void servletFireAndForget_theSameWorkerServesTwoRequestsEachGroupHoldsOnlyItsOwn()
      throws Exception {
    try (var assembly = ServletAssembly.of(Handover.FIRE_AND_FORGET, 1)) {
      assembly.serve(FIRST);
      assembly.serve(SECOND);
      var first = assembly.exporter.of(FIRST);
      var second = assembly.exporter.of(SECOND);
      Verdict.launcherInTheTraceAndOwnWorkInTheGroup(
          first, FIRST, SKUS, assembly.fireAndForget().groupOf(FIRST));
      Verdict.launcherInTheTraceAndOwnWorkInTheGroup(
          second, SECOND, SKUS, assembly.fireAndForget().groupOf(SECOND));
      assertThat(first.traceIds()).doesNotContainAnyElementsOf(second.traceIds());
    }
  }

  @Test
  void springWebAsync_theSameWorkerServesTwoRequestsEachExportsOnlyItsOwn() throws Exception {
    try (var assembly = SpringWebAssembly.of(Handover.ASYNC, 1)) {
      assembly.serve(FIRST);
      assembly.serve(SECOND);
      assertSameWorkerDifferentTraces(
          assembly.exporter().of(FIRST), assembly.exporter().of(SECOND), "reserveAsync");
    }
  }

  @Test
  void springWebDecoratedExecutor_theSameWorkerServesTwoRequestsEachExportsOnlyItsOwn()
      throws Exception {
    try (var assembly = SpringWebAssembly.of(Handover.DECORATED_EXECUTOR, 1)) {
      assembly.serve(FIRST);
      assembly.serve(SECOND);
      assertSameWorkerDifferentTraces(
          assembly.exporter().of(FIRST), assembly.exporter().of(SECOND), "reserve");
    }
  }

  @Test
  void springWebForkGroup_theSameWorkerServesTwoRequestsEachExportsOnlyItsOwn() throws Exception {
    try (var assembly = SpringWebAssembly.of(Handover.FORK_GROUP, 1)) {
      assembly.serve(FIRST);
      assembly.serve(SECOND);
      assertSameWorkerDifferentTraces(
          assembly.exporter().of(FIRST), assembly.exporter().of(SECOND), "reserve");
    }
  }

  private static void assertSameWorkerDifferentTraces(
      Exported first, Exported second, String workMethod) {
    Verdict.ownWorkNestedUnderTheSubmittingSpan(first, FIRST, SKUS, workMethod);
    Verdict.ownWorkNestedUnderTheSubmittingSpan(second, SECOND, SKUS, workMethod);
    assertThat(workerThreads(first, workMethod))
        .as("both requests were handed over to the one pool thread")
        .hasSize(1)
        .isEqualTo(workerThreads(second, workMethod));
    assertThat(first.traceIds())
        .as("the reused thread gives the second request its own trace id")
        .doesNotContainAnyElementsOf(second.traceIds());
  }

  private static Set<String> workerThreads(Exported exported, String workMethod) {
    var names = new TreeSet<String>();
    for (var node : TraceFacts.named(TraceFacts.all(exported.tree()), workMethod)) {
      names.add(node.thread().threadName());
    }
    return names;
  }
}
