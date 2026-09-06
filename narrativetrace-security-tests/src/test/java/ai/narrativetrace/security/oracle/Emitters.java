/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.oracle;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.export.JsonExporter;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.render.FrontmatterBuilder;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.core.render.ProseRenderer;
import ai.narrativetrace.core.render.ScenarioResult;
import ai.narrativetrace.core.render.StructuralTraceRenderer;
import ai.narrativetrace.core.render.TraceMetadata;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import ai.narrativetrace.diagrams.MermaidSequenceDiagramRenderer;
import ai.narrativetrace.diagrams.PlantUmlSequenceDiagramRenderer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every output NarrativeTrace can produce from one trace, in one map.
 *
 * <p>INTENT: An oracle that names the formats it checks goes stale the moment a format is added.
 * The suite asserts over <em>this</em> map, so a new emitter added here is immediately covered by
 * redaction, well-formedness, injection containment and boundedness at once.
 *
 * <p><b>@llmNote</b> Both halves matter. The in-memory renderers are what a library consumer calls;
 * the written artifacts are what the product puts on disk, and the two have diverged before —
 * {@code TraceArtifactSchemaConformanceTest} exists because a unit test of the exporter could not
 * see what the writer wrote. Everything here goes through the shipped path.
 *
 * <p><b>@sideEffects</b> {@link #everyOutput} creates and deletes a temporary directory per call.
 */
public final class Emitters {

  /** The scenario name every artifact is written under, unless a test supplies its own. */
  public static final String SCENARIO = "a hostile scenario";

  private static final String TEST_CLASS = "com.example.HostileTest";
  private static final String TEST_METHOD = "rendersHostileInput";

  private Emitters() {}

  /** A one-node tree whose parameter and return value carry the given rendered text. */
  public static TraceTree treeOf(String renderedArgument, String renderedReturn) {
    var node =
        new TraceNode(
            new MethodSignature(
                "CardRepository",
                "findByNumber",
                List.of(new ParameterCapture("probe", renderedArgument, false))),
            List.of(),
            new TraceOutcome.Returned(renderedReturn),
            42_000_000L);
    return new DefaultTraceTree(List.of(node));
  }

  /** A one-node tree whose narration and error context carry the given prose. */
  public static TraceTree treeNarrating(String narration, String errorContext) {
    var node =
        new TraceNode(
            new MethodSignature(
                "PaymentService", "charge", List.of(), narration, errorContext, "charging {card}"),
            List.of(),
            new TraceOutcome.Returned("true"),
            42_000_000L);
    return new DefaultTraceTree(List.of(node));
  }

  /**
   * A one-node tree whose <em>metadata</em> — class name, method name, parameter name and return
   * type — is the hostile input, rather than a rendered value.
   *
   * <p>INTENT: Every other builder here puts hostile text where a <em>value</em> goes, and values
   * reach the renderers already escaped by {@code ValueRenderer}. The 2026-09-02 audit's finding 4
   * was that metadata does not: {@code MethodSignature} is a public record with no validation, and
   * a trace tree built by public API, deserialized from JSON, or post-processed by an integration
   * can carry anything in those fields. Fuzzing only the value channel could never have found it.
   *
   * @param hostile the attacker-controlled text, placed in every metadata field at once
   */
  public static TraceTree treeWithHostileMetadata(String hostile) {
    var node =
        new TraceNode(
            new MethodSignature(
                hostile,
                hostile,
                List.of(new ParameterCapture(hostile, "\"safe\"", false)),
                null,
                null,
                null,
                hostile,
                hostile,
                null,
                null),
            List.of(),
            new TraceOutcome.Returned("true"),
            42_000_000L);
    return new DefaultTraceTree(List.of(node));
  }

  /** A one-node tree that threw, so the exception-message paths are exercised. */
  public static TraceTree treeThrowing(Throwable thrown) {
    var node =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(thrown),
            42_000_000L);
    return new DefaultTraceTree(List.of(node));
  }

  /**
   * Every output the product can produce from {@code tree}, keyed by emitter.
   *
   * @return renderer outputs keyed {@code renderer:<name>} and written artifacts keyed {@code
   *     artifact:<file name>}
   */
  public static Map<String, String> everyOutput(TraceTree tree) {
    var outputs = renderers(tree);
    var dir = temporaryDirectory();
    try {
      outputs.putAll(writtenArtifacts(tree, dir));
    } finally {
      deleteRecursively(dir);
    }
    return outputs;
  }

  /** The in-memory renderers, which a library consumer calls directly. */
  public static Map<String, String> renderers(TraceTree tree) {
    var metadata = new TraceMetadata(SCENARIO, ScenarioResult.SUCCESS);
    var outputs = new LinkedHashMap<String, String>();
    outputs.put("renderer:prose", new ProseRenderer().render(tree));
    outputs.put("renderer:indented", new IndentedTextRenderer().render(tree));
    outputs.put("renderer:markdown", new MarkdownRenderer().render(tree));
    outputs.put(
        "renderer:markdown-document", new MarkdownRenderer().renderDocument(tree, metadata));
    outputs.put("renderer:frontmatter", new FrontmatterBuilder().scenario(SCENARIO).build(tree));
    outputs.put(
        "renderer:structural", new StructuralTraceRenderer().renderDocument(tree, SCENARIO));
    outputs.put("renderer:json", new JsonExporter().exportDocument(tree, metadata));
    outputs.put("renderer:mermaid", new MermaidSequenceDiagramRenderer().render(tree));
    outputs.put(
        "renderer:mermaid-aliases", new MermaidSequenceDiagramRenderer().renderWithAliases(tree));
    outputs.put("renderer:plantuml", new PlantUmlSequenceDiagramRenderer().render(tree));
    return outputs;
  }

  /** Everything the shipped writers put on disk, plus the console summary they print. */
  public static Map<String, String> writtenArtifacts(TraceTree tree, Path dir) {
    return writtenArtifacts(tree, dir, TEST_CLASS, TEST_METHOD);
  }

  /**
   * The same, under caller-supplied names.
   *
   * <p>INTENT: The test class and method name are inputs the writers turn into paths, and
   * `TraceTestSupport` is public API whose callers do not all derive them from {@code
   * Class.getName()}. Hostile names therefore need the same emitter map hostile values get.
   */
  public static Map<String, String> writtenArtifacts(
      TraceTree tree, Path dir, String testClass, String testMethod) {
    var console = new ByteArrayOutputStream();
    write(tree, dir, new PrintStream(console, true, StandardCharsets.UTF_8), testClass, testMethod);
    var outputs = new LinkedHashMap<String, String>();
    outputs.put("artifact:console", console.toString(StandardCharsets.UTF_8));
    try (Stream<Path> files = Files.walk(dir)) {
      files
          .filter(Files::isRegularFile)
          .forEach(file -> outputs.put("artifact:" + file.getFileName(), read(file)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return outputs;
  }

  private static void write(
      TraceTree tree, Path dir, PrintStream console, String testClass, String testMethod) {
    try {
      TraceTestSupport.writeTraceFile(
          testClass,
          testMethod,
          SCENARIO,
          tree,
          false,
          dir,
          console,
          "markdown",
          new MermaidSequenceDiagramRenderer()::render,
          new PlantUmlSequenceDiagramRenderer()::render);
      TraceTestSupport.writeCanonicalTraceFile(testClass, testMethod, tree, dir);
      TraceTestSupport.writeStructuralTraceFile(testClass, testMethod, tree, dir);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path temporaryDirectory() {
    try {
      return Files.createTempDirectory("narrativetrace-security");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void deleteRecursively(Path dir) {
    try (Stream<Path> paths = Files.walk(dir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(Emitters::delete);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void delete(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String read(Path file) {
    try {
      return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
