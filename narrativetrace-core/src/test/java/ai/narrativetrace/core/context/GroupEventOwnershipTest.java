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
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.pipeline.BufferedEventConsumer;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import java.util.List;
import org.junit.jupiter.api.Test;

/** B-158: group lifecycle events belong to the span that launched the group. */
class GroupEventOwnershipTest {

  @Test
  void groupEventsCarryTheLaunchingSpanAsOwner() {
    try (var pipeline = new DualPathPipeline(null, new BufferedEventConsumer(32, false))) {
      var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
      var parent = context.enterMethod(new MethodSignature("Service", "request", List.of()));
      var group = ForkGroup.create(context);
      group.wrap((Runnable) () -> {}).run();
      group.merge();
      FireAndForgetGroup.create(context, "Service");
      context.exitMethodWithReturn("\"done\"", parent);
      pipeline.flush();

      var groupEvents =
          pipeline.events().stream()
              .filter(
                  e ->
                      e instanceof TraceEvent.ForkCreatedEvent
                          || e instanceof TraceEvent.MergeEvent
                          || e instanceof TraceEvent.FireAndForgetEvent)
              .toList();
      assertThat(groupEvents).hasSize(3);
      assertThat(groupEvents).allSatisfy(e -> assertThat(TraceEvent.spanIdOf(e)).isEqualTo(parent));
      context.reset();
    }
  }

  @Test
  void completedFireAndForgetRequestsLeaveNoLifecycleEvents() {
    try (var pipeline = new DualPathPipeline(null, new BufferedEventConsumer(32, false))) {
      var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
      for (int request = 0; request < 20; request++) {
        var parent = context.enterMethod(new MethodSignature("Service", "request", List.of()));
        FireAndForgetGroup.create(context, "Service");
        context.exitMethodWithReturn("\"done\"", parent);
        context.reset();
      }
      pipeline.flush();

      assertThat(
              pipeline.events().stream()
                  .filter(e -> e instanceof TraceEvent.FireAndForgetEvent)
                  .count())
          .isZero();
    }
  }

  @Test
  void aCaptureSeesItsOwnGroupEventsAndNoOtherRequests() throws Exception {
    try (var pipeline = new DualPathPipeline(null, new BufferedEventConsumer(32, false))) {
      var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
      var other =
          new Thread(
              () -> {
                var span = context.enterMethod(new MethodSignature("Other", "req", List.of()));
                ForkGroup.create(context).merge();
                context.exitMethodWithReturn("1", span);
              });
      other.start();
      other.join();
      var parent = context.enterMethod(new MethodSignature("Service", "request", List.of()));
      ForkGroup.create(context).merge();
      context.exitMethodWithReturn("1", parent);

      var mine =
          context.events().stream()
              .filter(e -> e instanceof TraceEvent.ForkCreatedEvent)
              .map(TraceEvent::spanIdOf)
              .toList();
      assertThat(mine).containsExactly(parent);
      context.reset();
    }
  }
}
