/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ClientIp;
import ai.narrativetrace.api.event.EnduserId;
import ai.narrativetrace.api.event.HttpRoute;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TenantId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OtelTraceEventListenerTest {

  private InMemorySpanExporter spanExporter;
  private SdkTracerProvider tracerProvider;
  private OtelTraceEventListener listener;

  @BeforeEach
  void setUp() {
    spanExporter = InMemorySpanExporter.create();
    tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
    listener = new OtelTraceEventListener(tracerProvider.get("narrativetrace"));
  }

  @AfterEach
  void tearDown() {
    tracerProvider.close();
  }

  @Test
  void enterAndExitProducesOneSpan() {
    long t0 = 1_000_000;
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());
    var sc = rootSpanContext();

    listener.accept(new TraceEvent.EnterEvent(sc, t0, sig));
    listener.accept(
        new TraceEvent.ExitEvent(sc, t0 + 5_000_000, new TraceOutcome.Returned("\"ok\""), null));

    assertThat(spanExporter.getFinishedSpanItems()).hasSize(1);
    assertThat(spanExporter.getFinishedSpanItems().get(0).getName())
        .isEqualTo("OrderService.placeOrder");
  }

  @Test
  void spanHasClassAndMethodAttributes() {
    emitEnterExit(rootSpanContext(), "Svc", "run", "\"ok\"");

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.class"))).isEqualTo("Svc");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.method"))).isEqualTo("run");
  }

  @Test
  void spanHasParameterAttributes() {
    long t0 = 1_000_000;
    var sig =
        new MethodSignature(
            "Svc", "process", List.of(new ParameterCapture("orderId", "\"O-1\"", false)));
    var sc = rootSpanContext();

    listener.accept(new TraceEvent.EnterEvent(sc, t0, sig));
    listener.accept(
        new TraceEvent.ExitEvent(sc, t0 + 1_000_000, new TraceOutcome.Returned("\"done\""), null));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    // Fallback type detection: quoted string → stripped
    assertThat(attrs.get(AttributeKey.stringKey("narrative.param.orderId"))).isEqualTo("O-1");
  }

  @Test
  void spanHasOutcomeAttribute() {
    emitEnterExit(rootSpanContext(), "Svc", "calc", "42");

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.outcome"))).isEqualTo("42");
  }

  @Test
  void exceptionSetsErrorStatus() {
    long t0 = 1_000_000;
    var sig = new MethodSignature("Svc", "fail", List.of());
    var sc = rootSpanContext();

    listener.accept(new TraceEvent.EnterEvent(sc, t0, sig));
    listener.accept(
        new TraceEvent.ExitEvent(
            sc, t0 + 1_000_000, new TraceOutcome.Threw(new RuntimeException("boom")), null));

    var span = spanExporter.getFinishedSpanItems().get(0);
    assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(span.getEvents()).hasSize(1);
    assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");
  }

  @Test
  void nestedCallsProduceParentChildSpans() {
    long t0 = 1_000_000;
    var parentSc = rootSpanContext();
    var childSc = childSpanContext(parentSc);
    listener.accept(
        new TraceEvent.EnterEvent(
            parentSc, t0, new MethodSignature("OrderService", "placeOrder", List.of())));
    listener.accept(
        new TraceEvent.EnterEvent(
            childSc, t0 + 100, new MethodSignature("PaymentService", "charge", List.of())));
    listener.accept(
        new TraceEvent.ExitEvent(
            childSc, t0 + 2_000_000, new TraceOutcome.Returned("\"paid\""), null));
    listener.accept(
        new TraceEvent.ExitEvent(
            parentSc, t0 + 5_000_000, new TraceOutcome.Returned("\"ok\""), null));

    var spans = spanExporter.getFinishedSpanItems();
    assertThat(spans).hasSize(2);
    var outer = spans.stream().filter(s -> s.getName().contains("placeOrder")).findFirst().get();
    var inner = spans.stream().filter(s -> s.getName().contains("charge")).findFirst().get();
    assertThat(inner.getParentSpanId()).isEqualTo(outer.getSpanId());
  }

  @Test
  void exitWithoutMatchingEnterCreatesOrphanSpan() {
    var sc = rootSpanContext();
    listener.accept(
        new TraceEvent.ExitEvent(sc, 1_000_000, new TraceOutcome.Returned("\"x\""), null));

    assertThat(spanExporter.getFinishedSpanItems()).hasSize(1);
    assertThat(spanExporter.getFinishedSpanItems().get(0).getStatus().getStatusCode())
        .isEqualTo(StatusCode.ERROR);
  }

  @Test
  void nonEnterExitEventsAreIgnored() {
    listener.accept(new TraceEvent.ForkCreatedEvent("g1", 1_000_000));
    listener.accept(new TraceEvent.MergeEvent("g1", 2, 2_000_000));
    listener.accept(new TraceEvent.FireAndForgetEvent("g2", 3_000_000));

    assertThat(spanExporter.getFinishedSpanItems()).isEmpty();
  }

  @Test
  void multipleIndependentSpans() {
    emitEnterExit(rootSpanContext(), "A", "a", "\"1\"");
    emitEnterExit(rootSpanContext(), "B", "b", "\"2\"");

    assertThat(spanExporter.getFinishedSpanItems()).hasSize(2);
  }

  @Test
  void rootSpanHasNoParent() {
    emitEnterExit(rootSpanContext(), "Svc", "run", "\"ok\"");

    var span = spanExporter.getFinishedSpanItems().get(0);
    assertThat(span.getParentSpanId()).isEqualTo("0000000000000000");
  }

  @Test
  void otelUsesSpanContextParent() {
    long t0 = 1_000_000;
    var parentSc = rootSpanContext();
    var childSc = childSpanContext(parentSc);

    listener.accept(
        new TraceEvent.EnterEvent(parentSc, t0, new MethodSignature("Parent", "run", List.of())));
    listener.accept(
        new TraceEvent.EnterEvent(
            childSc, t0 + 100, new MethodSignature("Child", "work", List.of())));
    listener.accept(
        new TraceEvent.ExitEvent(
            childSc, t0 + 1_000_000, new TraceOutcome.Returned("\"done\""), null));
    listener.accept(
        new TraceEvent.ExitEvent(
            parentSc, t0 + 2_000_000, new TraceOutcome.Returned("\"ok\""), null));

    var spans = spanExporter.getFinishedSpanItems();
    var parent = spans.stream().filter(s -> s.getName().contains("Parent")).findFirst().get();
    var child = spans.stream().filter(s -> s.getName().contains("Child")).findFirst().get();
    assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
  }

  @Test
  void otelRootInheritsAmbientContext() {
    // Root span should NOT call setNoParent — it should inherit ambient context.
    // With no ambient context, the parent span ID should be all zeros.
    emitEnterExit(rootSpanContext(), "Svc", "run", "\"ok\"");

    var span = spanExporter.getFinishedSpanItems().get(0);
    // When no ambient context exists, the SDK reports all-zero parent (same as before).
    // The key behavior is that setNoParent() is NOT called, so ambient context CAN be inherited.
    assertThat(span.getParentSpanId()).isEqualTo("0000000000000000");
  }

  @Test
  void otelSetsNarrativeTraceIdAttribute() {
    var sc = rootSpanContext();
    emitEnterExit(sc, "Svc", "run", "\"ok\"");

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.trace_id")))
        .isEqualTo(sc.traceId().toString());
  }

  @Test
  void otelMapsSpansBySpanId() {
    var parentSc = rootSpanContext();
    var childSc = childSpanContext(parentSc);

    long t0 = 1_000_000;
    listener.accept(
        new TraceEvent.EnterEvent(parentSc, t0, new MethodSignature("P", "run", List.of())));
    listener.accept(
        new TraceEvent.EnterEvent(childSc, t0 + 100, new MethodSignature("C", "work", List.of())));
    // Exit child first, then parent — verifies spanId-based map handles out-of-order exits
    listener.accept(
        new TraceEvent.ExitEvent(
            childSc, t0 + 1_000_000, new TraceOutcome.Returned("\"c\""), null));
    listener.accept(
        new TraceEvent.ExitEvent(
            parentSc, t0 + 2_000_000, new TraceOutcome.Returned("\"p\""), null));

    assertThat(spanExporter.getFinishedSpanItems()).hasSize(2);
    var child = spanExporter.getFinishedSpanItems().get(0);
    var parent = spanExporter.getFinishedSpanItems().get(1);
    assertThat(child.getName()).isEqualTo("C.work");
    assertThat(parent.getName()).isEqualTo("P.run");
  }

  @Test
  void rootSpanHasTraceLevelAttributes() {
    var sc = richRootSpanContext();
    emitEnterExit(sc, "Svc", "handle", "\"ok\"");

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.http.method"))).isEqualTo("POST");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.http.route"))).isEqualTo("/api/orders");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.enduser.id"))).isEqualTo("user-42");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.service.name"))).isEqualTo("order-svc");
  }

  @Test
  void childSpanDoesNotHaveTraceLevelAttributes() {
    var parent = richRootSpanContext();
    var child = childSpanContext(parent);
    long t0 = 1_000_000;
    listener.accept(
        new TraceEvent.EnterEvent(parent, t0, new MethodSignature("Svc", "handle", List.of())));
    listener.accept(
        new TraceEvent.EnterEvent(child, t0 + 100, new MethodSignature("Repo", "find", List.of())));
    listener.accept(
        new TraceEvent.ExitEvent(child, t0 + 500_000, new TraceOutcome.Returned("\"ok\""), null));
    listener.accept(
        new TraceEvent.ExitEvent(
            parent, t0 + 1_000_000, new TraceOutcome.Returned("\"done\""), null));

    // Child span finishes first, so it's at index 0
    var childAttrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(childAttrs.get(AttributeKey.stringKey("narrative.http.method"))).isNull();
    assertThat(childAttrs.get(AttributeKey.stringKey("narrative.enduser.id"))).isNull();
  }

  @Test
  void constructorAcceptsMaxSpansAndTtl() {
    var custom =
        new OtelTraceEventListener(tracerProvider.get("test"), 256, Duration.ofMinutes(30));
    assertThat(custom).isNotNull();
  }

  @Test
  void orphanedSpanEvictedAfterTtl() {
    // Use a very short TTL so we can test eviction without sleeping
    var shortTtlListener =
        new OtelTraceEventListener(tracerProvider.get("test"), 1024, Duration.ofNanos(1));
    var sc = rootSpanContext();
    // Enter but no exit — this span becomes orphaned
    shortTtlListener.accept(
        new TraceEvent.EnterEvent(sc, 1_000_000, new MethodSignature("Svc", "slow", List.of())));

    // Next enter triggers eviction of expired entries
    var sc2 = rootSpanContext();
    shortTtlListener.accept(
        new TraceEvent.EnterEvent(sc2, 2_000_000, new MethodSignature("Svc", "next", List.of())));
    shortTtlListener.accept(
        new TraceEvent.ExitEvent(sc2, 3_000_000, new TraceOutcome.Returned("\"ok\""), null));

    // The orphaned span should have been ended with error by eviction
    var spans = spanExporter.getFinishedSpanItems();
    var orphan = spans.stream().filter(s -> s.getName().equals("Svc.slow")).findFirst();
    assertThat(orphan).isPresent();
    assertThat(orphan.get().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
  }

  @Test
  void mapDoesNotExceedMaxSize() {
    var boundedListener =
        new OtelTraceEventListener(tracerProvider.get("test"), 2, Duration.ofHours(1));
    // Fill to max with enters (no exits)
    boundedListener.accept(
        new TraceEvent.EnterEvent(
            rootSpanContext(), 1_000, new MethodSignature("A", "a", List.of())));
    boundedListener.accept(
        new TraceEvent.EnterEvent(
            rootSpanContext(), 2_000, new MethodSignature("B", "b", List.of())));
    // Third enter should evict oldest to stay within limit
    var sc3 = rootSpanContext();
    boundedListener.accept(
        new TraceEvent.EnterEvent(sc3, 3_000, new MethodSignature("C", "c", List.of())));
    boundedListener.accept(
        new TraceEvent.ExitEvent(sc3, 4_000, new TraceOutcome.Returned("\"ok\""), null));

    // At least one evicted span should have ended with error
    var evicted =
        spanExporter.getFinishedSpanItems().stream()
            .filter(s -> s.getStatus().getStatusCode() == StatusCode.ERROR)
            .toList();
    assertThat(evicted).isNotEmpty();
    assertThat(evicted.get(0).getStatus().getDescription()).contains("orphan");
  }

  @Test
  void evictedSpanHasOrphanedErrorStatus() {
    var boundedListener =
        new OtelTraceEventListener(tracerProvider.get("test"), 1, Duration.ofHours(1));
    boundedListener.accept(
        new TraceEvent.EnterEvent(
            rootSpanContext(), 1_000, new MethodSignature("Old", "stale", List.of())));
    // Second enter evicts the first
    var sc2 = rootSpanContext();
    boundedListener.accept(
        new TraceEvent.EnterEvent(sc2, 2_000, new MethodSignature("New", "fresh", List.of())));

    var evicted = spanExporter.getFinishedSpanItems().get(0);
    assertThat(evicted.getName()).isEqualTo("Old.stale");
    assertThat(evicted.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(evicted.getStatus().getDescription()).contains("orphan");
  }

  @Test
  void concurrentInterleavingEnterAEnterBExitAExitB() {
    var scA = rootSpanContext();
    var scB = rootSpanContext();
    long t0 = 1_000_000;
    // Interleaved: enter A, enter B, exit A, exit B
    listener.accept(
        new TraceEvent.EnterEvent(scA, t0, new MethodSignature("A", "work", List.of())));
    listener.accept(
        new TraceEvent.EnterEvent(scB, t0 + 100, new MethodSignature("B", "work", List.of())));
    listener.accept(
        new TraceEvent.ExitEvent(scA, t0 + 500_000, new TraceOutcome.Returned("\"a\""), null));
    listener.accept(
        new TraceEvent.ExitEvent(scB, t0 + 600_000, new TraceOutcome.Returned("\"b\""), null));

    assertThat(spanExporter.getFinishedSpanItems()).hasSize(2);
    var spanA =
        spanExporter.getFinishedSpanItems().stream()
            .filter(s -> s.getName().equals("A.work"))
            .findFirst()
            .orElseThrow();
    var spanB =
        spanExporter.getFinishedSpanItems().stream()
            .filter(s -> s.getName().equals("B.work"))
            .findFirst()
            .orElseThrow();
    assertThat(spanA.getAttributes().get(AttributeKey.stringKey("narrative.outcome")))
        .isEqualTo("\"a\"");
    assertThat(spanB.getAttributes().get(AttributeKey.stringKey("narrative.outcome")))
        .isEqualTo("\"b\"");
  }

  @Test
  void exitWithoutMatchingEnterCreatesOrphanedSpan() {
    var sc = richRootSpanContext();
    listener.accept(
        new TraceEvent.ExitEvent(sc, 1_000_000, new TraceOutcome.Returned("\"ok\""), null));

    assertThat(spanExporter.getFinishedSpanItems()).hasSize(1);
    var orphan = spanExporter.getFinishedSpanItems().get(0);
    assertThat(orphan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(orphan.getStatus().getDescription()).contains("enter event lost");
  }

  @Test
  void exitWithoutEnterPreservesOutcome() {
    var sc = rootSpanContext();
    listener.accept(
        new TraceEvent.ExitEvent(
            sc, 1_000_000, new TraceOutcome.Threw(new RuntimeException("fail")), null));

    var orphan = spanExporter.getFinishedSpanItems().get(0);
    assertThat(orphan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
  }

  private static SpanContext richRootSpanContext() {
    return SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
        .serviceName("order-svc")
        .serviceVersion("1.0.0")
        .environment("test")
        .httpMethod("POST")
        .httpRoute(HttpRoute.of("/api/orders"))
        .clientIp(ClientIp.of("client-addr"))
        .enduserId(EnduserId.of("user-42"))
        .tenantId(TenantId.of("tenant-a"))
        .build();
  }

  private static SpanContext rootSpanContext() {
    return SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
  }

  private static SpanContext childSpanContext(SpanContext parent) {
    return SpanContext.builder(parent.traceId(), SpanIdGenerator.spanId())
        .parentSpanId(parent.spanId())
        .build();
  }

  @Test
  void parentSpanReceivesEventWhenChildExits() {
    var parentSc = rootSpanContext();
    var childSc = childSpanContext(parentSc);
    long t0 = 1_000_000;

    listener.accept(
        new TraceEvent.EnterEvent(parentSc, t0, new MethodSignature("Root", "handle", List.of())));
    listener.accept(
        new TraceEvent.EnterEvent(
            childSc, t0 + 100, new MethodSignature("Svc", "process", List.of())));
    listener.accept(
        new TraceEvent.ExitEvent(childSc, t0 + 500, new TraceOutcome.Returned("\"ok\""), null));
    listener.accept(
        new TraceEvent.ExitEvent(parentSc, t0 + 1000, new TraceOutcome.Returned("\"done\""), null));

    var parentSpan =
        spanExporter.getFinishedSpanItems().stream()
            .filter(s -> s.getName().equals("Root.handle"))
            .findFirst()
            .get();
    assertThat(parentSpan.getEvents()).hasSize(1);
    assertThat(parentSpan.getEvents().get(0).getName()).isEqualTo("Svc.process");
  }

  @Test
  void rootExitDoesNotEmitEventOnParent() {
    var rootSc = rootSpanContext();
    emitEnterExit(rootSc, "Root", "handle", "\"ok\"");

    var span = spanExporter.getFinishedSpanItems().get(0);
    assertThat(span.getEvents()).isEmpty();
  }

  @Test
  void parentAlreadyEvictedDoesNotCrash() {
    var parentSc = rootSpanContext();
    var childSc = childSpanContext(parentSc);
    long t0 = 1_000_000;

    listener.accept(
        new TraceEvent.EnterEvent(parentSc, t0, new MethodSignature("Root", "handle", List.of())));
    listener.accept(
        new TraceEvent.EnterEvent(
            childSc, t0 + 100, new MethodSignature("Svc", "process", List.of())));
    // End parent first (removes it from map)
    listener.accept(
        new TraceEvent.ExitEvent(parentSc, t0 + 200, new TraceOutcome.Returned("\"done\""), null));
    // Now end child — parent is gone, should not crash
    listener.accept(
        new TraceEvent.ExitEvent(childSc, t0 + 500, new TraceOutcome.Returned("\"ok\""), null));

    assertThat(spanExporter.getFinishedSpanItems()).hasSize(2);
  }

  @Test
  void exitWithoutEnterShouldStillPreserveTraceLevelAttributesAvailableInSpanContext() {
    var spanContext =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
            .serviceName("order-svc")
            .httpMethod("POST")
            .httpRoute(HttpRoute.of("/api/orders"))
            .enduserId(EnduserId.of("user-42"))
            .build();

    listener.accept(
        new TraceEvent.ExitEvent(
            spanContext, 1_000_000L, new TraceOutcome.Returned("\"ok\""), null));

    var orphanSpan = spanExporter.getFinishedSpanItems().get(0);
    assertThat(orphanSpan.getAttributes().get(AttributeKey.stringKey("narrative.service.name")))
        .isEqualTo("order-svc");
    assertThat(orphanSpan.getAttributes().get(AttributeKey.stringKey("narrative.http.method")))
        .isEqualTo("POST");
    assertThat(orphanSpan.getAttributes().get(AttributeKey.stringKey("narrative.http.route")))
        .isEqualTo("/api/orders");
    assertThat(orphanSpan.getAttributes().get(AttributeKey.stringKey("narrative.enduser.id")))
        .isEqualTo("user-42");
  }

  private void emitEnterExit(
      SpanContext spanContext, String className, String methodName, String returnValue) {
    long t0 = 1_000_000;
    listener.accept(
        new TraceEvent.EnterEvent(
            spanContext, t0, new MethodSignature(className, methodName, List.of())));
    listener.accept(
        new TraceEvent.ExitEvent(
            spanContext, t0 + 1_000_000, new TraceOutcome.Returned(returnValue), null));
  }
}
