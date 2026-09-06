/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Cross-process trace continuity: what an inbound {@code traceparent} does to a request. */
class TraceparentFilterTest {

  private static final String REMOTE_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String REMOTE_SPAN_ID = "00f067aa0ba902b7";
  private static final String HEADER = "00-" + REMOTE_TRACE_ID + "-" + REMOTE_SPAN_ID + "-01";

  private final ThreadLocalNarrativeContext context = new ThreadLocalNarrativeContext();
  private final AtomicReference<TraceTree> exported = new AtomicReference<>();

  @AfterEach
  void tearDown() {
    MDC.clear();
    context.reset();
  }

  @Test
  void continuesTheCallersTraceWhenTheHeaderIsPresent() throws Exception {
    runRequest(new StubHttpServletRequest("GET", "/orders").withHeader("traceparent", HEADER));

    var root = exported.get().roots().get(0);
    assertThat(root.spanContext().traceId()).isEqualTo(TraceId.of(REMOTE_TRACE_ID));
    assertThat(root.spanContext().parentSpanId()).isEqualTo(SpanId.of(REMOTE_SPAN_ID));
  }

  @Test
  void matchesTheHeaderNameCaseInsensitively() throws Exception {
    runRequest(new StubHttpServletRequest("GET", "/orders").withHeader("TraceParent", HEADER));

    assertThat(exported.get().roots().get(0).spanContext().traceId())
        .isEqualTo(TraceId.of(REMOTE_TRACE_ID));
  }

  @Test
  void startsAFreshTraceWhenNoHeaderArrives() throws Exception {
    runRequest(new StubHttpServletRequest("GET", "/orders"));

    var root = exported.get().roots().get(0);
    assertThat(root.spanContext().traceId()).isNotEqualTo(TraceId.of(REMOTE_TRACE_ID));
    assertThat(root.spanContext().parentSpanId()).isNull();
  }

  @Test
  void startsAFreshTraceAndServesTheRequestWhenTheHeaderIsMalformed() throws Exception {
    var chainRan = new AtomicBoolean(false);
    var request =
        new StubHttpServletRequest("GET", "/orders").withHeader("traceparent", "00-nonsense-01");

    runRequest(request, chainRan);

    assertThat(chainRan).isTrue();
    var root = exported.get().roots().get(0);
    assertThat(root.spanContext().traceId()).isNotEqualTo(TraceId.of(REMOTE_TRACE_ID));
    assertThat(root.spanContext().parentSpanId()).isNull();
  }

  @Test
  void refusesAnAllZeroTraceIdAndStartsFresh() throws Exception {
    var request =
        new StubHttpServletRequest("GET", "/orders")
            .withHeader("traceparent", "00-" + "0".repeat(32) + "-" + REMOTE_SPAN_ID + "-01");

    runRequest(request);

    assertThat(exported.get().roots().get(0).spanContext().traceId())
        .isNotEqualTo(TraceId.of("0".repeat(32)));
  }

  @Test
  void publishesTheAdoptedTraceIdToMdcForDownstreamLogs() throws Exception {
    var mdcTraceId = new AtomicReference<String>();
    FilterChain chain =
        (req, res) -> {
          mdcTraceId.set(MDC.get("traceId"));
          traceOneMethod();
        };

    new NarrativeTraceFilter(context, (tree, req) -> exported.set(tree))
        .doFilter(
            new StubHttpServletRequest("GET", "/orders").withHeader("traceparent", HEADER),
            new StubHttpServletResponse(),
            chain);

    assertThat(mdcTraceId.get()).isEqualTo(REMOTE_TRACE_ID);
  }

  @Test
  void offersAnOutboundHeaderNamingTheServicesOwnSpan() throws Exception {
    var outbound = new AtomicReference<Traceparent>();
    FilterChain chain =
        (req, res) -> {
          var spanId = context.enterMethod(new MethodSignature("OrderService", "list", List.of()));
          outbound.set(context.outboundTraceparent());
          context.exitMethodWithReturn("\"ok\"", spanId);
        };

    new NarrativeTraceFilter(context, (tree, req) -> exported.set(tree))
        .doFilter(
            new StubHttpServletRequest("GET", "/orders").withHeader("traceparent", HEADER),
            new StubHttpServletResponse(),
            chain);

    assertThat(outbound.get().traceId()).isEqualTo(TraceId.of(REMOTE_TRACE_ID));
    assertThat(outbound.get().parentSpanId())
        .as("the next service's parent is this service's span, not the one we adopted")
        .isNotEqualTo(SpanId.of(REMOTE_SPAN_ID));
    assertThat(outbound.get().format()).startsWith("00-" + REMOTE_TRACE_ID + "-");
  }

  @Test
  void doesNotLeakTheAdoptedTraceIntoTheNextRequest() throws Exception {
    runRequest(new StubHttpServletRequest("GET", "/orders").withHeader("traceparent", HEADER));
    runRequest(new StubHttpServletRequest("GET", "/orders"));

    var root = exported.get().roots().get(0);
    assertThat(root.spanContext().traceId()).isNotEqualTo(TraceId.of(REMOTE_TRACE_ID));
    assertThat(root.spanContext().parentSpanId()).isNull();
  }

  private void runRequest(StubHttpServletRequest request) throws Exception {
    runRequest(request, new AtomicBoolean());
  }

  private void runRequest(StubHttpServletRequest request, AtomicBoolean chainRan) throws Exception {
    FilterChain chain =
        (req, res) -> {
          chainRan.set(true);
          traceOneMethod();
        };
    new NarrativeTraceFilter(context, (tree, req) -> exported.set(tree))
        .doFilter(request, new StubHttpServletResponse(), chain);
  }

  private void traceOneMethod() {
    var spanId = context.enterMethod(new MethodSignature("OrderService", "list", List.of()));
    context.exitMethodWithReturn("\"ok\"", spanId);
  }
}
