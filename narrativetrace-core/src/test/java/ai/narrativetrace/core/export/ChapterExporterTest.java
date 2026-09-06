/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.render.ScenarioResult;
import ai.narrativetrace.core.render.TraceMetadata;
import ai.narrativetrace.core.render.TraceNamer;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ChapterExporterTest {

  private final ChapterExporter exporter = new ChapterExporter();

  private static SpanContext rootSpanContext() {
    return SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
        .serviceName("order-service")
        .storyId("OrderService.placeOrder")
        .chapterId("OrderService.placeOrder")
        .build();
  }

  @Test
  void controlCharactersInMethodNamesProduceParseableJson() throws Exception {
    var hostileClass = "Order" + (char) 1 + "\b\"Service\"";
    var node =
        new TraceNode(
            new MethodSignature(hostileClass, "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            100_000_000L,
            System.nanoTime(),
            null,
            rootSpanContext());
    var tree = new DefaultTraceTree(List.of(node));
    var metadata = new TraceMetadata("Scenario \b\f \"quoted\"", "success");

    var json = exporter.exportChapter(tree, metadata);

    var parsed = new ObjectMapper().readTree(json);
    assertThat(parsed.at("/nt.title").asText()).isEqualTo(hostileClass + ".placeOrder");
  }

  @Test
  void chapterJsonContainsRequiredSchemaFields() {
    var sc = rootSpanContext();
    var node = rootNode(sc, new TraceOutcome.Returned("\"ok\""), 251_000_000L);
    var tree = new DefaultTraceTree(List.of(node));
    var metadata = new TraceMetadata("Customer places order", "success");

    var json = exporter.exportChapter(tree, metadata);

    assertThat(json).contains("\"nt.entryType\": \"chapter\"");
    assertThat(json).contains("\"nt.schemaVersion\": \"" + CanonicalEntry.SCHEMA_VERSION + "\"");
    assertThat(json).contains("\"nt.storyId\": \"OrderService.placeOrder\"");
    assertThat(json).contains("\"nt.chapterId\": \"OrderService.placeOrder\"");
    assertThat(json).contains("\"service\": \"order-service\"");
    assertThat(json).contains("\"trace_id\": \"" + sc.traceId() + "\"");
  }

  @Test
  void chapterJsonContainsTitle() {
    var node = rootNode(rootSpanContext(), new TraceOutcome.Returned("\"ok\""), 100_000_000L);
    var tree = new DefaultTraceTree(List.of(node));
    var metadata = new TraceMetadata("Test", "success");

    var json = exporter.exportChapter(tree, metadata);

    assertThat(json).contains("\"nt.title\": \"OrderService.placeOrder\"");
  }

  @Test
  void chapterJsonContainsOutcomeSuccess() {
    var node = rootNode(rootSpanContext(), new TraceOutcome.Returned("\"ok\""), 100_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json).contains("\"nt.outcome\": \"success\"");
    assertThat(json).contains("\"level\": \"info\"");
  }

  @Test
  void chapterJsonContainsOutcomeFailure() {
    var node =
        rootNode(
            rootSpanContext(), new TraceOutcome.Threw(new RuntimeException("boom")), 100_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "error"));

    assertThat(json).contains("\"nt.outcome\": \"failure\"");
    assertThat(json).contains("\"level\": \"error\"");
  }

  @Test
  void chapterJsonContainsDurationAndEntryCount() {
    var child =
        new TraceNode(
            new MethodSignature("Svc", "inner", List.of()),
            List.of(),
            new TraceOutcome.Returned("1"),
            10_000_000L);
    var sc = rootSpanContext();
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"ok\""),
            251_000_000L,
            System.nanoTime(),
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(root));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json).contains("\"nt.totalDurationMs\": 251");
    assertThat(json).contains("\"nt.entryCount\": 2");
  }

  @Test
  void chapterJsonContainsTraceName() {
    var sc = rootSpanContext();
    var node = rootNode(sc, new TraceOutcome.Returned("\"ok\""), 100_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json)
        .contains("\"nt.traceName\": \"" + TraceNamer.name(sc.traceId().value()) + "\"");
  }

  @Test
  void chapterJsonContainsEmbeddedChapterTree() {
    var node = rootNode(rootSpanContext(), new TraceOutcome.Returned("\"ok\""), 100_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json).contains("\"nt.chapterTree\":");
  }

  @Test
  void chapterJsonContainsCompletionStatus() {
    var node = rootNode(rootSpanContext(), new TraceOutcome.Returned("\"ok\""), 100_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json).contains("\"nt.completionStatus\": \"complete\"");
  }

  @Test
  void chapterJsonContainsMessageSummary() {
    var node = rootNode(rootSpanContext(), new TraceOutcome.Returned("\"ok\""), 251_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    // The root's own duration and the outcome are part of the summary line, not decoration.
    assertThat(json)
        .contains("\"message\": \"Chapter complete: OrderService.placeOrder [251ms] success\"");
  }

  @Test
  void chapterJsonForIncompleteOutcome() {
    var node = rootNode(rootSpanContext(), new TraceOutcome.Incomplete(), 100_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", ScenarioResult.ERROR));

    assertThat(json).contains("\"nt.outcome\": \"partial\"");
  }

  @Test
  void chapterJsonForTreeWithoutSpanContextStillCarriesTheThreeIdentityFields() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "m", List.of()),
            List.of(),
            new TraceOutcome.Returned("ok"),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json).contains("\"nt.entryType\": \"chapter\"");
    assertThat(json).contains("\"nt.title\": \"Svc.m\"");
    // Derived, not generated: the story is the first root-level call and the chapter equals it.
    assertThat(json).contains("\"nt.storyId\": \"Svc.m\"");
    assertThat(json).contains("\"nt.chapterId\": \"Svc.m\"");
    assertThat(json).containsPattern("\"trace_id\": \"[0-9a-f]{32}\"");
  }

  @Test
  void chapterJsonAdoptsTheTraceIdTheCapturingContextAssigned() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "m", List.of()),
            List.of(),
            new TraceOutcome.Returned("ok"),
            1_000_000L);
    var assigned = TraceId.of("0af7651916cd43dd8448eb211c80319c");
    var tree = new DefaultTraceTree(List.of(node), assigned);

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json).contains("\"trace_id\": \"0af7651916cd43dd8448eb211c80319c\"");
    assertThat(json).contains("\"nt.traceName\": \"" + TraceNamer.name(assigned.value()) + "\"");
  }

  @Test
  void aRealSpanContextStillBeatsTheTreesAssignedTraceId() {
    // Inheritance beats every other rung: a node that kept its context is the trace's own
    // witness, and a stale id on the tree must never overwrite it.
    var sc = rootSpanContext();
    var tree =
        new DefaultTraceTree(
            List.of(rootNode(sc, new TraceOutcome.Returned("\"ok\""), 1_000_000L)),
            TraceId.of("11111111111111111111111111111111"));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json).contains("\"trace_id\": \"" + sc.traceId() + "\"");
  }

  @Test
  void chapterOfAMixedTreeInheritsTheIdentityOfTheOnlyNodeThatKeptIt() {
    // The launcher root of fire-and-forget work carries no span context; its child does.
    var sc = rootSpanContext();
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"TXN-1\""),
            1_000_000L,
            System.nanoTime(),
            null,
            sc);
    var root =
        new TraceNode(
            new MethodSignature("Svc", "fire-and-forget", List.of()), List.of(child), null, 0L);
    var tree = new DefaultTraceTree(List.of(root));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json).contains("\"trace_id\": \"" + sc.traceId() + "\"");
    assertThat(json).contains("\"nt.storyId\": \"OrderService.placeOrder\"");
    assertThat(json).contains("\"service\": \"order-service\"");
  }

  @Test
  void rejectsANullTree() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> exporter.exportChapter(null, new TraceMetadata("Test", "success")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tree must not be null");
  }

  @Test
  void chapterJsonForEmptyTree() {
    var tree = new DefaultTraceTree(List.of());

    var json = exporter.exportChapter(tree, new TraceMetadata("Empty", "success"));

    assertThat(json).contains("\"nt.entryType\": \"chapter\"");
    assertThat(json).contains("\"nt.entryCount\": 0");
    // Nothing was traced, so there is no root call to derive a story from; the schema still
    // requires the field, so it takes the same "unknown" fallback the title does.
    assertThat(json).contains("\"nt.title\": \"unknown\"");
    assertThat(json).contains("\"nt.storyId\": \"unknown\"");
    assertThat(json).contains("\"nt.chapterId\": \"unknown\"");
    assertThat(json).containsPattern("\"trace_id\": \"[0-9a-f]{32}\"");
  }

  @Test
  void chapterJsonNullOutcomeDefaultsToSuccess() {
    var sc = rootSpanContext();
    var node =
        new TraceNode(
            new MethodSignature("Svc", "m", List.of()), List.of(), null, 0L, 0L, null, sc);
    var tree = new DefaultTraceTree(List.of(node));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json).contains("\"nt.outcome\": \"success\"");
  }

  private static TraceNode rootNode(SpanContext sc, TraceOutcome outcome, long durationNanos) {
    return new TraceNode(
        new MethodSignature(
            "OrderService", "placeOrder", List.of(new ParameterCapture("id", "\"42\"", false))),
        List.of(),
        outcome,
        durationNanos,
        System.nanoTime(),
        null,
        sc);
  }

  @Test
  void chapterWithoutServiceNameFallsBackToUnknownService() {
    var sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    var tree =
        new DefaultTraceTree(
            List.of(rootNode(sc, new TraceOutcome.Returned("\"ok\""), 100_000_000L)));

    var json = exporter.exportChapter(tree, new TraceMetadata("Scenario", "success"));

    assertThat(json).contains("\"service\": \"unknown_service:java\"");
  }

  @Test
  void chapterWithoutSpanContextFallsBackToUnknownService() {
    var tree =
        new DefaultTraceTree(
            List.of(rootNode(null, new TraceOutcome.Returned("\"ok\""), 100_000_000L)));

    var json = exporter.exportChapter(tree, new TraceMetadata("Scenario", "success"));

    assertThat(json).contains("\"service\": \"unknown_service:java\"");
  }

  @Test
  void chapterWithBlankServiceNameFallsBackToUnknownService() {
    var sc =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
            .serviceName("  ")
            .build();
    var tree =
        new DefaultTraceTree(
            List.of(rootNode(sc, new TraceOutcome.Returned("\"ok\""), 100_000_000L)));

    var json = exporter.exportChapter(tree, new TraceMetadata("Scenario", "success"));

    assertThat(json).contains("\"service\": \"unknown_service:java\"");
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
  void aVeryDeepCallTreeCountsEntriesWithoutStackOverflow() {
    TraceNode current = node("call5000", List.of());
    for (var i = 0; i < 5_000; i++) {
      current = node("call" + i, List.of(current));
    }
    var tree = new DefaultTraceTree(List.of(current));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json).contains("\"nt.entryCount\": 5001");
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aCyclicCallTreeCountsEntriesWithoutHanging() {
    var childHolder = new ArrayList<TraceNode>();
    var b = node("b", childHolder);
    var a = node("a", List.of(b));
    childHolder.add(a);
    var tree = new DefaultTraceTree(List.of(a));

    var json = exporter.exportChapter(tree, new TraceMetadata("Test", "success"));

    assertThat(json).contains("\"nt.entryCount\": 3");
  }
}
