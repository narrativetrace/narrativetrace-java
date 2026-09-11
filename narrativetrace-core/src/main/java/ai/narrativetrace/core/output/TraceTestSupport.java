/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.export.JsonExporter;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.core.render.ScenarioResult;
import ai.narrativetrace.core.render.StructuralTraceRenderer;
import ai.narrativetrace.core.render.TraceMetadata;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Shared file and console output helpers for the JUnit integrations.
 *
 * <p>INTENT: JUnit 4 and JUnit 5 modules delegate here so format selection, file naming, Markdown
 * companions, and console summaries stay consistent.
 *
 * <p><b>@layer</b> Test-output utility. Production integrations should prefer {@code TraceExporter}
 * and renderers directly.
 */
public final class TraceTestSupport {

  private TraceTestSupport() {}

  public static String extensionForFormat(String format) {
    return switch (format.toLowerCase()) {
      case "text" -> ".txt";
      case "mermaid" -> ".mmd";
      case "plantuml" -> ".puml";
      default -> ".md";
    };
  }

  public static String buildFailureReport(String scenario, String trace) {
    return "\n\n" + scenario + "\n\nExecution trace:\n" + trace;
  }

  /**
   * Failure report that localizes change instead of dumping the trace: when the failing scenario's
   * structure CHANGED since last green, the report is the delta — summary plus readable diff —
   * because assertion output already covers detection and the trace's job here is saying
   * <em>where</em> behavior moved.
   */
  public static String buildFailureReport(String scenario, String trace, ScenarioDelta delta) {
    if (delta.kind() == ScenarioDelta.Kind.CHANGED) {
      return "\n\n"
          + scenario
          + "\n\nChanged since last green ("
          + delta.summary()
          + "):\n"
          + delta.diff();
    }
    if (delta.kind() == ScenarioDelta.Kind.UNCHANGED) {
      return "\n\n"
          + scenario
          + "\n\nStructure unchanged since last green — the flow held; check values and"
          + " assertions.\n\nExecution trace:\n"
          + trace;
    }
    return buildFailureReport(scenario, trace);
  }

  public static String renderForFormat(
      String format,
      TraceTree trace,
      String displayName,
      boolean failed,
      NarrativeRenderer mermaidRenderer,
      NarrativeRenderer plantumlRenderer) {
    return renderForFormat(
        format, trace, displayName, failed, mermaidRenderer, plantumlRenderer, true);
  }

  /**
   * The same render, with loop folding selectable.
   *
   * @param foldLoops {@code false} renders every iteration of a loop in full instead of summarizing
   *     the repeats as {@code ×k more …}; Markdown only, since no other format folds
   */
  public static String renderForFormat(
      String format,
      TraceTree trace,
      String displayName,
      boolean failed,
      NarrativeRenderer mermaidRenderer,
      NarrativeRenderer plantumlRenderer,
      boolean foldLoops) {
    var scenario = ScenarioFramer.humanize(displayName);
    return switch (format.toLowerCase()) {
      case "text" ->
          ScenarioFramer.frame(displayName) + "\n\n" + new IndentedTextRenderer().render(trace);
      case "mermaid" -> mermaidRenderer.render(trace);
      case "plantuml" -> plantumlRenderer.render(trace);
      default -> {
        var metadata = new TraceMetadata(scenario, ScenarioResult.of(failed));
        var renderer = foldLoops ? new MarkdownRenderer() : MarkdownRenderer.unfolded();
        yield renderer.renderDocument(trace, metadata);
      }
    };
  }

  /**
   * Writes the per-test trace artifacts and reports the scenario's structural delta against its
   * last green {@code .nt} artifact.
   *
   * @return the delta when the markdown path emitted the structural artifact; empty for other
   *     formats and empty traces
   */
  public static Optional<ScenarioDelta> writeTraceFile(
      String testClassName,
      String testMethodName,
      String displayName,
      TraceTree trace,
      boolean failed,
      Path outputDir,
      PrintStream out,
      String format,
      NarrativeRenderer mermaidRenderer,
      NarrativeRenderer plantumlRenderer)
      throws IOException {
    return writeTraceFile(
        ArtifactIdentity.ofMethod(testClassName, testMethodName),
        displayName,
        trace,
        failed,
        outputDir,
        out,
        format,
        mermaidRenderer,
        plantumlRenderer);
  }

  /**
   * The same write, keyed by the full {@link ArtifactIdentity} — the overload an integration whose
   * test method may run more than once (parameterized, repeated) must use, so each invocation gets
   * its own files instead of overwriting the previous one's.
   *
   * @param failed the run's verdict: {@code true} when the test failed <em>or</em> its approval was
   *     rejected. A non-green run compares against the last-green artifact and never advances it
   */
  public static Optional<ScenarioDelta> writeTraceFile(
      ArtifactIdentity identity,
      String displayName,
      TraceTree trace,
      boolean failed,
      Path outputDir,
      PrintStream out,
      String format,
      NarrativeRenderer mermaidRenderer,
      NarrativeRenderer plantumlRenderer)
      throws IOException {
    return writeTraceFile(
        identity,
        displayName,
        trace,
        failed,
        outputDir,
        out,
        format,
        mermaidRenderer,
        plantumlRenderer,
        true);
  }

  /**
   * The same write, with loop folding selectable.
   *
   * @param foldLoops {@code false} renders every iteration of a loop in full in the Markdown
   *     narrative instead of summarizing the repeats as {@code ×k more …}. The structural artifact
   *     and the delta are unaffected — they carry no values, so folding never applied to them
   */
  public static Optional<ScenarioDelta> writeTraceFile(
      ArtifactIdentity identity,
      String displayName,
      TraceTree trace,
      boolean failed,
      Path outputDir,
      PrintStream out,
      String format,
      NarrativeRenderer mermaidRenderer,
      NarrativeRenderer plantumlRenderer,
      boolean foldLoops)
      throws IOException {
    if (trace.isEmpty()) {
      return Optional.empty();
    }
    var resolver = new OutputDirectoryResolver(outputDir);
    var file = resolver.traceArtifact(identity, extensionForFormat(format));
    var content =
        renderForFormat(
            format, trace, displayName, failed, mermaidRenderer, plantumlRenderer, foldLoops);
    var writer = new TraceFileWriter();
    writer.write(content, file);
    out.println(
        "\n"
            + ScenarioFramer.frame(displayName)
            + "\n\n"
            + new IndentedTextRenderer().render(trace));
    // A file:// URI so terminals and IDE consoles render the path as a clickable link.
    out.println("Trace written: " + file.toUri());

    if (!"markdown".equalsIgnoreCase(format)) {
      return Optional.empty();
    }
    return Optional.of(
        writeMarkdownExtras(
            writer, resolver, identity, displayName, trace, failed, mermaidRenderer));
  }

  /**
   * Writes the per-test canonical entry artifact ({@code <test>.canonical.json}): a JSON array of
   * current-schema entries (see {@code CanonicalEntry.SCHEMA_VERSION}) flattened from the tree. A
   * machine artifact for canonical-schema consumers (ports, conformance fixtures); written only
   * when {@code narrativetrace.canonicalJson=true}.
   */
  public static void writeCanonicalTraceFile(
      String testClassName, String testMethodName, TraceTree trace, Path outputDir)
      throws IOException {
    writeCanonicalTraceFile(
        ArtifactIdentity.ofMethod(testClassName, testMethodName), trace, outputDir);
  }

  /** The canonical entry artifact of one invocation, keyed by the full artifact identity. */
  public static void writeCanonicalTraceFile(
      ArtifactIdentity identity, TraceTree trace, Path outputDir) throws IOException {
    writeEntryArtifact(identity, trace, outputDir, ".canonical.json", entry -> entry);
  }

  /**
   * Writes the per-test AI-safe structural artifact ({@code <test>.structural.json}): the same
   * entry array as the canonical artifact with every runtime-value field elided via {@link
   * ai.narrativetrace.core.export.StructuralProjection} — Level 1 ("Structure Only") of the AI
   * output ladder (ADR-002). Written only when {@code narrativetrace.structuralJson=true}.
   */
  public static void writeStructuralTraceFile(
      String testClassName, String testMethodName, TraceTree trace, Path outputDir)
      throws IOException {
    writeStructuralTraceFile(
        ArtifactIdentity.ofMethod(testClassName, testMethodName), trace, outputDir);
  }

  /** The AI-safe entry artifact of one invocation, keyed by the full artifact identity. */
  public static void writeStructuralTraceFile(
      ArtifactIdentity identity, TraceTree trace, Path outputDir) throws IOException {
    writeEntryArtifact(
        identity,
        trace,
        outputDir,
        ".structural.json",
        ai.narrativetrace.core.export.StructuralProjection::project);
  }

  private static void writeEntryArtifact(
      ArtifactIdentity identity,
      TraceTree trace,
      Path outputDir,
      String suffix,
      java.util.function.UnaryOperator<ai.narrativetrace.core.export.CanonicalEntry> projection)
      throws IOException {
    if (trace.isEmpty()) {
      return;
    }
    var file = new OutputDirectoryResolver(outputDir).traceArtifact(identity, suffix);
    var json =
        ai.narrativetrace.core.export.TraceTreeCanonicalMapper.fromTree(trace).stream()
            .map(projection)
            .map(ai.narrativetrace.core.export.CanonicalEntrySerializer::toJson)
            .collect(java.util.stream.Collectors.joining(",\n", "[\n", "\n]"));
    new TraceFileWriter().write(json, file);
  }

  private static ScenarioDelta writeMarkdownExtras(
      TraceFileWriter writer,
      OutputDirectoryResolver resolver,
      ArtifactIdentity identity,
      String displayName,
      TraceTree trace,
      boolean failed,
      NarrativeRenderer mermaidRenderer)
      throws IOException {
    writer.write(mermaidRenderer.render(trace), resolver.diagramFile(identity));

    var scenario = ScenarioFramer.humanize(displayName);
    var metadata = new TraceMetadata(scenario, ScenarioResult.of(failed));
    writer.write(
        new JsonExporter().exportDocument(trace, metadata),
        resolver.traceArtifact(identity, ".json"));

    return writeStructuralArtifact(
        writer, trace, failed, resolver.structuralFile(identity), scenario);
  }

  /**
   * Writes the ADR-002 structural artifact ({@code .nt}) and classifies the scenario against it.
   *
   * <p>The file on disk is the LAST GREEN baseline: a green run advances it, a non-green run
   * compares against it but never overwrites it — so the delta always reads "what changed since the
   * last time this scenario passed".
   *
   * <p><b>@llmNote</b> "Green" is the run's whole verdict, not just its assertions: a test that
   * passed but whose structure the approval gate <em>rejected</em> ends red, and a rejected
   * structure must never become the baseline. An integration therefore has to know the approval
   * verdict before it calls this — advancing first and verifying afterwards left the rejected
   * structure in the baseline, and the next (reverted, correct) run reported a removal that never
   * happened (2026-09-08 agent evaluation).
   */
  private static ScenarioDelta writeStructuralArtifact(
      TraceFileWriter writer, TraceTree trace, boolean failed, Path ntFile, String scenario)
      throws IOException {
    var current = new StructuralTraceRenderer().renderDocument(trace, scenario);
    var baseline = Files.exists(ntFile) ? Files.readString(ntFile) : null;
    var delta = ScenarioDelta.of(scenario, baseline, current);
    if (!failed) {
      writer.write(current, ntFile);
    }
    return delta;
  }

  public static void writeClarityReport(
      Map<String, TraceTree> traces,
      Path outputDir,
      Function<Map<String, TraceTree>, String> reportRenderer)
      throws IOException {
    if (traces.isEmpty()) {
      return;
    }
    var report = reportRenderer.apply(traces);
    new TraceFileWriter().write(report, outputDir.resolve("clarity-report.md"));
  }

  public static void writeClarityReport(
      Map<String, TraceTree> traces,
      Path outputDir,
      Function<Map<String, TraceTree>, String> reportRenderer,
      Function<Map<String, TraceTree>, String> jsonExporter)
      throws IOException {
    writeClarityReport(traces, outputDir, reportRenderer);
    if (traces.isEmpty()) {
      return;
    }
    var json = jsonExporter.apply(traces);
    new TraceFileWriter().write(json, outputDir.resolve("clarity-results.json"));
  }

  public static void printConsoleSummary(
      Map<String, TraceTree> traces,
      Path outputDir,
      PrintStream out,
      Function<TraceTree, Double> clarityScorer) {
    var scores = new ArrayList<Double>();
    for (var tree : traces.values()) {
      scores.add(clarityScorer.apply(tree));
    }
    var reporter = new ConsoleSummaryReporter();
    out.println(reporter.formatSuiteFooter(traces.size(), outputDir.toString(), scores));
  }

  /**
   * Writes the clarity report from an ordered list of (scenarioName, trace) pairs.
   *
   * <p>Use this overload when the same scenario name may appear more than once (e.g., two test
   * classes that share the same display name). The list preserves all entries whereas a {@code Map}
   * would silently drop duplicates.
   */
  public static void writeClarityReport(
      List<Map.Entry<String, TraceTree>> traces,
      Path outputDir,
      Function<List<Map.Entry<String, TraceTree>>, String> reportRenderer,
      Function<List<Map.Entry<String, TraceTree>>, String> jsonExporter)
      throws IOException {
    if (traces.isEmpty()) {
      return;
    }
    var report = reportRenderer.apply(traces);
    new TraceFileWriter().write(report, outputDir.resolve("clarity-report.md"));
    var json = jsonExporter.apply(traces);
    new TraceFileWriter().write(json, outputDir.resolve("clarity-results.json"));
  }

  /**
   * Prints the console summary from an ordered list of (scenarioName, trace) pairs.
   *
   * <p>Use this overload when the same scenario name may appear more than once across test classes.
   */
  public static void printConsoleSummary(
      List<Map.Entry<String, TraceTree>> traces,
      Path outputDir,
      PrintStream out,
      Function<TraceTree, Double> clarityScorer) {
    printConsoleSummary(traces, outputDir, out, clarityScorer, TraceLoss.none());
  }

  private static void printConsoleSummary(
      List<Map.Entry<String, TraceTree>> traces,
      Path outputDir,
      PrintStream out,
      Function<TraceTree, Double> clarityScorer,
      TraceLoss loss) {
    var scores = new ArrayList<Double>();
    for (var entry : traces) {
      scores.add(clarityScorer.apply(entry.getValue()));
    }
    var reporter = new ConsoleSummaryReporter();
    out.println(reporter.formatSuiteFooter(traces.size(), outputDir.toString(), scores, loss));
  }

  /**
   * Prints the console summary followed by the structural delta line — the suite's one-line answer
   * to "did behavior change since the last green run?". The delta line is last so it is the first
   * thing read bottom-up in a terminal; it is omitted entirely when no scenario produced a
   * structural artifact.
   */
  public static void printConsoleSummary(
      List<Map.Entry<String, TraceTree>> traces,
      Path outputDir,
      PrintStream out,
      Function<TraceTree, Double> clarityScorer,
      List<ScenarioDelta> deltas) {
    printConsoleSummary(traces, outputDir, out, clarityScorer, deltas, TraceLoss.none());
  }

  /** The summary, naming what the suite lost when the best-effort path shed anything. */
  public static void printConsoleSummary(
      List<Map.Entry<String, TraceTree>> traces,
      Path outputDir,
      PrintStream out,
      Function<TraceTree, Double> clarityScorer,
      List<ScenarioDelta> deltas,
      TraceLoss loss) {
    printConsoleSummary(traces, outputDir, out, clarityScorer, loss);
    var deltaLine = new ConsoleSummaryReporter().formatDeltaLine(deltas);
    if (!deltaLine.isEmpty()) {
      out.println("  Since last green: " + deltaLine);
    }
  }
}
