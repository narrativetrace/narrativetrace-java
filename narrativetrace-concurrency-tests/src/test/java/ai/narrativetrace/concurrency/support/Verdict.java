/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.core.context.FireAndForgetGroup;
import java.util.List;

/** What every cell asserts about one request's exported trace. */
public final class Verdict {

  private Verdict() {}

  /**
   * The request's export holds its own handed-over work and nothing else: one submitting span
   * ({@code place}) for this request, every worker call nested under it (and none outside it), each
   * carrying this request's value, all on one trace id, no other request's id anywhere.
   */
  public static void ownWorkNestedUnderTheSubmittingSpan(
      Observation observed, String requestId, List<String> skus, String workMethod) {
    var place = observed.only("place");
    assertThat(place.firstArgument())
        .as("the submitting span is this request's")
        .contains(requestId);
    var nested = observed.below(place).stream().filter(n -> n.method().equals(workMethod)).toList();
    assertThat(nested)
        .as("every worker call nests under the submitting span (%s)", observed.text())
        .hasSize(skus.size());
    assertThat(observed.named(workMethod))
        .as("and no worker call sits outside it")
        .hasSameSizeAs(nested);
    for (var sku : skus) {
      assertThat(nested.stream().map(FlatNode::firstArgument))
          .as("the worker call for %s carries this request's value", sku)
          .anyMatch(arg -> arg.contains(requestId + ":" + sku));
    }
    assertNothingFromAnotherRequest(observed, requestId);
  }

  /** One trace id, and no other request's id anywhere in the rendered trace. */
  public static void assertNothingFromAnotherRequest(Observation observed, String requestId) {
    assertThat(TraceFacts.requestIdsIn(observed.text()))
        .as("only %s appears in its own trace", requestId)
        .containsExactly(requestId);
    assertThat(observed.traceIds()).as("the request's work is one trace").hasSize(1);
  }

  /** The worker calls ran on pool threads, not on the request thread. */
  public static void workRanOnWorkerThreads(Observation observed, String workMethod) {
    var workers = observed.named(workMethod);
    assertThat(workers).isNotEmpty();
    for (var worker : workers) {
      assertThat(worker.thread())
          .as("worker call ran on a pool thread, not the request thread")
          .startsWith(Workers.PREFIX);
    }
  }

  /**
   * Fire-and-forget: the request's trace holds the launcher node and nothing of another request;
   * the background work is the group's {@code childRoots()}, each root this request's own.
   */
  public static void launcherInTheTraceAndOwnWorkInTheGroup(
      Exported exported, String requestId, List<String> skus, FireAndForgetGroup group) {
    var place = exported.only("place");
    assertThat(exported.below(place).stream().filter(n -> n.method().equals("fire-and-forget")))
        .as("the launcher node sits under the submitting span (%s)", exported.text())
        .hasSize(1);
    assertNothingFromAnotherRequest(exported, requestId);
    var roots = group.childRoots();
    assertThat(roots).as("one background root per launched task").hasSameSizeAs(skus);
    for (var root : roots) {
      assertThat(root.concurrency().kind()).isEqualTo(ConcurrencyKind.FIRE_AND_FORGET);
      assertThat(TraceFacts.firstArgument(root)).contains(requestId + ":");
      assertThat(root.concurrency().threadName()).startsWith(Workers.PREFIX);
      assertThat(root.spanContext().traceId().toString())
          .as("the background work is on the request's trace")
          .isEqualTo(exported.traceIds().iterator().next());
    }
  }
}
