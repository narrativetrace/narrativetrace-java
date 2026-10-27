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
import ai.narrativetrace.concurrency.support.ServletAssembly;
import ai.narrativetrace.concurrency.support.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Request scope: the servlet {@code NarrativeTraceFilter}. Route: the proxy. One test per
 * hand-over, single request and eight overlapping ones. {@code @Async} is not here: it is a Spring
 * mechanism and the servlet filter alone has no Spring context (it is in the Spring Web matrix).
 */
class ServletProxyMatrixTest {

  private static final int OVERLAPPING = 8;

  @Test
  void servletForkGroup_workersNestUnderTheSubmittingSpanWithTheirValues() throws Exception {
    try (var assembly = ServletAssembly.of(Handover.FORK_GROUP, 4)) {
      var id = Requests.id(1);
      assembly.serve(id);
      var exported = assembly.exporter.of(id);
      Verdict.ownWorkNestedUnderTheSubmittingSpan(exported, id, SKUS, "reserve");
      Verdict.workRanOnWorkerThreads(exported, "reserve");
    }
  }

  @Test
  void servletForkGroup_eightOverlappingRequestsExportOnlyTheirOwnWork() {
    try (var assembly = ServletAssembly.of(Handover.FORK_GROUP, 4)) {
      var ids = Requests.ids(100, OVERLAPPING);
      assembly.serveConcurrently(ids);
      assertEachOwn(assembly, ids, "reserve");
    }
  }

  @Test
  void servletFireAndForget_launcherInTheTraceAndTheWorkInTheGroup() throws Exception {
    try (var assembly = ServletAssembly.of(Handover.FIRE_AND_FORGET, 4)) {
      var id = Requests.id(2);
      assembly.serve(id);
      Verdict.launcherInTheTraceAndOwnWorkInTheGroup(
          assembly.exporter.of(id), id, SKUS, assembly.fireAndForget().groupOf(id));
    }
  }

  @Test
  void servletFireAndForget_eightOverlappingRequestsKeepTheirOwnLauncherAndGroup() {
    try (var assembly = ServletAssembly.of(Handover.FIRE_AND_FORGET, 4)) {
      var ids = Requests.ids(200, OVERLAPPING);
      assembly.serveConcurrently(ids);
      for (var id : ids) {
        Verdict.launcherInTheTraceAndOwnWorkInTheGroup(
            assembly.exporter.of(id), id, SKUS, assembly.fireAndForget().groupOf(id));
      }
    }
  }

  @Test
  void servletDecoratedExecutor_workersNestUnderTheSubmittingSpanWithTheirValues()
      throws Exception {
    try (var assembly = ServletAssembly.of(Handover.DECORATED_EXECUTOR, 4)) {
      var id = Requests.id(3);
      assembly.serve(id);
      var exported = assembly.exporter.of(id);
      Verdict.ownWorkNestedUnderTheSubmittingSpan(exported, id, SKUS, "reserve");
      Verdict.workRanOnWorkerThreads(exported, "reserve");
    }
  }

  @Test
  void servletDecoratedExecutor_eightOverlappingRequestsExportOnlyTheirOwnWork() {
    try (var assembly = ServletAssembly.of(Handover.DECORATED_EXECUTOR, 4)) {
      var ids = Requests.ids(300, OVERLAPPING);
      assembly.serveConcurrently(ids);
      assertEachOwn(assembly, ids, "reserve");
    }
  }

  private static void assertEachOwn(ServletAssembly assembly, List<String> ids, String workMethod) {
    for (var id : ids) {
      var exported = assembly.exporter.of(id);
      Verdict.ownWorkNestedUnderTheSubmittingSpan(exported, id, SKUS, workMethod);
      Verdict.workRanOnWorkerThreads(exported, workMethod);
    }
  }
}
