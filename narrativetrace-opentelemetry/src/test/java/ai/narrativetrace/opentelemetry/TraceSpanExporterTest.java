/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.event.ClientIp;
import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.EnduserId;
import ai.narrativetrace.api.event.HttpRoute;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.SessionId;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TenantId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.render.TraceNamer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TraceSpanExporterTest {

  private InMemorySpanExporter spanExporter;
  private TraceSpanExporter exporter;
  private SdkTracerProvider tracerProvider;

  @BeforeEach
  void setUp() {
    spanExporter = InMemorySpanExporter.create();
    tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
    var tracer = tracerProvider.get("narrativetrace");
    exporter = new TraceSpanExporter(tracer);
  }

  @AfterEach
  void tearDown() {
    tracerProvider.close();
  }

  @Test
  void createsOneSpanPerTraceNode() {
    var node1 = node("OrderService", "placeOrder");
    var node2 = node("PaymentService", "charge");

    exporter.export(List.of(node1, node2));

    assertThat(spanExporter.getFinishedSpanItems()).hasSize(2);
  }

  @Test
  void spanNameIsClassNameDotMethodName() {
    exporter.export(List.of(node("OrderService", "placeOrder")));

    var span = spanExporter.getFinishedSpanItems().get(0);
    assertThat(span.getName()).isEqualTo("OrderService.placeOrder");
  }

  @Test
  void spanHasClassAndMethodAttributes() {
    exporter.export(List.of(node("OrderService", "placeOrder")));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.class"))).isEqualTo("OrderService");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.method"))).isEqualTo("placeOrder");
  }

  @Test
  void spanHasParameterAttributes() {
    var sig =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(
                new ParameterCapture("orderId", "\"O-123\"", false),
                new ParameterCapture("quantity", "3", false)));
    var traceNode = new TraceNode(sig, List.of(), new TraceOutcome.Returned("null"));

    exporter.export(List.of(traceNode));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    // Fallback type detection: quoted string → stripped, numeric → long
    assertThat(attrs.get(AttributeKey.stringKey("narrative.param.orderId"))).isEqualTo("O-123");
    assertThat(attrs.get(AttributeKey.longKey("narrative.param.quantity"))).isEqualTo(3L);
  }

  @Test
  void spanHasOutcomeAttributeForReturnedValue() {
    var traceNode =
        new TraceNode(
            new MethodSignature("Svc", "calc", List.of()),
            List.of(),
            new TraceOutcome.Returned("42"));

    exporter.export(List.of(traceNode));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.outcome"))).isEqualTo("42");
  }

  @Test
  void exceptionNodeSetsSpanStatusToErrorWithEvent() {
    var traceNode =
        new TraceNode(
            new MethodSignature("Svc", "fail", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("boom")));

    exporter.export(List.of(traceNode));

    var span = spanExporter.getFinishedSpanItems().get(0);
    assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(span.getEvents()).hasSize(1);
    assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");
  }

  @Test
  void childTraceNodesCreateChildSpans() {
    var child = node("PaymentService", "charge");
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"ok\""));

    exporter.export(List.of(parent));

    var spans = spanExporter.getFinishedSpanItems();
    assertThat(spans).hasSize(2);
    var parentSpan =
        spans.stream().filter(s -> s.getName().contains("placeOrder")).findFirst().get();
    var childSpan = spans.stream().filter(s -> s.getName().contains("charge")).findFirst().get();
    assertThat(childSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
  }

  @Test
  void sequentialNodeHasNoConcurrencyAttributes() {
    exporter.export(List.of(node("Svc", "work")));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.concurrency.groupId"))).isNull();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.concurrency.kind"))).isNull();
  }

  @Test
  void concurrentNodeHasGroupIdAttribute() {
    exporter.export(List.of(concurrentNode("Svc", "work", "fork-1", ConcurrencyKind.FORK_JOIN)));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.concurrency.groupId")))
        .isEqualTo("fork-1");
  }

  @Test
  void concurrentNodeHasKindAttribute() {
    exporter.export(List.of(concurrentNode("Svc", "work", "fork-1", ConcurrencyKind.FORK_JOIN)));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.concurrency.kind")))
        .isEqualTo("FORK_JOIN");
  }

  @Test
  void concurrentNodeHasThreadIdAttribute() {
    exporter.export(List.of(concurrentNode("Svc", "work", "fork-1", ConcurrencyKind.FORK_JOIN)));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.longKey("narrative.concurrency.threadId"))).isEqualTo(42L);
  }

  @Test
  void fireAndForgetNodeHasCorrectKindAttribute() {
    exporter.export(
        List.of(concurrentNode("Svc", "notify", "fanf-1", ConcurrencyKind.FIRE_AND_FORGET)));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.concurrency.kind")))
        .isEqualTo("FIRE_AND_FORGET");
  }

  @Test
  void forkJoinMembersShareSameGroupId() {
    var member1 = concurrentNode("SvcA", "calcA", "fork-5", ConcurrencyKind.FORK_JOIN);
    var member2 = concurrentNode("SvcB", "calcB", "fork-5", ConcurrencyKind.FORK_JOIN);

    exporter.export(List.of(member1, member2));

    var spans = spanExporter.getFinishedSpanItems();
    var groupIds =
        spans.stream()
            .map(
                s -> s.getAttributes().get(AttributeKey.stringKey("narrative.concurrency.groupId")))
            .toList();
    assertThat(groupIds).containsExactly("fork-5", "fork-5");
  }

  @Test
  void virtualThreadNodeHasVirtualTrue() {
    var info = new ConcurrencyInfo("fork-1", "vthread-1", 99L, true, ConcurrencyKind.FORK_JOIN);
    var traceNode =
        new TraceNode(
            new MethodSignature("Svc", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("null"),
            100_000L,
            0L,
            info);

    exporter.export(List.of(traceNode));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.booleanKey("narrative.concurrency.virtual"))).isTrue();
  }

  @Test
  void spanHasDurationMillisAttribute() {
    var traceNode =
        new TraceNode(
            new MethodSignature("Svc", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("null"),
            5_000_000L,
            0L,
            null);

    exporter.export(List.of(traceNode));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.doubleKey("narrative.duration_ms"))).isEqualTo(5.0);
  }

  @Test
  void incompleteOutcomeSetsInFlightAttribute() {
    var traceNode =
        new TraceNode(
            new MethodSignature("Svc", "pending", List.of()),
            List.of(),
            new TraceOutcome.Incomplete());

    exporter.export(List.of(traceNode));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.outcome"))).isEqualTo("in-flight");
  }

  @Test
  void emptyTraceListProducesNoSpans() {
    exporter.export(List.of());

    assertThat(spanExporter.getFinishedSpanItems()).isEmpty();
  }

  private static TraceNode node(String className, String methodName) {
    return new TraceNode(
        new MethodSignature(className, methodName, List.of()),
        List.of(),
        new TraceOutcome.Returned("null"));
  }

  @Test
  void rootSpanHasTraceLevelAttributes() {
    var rootSc = rootSpanContext();
    var root = nodeWithContext("Svc", "handle", rootSc, List.of());
    exporter.export(List.of(root));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.http.method"))).isEqualTo("POST");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.http.route"))).isEqualTo("/api/orders");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.client_ip"))).isEqualTo("client-addr");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.enduser.id"))).isEqualTo("user-42");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.session.id"))).isEqualTo("sess-1");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.tenant.id"))).isEqualTo("tenant-a");
  }

  @Test
  void rootSpanHasServiceAttributes() {
    var rootSc = rootSpanContext();
    var root = nodeWithContext("Svc", "handle", rootSc, List.of());
    exporter.export(List.of(root));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.service.name"))).isEqualTo("order-svc");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.service.version"))).isEqualTo("1.0.0");
    assertThat(attrs.get(AttributeKey.stringKey("narrative.service.environment")))
        .isEqualTo("test");
  }

  @Test
  void childSpanDoesNotHaveTraceLevelAttributes() {
    var rootSc = rootSpanContext();
    var childSc = childSpanContext(rootSc);
    var child = nodeWithContext("Repo", "findAll", childSc, List.of());
    var root = nodeWithContext("Svc", "handle", rootSc, List.of(child));
    exporter.export(List.of(root));

    // Child finishes before parent — it's at index 0
    var childAttrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(childAttrs.get(AttributeKey.stringKey("narrative.http.method"))).isNull();
    assertThat(childAttrs.get(AttributeKey.stringKey("narrative.http.route"))).isNull();
    assertThat(childAttrs.get(AttributeKey.stringKey("narrative.enduser.id"))).isNull();
  }

  private static SpanContext rootSpanContext() {
    return SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
        .serviceName("order-svc")
        .serviceVersion("1.0.0")
        .environment("test")
        .httpMethod("POST")
        .httpRoute(HttpRoute.of("/api/orders"))
        .clientIp(ClientIp.of("client-addr"))
        .enduserId(EnduserId.of("user-42"))
        .sessionId(SessionId.of("sess-1"))
        .tenantId(TenantId.of("tenant-a"))
        .build();
  }

  private static SpanContext childSpanContext(SpanContext parent) {
    return SpanContext.builder(parent.traceId(), SpanIdGenerator.spanId())
        .parentSpanId(parent.spanId())
        .serviceName("order-svc")
        .httpMethod("POST")
        .httpRoute(HttpRoute.of("/api/orders"))
        .enduserId(EnduserId.of("user-42"))
        .build();
  }

  private static TraceNode nodeWithContext(
      String className, String methodName, SpanContext spanContext, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature(className, methodName, List.of()),
        children,
        new TraceOutcome.Returned("null"),
        100_000L,
        0L,
        null,
        spanContext);
  }

  @Test
  void structuredIntegerParamUsesLongKey() {
    var sig =
        new MethodSignature(
            "Svc",
            "calc",
            List.of(new ParameterCapture("count", "42", false, new RenderedValue.LongVal(42L))));
    exporter.export(List.of(new TraceNode(sig, List.of(), new TraceOutcome.Returned("null"))));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.longKey("narrative.param.count"))).isEqualTo(42L);
  }

  @Test
  void structuredDoubleParamUsesDoubleKey() {
    var sig =
        new MethodSignature(
            "Svc",
            "calc",
            List.of(
                new ParameterCapture(
                    "total", "129.99", false, new RenderedValue.DoubleVal(129.99))));
    exporter.export(List.of(new TraceNode(sig, List.of(), new TraceOutcome.Returned("null"))));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.doubleKey("narrative.param.total"))).isEqualTo(129.99);
  }

  @Test
  void structuredBooleanParamUsesBooleanKey() {
    var sig =
        new MethodSignature(
            "Svc",
            "check",
            List.of(
                new ParameterCapture("active", "true", false, new RenderedValue.BooleanVal(true))));
    exporter.export(List.of(new TraceNode(sig, List.of(), new TraceOutcome.Returned("null"))));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.booleanKey("narrative.param.active"))).isTrue();
  }

  @Test
  void structuredObjectParamFlattensToDotSeparatedKeys() {
    var fields = new java.util.LinkedHashMap<String, RenderedValue>();
    fields.put("id", new RenderedValue.StringVal("X"));
    fields.put("total", new RenderedValue.DoubleVal(99.9));
    var objVal = new RenderedValue.ObjectVal("Order", fields);
    var sig =
        new MethodSignature(
            "Svc",
            "place",
            List.of(new ParameterCapture("order", "Order(id: X, total: 99.9)", false, objVal)));
    exporter.export(List.of(new TraceNode(sig, List.of(), new TraceOutcome.Returned("null"))));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.param.order.id"))).isEqualTo("X");
    assertThat(attrs.get(AttributeKey.doubleKey("narrative.param.order.total"))).isEqualTo(99.9);
  }

  @Test
  void redactedParamIsSkipped() {
    var sig =
        new MethodSignature(
            "Svc", "login", List.of(new ParameterCapture("password", "[REDACTED]", true)));
    exporter.export(List.of(new TraceNode(sig, List.of(), new TraceOutcome.Returned("null"))));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.param.password"))).isNull();
  }

  @Test
  void suppressedParamIsSkipped() {
    var sig = new MethodSignature("Svc", "work", List.of(new ParameterCapture("x", "", false)));
    exporter.export(List.of(new TraceNode(sig, List.of(), new TraceOutcome.Returned("null"))));

    var attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertThat(attrs.get(AttributeKey.stringKey("narrative.param.x"))).isNull();
  }

  @Test
  void parentSpanHasEventsForEachDirectChild() {
    var child1 = nodeWithStartTime("SvcA", "methodA", 1000L);
    var child2 = nodeWithStartTime("SvcB", "methodB", 2000L);
    var parent =
        new TraceNode(
            new MethodSignature("Root", "handle", List.of()),
            List.of(child1, child2),
            new TraceOutcome.Returned("null"),
            100_000L,
            0L,
            null);

    exporter.export(List.of(parent));

    var parentSpan =
        spanExporter.getFinishedSpanItems().stream()
            .filter(s -> s.getName().equals("Root.handle"))
            .findFirst()
            .get();
    // Parent events: 2 child events
    var events = parentSpan.getEvents();
    assertThat(events).hasSize(2);
    assertThat(events.get(0).getName()).isEqualTo("SvcA.methodA");
    assertThat(events.get(1).getName()).isEqualTo("SvcB.methodB");
  }

  @Test
  void childEventsHaveTimestampFromChildCompletionTime() {
    var child = nodeWithStartTime("Svc", "work", 5000L);
    var parent =
        new TraceNode(
            new MethodSignature("Root", "handle", List.of()),
            List.of(child),
            new TraceOutcome.Returned("null"),
            100_000L,
            0L,
            null);

    exporter.export(List.of(parent));

    var parentSpan =
        spanExporter.getFinishedSpanItems().stream()
            .filter(s -> s.getName().equals("Root.handle"))
            .findFirst()
            .get();
    assertThat(parentSpan.getEvents().get(0).getEpochNanos()).isEqualTo(5000L + 50_000L);
  }

  @Test
  void rootWithNoChildrenHasNoEvents() {
    exporter.export(List.of(node("Svc", "work")));

    var span = spanExporter.getFinishedSpanItems().get(0);
    assertThat(span.getEvents()).isEmpty();
  }

  @Test
  void grandchildrenDoNotCreateEventsOnGrandparent() {
    var grandchild = node("Repo", "findAll");
    var child =
        new TraceNode(
            new MethodSignature("Svc", "process", List.of()),
            List.of(grandchild),
            new TraceOutcome.Returned("null"),
            50_000L,
            1000L,
            null);
    var parent =
        new TraceNode(
            new MethodSignature("Root", "handle", List.of()),
            List.of(child),
            new TraceOutcome.Returned("null"),
            100_000L,
            0L,
            null);

    exporter.export(List.of(parent));

    var parentSpan =
        spanExporter.getFinishedSpanItems().stream()
            .filter(s -> s.getName().equals("Root.handle"))
            .findFirst()
            .get();
    // Only 1 event (direct child), not 2 (grandchild)
    assertThat(parentSpan.getEvents()).hasSize(1);
    assertThat(parentSpan.getEvents().get(0).getName()).isEqualTo("Svc.process");
  }

  private static TraceNode nodeWithStartTime(String className, String methodName, long startNanos) {
    return new TraceNode(
        new MethodSignature(className, methodName, List.of()),
        List.of(),
        new TraceOutcome.Returned("null"),
        50_000L,
        startNanos,
        null);
  }

  // --- Cross-port residual recursion: exportNode used to walk TraceNode.children() by ordinary
  // call-stack recursion, the same crash risk every core/clarity/diagrams renderer already closed
  // via ai.narrativetrace.core.tree.TreeWalk. ---

  @Test
  void exportSurvivesAVeryDeepLegitimateChainWithoutStackOverflow() {
    var depth = 5_000;
    var root = chain(depth);

    assertThatCode(() -> exporter.export(List.of(root))).doesNotThrowAnyException();

    assertThat(spanExporter.getFinishedSpanItems()).hasSize(depth + 1);
  }

  @Test
  void exportTruncatesAChainDeeperThanMaxDepthInsteadOfOverflowing() {
    var depth = ai.narrativetrace.core.tree.TreeWalk.MAX_DEPTH + 50;
    var root = chain(depth);

    assertThatCode(() -> exporter.export(List.of(root))).doesNotThrowAnyException();

    // Nodes at depth 0..MAX_DEPTH are visited normally (MAX_DEPTH + 1 of them), plus one more span
    // for the node the walk stopped at instead of descending into a second time.
    var spans = spanExporter.getFinishedSpanItems();
    assertThat(spans).hasSize(ai.narrativetrace.core.tree.TreeWalk.MAX_DEPTH + 2);
    assertThat(spans)
        .extracting(s -> s.getAttributes().get(AttributeKey.stringKey("narrative.truncated")))
        .contains("depth-limit");
  }

  @Test
  void exportTerminatesOnATwoNodeCycleInsteadOfHanging() {
    var aChildren = new java.util.ArrayList<TraceNode>();
    var aNode =
        new TraceNode(
            new MethodSignature("Svc", "a", List.of()),
            aChildren,
            new TraceOutcome.Returned("null"));
    var bChildren = new java.util.ArrayList<TraceNode>();
    bChildren.add(aNode);
    var bNode =
        new TraceNode(
            new MethodSignature("Svc", "b", List.of()),
            bChildren,
            new TraceOutcome.Returned("null"));
    aChildren.add(bNode); // a -> b -> a

    assertThatCode(() -> exporter.export(List.of(aNode))).doesNotThrowAnyException();

    var spans = spanExporter.getFinishedSpanItems();
    // a, b, and the boundary occurrence of a the walk stops at instead of re-descending.
    assertThat(spans).hasSize(3);
    assertThat(spans)
        .extracting(s -> s.getAttributes().get(AttributeKey.stringKey("narrative.truncated")))
        .contains("cycle");
  }

  /** A linear chain {@code depth} nodes deep, root first. */
  private static TraceNode chain(int depth) {
    var current = node("Leaf", "leaf");
    for (var i = 0; i < depth; i++) {
      current =
          new TraceNode(
              new MethodSignature("Svc" + i, "call", List.of()),
              List.of(current),
              new TraceOutcome.Returned("null"));
    }
    return current;
  }

  @Test
  void parentCompletionEventShouldUseChildCompletionTimestampRatherThanChildStartTime() {
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"paid\""),
            7_000_000L,
            11_000_000L,
            null);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"ok\""),
            20_000_000L,
            10_000_000L,
            null);

    exporter.export(List.of(parent));

    var parentSpan =
        spanExporter.getFinishedSpanItems().stream()
            .filter(span -> span.getName().equals("OrderService.placeOrder"))
            .findFirst()
            .orElseThrow();

    assertThat(parentSpan.getEvents()).hasSize(1);
    assertThat(parentSpan.getEvents().get(0).getEpochNanos())
        .isEqualTo(child.startTimeNanos() + child.durationNanos());
  }

  @Test
  void batchExportShouldPopulateDocumentedTraceIdentityAttributes() {
    var spanContext =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            5_000_000L,
            1_000_000L,
            null,
            spanContext);

    exporter.export(List.of(root));

    var exportedSpan = spanExporter.getFinishedSpanItems().get(0);
    assertThat(exportedSpan.getAttributes().get(AttributeKey.stringKey("narrative.trace_id")))
        .isEqualTo(spanContext.traceId().toString());
    assertThat(exportedSpan.getAttributes().get(AttributeKey.stringKey("narrative.trace_name")))
        .isEqualTo(TraceNamer.name(spanContext.traceId().value()));
  }

  private static TraceNode concurrentNode(
      String className, String methodName, String groupId, ConcurrencyKind kind) {
    var info = new ConcurrencyInfo(groupId, "pool-1", 42L, false, kind);
    return new TraceNode(
        new MethodSignature(className, methodName, List.of()),
        List.of(),
        new TraceOutcome.Returned("null"),
        100_000L,
        0L,
        info);
  }
}
