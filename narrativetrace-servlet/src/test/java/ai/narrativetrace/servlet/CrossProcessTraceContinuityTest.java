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
import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * The whole point of the feature, end to end: one story crossing a process boundary. Service A
 * traces a call, formats the outbound header from its own context; service B's filter adopts it.
 * Both trees must belong to one trace, and B's root must hang off A's span.
 */
class CrossProcessTraceContinuityTest {

  private final ThreadLocalNarrativeContext serviceA = new ThreadLocalNarrativeContext();
  private final ThreadLocalNarrativeContext serviceB = new ThreadLocalNarrativeContext();

  @AfterEach
  void tearDown() {
    MDC.clear();
    serviceA.reset();
    serviceB.reset();
  }

  @Test
  void oneStoryCrossesTheProcessBoundary() throws Exception {
    var callerSpan = serviceA.enterMethod(new MethodSignature("OrderService", "place", List.of()));
    String header = serviceA.outboundTraceparent().format();

    var calleeTree = serveRequestOn(serviceB, header);

    serviceA.exitMethodWithReturn("\"placed\"", callerSpan);
    var callerTree = serviceA.captureTrace();

    var callerRoot = callerTree.roots().get(0);
    var calleeRoot = calleeTree.roots().get(0);
    assertThat(calleeRoot.spanContext().traceId())
        .as("both services narrate one story, so they share one trace id")
        .isEqualTo(callerRoot.spanContext().traceId());
    assertThat(calleeRoot.spanContext().parentSpanId())
        .as("the callee's root hangs off the exact span that made the call")
        .isEqualTo(callerSpan);
  }

  @Test
  void theCalleeIsAGoodCitizenForTheNextHopToo() throws Exception {
    var callerSpan = serviceA.enterMethod(new MethodSignature("OrderService", "place", List.of()));
    String firstHop = serviceA.outboundTraceparent().format();
    var secondHop = new AtomicReference<String>();

    var chain =
        (jakarta.servlet.FilterChain)
            (req, res) -> {
              var spanId =
                  serviceB.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
              secondHop.set(serviceB.outboundTraceparent().format());
              serviceB.exitMethodWithReturn("\"charged\"", spanId);
            };
    new NarrativeTraceFilter(serviceB, (tree, req) -> {})
        .doFilter(
            new StubHttpServletRequest("POST", "/pay")
                .withHeader(Traceparent.HEADER_NAME, firstHop),
            new StubHttpServletResponse(),
            chain);
    serviceA.exitMethodWithReturn("\"placed\"", callerSpan);

    var forwarded = Traceparent.parse(secondHop.get());
    assertThat(forwarded).isNotNull();
    assertThat(forwarded.traceId()).isEqualTo(Traceparent.parse(firstHop).traceId());
    assertThat(forwarded.parentSpanId())
        .as("hop two is parented on the callee's own span, not on the caller's")
        .isNotEqualTo(callerSpan);
  }

  @Test
  void anUnsampledUpstreamDecisionSurvivesTheHopAndTheNextOne() throws Exception {
    var callerSpan = serviceA.enterMethod(new MethodSignature("OrderService", "place", List.of()));
    var outbound = serviceA.outboundTraceparent();
    var unsampled = new Traceparent(outbound.traceId(), outbound.parentSpanId(), 0);
    var forwarded = new AtomicReference<Traceparent>();

    var chain =
        (jakarta.servlet.FilterChain)
            (req, res) -> {
              var spanId =
                  serviceB.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
              forwarded.set(serviceB.outboundTraceparent());
              serviceB.exitMethodWithReturn("\"charged\"", spanId);
            };
    var exported = new AtomicReference<TraceTree>();
    new NarrativeTraceFilter(serviceB, (tree, req) -> exported.set(tree))
        .doFilter(
            new StubHttpServletRequest("POST", "/pay")
                .withHeader(Traceparent.HEADER_NAME, unsampled.format()),
            new StubHttpServletResponse(),
            chain);
    serviceA.exitMethodWithReturn("\"placed\"", callerSpan);

    assertThat(exported.get().roots().get(0).spanContext().sampled()).isFalse();
    assertThat(forwarded.get().sampled())
        .as("upstream owns the sampling decision; we forward it, we do not overrule it")
        .isFalse();
  }

  private TraceTree serveRequestOn(ThreadLocalNarrativeContext context, String header)
      throws Exception {
    var exported = new AtomicReference<TraceTree>();
    var chain =
        (jakarta.servlet.FilterChain)
            (req, res) -> {
              var spanId =
                  serviceB.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
              serviceB.exitMethodWithReturn("\"charged\"", spanId);
            };
    new NarrativeTraceFilter(context, (tree, req) -> exported.set(tree))
        .doFilter(
            new StubHttpServletRequest("POST", "/pay").withHeader(Traceparent.HEADER_NAME, header),
            new StubHttpServletResponse(),
            chain);
    return exported.get();
  }
}
