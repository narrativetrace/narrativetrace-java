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
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.render.TraceMetadata;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Validates exporter outputs against the canonical JSON schemas from {@code
 * schema/entry.schema.json}, {@code schema/chapter.schema.json}, and {@code
 * schema/chapter-tree.schema.json}.
 *
 * <p>These tests close the loop: the schema files become executable test fixtures that enforce the
 * contract between the library and the backend platform.
 */
class SchemaValidationTest {

  private static final JsonSchemaFactory FACTORY =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

  // ── entry.schema.json ───────────────────────────────────────────────────────

  @Test
  void canonicalEntryForEnterEventValidatesAgainstSchema() {
    var sc = rootSpanContext();
    var sig =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(new ParameterCapture("customerId", "\"C-123\"", false)));
    var event = new TraceEvent.EnterEvent(sc, System.nanoTime(), sig);
    var entry = CanonicalEntryMapper.fromEvent(event);
    var json = CanonicalEntrySerializer.toJson(entry);

    var errors = validateAgainstSchema("schema/entry.schema.json", json);

    assertThat(errors).as("Entry schema violations: %s", errors).isEmpty();
  }

  @Test
  void canonicalEntryForExitSuccessValidatesAgainstSchema() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(sc, System.nanoTime(), new TraceOutcome.Returned("42"), null);
    var entry = CanonicalEntryMapper.fromEvent(event);
    var json = CanonicalEntrySerializer.toJson(entry);

    var errors = validateAgainstSchema("schema/entry.schema.json", json);

    assertThat(errors).as("Entry schema violations: %s", errors).isEmpty();
  }

  @Test
  void canonicalEntryForExitErrorValidatesAgainstSchema() {
    var sc = rootSpanContext();
    var ex = new IllegalArgumentException("bad input");
    var event = new TraceEvent.ExitEvent(sc, System.nanoTime(), new TraceOutcome.Threw(ex), null);
    var entry = CanonicalEntryMapper.fromEvent(event);
    var json = CanonicalEntrySerializer.toJson(entry);

    var errors = validateAgainstSchema("schema/entry.schema.json", json);

    assertThat(errors).as("Entry schema violations: %s", errors).isEmpty();
  }

  @Test
  void structuralProjectionOfEnterEventValidatesAgainstSchema() {
    var sc = rootSpanContext();
    var sig =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(new ParameterCapture("customerId", "\"C-123\"", false)));
    var event = new TraceEvent.EnterEvent(sc, System.nanoTime(), sig);
    var entry = StructuralProjection.project(CanonicalEntryMapper.fromEvent(event));
    var json = CanonicalEntrySerializer.toJson(entry);

    var errors = validateAgainstSchema("schema/entry.schema.json", json);

    assertThat(errors).as("Structural entry schema violations: %s", errors).isEmpty();
  }

  @Test
  void structuralProjectionOfExitErrorValidatesAgainstSchema() {
    var sc = rootSpanContext();
    var ex = new IllegalArgumentException("bad input");
    var event = new TraceEvent.ExitEvent(sc, System.nanoTime(), new TraceOutcome.Threw(ex), null);
    var entry = StructuralProjection.project(CanonicalEntryMapper.fromEvent(event));
    var json = CanonicalEntrySerializer.toJson(entry);

    var errors = validateAgainstSchema("schema/entry.schema.json", json);

    assertThat(errors).as("Structural entry schema violations: %s", errors).isEmpty();
  }

  @Test
  void forkEventDoesNotConformToEntrySchema() {
    // Fork/join/async_dispatch events are lifecycle markers without SpanContext.
    // They intentionally do not satisfy the full entry schema (missing required
    // trace_id, span_id, code.namespace, code.function). This test documents
    // the divergence rather than asserting conformance.
    var event = new TraceEvent.ForkCreatedEvent("group-1", System.nanoTime());
    var entry = CanonicalEntryMapper.fromEvent(event);
    var json = CanonicalEntrySerializer.toJson(entry);

    var errors = validateAgainstSchema("schema/entry.schema.json", json);

    assertThat(errors).isNotEmpty();
    assertThat(errors.toString()).contains("trace_id");
  }

  // ── chapter-tree.schema.json ────────────────────────────────────────────────

  @Test
  void jsonExporterOutputValidatesAgainstChapterTreeSchema() {
    var sc = rootSpanContext();
    var child =
        new TraceNode(
            new MethodSignature("InventoryService", "reserve", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            24_000_000L);
    var root =
        new TraceNode(
            new MethodSignature(
                "OrderService", "placeOrder", List.of(new ParameterCapture("id", "\"42\"", false))),
            List.of(child),
            new TraceOutcome.Returned("\"order-42\""),
            251_000_000L,
            System.nanoTime(),
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(root));
    var metadata = new TraceMetadata("Customer places order", "success");

    var json = new JsonExporter().exportDocument(tree, metadata);
    var errors = validateAgainstSchema("schema/chapter-tree.schema.json", json);

    assertThat(errors).as("Chapter-tree schema violations: %s", errors).isEmpty();
  }

  @Test
  void jsonExporterWithConcurrencyValidatesAgainstSchema() {
    var sc = rootSpanContext();
    var info = new ConcurrencyInfo("fork-1", "pool-1", 42, false, ConcurrencyKind.FORK_JOIN);
    var node =
        new TraceNode(
            new MethodSignature("Svc", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            85_000_000L,
            System.nanoTime(),
            info,
            sc);
    var tree = new DefaultTraceTree(List.of(node));
    var metadata = new TraceMetadata("Concurrent work", "success");

    var json = new JsonExporter().exportDocument(tree, metadata);
    var errors = validateAgainstSchema("schema/chapter-tree.schema.json", json);

    assertThat(errors).as("Chapter-tree schema violations: %s", errors).isEmpty();
  }

  @Test
  void jsonExporterWithErrorValidatesAgainstSchema() {
    var sc = rootSpanContext();
    var node =
        new TraceNode(
            new MethodSignature("Svc", "fail", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("boom")),
            10_000_000L,
            System.nanoTime(),
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(node));
    var metadata = new TraceMetadata("Error case", "error");

    var json = new JsonExporter().exportDocument(tree, metadata);
    var errors = validateAgainstSchema("schema/chapter-tree.schema.json", json);

    assertThat(errors).as("Chapter-tree schema violations: %s", errors).isEmpty();
  }

  // ── chapter.schema.json ─────────────────────────────────────────────────────

  @Test
  void chapterExporterOutputValidatesAgainstSchema() {
    var sc = rootSpanContext();
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            251_000_000L,
            System.nanoTime(),
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(node));
    var metadata = new TraceMetadata("Customer places order", "success");

    var json = new ChapterExporter().exportChapter(tree, metadata);
    var errors = validateAgainstSchema("schema/chapter.schema.json", json);

    assertThat(errors).as("Chapter schema violations: %s", errors).isEmpty();
  }

  @Test
  void chapterExporterWithFailureValidatesAgainstSchema() {
    var sc = rootSpanContext();
    var node =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("declined")),
            100_000_000L,
            System.nanoTime(),
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(node));
    var metadata = new TraceMetadata("Payment fails", "error");

    var json = new ChapterExporter().exportChapter(tree, metadata);
    var errors = validateAgainstSchema("schema/chapter.schema.json", json);

    assertThat(errors).as("Chapter schema violations: %s", errors).isEmpty();
  }

  @Test
  void entryBuiltWithoutASpanContextCarriesTheServiceFallback() {
    // The case that would have caught "service": "null" -- the plain-proxy path.
    // Schema conformance alone is NOT enough here: "null" is a valid JSON string
    // and passes {"type": "string"}, so the value itself must be asserted.
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());
    var event = new TraceEvent.EnterEvent(null, System.nanoTime(), sig);
    var json = CanonicalEntrySerializer.toJson(CanonicalEntryMapper.fromEvent(event));

    assertThat(json).contains("\"service\": \"unknown_service:java\"");
    assertThat(json).doesNotContain("\"service\": \"null\"");
    // Without a SpanContext the correlation ids are genuinely unknown, so they stay
    // absent and the entry cannot be schema-complete. Pinned so the remaining gap is
    // visible and scoped: service is no longer among the violations.
    assertThat(validateAgainstSchema("schema/entry.schema.json", json).toString())
        .contains("trace_id")
        .contains("span_id")
        .doesNotContain("service");
  }

  @Test
  void entryWithoutAServiceNameValidatesAndCarriesTheServiceFallback() {
    var sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());
    var event = new TraceEvent.EnterEvent(sc, System.nanoTime(), sig);
    var json = CanonicalEntrySerializer.toJson(CanonicalEntryMapper.fromEvent(event));

    var errors = validateAgainstSchema("schema/entry.schema.json", json);

    assertThat(errors).as("Entry schema violations: %s", errors).isEmpty();
    assertThat(json).contains("\"service\": \"unknown_service:java\"");
  }

  @Test
  void chapterOfAContextFreeTreeFullyValidatesAgainstTheChapterSchema() {
    // chapter.schema.json lists service, trace_id, nt.storyId and nt.chapterId as required; a
    // context-free chapter used to omit all four. Since identity is resolved eagerly
    // (2026-08-30) the chapter is schema-COMPLETE, not merely missing one violation fewer.
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            251_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var json = new ChapterExporter().exportChapter(tree, new TraceMetadata("Scenario", "success"));

    var errors = validateAgainstSchema("schema/chapter.schema.json", json);

    assertThat(errors).as("Chapter schema violations: %s -- %s", errors, json).isEmpty();
    assertThat(json).contains("\"service\": \"unknown_service:java\"");
    assertThat(json).contains("\"nt.storyId\": \"OrderService.placeOrder\"");
  }

  @Test
  void chapterOfAnEmptyTreeStillValidatesAgainstTheChapterSchema() {
    // Nothing captured: no root call to derive a story from, and no span context to inherit
    // one -- the required fields still have to be there and still have to be well-formed.
    var json =
        new ChapterExporter()
            .exportChapter(new DefaultTraceTree(List.of()), new TraceMetadata("Empty", "success"));

    var errors = validateAgainstSchema("schema/chapter.schema.json", json);

    assertThat(errors).as("Chapter schema violations: %s -- %s", errors, json).isEmpty();
  }

  @Test
  void chapterWithAServiceNameValidatesAgainstSchema() {
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            251_000_000L,
            System.nanoTime(),
            null,
            rootSpanContext());
    var tree = new DefaultTraceTree(List.of(node));

    var json = new ChapterExporter().exportChapter(tree, new TraceMetadata("Scenario", "success"));
    var errors = validateAgainstSchema("schema/chapter.schema.json", json);

    assertThat(errors).as("Chapter schema violations: %s", errors).isEmpty();
    assertThat(json).contains("\"service\": \"order-service\"");
  }

  @Test
  void perTestCanonicalArtifactEntriesFullyValidateAgainstTheEntrySchema() {
    // Item 26a: with service, trace_id, span_id, nt.storyId and nt.chapterId all supplied,
    // a context-free tree now produces a schema-COMPLETE artifact -- which is what item 7's
    // goldens require.
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            251_000_000L);
    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(node)));

    assertThat(entries).isNotEmpty();
    for (var entry : entries) {
      var json = CanonicalEntrySerializer.toJson(entry);
      var errors = validateAgainstSchema("schema/entry.schema.json", json);
      assertThat(errors).as("Entry schema violations: %s -- %s", errors, json).isEmpty();
    }
  }

  @Test
  void perTestCanonicalArtifactEntriesCarryTheServiceFallback() {
    // TraceTreeCanonicalMapper backs every per-test .canonical.json artifact; it never
    // set service at all, so every such file carried "service": "null".
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            251_000_000L);
    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(node)));

    assertThat(entries).isNotEmpty();
    for (var entry : entries) {
      var json = CanonicalEntrySerializer.toJson(entry);
      assertThat(json).contains("\"service\": \"unknown_service:java\"");
      assertThat(json).doesNotContain("\"service\": \"null\"");
      assertThat(validateAgainstSchema("schema/entry.schema.json", json)).isEmpty();
    }
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private static SpanContext rootSpanContext() {
    return SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
        .serviceName("order-service")
        .environment("test")
        .storyId("OrderService.placeOrder")
        .chapterId("OrderService.placeOrder")
        .build();
  }

  private static Set<ValidationMessage> validateAgainstSchema(String schemaResource, String json) {
    var schemaStream =
        SchemaValidationTest.class.getClassLoader().getResourceAsStream(schemaResource);
    if (schemaStream == null) {
      throw new IllegalStateException("Schema not found on classpath: " + schemaResource);
    }
    JsonSchema schema = FACTORY.getSchema(schemaStream);
    return schema.validate(json, com.networknt.schema.InputFormat.JSON);
  }
}
