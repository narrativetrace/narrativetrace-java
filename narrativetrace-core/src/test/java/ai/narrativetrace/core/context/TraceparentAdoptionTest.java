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
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.Traceparent;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ADR-014 rung 1, cross-process half: what an inbound {@code traceparent} does to a trace. */
class TraceparentAdoptionTest {

  private static final String REMOTE_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String REMOTE_SPAN_ID = "00f067aa0ba902b7";
  private static final Traceparent REMOTE =
      new Traceparent(TraceId.of(REMOTE_TRACE_ID), SpanId.of(REMOTE_SPAN_ID), 1);

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    context.reset();
  }

  @AfterEach
  void tearDown() {
    context.reset();
  }

  @Test
  void adoptedTraceIdBecomesTheTraceIdOfEverySpan() {
    context.adoptTraceparent(REMOTE);

    enter("OrderService", "placeOrder");
    enter("PaymentService", "charge");
    context.exitMethodWithReturn("\"paid\"");
    context.exitMethodWithReturn("\"placed\"");

    var root = context.captureTrace().roots().get(0);
    assertThat(root.spanContext().traceId()).isEqualTo(TraceId.of(REMOTE_TRACE_ID));
    assertThat(root.children().get(0).spanContext().traceId())
        .isEqualTo(TraceId.of(REMOTE_TRACE_ID));
  }

  @Test
  void adoptedSpanIdParentsTheRootAndOnlyTheRoot() {
    context.adoptTraceparent(REMOTE);

    enter("OrderService", "placeOrder");
    enter("PaymentService", "charge");
    context.exitMethodWithReturn("\"paid\"");
    context.exitMethodWithReturn("\"placed\"");

    var root = context.captureTrace().roots().get(0);
    assertThat(root.spanContext().parentSpanId()).isEqualTo(SpanId.of(REMOTE_SPAN_ID));
    assertThat(root.children().get(0).spanContext().parentSpanId())
        .as("a nested call's parent is its local caller, never the remote span")
        .isEqualTo(root.spanContext().spanId());
  }

  @Test
  void adoptedTraceIdIsVisibleBeforeAnySpanIsEntered() {
    context.adoptTraceparent(REMOTE);

    assertThat(context.traceId()).isEqualTo(TraceId.of(REMOTE_TRACE_ID));
  }

  @Test
  void adoptingNothingLeavesTheTraceToGenerateItsOwnId() {
    context.adoptTraceparent(null);

    enter("OrderService", "placeOrder");
    context.exitMethodWithReturn("\"placed\"");

    var root = context.captureTrace().roots().get(0);
    assertThat(root.spanContext().traceId()).isNotNull();
    assertThat(root.spanContext().traceId()).isNotEqualTo(TraceId.of(REMOTE_TRACE_ID));
    assertThat(root.spanContext().parentSpanId()).isNull();
  }

  @Test
  void adoptedTraceFlagsReachTheSpanContext() {
    context.adoptTraceparent(
        new Traceparent(TraceId.of(REMOTE_TRACE_ID), SpanId.of(REMOTE_SPAN_ID), 0));

    enter("OrderService", "placeOrder");
    context.exitMethodWithReturn("\"placed\"");

    var root = context.captureTrace().roots().get(0);
    assertThat(root.spanContext().traceFlags()).isZero();
    assertThat(root.spanContext().sampled()).isFalse();
  }

  @Test
  void aLocallyStartedTraceIsSampled() {
    enter("OrderService", "placeOrder");
    context.exitMethodWithReturn("\"placed\"");

    var root = context.captureTrace().roots().get(0);
    assertThat(root.spanContext().sampled())
        .as("NarrativeTrace recorded the span, so downstream services must be told it is sampled")
        .isTrue();
  }

  @Test
  void resetDropsTheAdoptedContext() {
    context.adoptTraceparent(REMOTE);
    context.reset();

    assertThat(context.traceId()).isNotEqualTo(TraceId.of(REMOTE_TRACE_ID));
  }

  @Test
  void outboundHeaderCarriesTheCurrentSpanAsTheParentForTheNextService() {
    context.adoptTraceparent(REMOTE);
    var spanId = enter("OrderService", "placeOrder");

    var outbound = context.outboundTraceparent();

    assertThat(outbound).isNotNull();
    assertThat(outbound.traceId()).isEqualTo(TraceId.of(REMOTE_TRACE_ID));
    assertThat(outbound.parentSpanId()).isEqualTo(spanId);
    assertThat(outbound.traceFlags()).isEqualTo(1);
    context.exitMethodWithReturn("\"placed\"");
  }

  @Test
  void outboundHeaderFallsBackToTheAdoptedSpanWhenNoLocalSpanIsOpen() {
    context.adoptTraceparent(REMOTE);

    assertThat(context.outboundTraceparent()).isEqualTo(REMOTE);
  }

  @Test
  void outboundHeaderIsAbsentWhenThereIsNothingToCorrelate() {
    assertThat(context.outboundTraceparent())
        .as("a fresh thread with no span has no parent id to name, so it sends no header")
        .isNull();
  }

  /**
   * The mirror case: a filter that eagerly asked for the trace id (to put it in MDC) has a trace
   * but no span yet. A header naming a parent span that does not exist is worse than no header.
   */
  @Test
  void outboundHeaderIsAbsentWhenTheTraceHasAnIdButNoOpenSpan() {
    var traceId = context.traceId();

    assertThat(traceId).isNotNull();
    assertThat(context.outboundTraceparent()).isNull();
  }

  /**
   * A scope can name a parent span without the stack ever having acquired a trace id — {@code
   * beginScope} sets one and not the other. Emitting a header there would mean building a {@code
   * Traceparent} with a null trace id, which throws.
   */
  @Test
  void outboundHeaderIsAbsentWhenAScopeHasAParentButTheStackHasNoTraceId() {
    context.beginScope(SpanId.generate());

    assertThat(context.outboundTraceparent())
        .as("a parent span without a trace is not a trace context")
        .isNull();
  }

  @Test
  void outboundHeaderNamesTheInnermostOpenSpan() {
    enter("OrderService", "placeOrder");
    var inner = enter("PaymentService", "charge");

    assertThat(context.outboundTraceparent().parentSpanId()).isEqualTo(inner);

    context.exitMethodWithReturn("\"paid\"");
    context.exitMethodWithReturn("\"placed\"");
  }

  @Test
  void outboundHeaderRoundTripsThroughTheWireFormat() {
    context.adoptTraceparent(REMOTE);
    enter("OrderService", "placeOrder");

    var header = context.outboundTraceparent().format();

    assertThat(Traceparent.parse(header)).isEqualTo(context.outboundTraceparent());
    context.exitMethodWithReturn("\"placed\"");
  }

  @Test
  void noopContextAdoptsNothingAndSendsNothing() {
    NoopNarrativeContext.INSTANCE.adoptTraceparent(REMOTE);

    assertThat(NoopNarrativeContext.INSTANCE.outboundTraceparent()).isNull();
  }

  private SpanId enter(String className, String methodName) {
    return context.enterMethod(
        new MethodSignature(
            className, methodName, List.of(new ParameterCapture("id", "\"42\"", false))));
  }
}
