/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.export.ChapterExporter;
import ai.narrativetrace.core.output.TraceFileWriter;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.render.TraceMetadata;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validates the identity fields of the artifacts the <em>production writers</em> put on disk,
 * against all three canonical schemas, for a scenario captured the way a plain unit test captures
 * one: no HTTP filter, no service identity, nothing supplying a trace from outside.
 *
 * <p>INTENT: {@code chapter.schema.json} requires {@code trace_id}, {@code nt.storyId} and {@code
 * nt.chapterId}. The chapter exporter used to write all three through the optional-field helper and
 * so omitted them whenever the tree carried no span context — every such chapter failed Java's own
 * schema, and no fixture-driven test could see it because fixtures always supplied a context. This
 * test reads the bytes back off the filesystem instead.
 *
 * <p><b>@llmNote</b> Deliberately not a unit test of the exporters: {@code SchemaValidationTest}
 * builds their input by hand and validates the returned string, so it cannot observe what a capture
 * actually produces. Do not "simplify" this by dropping the writers — that reintroduces the blind
 * spot. The chapter has no production writer yet (it is library API without a caller), so the
 * chapter case pairs {@link ChapterExporter} with the real {@link TraceFileWriter}.
 */
class CapturedIdentitySchemaConformanceTest {

  private static final String ENTRY_SCHEMA = "schema/entry.schema.json";
  private static final String CHAPTER_SCHEMA = "schema/chapter.schema.json";
  private static final String CHAPTER_TREE_SCHEMA = "schema/chapter-tree.schema.json";

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
  }

  @AfterEach
  void tearDown() {
    context.reset();
  }

  @Test
  void chapterOfAPlainCaptureValidatesOnDisk(@TempDir Path dir) throws IOException {
    var json = writeChapter(capturePlainScenario(), dir);

    assertThat(violations(CHAPTER_SCHEMA, json)).isEmpty();
  }

  @Test
  void canonicalEntriesOfAPlainCaptureValidateOnDisk(@TempDir Path dir) throws IOException {
    var entries = writeCanonicalEntries(capturePlainScenario(), dir);

    assertThat(entries).isNotEmpty();
    for (var entry : entries) {
      assertThat(violations(ENTRY_SCHEMA, entry)).as("Entry violations in %s", entry).isEmpty();
    }
  }

  @Test
  void chapterTreeOfAPlainCaptureValidatesOnDisk(@TempDir Path dir) throws IOException {
    var json = writeTraceArtifacts(capturePlainScenario(), dir);

    assertThat(violations(CHAPTER_TREE_SCHEMA, json)).isEmpty();
  }

  @Test
  void chapterOfATreeThatKeptNoSpanContextValidatesOnDisk(@TempDir Path dir) throws IOException {
    var json = writeChapter(contextFreeTree(), dir);

    assertThat(violations(CHAPTER_SCHEMA, json)).isEmpty();
    assertThat(json).contains("\"nt.storyId\": \"OrderService.placeOrder\"");
    assertThat(json).contains("\"nt.chapterId\": \"OrderService.placeOrder\"");
    assertThat(json).containsPattern("\"trace_id\": \"[0-9a-f]{32}\"");
  }

  @Test
  void canonicalEntriesOfATreeThatKeptNoSpanContextValidateOnDisk(@TempDir Path dir)
      throws IOException {
    var entries = writeCanonicalEntries(contextFreeTree(), dir);

    assertThat(entries).isNotEmpty();
    for (var entry : entries) {
      assertThat(violations(ENTRY_SCHEMA, entry)).as("Entry violations in %s", entry).isEmpty();
    }
  }

  @Test
  void theChapterAndItsOwnEntriesOnDiskNameTheSameTrace(@TempDir Path dir) throws IOException {
    var tree = capturePlainScenario();

    var chapter = new ObjectMapper().readTree(writeChapter(tree, dir));
    var entries = writeCanonicalEntries(tree, dir);

    var traceId = chapter.at("/trace_id").asText();
    assertThat(traceId).matches("^[0-9a-f]{32}$");
    for (var entry : entries) {
      assertThat(new ObjectMapper().readTree(entry).at("/trace_id").asText()).isEqualTo(traceId);
      assertThat(new ObjectMapper().readTree(entry).at("/nt.storyId").asText())
          .isEqualTo(chapter.at("/nt.storyId").asText());
    }
  }

  @Test
  void theChapterAndItsOwnEntriesAgreeForATreeThatKeptNoSpanContext(@TempDir Path dir)
      throws IOException {
    // The case the shared resolution exists for: two exporters, one tree, no context to inherit
    // from -- they must still not invent two different traces.
    var tree = contextFreeTree();

    var chapter = new ObjectMapper().readTree(writeChapter(tree, dir));
    var entries = writeCanonicalEntries(tree, dir);

    for (var entry : entries) {
      assertThat(new ObjectMapper().readTree(entry).at("/trace_id").asText())
          .isEqualTo(chapter.at("/trace_id").asText());
    }
  }

  @Test
  void theChapterAndTheTreeEmbeddedInItNameTheSameTrace(@TempDir Path dir) throws IOException {
    var chapter = new ObjectMapper().readTree(writeChapter(capturePlainScenario(), dir));

    var embedded = new ObjectMapper().readTree(chapter.at("/nt.chapterTree").asText());

    assertThat(embedded.at("/trace/traceId").asText())
        .as("a chapter that names one trace must not embed a tree naming another")
        .isEqualTo(chapter.at("/trace_id").asText());
  }

  @Test
  void theChapterAndTheTreeEmbeddedInItAgreeForATreeThatKeptNoSpanContext(@TempDir Path dir)
      throws IOException {
    // The third emitter. It used to omit the whole `trace` block when no *root* carried a span
    // context -- legal against chapter-tree.schema.json, and still a chapter naming a trace while
    // the tree inside it named none.
    var chapter = new ObjectMapper().readTree(writeChapter(contextFreeTree(), dir));

    var embedded = new ObjectMapper().readTree(chapter.at("/nt.chapterTree").asText());

    assertThat(embedded.at("/trace/traceId").asText()).isEqualTo(chapter.at("/trace_id").asText());
    assertThat(embedded.at("/trace/traceName").asText())
        .isEqualTo(chapter.at("/nt.traceName").asText());
  }

  @Test
  void theEmbeddedTreeFindsTheTraceOnADeeperNodeWhenNoRootCarriesOne(@TempDir Path dir)
      throws IOException {
    // Roots-only scanning is the other half of the divergence: a mixed tree whose context sits on
    // a child resolved to "no trace at all" here while both other emitters inherited it.
    var tree = treeWithContextOnAChildOnly();

    var chapter = new ObjectMapper().readTree(writeChapter(tree, dir));
    var embedded = new ObjectMapper().readTree(chapter.at("/nt.chapterTree").asText());

    assertThat(embedded.at("/trace/traceId").asText()).isEqualTo(chapter.at("/trace_id").asText());
    assertThat(embedded.at("/trace/serviceName").asText())
        .as("the depth-first scan inherits the whole context, not only its trace id")
        .isEqualTo("order-service");
  }

  @Test
  void theEmbeddedTreeOfASpanLessCaptureStillValidatesOnDisk(@TempDir Path dir) throws IOException {
    var json = writeTraceArtifacts(contextFreeTree(), dir);

    assertThat(violations(CHAPTER_TREE_SCHEMA, json)).isEmpty();
    assertThat(new ObjectMapper().readTree(json).at("/trace/traceId").asText())
        .matches("^[0-9a-f]{32}$");
  }

  // ── the scenario ────────────────────────────────────────────────────────────

  /** A plain nested capture: what a unit test gets with no filter and no service identity. */
  private TraceTree capturePlainScenario() {
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    context.exitMethodWithReturn("\"TXN-1\"");
    context.exitMethodWithReturn("\"order-1\"");
    return context.captureTrace();
  }

  /** A tree whose nodes never had a span context — hand-built, replayed, or statically scanned. */
  private static TraceTree contextFreeTree() {
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"TXN-1\""),
            2_000_000L);
    return new DefaultTraceTree(
        List.of(
            new TraceNode(
                new MethodSignature("OrderService", "placeOrder", List.of()),
                List.of(child),
                new TraceOutcome.Returned("\"order-1\""),
                4_000_000L)));
  }

  /** A mixed tree: the root lost its context, a descendant kept one. ADR-014's inherit rung. */
  private static TraceTree treeWithContextOnAChildOnly() {
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"TXN-1\""),
            2_000_000L,
            System.nanoTime(),
            null,
            SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
                .serviceName("order-service")
                .build());
    return new DefaultTraceTree(
        List.of(
            new TraceNode(
                new MethodSignature("OrderService", "placeOrder", List.of()),
                List.of(child),
                new TraceOutcome.Returned("\"order-1\""),
                4_000_000L)));
  }

  // ── the real writers ────────────────────────────────────────────────────────

  /** Writes the chapter through the real file writer and reads the bytes back. */
  private static String writeChapter(TraceTree tree, Path dir) throws IOException {
    var file = dir.resolve("chapters").resolve("placesOrder.chapter.json");
    var chapter =
        new ChapterExporter().exportChapter(tree, new TraceMetadata("places an order", "success"));
    new TraceFileWriter().write(chapter, file);
    return Files.readString(file);
  }

  /** Writes {@code <test>.canonical.json} through the production helper; returns its elements. */
  private static List<String> writeCanonicalEntries(TraceTree tree, Path dir) throws IOException {
    TraceTestSupport.writeCanonicalTraceFile("com.example.OrderTest", "placesOrder", tree, dir);
    var array = new ObjectMapper().readTree(readArtifact(dir, ".canonical.json"));
    var entries = new java.util.ArrayList<String>();
    for (JsonNode element : array) {
      entries.add(element.toString());
    }
    return entries;
  }

  /**
   * Writes the markdown-path artifacts through the production helper; returns the {@code .json}.
   */
  private static String writeTraceArtifacts(TraceTree tree, Path dir) throws IOException {
    TraceTestSupport.writeTraceFile(
        "com.example.OrderTest",
        "placesOrder",
        "places an order",
        tree,
        false,
        dir,
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
        "markdown",
        t -> "graph TD",
        t -> "@startuml\n@enduml");
    return readArtifact(dir, ".json");
  }

  private static String readArtifact(Path dir, String suffix) throws IOException {
    try (Stream<Path> files = Files.walk(dir)) {
      var artifact =
          files
              .filter(path -> path.getFileName().toString().endsWith(suffix))
              .findFirst()
              .orElseThrow(() -> new AssertionError("no " + suffix + " artifact was written"));
      return Files.readString(artifact);
    }
  }

  private static Set<ValidationMessage> violations(String schemaResource, String json) {
    var stream =
        CapturedIdentitySchemaConformanceTest.class
            .getClassLoader()
            .getResourceAsStream(schemaResource);
    if (stream == null) {
      throw new IllegalStateException("Schema not found on classpath: " + schemaResource);
    }
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(stream)
        .validate(json, InputFormat.JSON);
  }
}
