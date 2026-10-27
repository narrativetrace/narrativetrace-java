/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.matrix;

import static ai.narrativetrace.concurrency.support.Requests.SKUS;

import ai.narrativetrace.concurrency.support.Handover;
import ai.narrativetrace.concurrency.support.Requests;
import ai.narrativetrace.concurrency.support.SpringWebAssembly;
import ai.narrativetrace.concurrency.support.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Request scope: Spring Web ({@code narrativetrace-spring-web}'s filter bean, in a Spring context
 * with {@code @EnableNarrativeTrace}). Route: the proxy, via the bean post-processor. One test per
 * hand-over, single request and eight overlapping ones.
 */
class SpringWebProxyMatrixTest {

  private static final int OVERLAPPING = 8;

  @Test
  void springWebForkGroup_workersNestUnderTheSubmittingSpanWithTheirValues() throws Exception {
    try (var assembly = SpringWebAssembly.of(Handover.FORK_GROUP)) {
      singleRequestIsOwn(assembly, 11, "reserve");
    }
  }

  @Test
  void springWebForkGroup_eightOverlappingRequestsExportOnlyTheirOwnWork() {
    try (var assembly = SpringWebAssembly.of(Handover.FORK_GROUP)) {
      overlappingRequestsAreOwn(assembly, 1100, "reserve");
    }
  }

  @Test
  void springWebFireAndForget_launcherInTheTraceAndTheWorkInTheGroup() throws Exception {
    try (var assembly = SpringWebAssembly.of(Handover.FIRE_AND_FORGET)) {
      var id = Requests.id(12);
      assembly.serve(id);
      Verdict.launcherInTheTraceAndOwnWorkInTheGroup(
          assembly.exporter().of(id), id, SKUS, assembly.fireAndForget().groupOf(id));
    }
  }

  @Test
  void springWebFireAndForget_eightOverlappingRequestsKeepTheirOwnLauncherAndGroup() {
    try (var assembly = SpringWebAssembly.of(Handover.FIRE_AND_FORGET)) {
      var ids = Requests.ids(1200, OVERLAPPING);
      assembly.serveConcurrently(ids);
      for (var id : ids) {
        Verdict.launcherInTheTraceAndOwnWorkInTheGroup(
            assembly.exporter().of(id), id, SKUS, assembly.fireAndForget().groupOf(id));
      }
    }
  }

  @Test
  void springWebDecoratedExecutor_workersNestUnderTheSubmittingSpanWithTheirValues()
      throws Exception {
    try (var assembly = SpringWebAssembly.of(Handover.DECORATED_EXECUTOR)) {
      singleRequestIsOwn(assembly, 13, "reserve");
    }
  }

  @Test
  void springWebDecoratedExecutor_eightOverlappingRequestsExportOnlyTheirOwnWork() {
    try (var assembly = SpringWebAssembly.of(Handover.DECORATED_EXECUTOR)) {
      overlappingRequestsAreOwn(assembly, 1300, "reserve");
    }
  }

  @Test
  void springWebAsync_workersNestUnderTheSubmittingSpanWithTheirValues() throws Exception {
    try (var assembly = SpringWebAssembly.of(Handover.ASYNC)) {
      singleRequestIsOwn(assembly, 14, "reserveAsync");
    }
  }

  @Test
  void springWebAsync_eightOverlappingRequestsExportOnlyTheirOwnWork() {
    try (var assembly = SpringWebAssembly.of(Handover.ASYNC)) {
      overlappingRequestsAreOwn(assembly, 1400, "reserveAsync");
    }
  }

  private static void singleRequestIsOwn(SpringWebAssembly assembly, int n, String workMethod)
      throws Exception {
    var id = Requests.id(n);
    assembly.serve(id);
    var exported = assembly.exporter().of(id);
    Verdict.ownWorkNestedUnderTheSubmittingSpan(exported, id, SKUS, workMethod);
    Verdict.workRanOnWorkerThreads(exported, workMethod);
  }

  private static void overlappingRequestsAreOwn(
      SpringWebAssembly assembly, int first, String workMethod) {
    List<String> ids = Requests.ids(first, OVERLAPPING);
    assembly.serveConcurrently(ids);
    for (var id : ids) {
      var exported = assembly.exporter().of(id);
      Verdict.ownWorkNestedUnderTheSubmittingSpan(exported, id, SKUS, workMethod);
      Verdict.workRanOnWorkerThreads(exported, workMethod);
    }
  }
}
