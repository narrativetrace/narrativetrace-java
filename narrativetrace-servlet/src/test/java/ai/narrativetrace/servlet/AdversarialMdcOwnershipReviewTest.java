/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.BufferedEventConsumer;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Desired-contract reproducer for the 2026-10-22 review; failure is intentional. */
class AdversarialMdcOwnershipReviewTest {

  @Test
  void filterMustPreserveMdcOwnedByAnOuterFilter() throws Exception {
    try (var pipeline = new DualPathPipeline(null, new BufferedEventConsumer(32, false))) {
      var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
      var filter = new NarrativeTraceFilter(context, (tree, request) -> {});
      MDC.put("correlationId", "outer-request");
      try {
        filter.doFilter(
            new StubHttpServletRequest("GET", "/orders"),
            new StubHttpServletResponse(),
            (request, response) -> assertThat(MDC.get("correlationId")).isEqualTo("outer-request"));

        assertThat(MDC.get("correlationId"))
            .as("the outer filter still needs its correlation id for its access log")
            .isEqualTo("outer-request");
      } finally {
        context.reset();
        MDC.clear();
      }
    }
  }
}
