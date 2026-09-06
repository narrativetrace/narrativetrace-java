/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring.web;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import ai.narrativetrace.spring.EnableNarrativeTrace;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The Spring-Web auto-configuration must inherit the servlet filter's cross-process adoption — the
 * wiring is what a Spring application actually runs, so it needs its own assertion.
 */
class TraceparentWebFilterTest {

  private static final String REMOTE_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String REMOTE_SPAN_ID = "00f067aa0ba902b7";
  private static final String HEADER = "00-" + REMOTE_TRACE_ID + "-" + REMOTE_SPAN_ID + "-01";

  private static final AtomicReference<TraceTree> EXPORTED = new AtomicReference<>();

  @Configuration
  @EnableNarrativeTrace
  static class TestConfig {

    @Bean
    TraceExporter capturingExporter() {
      return (tree, requestContext) -> EXPORTED.set(tree);
    }
  }

  @Test
  void theWiredFilterContinuesTheCallersTrace() throws Exception {
    var request = new MockHttpServletRequest("GET", "/orders");
    request.addHeader(Traceparent.HEADER_NAME, HEADER);

    var root = runRequest(request).roots().get(0);

    assertThat(root.spanContext().traceId()).isEqualTo(TraceId.of(REMOTE_TRACE_ID));
    assertThat(root.spanContext().parentSpanId()).isEqualTo(SpanId.of(REMOTE_SPAN_ID));
  }

  @Test
  void theWiredFilterStartsAFreshTraceWithoutAHeader() throws Exception {
    var root = runRequest(new MockHttpServletRequest("GET", "/orders")).roots().get(0);

    assertThat(root.spanContext().traceId()).isNotEqualTo(TraceId.of(REMOTE_TRACE_ID));
    assertThat(root.spanContext().parentSpanId()).isNull();
  }

  @Test
  void theWiredFilterIgnoresAMalformedHeader() throws Exception {
    var request = new MockHttpServletRequest("GET", "/orders");
    request.addHeader(
        Traceparent.HEADER_NAME, "ff-" + REMOTE_TRACE_ID + "-" + REMOTE_SPAN_ID + "-01");

    var root = runRequest(request).roots().get(0);

    assertThat(root.spanContext().traceId()).isNotEqualTo(TraceId.of(REMOTE_TRACE_ID));
  }

  private TraceTree runRequest(MockHttpServletRequest request) throws Exception {
    EXPORTED.set(null);
    try (var ctx =
        new AnnotationConfigApplicationContext(
            TestConfig.class, NarrativeTraceWebConfiguration.class)) {
      var filter = ctx.getBean(NarrativeTraceFilter.class);
      var context = ctx.getBean(NarrativeContext.class);
      filter.doFilter(
          request,
          new MockHttpServletResponse(),
          (req, res) -> {
            var spanId =
                context.enterMethod(new MethodSignature("OrderService", "list", List.of()));
            context.exitMethodWithReturn("\"ok\"", spanId);
          });
      return EXPORTED.get();
    }
  }
}
