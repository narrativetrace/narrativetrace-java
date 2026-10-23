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
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.pipeline.BufferedEventConsumer;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Desired-contract reproducer for the 2026-10-22 review; failure is intentional. */
class AdversarialLifecycleRetentionReviewTest {

  @Test
  void completedForkJoinRequestsMustNotAccumulateLifecycleEvents() {
    try (var pipeline = new DualPathPipeline(null, new BufferedEventConsumer(32, false))) {
      var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
      for (int request = 0; request < 100; request++) {
        var parent = context.enterMethod(new MethodSignature("Service", "request", List.of()));
        var group = ForkGroup.create(context);
        group
            .wrap(
                (Runnable)
                    () -> {
                      var child =
                          context.enterMethod(new MethodSignature("Worker", "work", List.of()));
                      context.exitMethodWithReturn("42", child);
                    })
            .run();
        group.merge();
        context.exitMethodWithReturn("\"done\"", parent);
        assertThat(context.captureTrace().roots()).hasSize(1);
        context.reset();
      }
      pipeline.flush();

      assertThat(pipeline.droppedEventCount()).isZero();
      assertThat(context.retainedSpanCount()).isZero();
      assertThat(pipeline.events().size())
          .as("all 100 requests ended; group events need request ownership too")
          .isZero();
    }
  }
}
