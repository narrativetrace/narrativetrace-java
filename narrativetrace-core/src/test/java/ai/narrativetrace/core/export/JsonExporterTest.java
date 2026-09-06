/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.HttpRoute;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TenantId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.render.ScenarioResult;
import ai.narrativetrace.core.render.TraceMetadata;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class JsonExporterTest {

  @Test
  void voidMethodOmitsReturnValueFieldInsteadOfEmittingStringNull() throws Exception {
    var node =
        new TraceNode(
            new MethodSignature("AuditSink", "record", List.of()),
            List.of(),
            new TraceOutcome.Returned(null),
            1_000_000L);

    var json = new JsonExporter().export(new DefaultTraceTree(List.of(node)));

    var parsed = new ObjectMapper().readTree(json);
    var exit = parsed.at("/events/1");
    assertThat(exit.at("/outcome").asText()).isEqualTo("returned");
    assertThat(exit.has("returnValue")).as("void must not fake a returnValue").isFalse();
  }

  @Test
  void nonVoidReturnEmitsItsRenderedValue() throws Exception {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "answer", List.of()),
            List.of(),
            new TraceOutcome.Returned("42"),
            1_000_000L);

    var json = new JsonExporter().export(new DefaultTraceTree(List.of(node)));

    var parsed = new ObjectMapper().readTree(json);
    assertThat(parsed.at("/events/1/returnValue").asText()).isEqualTo("42");
  }

  @Test
  void hostileTraceFieldValuesAreEscapedNotInjected() throws Exception {
    var hostileTenant = "evil\",\"admin\":\"true";
    var spanContext =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
            .tenantId(TenantId.of(hostileTenant))
            .httpRoute(HttpRoute.of("/orders/\"quoted\""))
            .build();
    var node =
        new TraceNode(
            new MethodSignature("Svc", "handle", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L,
            0L,
            null,
            spanContext);

    var json = new JsonExporter().export(new DefaultTraceTree(List.of(node)));

    var parsed = new ObjectMapper().readTree(json);
    assertThat(parsed.at("/trace/tenantId").asText()).isEqualTo(hostileTenant);
    assertThat(parsed.at("/trace/httpRoute").asText()).isEqualTo("/orders/\"quoted\"");
    assertThat(parsed.at("/trace/admin").isMissingNode())
        .as("forged field must not exist")
        .isTrue();
  }

  /**
   * The two halves answer differently on purpose. A parameter value is text {@code ValueRenderer}
   * already rendered, and this fixture hands the exporter a raw one, so JSON's own escaping is all
   * that applies to it. An exception message is read at emission time through {@code
   * ExceptionMessage}, which folds control characters into visible escapes on every emitter path —
   * so the message arrives here already inert, and the document carries the escape, not the byte.
   */
  @Test
  void controlCharactersInValuesProduceParseableJson() throws Exception {
    var node =
        new TraceNode(
            new MethodSignature(
                "Svc", "handle", List.of(new ParameterCapture("data", "\"ab\bc\fd\"", false))),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("badinput")),
            1_000_000L);

    var json = new JsonExporter().export(new DefaultTraceTree(List.of(node)));

    var parsed = new ObjectMapper().readTree(json);
    assertThat(parsed.at("/events/0/parameters/0/value").asText()).isEqualTo("\"ab\bc\fd\"");
    assertThat(parsed.at("/events/1/errorMessage").asText()).isEqualTo("bad\\u0001input");
  }

  @Test
  void exportsSingleLeafNodeAsEnterAndExitEvents() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"order-42\""),
            412_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).contains("\"type\": \"enter\"");
    assertThat(json).contains("\"type\": \"exit\"");
    assertThat(json).contains("\"className\": \"OrderService\"");
    assertThat(json).contains("\"methodName\": \"placeOrder\"");
    assertThat(json).contains("\"name\": \"customerId\"");
    assertThat(json).contains("\"durationMs\": 412");
  }

  @Test
  void exportsNestedNodesWithDepthAndParentId() {
    var child =
        new TraceNode(
            new MethodSignature("InventoryService", "reserve", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            24_000_000L);
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"order-42\""),
            412_000_000L);
    var tree = new DefaultTraceTree(List.of(root));

    var json = new JsonExporter().export(tree);

    // Root enter at depth 0, parentId null
    assertThat(json).contains("\"className\": \"OrderService\"");
    assertThat(json).contains("\"depth\": 0");
    assertThat(json).contains("\"parentId\": null");

    // Child enter at depth 1, parentId is a string (parent's spanId)
    assertThat(json).contains("\"className\": \"InventoryService\"");
    assertThat(json).contains("\"depth\": 1");
  }

  @Test
  void exportsExceptionAsErrorEventType() {
    var node =
        new TraceNode(
            new MethodSignature("PaymentGateway", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("Card expired")),
            203_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).contains("\"outcome\": \"threw\"");
    assertThat(json).contains("\"errorType\": \"IllegalStateException\"");
    assertThat(json).contains("\"errorMessage\": \"Card expired\"");
    assertThat(json).contains("\"durationMs\": 203");
  }

  @Test
  void serializesParametersAccordingToSpecRules() {
    var node =
        new TraceNode(
            new MethodSignature(
                "AuthService",
                "login",
                List.of(
                    new ParameterCapture("username", "\"admin\"", false),
                    new ParameterCapture("count", "42", false),
                    new ParameterCapture("active", "true", false),
                    new ParameterCapture("password", "[REDACTED]", true),
                    new ParameterCapture("nullParam", "null", false))),
            List.of(),
            new TraceOutcome.Returned("true"),
            5_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).contains("\"name\": \"username\"");
    assertThat(json).contains("\"name\": \"count\"");
    assertThat(json).contains("\"name\": \"active\"");
    assertThat(json).contains("\"name\": \"password\"");
    assertThat(json).contains("\"redacted\": true");
    assertThat(json).contains("\"name\": \"nullParam\"");
  }

  @Test
  void exportsFullTraceDocumentWithVersionAndScenarioMetadata() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"order-42\""),
            412_000_000L);
    var tree = new DefaultTraceTree(List.of(node));
    var metadata = new TraceMetadata("Customer places order", ScenarioResult.SUCCESS);

    var json = new JsonExporter().exportDocument(tree, metadata);

    assertThat(json).contains("\"version\": \"1.0\"");
    assertThat(json).contains("\"scenario\":");
    assertThat(json).contains("\"name\": \"Customer places order\"");
    assertThat(json).contains("\"result\": \"success\"");
    assertThat(json).contains("\"durationMs\": 412");
    assertThat(json).contains("\"events\":");
    assertThat(json).contains("\"type\": \"enter\"");
    assertThat(json).contains("\"type\": \"exit\"");
  }

  @Test
  void exportDocumentWithEmptyTreeProducesValidJson() {
    var tree = new DefaultTraceTree(List.of());
    var metadata = new TraceMetadata("Empty", ScenarioResult.SUCCESS);

    var json = new JsonExporter().exportDocument(tree, metadata);

    assertThat(json).doesNotContain(",\n  }");
  }

  @Test
  void exportDocumentWithEmptyTreeOmitsDuration() {
    var tree = new DefaultTraceTree(List.of());
    var metadata = new TraceMetadata("Empty", ScenarioResult.SUCCESS);

    var json = new JsonExporter().exportDocument(tree, metadata);

    assertThat(json).contains("\"version\": \"1.0\"");
    assertThat(json).contains("\"name\": \"Empty\"");
    assertThat(json).doesNotContain("\"durationMs\"");
  }

  @Test
  void serializesCustomObjectParamUsingToString() {
    var node =
        new TraceNode(
            new MethodSignature(
                "Svc", "method", List.of(new ParameterCapture("obj", "[1, 2]", false))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).contains("\"name\": \"obj\"");
    assertThat(json).contains("\"value\": \"[1, 2]\"");
  }

  @Test
  void exportsExceptionWithNullMessage() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "method", List.of()),
            List.of(),
            new TraceOutcome.Threw(new NullPointerException()),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).contains("\"outcome\": \"threw\"");
    assertThat(json).contains("\"errorMessage\": \"null\"");
  }

  @Test
  void emptyAndNullRenderedValuesSerializeAsEmptyJsonString() {
    var node =
        new TraceNode(
            new MethodSignature(
                "Svc",
                "method",
                List.of(
                    new ParameterCapture("empty", "", false),
                    new ParameterCapture("nul", null, false))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    // chapter-tree.schema.json requires parameters[].value to be a string, so "no rendered
    // value" is the empty string in both cases — never a JSON null.
    assertThat(json).contains("\"name\": \"empty\", \"value\": \"\"");
    assertThat(json).contains("\"name\": \"nul\", \"value\": \"\"");
    assertThat(json).doesNotContain("\"value\": null");
  }

  @Test
  void multiRootTreeProducesCommaSeparatedEnterEvents() {
    var root1 =
        new TraceNode(
            new MethodSignature("A", "first", List.of()),
            List.of(),
            new TraceOutcome.Returned("1"),
            1_000_000L);
    var root2 =
        new TraceNode(
            new MethodSignature("B", "second", List.of()),
            List.of(),
            new TraceOutcome.Returned("2"),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(root1, root2));

    var json = new JsonExporter().export(tree);

    // Both roots present
    assertThat(json).contains("\"className\": \"A\"");
    assertThat(json).contains("\"className\": \"B\"");
    // JSON must parse — no missing commas between events
    // The second enter event must be preceded by a comma
    int secondEnterPos = json.indexOf("\"methodName\": \"second\"");
    assertThat(secondEnterPos).isGreaterThan(0);
    String beforeSecondEnter = json.substring(0, secondEnterPos);
    // Find the last closing brace before the second enter
    int lastBrace = beforeSecondEnter.lastIndexOf('}');
    String between = json.substring(lastBrace + 1, secondEnterPos);
    assertThat(between).contains(",");
  }

  @Test
  void bothEnterAndExitEventsContainDepthAndParentId() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "method", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    // A single node produces exactly 2 events (enter + exit)
    // Both must have depth and parentId
    long depthCount = countOccurrences(json, "\"depth\": 0");
    long parentIdCount = countOccurrences(json, "\"parentId\": null");
    assertThat(depthCount).isEqualTo(2);
    assertThat(parentIdCount).isEqualTo(2);
  }

  @Test
  void exportDocumentIncludesDurationInScenarioSection() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "method", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            500_000_000L);
    var tree = new DefaultTraceTree(List.of(node));
    var metadata = new TraceMetadata("Test scenario", ScenarioResult.SUCCESS);

    var json = new JsonExporter().exportDocument(tree, metadata);

    // The durationMs must appear in the scenario section (before "events")
    int eventsPos = json.indexOf("\"events\"");
    String scenarioSection = json.substring(0, eventsPos);
    assertThat(scenarioSection).contains("\"durationMs\": 500");
  }

  @Test
  void fireAndForgetNodeIncludesConcurrencyFieldsInJson() {
    var info =
        new ConcurrencyInfo("fanf-1", "notify-1", 200, false, ConcurrencyKind.FIRE_AND_FORGET);
    var launcher =
        new TraceNode(
            new MethodSignature("Parent", "fire-and-forget", List.of()),
            List.of(),
            null,
            0L,
            0L,
            info);
    var tree = new DefaultTraceTree(List.of(launcher));

    var json = new JsonExporter().export(tree);

    assertThat(json).contains("\"groupId\": \"fanf-1\"");
    assertThat(json).contains("\"threadName\": \"notify-1\"");
    assertThat(json).contains("\"threadId\": 200");
    assertThat(json).contains("\"virtual\": false");
    assertThat(json).contains("\"kind\": \"fire-and-forget\"");
  }

  @Test
  void concurrentNodeHasConcurrencyObjectWithAllFields() {
    var info = new ConcurrencyInfo("fork-1", "calc-pool-1", 42, false, ConcurrencyKind.FORK_JOIN);
    var node =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            85_000_000L,
            0L,
            info);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).contains("\"groupId\": \"fork-1\"");
    assertThat(json).contains("\"threadName\": \"calc-pool-1\"");
    assertThat(json).contains("\"threadId\": 42");
    assertThat(json).contains("\"virtual\": false");
    assertThat(json).contains("\"kind\": \"fork-join\"");
  }

  @Test
  void virtualThreadFlagRendersCorrectlyInJson() {
    var info = new ConcurrencyInfo("fork-2", "vthread-1", 999, true, ConcurrencyKind.FORK_JOIN);
    var node =
        new TraceNode(
            new MethodSignature("SvcA", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            10_000_000L,
            0L,
            info);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).contains("\"virtual\": true");
    assertThat(json).contains("\"threadId\": 999");
  }

  @Test
  void sequentialNodeHasNoConcurrencyField() {
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            10_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).doesNotContain("\"concurrency\"");
  }

  @Test
  void nullOutcomeExitEventIsReportedAsIncompleteWithCommonFields() {
    var info =
        new ConcurrencyInfo("fanf-1", "notify-1", 200, false, ConcurrencyKind.FIRE_AND_FORGET);
    var launcher =
        new TraceNode(
            new MethodSignature("Parent", "fire-and-forget", List.of()),
            List.of(),
            null,
            0L,
            0L,
            info);
    var tree = new DefaultTraceTree(List.of(launcher));

    var json = new JsonExporter().export(tree);

    // Exit event for null outcome must still have id, type, class, method
    // Find the second event object (the exit event)
    int firstClose = json.indexOf('}', json.indexOf("\"type\": \"enter\""));
    String exitPortion = json.substring(firstClose);
    assertThat(exitPortion).contains("\"type\": \"exit\"");
    // A synthetic launcher never returned; reporting it as such is a lie the reader cannot
    // detect. Matches TraceTreeCanonicalMapper, which already maps a null outcome to incomplete.
    assertThat(exitPortion).contains("\"outcome\": \"incomplete\"");
    assertThat(exitPortion).doesNotContain("\"outcome\": \"returned\"");
    assertThat(exitPortion).contains("\"className\": \"Parent\"");
    assertThat(exitPortion).contains("\"methodName\": \"fire-and-forget\"");
  }

  @Test
  void jsonIncludesSpanContext() {
    var sc =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
            .parentSpanId(SpanIdGenerator.spanId())
            .build();
    var node =
        new TraceNode(
            new MethodSignature("Svc", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L,
            System.nanoTime(),
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).contains("\"traceId\": \"" + sc.traceId() + "\"");
    // spanId now appears in every event (enter + exit)
    assertThat(countOccurrences(json, "\"spanId\": \"" + sc.spanId() + "\"")).isEqualTo(2);
    assertThat(json).contains("\"parentSpanId\": \"" + sc.parentSpanId() + "\"");
  }

  @Test
  void jsonIncludesRequestContext() {
    var sc =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
            .httpMethod("POST")
            .httpRoute(ai.narrativetrace.api.event.HttpRoute.of("/api/orders"))
            .clientIp(ai.narrativetrace.api.event.ClientIp.of("client-ip-1"))
            .serviceName("order-svc")
            .build();
    var node =
        new TraceNode(
            new MethodSignature("Svc", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L,
            System.nanoTime(),
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).contains("\"httpMethod\": \"POST\"");
    assertThat(json).contains("\"httpRoute\": \"/api/orders\"");
    assertThat(json).contains("\"clientIp\": \"client-ip-1\"");
    assertThat(json).contains("\"serviceName\": \"order-svc\"");
  }

  /**
   * Inverted, not deleted: this used to assert the block was omitted. Owner decision 2026-08-31 —
   * the chapter-tree document resolves identity through the shared {@code TraceIdentity} like the
   * other two emitters, so a chapter can no longer name a trace whose embedded tree names none.
   */
  @Test
  void jsonNamesTheTraceEvenWhenNoNodeKeptASpanContext() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).containsPattern("\"traceId\": \"[0-9a-f]{32}\"");
    assertThat(json).containsPattern("\"traceName\": \"[a-z]+ [a-z]+ [a-z]+\"");
    // spanId is always present (synthetic fallback when no SpanContext)
    assertThat(json).contains("\"spanId\"");
  }

  @Test
  void theTraceBlockCarriesNoRequestFieldsWhenThereWasNoContextToInheritThemFrom() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L);

    var json = new JsonExporter().export(new DefaultTraceTree(List.of(node)));

    assertThat(json)
        .as("a generated identity knows which trace this is and nothing about who called it")
        .doesNotContain("\"serviceName\"")
        .doesNotContain("\"clientIp\"")
        .doesNotContain("\"httpRoute\"");
  }

  @Test
  void theTraceBlockCarriesEveryFieldTheSpanContextSupplies() throws Exception {
    var sc =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
            .serviceName("order-svc")
            .serviceVersion("2.1.0")
            .environment("staging")
            .httpMethod("POST")
            .httpRoute(HttpRoute.of("/api/orders"))
            .clientIp(ai.narrativetrace.api.event.ClientIp.of("client-ip-7"))
            .enduserId(ai.narrativetrace.api.event.EnduserId.of("user-42"))
            .sessionId(ai.narrativetrace.api.event.SessionId.of("sess-9"))
            .tenantId(TenantId.of("tenant-a"))
            .build();
    var node =
        new TraceNode(
            new MethodSignature("Svc", "handle", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L,
            0L,
            null,
            sc);

    var trace =
        new ObjectMapper()
            .readTree(new JsonExporter().export(new DefaultTraceTree(List.of(node))))
            .at("/trace");

    assertThat(trace.at("/serviceName").asText()).isEqualTo("order-svc");
    assertThat(trace.at("/serviceVersion").asText()).isEqualTo("2.1.0");
    assertThat(trace.at("/environment").asText()).isEqualTo("staging");
    assertThat(trace.at("/httpMethod").asText()).isEqualTo("POST");
    assertThat(trace.at("/httpRoute").asText()).isEqualTo("/api/orders");
    assertThat(trace.at("/clientIp").asText()).isEqualTo("client-ip-7");
    assertThat(trace.at("/enduserId").asText()).isEqualTo("user-42");
    assertThat(trace.at("/sessionId").asText()).isEqualTo("sess-9");
    assertThat(trace.at("/tenantId").asText()).isEqualTo("tenant-a");
  }

  @Test
  void theTraceBlockInheritsFromADescendantWhenNoRootKeptItsContext() {
    var child =
        new TraceNode(
            new MethodSignature("Payment", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L,
            System.nanoTime(),
            null,
            SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
                .serviceName("order-svc")
                .build());
    var root =
        new TraceNode(
            new MethodSignature("Svc", "work", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"ok\""),
            2_000_000L);

    var json = new JsonExporter().export(new DefaultTraceTree(List.of(root)));

    assertThat(json)
        .as("the scan is depth-first, as the other two emitters have always been")
        .contains("\"traceId\": \"" + child.spanContext().traceId() + "\"")
        .contains("\"serviceName\": \"order-svc\"");
  }

  @Test
  void exportIncludesTraceLevelBlockFromRootSpanContext() {
    var sc =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
            .serviceName("order-svc")
            .httpMethod("POST")
            .httpRoute(ai.narrativetrace.api.event.HttpRoute.of("/api/orders"))
            .clientIp(ai.narrativetrace.api.event.ClientIp.of("client-ip-1"))
            .enduserId(ai.narrativetrace.api.event.EnduserId.of("user-42"))
            .tenantId(ai.narrativetrace.api.event.TenantId.of("tenant-a"))
            .build();
    var node =
        new TraceNode(
            new MethodSignature("Svc", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L,
            System.nanoTime(),
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    assertThat(json).contains("\"trace\":");
    assertThat(json).contains("\"traceId\": \"" + sc.traceId() + "\"");
    assertThat(json).contains("\"serviceName\": \"order-svc\"");
    assertThat(json).contains("\"httpMethod\": \"POST\"");
    assertThat(json).contains("\"httpRoute\": \"/api/orders\"");
    assertThat(json).contains("\"enduserId\": \"user-42\"");
    assertThat(json).contains("\"tenantId\": \"tenant-a\"");
  }

  @Test
  void perEventOnlyHasSpanIdNotTraceLevelFields() {
    var sc =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
            .serviceName("order-svc")
            .httpMethod("POST")
            .httpRoute(ai.narrativetrace.api.event.HttpRoute.of("/api/orders"))
            .build();
    var node =
        new TraceNode(
            new MethodSignature("Svc", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L,
            System.nanoTime(),
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new JsonExporter().export(tree);

    // traceId should appear once (in trace block), not in per-event
    assertThat(countOccurrences(json, "\"traceId\"")).isEqualTo(1);
    // serviceName should appear once (in trace block), not in per-event
    assertThat(countOccurrences(json, "\"serviceName\"")).isEqualTo(1);
    // spanId should appear in per-event (enter + exit = 2 occurrences)
    assertThat(countOccurrences(json, "\"spanId\": \"" + sc.spanId() + "\"")).isEqualTo(2);
  }

  private static TraceNode node(String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature("Recursive", methodName, List.of()),
        children,
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L);
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aVeryDeepCallTreeExportsWithoutStackOverflow() throws Exception {
    TraceNode current = node("call5000", List.of());
    for (var i = 0; i < 5_000; i++) {
      current = node("call" + i, List.of(current));
    }

    var json = new JsonExporter().export(new DefaultTraceTree(List.of(current)));

    var parsed = new ObjectMapper().readTree(json);
    assertThat(parsed.at("/events")).hasSize(2 * 5_001);
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aCyclicCallTreeExportsWithATruncatedMarkerInsteadOfHanging() throws Exception {
    var childHolder = new ArrayList<TraceNode>();
    var b = node("b", childHolder);
    var a = node("a", List.of(b));
    childHolder.add(a);

    var json = new JsonExporter().export(new DefaultTraceTree(List.of(a)));

    var parsed = new ObjectMapper().readTree(json);
    assertThat(countOccurrences(json, "\"truncated\": \"cycle\"")).isEqualTo(1);
    assertThat(parsed.at("/events")).hasSize(6); // enter/exit for a, b, and the cycle-closing a
  }

  private static long countOccurrences(String text, String substring) {
    long count = 0;
    int idx = text.indexOf(substring, 0);
    while (idx != -1) {
      count++;
      idx = text.indexOf(substring, idx + substring.length());
    }
    return count;
  }
}
