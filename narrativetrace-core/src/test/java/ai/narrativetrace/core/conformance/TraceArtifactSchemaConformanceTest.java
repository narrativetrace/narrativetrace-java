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
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.tree.DefaultTraceTree;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validates the JSON artifact the <em>production writer</em> puts on disk against the canonical
 * {@code chapter-tree.schema.json}.
 *
 * <p>INTENT: Close the gap between "a conforming document can exist" and "the document we ship
 * conforms". Every case here routes through {@link TraceTestSupport#writeTraceFile} and reads the
 * bytes back off the filesystem, so any divergence between what production writes and what the
 * cross-port schema permits fails the build.
 *
 * <p><b>@llmNote</b> Deliberately not a unit test of {@code JsonExporter}. {@code
 * SchemaValidationTest} constructs its own {@link ai.narrativetrace.core.render.TraceMetadata} and
 * validates that; it therefore cannot observe what the shipped artifact contains, because it never
 * routes through the writer. Do not "simplify" this by calling the exporter directly — that
 * reintroduces the exact blind spot this test exists to close.
 */
class TraceArtifactSchemaConformanceTest {

  private static final String SCHEMA = "schema/chapter-tree.schema.json";

  @Test
  void artifactOfAPassingTestConformsToSchema(@TempDir Path dir) throws IOException {
    var json = writeAndRead(simpleTree(), false, dir);

    assertThat(violations(json)).isEmpty();
  }

  @Test
  void artifactOfAFailingTestConformsToSchema(@TempDir Path dir) throws IOException {
    var json = writeAndRead(simpleTree(), true, dir);

    assertThat(violations(json)).isEmpty();
  }

  @Test
  void artifactConformsWhenAParameterRendersToNull(@TempDir Path dir) throws IOException {
    var json = writeAndRead(treeWithParameter(null), false, dir);

    assertThat(violations(json)).isEmpty();
  }

  @Test
  void artifactConformsWhenAParameterRendersToTheEmptyString(@TempDir Path dir) throws IOException {
    var json = writeAndRead(treeWithParameter(""), false, dir);

    assertThat(violations(json)).isEmpty();
  }

  @Test
  void artifactConformsWhenAParameterIsRedacted(@TempDir Path dir) throws IOException {
    var node =
        node(
            new MethodSignature(
                "PaymentService", "charge", List.of(new ParameterCapture("card", "4111", true))),
            new TraceOutcome.Returned("\"ok\""));
    var json = writeAndRead(new DefaultTraceTree(List.of(node)), false, dir);

    assertThat(violations(json)).isEmpty();
  }

  @Test
  void artifactConformsWhenACallThrows(@TempDir Path dir) throws IOException {
    var node =
        node(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            new TraceOutcome.Threw(new IllegalStateException("out of stock")));
    var json = writeAndRead(new DefaultTraceTree(List.of(node)), true, dir);

    assertThat(violations(json)).isEmpty();
  }

  @Test
  void artifactConformsWhenACallNeverCompleted(@TempDir Path dir) throws IOException {
    var node =
        node(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            new TraceOutcome.Incomplete());
    var json = writeAndRead(new DefaultTraceTree(List.of(node)), true, dir);

    assertThat(violations(json)).isEmpty();
  }

  @Test
  void artifactConformsForASyntheticLauncherNodeCarryingNoOutcome(@TempDir Path dir)
      throws IOException {
    var node = node(new MethodSignature("Launcher", "main", List.of()), null);
    var json = writeAndRead(new DefaultTraceTree(List.of(node)), false, dir);

    assertThat(violations(json)).isEmpty();
  }

  @Test
  void artifactConformsForANestedTreeCarryingASpanContext(@TempDir Path dir) throws IOException {
    var child =
        node(
            new MethodSignature("InventoryService", "reserve", List.of()),
            new TraceOutcome.Returned("true"));
    var root =
        new TraceNode(
            new MethodSignature(
                "OrderService", "placeOrder", List.of(new ParameterCapture("id", "\"42\"", false))),
            List.of(child),
            new TraceOutcome.Returned("\"order-42\""),
            251_000_000L,
            System.nanoTime(),
            null,
            rootSpanContext());
    var json = writeAndRead(new DefaultTraceTree(List.of(root)), false, dir);

    assertThat(violations(json)).isEmpty();
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private static TraceNode node(MethodSignature signature, TraceOutcome outcome) {
    return new TraceNode(signature, List.of(), outcome, 42_000_000L);
  }

  private static TraceTree simpleTree() {
    return new DefaultTraceTree(
        List.of(
            node(
                new MethodSignature("OrderService", "placeOrder", List.of()),
                new TraceOutcome.Returned("\"order-42\""))));
  }

  private static TraceTree treeWithParameter(String renderedValue) {
    return new DefaultTraceTree(
        List.of(
            node(
                new MethodSignature(
                    "OrderService",
                    "placeOrder",
                    List.of(new ParameterCapture("note", renderedValue, false))),
                new TraceOutcome.Returned("\"order-42\""))));
  }

  private static SpanContext rootSpanContext() {
    return SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
        .serviceName("order-service")
        .environment("test")
        .build();
  }

  /** Runs the real production writer and returns the {@code .json} artifact it produced. */
  private static String writeAndRead(TraceTree trace, boolean failed, Path dir) throws IOException {
    TraceTestSupport.writeTraceFile(
        "com.example.OrderTest",
        "placesOrder",
        "places an order",
        trace,
        failed,
        dir,
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
        "markdown",
        tree -> "graph TD",
        tree -> "@startuml\n@enduml");
    try (Stream<Path> files = Files.walk(dir)) {
      var artifact =
          files
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .findFirst()
              .orElseThrow(
                  () -> new AssertionError("production writer produced no .json artifact"));
      return Files.readString(artifact);
    }
  }

  private static Set<ValidationMessage> violations(String json) {
    var stream =
        TraceArtifactSchemaConformanceTest.class.getClassLoader().getResourceAsStream(SCHEMA);
    if (stream == null) {
      throw new IllegalStateException("Schema not found on classpath: " + SCHEMA);
    }
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(stream)
        .validate(json, InputFormat.JSON);
  }
}
