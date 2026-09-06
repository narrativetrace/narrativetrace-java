/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceTestSupportTest {

  @Test
  void extensionForMarkdownFormat() {
    assertThat(TraceTestSupport.extensionForFormat("markdown")).isEqualTo(".md");
  }

  @Test
  void extensionForTextFormat() {
    assertThat(TraceTestSupport.extensionForFormat("text")).isEqualTo(".txt");
  }

  @Test
  void extensionForMermaidFormat() {
    assertThat(TraceTestSupport.extensionForFormat("mermaid")).isEqualTo(".mmd");
  }

  @Test
  void extensionForPlantUmlFormat() {
    assertThat(TraceTestSupport.extensionForFormat("plantuml")).isEqualTo(".puml");
  }

  @Test
  void buildFailureReportFormatsScenarioAndTrace() {
    var report =
        TraceTestSupport.buildFailureReport(
            "Scenario: Customer places order", "Service.doWork() → ok");

    assertThat(report)
        .isEqualTo(
            "\n\nScenario: Customer places order\n\nExecution trace:\nService.doWork() → ok");
  }

  @Test
  void failureReportWithChangedDeltaShowsTheDiffInsteadOfTheFullTrace() {
    var delta =
        new ScenarioDelta(
            "Customer places order",
            ScenarioDelta.Kind.CHANGED,
            "+1 call Ledger.record",
            " scenario: Customer places order\n+  - Ledger.record()\n");

    var report =
        TraceTestSupport.buildFailureReport(
            "Scenario: Customer places order", "Service.doWork() → ok", delta);

    assertThat(report)
        .isEqualTo(
            "\n\nScenario: Customer places order\n\n"
                + "Changed since last green (+1 call Ledger.record):\n"
                + " scenario: Customer places order\n+  - Ledger.record()\n");
    assertThat(report).doesNotContain("Execution trace:");
  }

  @Test
  void failureReportWithUnchangedDeltaKeepsTheTraceAndSaysStructureHeld() {
    var delta = new ScenarioDelta("Customer places order", ScenarioDelta.Kind.UNCHANGED, "", "");

    var report =
        TraceTestSupport.buildFailureReport(
            "Scenario: Customer places order", "Service.doWork() → ok", delta);

    assertThat(report)
        .isEqualTo(
            "\n\nScenario: Customer places order\n\n"
                + "Structure unchanged since last green — the flow held; check values and"
                + " assertions.\n\n"
                + "Execution trace:\nService.doWork() → ok");
  }

  @Test
  void renderForTextFormat() {
    var trace = traceWithOneCall();

    var result =
        TraceTestSupport.renderForFormat("text", trace, "customerPlacesOrder()", false, null, null);

    assertThat(result).contains("Scenario: Customer places order");
    assertThat(result).contains("Service.doWork");
  }

  @Test
  void renderForMarkdownFormat() {
    var trace = traceWithOneCall();

    var result =
        TraceTestSupport.renderForFormat(
            "markdown", trace, "customerPlacesOrder()", false, null, null);

    assertThat(result).contains("Service.doWork");
    assertThat(result).contains("Customer places order");
  }

  @Test
  void renderForMermaidFormat() {
    var trace = traceWithOneCall();
    var mermaidRenderer = stubRenderer("sequenceDiagram\n  Test->>Service: doWork");

    var result =
        TraceTestSupport.renderForFormat("mermaid", trace, "test", false, mermaidRenderer, null);

    assertThat(result).startsWith("sequenceDiagram");
    assertThat(result).contains("Service");
  }

  @Test
  void renderForPlantUmlFormat() {
    var trace = traceWithOneCall();
    var plantumlRenderer = stubRenderer("@startuml\nTest -> Service: doWork\n@enduml");

    var result =
        TraceTestSupport.renderForFormat("plantuml", trace, "test", false, null, plantumlRenderer);

    assertThat(result).startsWith("@startuml");
    assertThat(result).contains("Service");
  }

  @Test
  void writeStructuralTraceFileWritesValueFreeEntries(@TempDir Path tempDir) throws Exception {
    var node =
        new TraceNode(
            new MethodSignature(
                "Service",
                "doWork",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"secret-77\""));
    var trace = new DefaultTraceTree(List.of(node));

    TraceTestSupport.writeStructuralTraceFile(
        "com.example.OrderTest", "placesOrder", trace, tempDir);

    var files = Files.walk(tempDir).filter(p -> p.toString().endsWith(".structural.json")).toList();
    assertThat(files).hasSize(1);
    var content = Files.readString(files.get(0));
    assertThat(content).doesNotContain("C-123").doesNotContain("secret-77");
    assertThat(content).contains("[ELIDED]");
    assertThat(content).contains("\"nt.eventType\": \"method_enter\"");
    assertThat(content).contains("\"nt.eventType\": \"method_exit\"");
  }

  @Test
  void writeStructuralTraceFileSkipsEmptyTraces(@TempDir Path tempDir) throws Exception {
    TraceTestSupport.writeStructuralTraceFile(
        "com.example.OrderTest", "placesOrder", emptyTrace(), tempDir);

    var files = Files.walk(tempDir).filter(p -> p.toString().endsWith(".structural.json")).toList();
    assertThat(files).isEmpty();
  }

  private static TraceTree traceWithOneCall() {
    var node =
        new TraceNode(
            new MethodSignature("Service", "doWork", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    return new DefaultTraceTree(List.of(node));
  }

  private static TraceTree emptyTrace() {
    return new DefaultTraceTree(List.of());
  }

  @Test
  void writeTraceFileEmitsStructuralNtArtifactWithoutValues(@TempDir Path tempDir)
      throws Exception {
    var trace = traceWithOneCall();
    var out = new ByteArrayOutputStream();
    var mermaid = stubRenderer("sequenceDiagram");

    TraceTestSupport.writeTraceFile(
        "com.example.FooTest",
        "testSomething",
        "test something",
        trace,
        false,
        tempDir,
        new PrintStream(out),
        "markdown",
        mermaid,
        null);

    var ntFile = tempDir.resolve("structural/FooTest/test_something.nt");
    assertThat(ntFile).exists();
    var content = Files.readString(ntFile);
    assertThat(content)
        .isEqualTo(
            """
            scenario: test something

            - Service.doWork() → value
            """);
    assertThat(content).doesNotContain("\"ok\"");
  }

  @Test
  void firstMarkdownWriteReportsTheScenarioAsNew(@TempDir Path tempDir) throws Exception {
    var trace = traceWithOneCall();
    var out = new ByteArrayOutputStream();

    var delta =
        TraceTestSupport.writeTraceFile(
            "com.example.FooTest",
            "testSomething",
            "test something",
            trace,
            false,
            tempDir,
            new PrintStream(out),
            "markdown",
            stubRenderer("sequenceDiagram"),
            null);

    assertThat(delta).isPresent();
    assertThat(delta.get().kind()).isEqualTo(ScenarioDelta.Kind.NEW);
    assertThat(delta.get().scenario()).isEqualTo("test something");
  }

  @Test
  void greenRunWithChangedStructureReportsChangeAndAdvancesTheBaseline(@TempDir Path tempDir)
      throws Exception {
    var out = new PrintStream(new ByteArrayOutputStream());
    writeMarkdown(traceWithOneCall(), false, tempDir, out);

    var grown =
        new DefaultTraceTree(
            List.of(
                new TraceNode(
                    new MethodSignature("Service", "doWork", List.of()),
                    List.of(
                        new TraceNode(
                            new MethodSignature("Ledger", "record", List.of()),
                            List.of(),
                            new TraceOutcome.Returned(null))),
                    new TraceOutcome.Returned("\"ok\""))));
    var delta = writeMarkdown(grown, false, tempDir, out);

    assertThat(delta).isPresent();
    assertThat(delta.get().kind()).isEqualTo(ScenarioDelta.Kind.CHANGED);
    assertThat(delta.get().summary()).isEqualTo("+1 call Ledger.record");
    assertThat(Files.readString(tempDir.resolve("structural/FooTest/test_something.nt")))
        .contains("- Ledger.record()");
  }

  @Test
  void failedRunComparesAgainstLastGreenButNeverOverwritesIt(@TempDir Path tempDir)
      throws Exception {
    var out = new PrintStream(new ByteArrayOutputStream());
    writeMarkdown(traceWithOneCall(), false, tempDir, out);

    var grown =
        new DefaultTraceTree(
            List.of(
                new TraceNode(
                    new MethodSignature("Service", "doWork", List.of()),
                    List.of(
                        new TraceNode(
                            new MethodSignature("Ledger", "record", List.of()),
                            List.of(),
                            new TraceOutcome.Returned(null))),
                    new TraceOutcome.Returned("\"ok\""))));
    var delta = writeMarkdown(grown, true, tempDir, out);

    assertThat(delta).isPresent();
    assertThat(delta.get().kind()).isEqualTo(ScenarioDelta.Kind.CHANGED);
    assertThat(delta.get().summary()).isEqualTo("+1 call Ledger.record");
    assertThat(delta.get().diff()).contains("+  - Ledger.record()");
    assertThat(Files.readString(tempDir.resolve("structural/FooTest/test_something.nt")))
        .doesNotContain("Ledger.record");
  }

  @Test
  void failedFirstRunEstablishesNoBaseline(@TempDir Path tempDir) throws Exception {
    var out = new PrintStream(new ByteArrayOutputStream());

    var delta = writeMarkdown(traceWithOneCall(), true, tempDir, out);

    assertThat(delta).isPresent();
    assertThat(delta.get().kind()).isEqualTo(ScenarioDelta.Kind.NEW);
    assertThat(tempDir.resolve("structural/FooTest/test_something.nt")).doesNotExist();
  }

  private static java.util.Optional<ScenarioDelta> writeMarkdown(
      TraceTree trace, boolean failed, Path tempDir, PrintStream out) throws Exception {
    return TraceTestSupport.writeTraceFile(
        "com.example.FooTest",
        "testSomething",
        "test something",
        trace,
        failed,
        tempDir,
        out,
        "markdown",
        stubRenderer("sequenceDiagram"),
        null);
  }

  @Test
  void writeTraceFileWritesMarkdownAndPrintsPath(@TempDir Path tempDir) throws Exception {
    var trace = traceWithOneCall();
    var out = new ByteArrayOutputStream();
    var mermaid = stubRenderer("sequenceDiagram\n  Test->>Service: doWork");

    TraceTestSupport.writeTraceFile(
        "com.example.FooTest",
        "testSomething",
        "test something",
        trace,
        false,
        tempDir,
        new PrintStream(out),
        "markdown",
        mermaid,
        null);

    var file = tempDir.resolve("traces/FooTest/test_something.md");
    assertThat(file).exists();
    var content = Files.readString(file);
    assertThat(content).contains("Service.doWork");
    var output = out.toString();
    assertThat(output).contains("Service.doWork");
    assertThat(output).contains("Trace written: " + file.toUri());
    assertThat(output).contains("Trace written: file://");
  }

  @Test
  void writeTraceFileGeneratesJsonAndDiagramForMarkdown(@TempDir Path tempDir) throws Exception {
    var trace = traceWithOneCall();
    var out = new ByteArrayOutputStream();
    var mermaid = stubRenderer("sequenceDiagram\n  Test->>Service: doWork");

    TraceTestSupport.writeTraceFile(
        "com.example.FooTest",
        "testSomething",
        "test something",
        trace,
        false,
        tempDir,
        new PrintStream(out),
        "markdown",
        mermaid,
        null);

    assertThat(tempDir.resolve("traces/FooTest/test_something.json")).exists();
    assertThat(tempDir.resolve("diagrams/FooTest/test_something.mmd")).exists();
  }

  @Test
  void writeTraceFileSkipsWhenTraceIsEmpty(@TempDir Path tempDir) throws Exception {
    var out = new ByteArrayOutputStream();

    TraceTestSupport.writeTraceFile(
        "com.example.FooTest",
        "testSomething",
        "test something",
        emptyTrace(),
        false,
        tempDir,
        new PrintStream(out),
        "markdown",
        stubRenderer(""),
        null);

    assertThat(tempDir.resolve("traces")).doesNotExist();
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void writeTraceFileWritesTextFormat(@TempDir Path tempDir) throws Exception {
    var trace = traceWithOneCall();
    var out = new ByteArrayOutputStream();

    TraceTestSupport.writeTraceFile(
        "com.example.FooTest",
        "testSomething",
        "test something",
        trace,
        false,
        tempDir,
        new PrintStream(out),
        "text",
        stubRenderer(""),
        null);

    var file = tempDir.resolve("traces/FooTest/test_something.txt");
    assertThat(file).exists();
    assertThat(Files.readString(file)).contains("Service.doWork");
  }

  @Test
  void writeClarityReportWritesFile(@TempDir Path tempDir) throws Exception {
    var traces = new LinkedHashMap<String, TraceTree>();
    traces.put("Customer places order", traceWithOneCall());

    TraceTestSupport.writeClarityReport(traces, tempDir, t -> "# Clarity Report\nscored");

    var reportFile = tempDir.resolve("clarity-report.md");
    assertThat(reportFile).exists();
    assertThat(Files.readString(reportFile)).contains("Clarity Report");
  }

  @Test
  void writeClarityReportSkipsWhenEmpty(@TempDir Path tempDir) throws Exception {
    TraceTestSupport.writeClarityReport(new LinkedHashMap<>(), tempDir, t -> "report");

    assertThat(tempDir.resolve("clarity-report.md")).doesNotExist();
  }

  @Test
  void printConsoleSummaryOutputsSuiteFooter() {
    var traces = new LinkedHashMap<String, TraceTree>();
    traces.put("Customer places order", traceWithOneCall());
    traces.put("Customer cancels order", traceWithOneCall());
    var out = new ByteArrayOutputStream();

    TraceTestSupport.printConsoleSummary(
        traces, Path.of("build/narrativetrace"), new PrintStream(out), tree -> 0.85);

    var output = out.toString();
    assertThat(output).contains("NarrativeTrace — Suite complete");
    assertThat(output).contains("2 scenarios recorded");
    assertThat(output).contains("Clarity:");
  }

  @Test
  void existingWriteClarityReportDoesNotWriteJson(@TempDir Path tempDir) throws Exception {
    var traces = new LinkedHashMap<String, TraceTree>();
    traces.put("Scenario", traceWithOneCall());

    TraceTestSupport.writeClarityReport(traces, tempDir, t -> "# Report");

    assertThat(tempDir.resolve("clarity-report.md")).exists();
    assertThat(tempDir.resolve("clarity-results.json")).doesNotExist();
  }

  @Test
  void writeClarityReportAlsoWritesJson(@TempDir Path tempDir) throws Exception {
    var traces = new LinkedHashMap<String, TraceTree>();
    traces.put("Customer places order", traceWithOneCall());

    TraceTestSupport.writeClarityReport(
        traces, tempDir, t -> "# Report", t -> "{\"version\":\"1.0\"}");

    assertThat(tempDir.resolve("clarity-report.md")).exists();
    assertThat(tempDir.resolve("clarity-results.json")).exists();
    assertThat(Files.readString(tempDir.resolve("clarity-results.json")))
        .isEqualTo("{\"version\":\"1.0\"}");
  }

  @Test
  void listBasedWriteClarityReportWritesBothFiles(@TempDir Path tempDir) throws Exception {
    List<Map.Entry<String, TraceTree>> traces =
        List.of(new AbstractMap.SimpleEntry<>("Scenario A", traceWithOneCall()));

    TraceTestSupport.writeClarityReport(
        traces, tempDir, t -> "# Report", t -> "{\"version\":\"1.0\"}");

    assertThat(tempDir.resolve("clarity-report.md")).exists();
    assertThat(tempDir.resolve("clarity-results.json")).exists();
  }

  @Test
  void listBasedWriteClarityReportSkipsWhenEmpty(@TempDir Path tempDir) throws Exception {
    TraceTestSupport.writeClarityReport(List.of(), tempDir, t -> "# Report", t -> "{}");

    assertThat(tempDir.resolve("clarity-report.md")).doesNotExist();
  }

  @Test
  void printConsoleSummaryEndsWithTheDeltaLine() {
    List<Map.Entry<String, TraceTree>> traces =
        List.of(
            new AbstractMap.SimpleEntry<>("Order ships", traceWithOneCall()),
            new AbstractMap.SimpleEntry<>("Weekend trip settles", traceWithOneCall()));
    var deltas =
        List.of(
            new ScenarioDelta("Order ships", ScenarioDelta.Kind.UNCHANGED, "", ""),
            new ScenarioDelta(
                "Weekend trip settles", ScenarioDelta.Kind.CHANGED, "+1 call Ledger.record", "d"));
    var out = new ByteArrayOutputStream();

    TraceTestSupport.printConsoleSummary(
        traces, Path.of("build/narrativetrace"), new PrintStream(out), tree -> 0.85, deltas);

    assertThat(out.toString())
        .contains("NarrativeTrace — Suite complete")
        .endsWith(
            "  Since last green: 1 scenario unchanged · 1 changed:"
                + " \"Weekend trip settles\" (+1 call Ledger.record)\n");
  }

  @Test
  void printConsoleSummaryOmitsTheDeltaLineWhenNothingWasCompared() {
    List<Map.Entry<String, TraceTree>> traces =
        List.of(new AbstractMap.SimpleEntry<>("Order ships", traceWithOneCall()));
    var out = new ByteArrayOutputStream();

    TraceTestSupport.printConsoleSummary(
        traces, Path.of("build/narrativetrace"), new PrintStream(out), tree -> 0.85, List.of());

    assertThat(out.toString()).doesNotContain("Since last green");
  }

  @Test
  void listBasedPrintConsoleSummaryOutputsSuiteFooter() {
    List<Map.Entry<String, TraceTree>> traces =
        List.of(
            new AbstractMap.SimpleEntry<>("A", traceWithOneCall()),
            new AbstractMap.SimpleEntry<>("B", traceWithOneCall()));
    var out = new ByteArrayOutputStream();

    TraceTestSupport.printConsoleSummary(
        traces, Path.of("build/narrativetrace"), new PrintStream(out), tree -> 0.85);

    assertThat(out.toString()).contains("2 scenarios recorded");
  }

  /**
   * Regression: the diagram and structural trees resolved the class name verbatim while the trace
   * tree sanitized it. A name whose last dot is followed by a separator — {@code ../../../tmp/evil}
   * — leaves an <em>absolute</em> simple name, and {@code Path.resolve} takes an absolute argument
   * as the whole answer, so those two artifacts landed outside the directory the caller gave. Found
   * by the security suite's hostile-name corpus.
   */
  @Test
  void aClassNameThatResolvesToAnAbsolutePathCannotPlaceArtifactsOutsideTheOutputDirectory(
      @TempDir Path tempDir) throws Exception {
    var out = new PrintStream(new ByteArrayOutputStream());

    TraceTestSupport.writeTraceFile(
        "../../../tmp/nt-escape",
        "testSomething",
        "test something",
        traceWithOneCall(),
        false,
        tempDir,
        out,
        "markdown",
        stubRenderer("sequenceDiagram"),
        null);

    assertThat(Path.of("/tmp/nt-escape")).doesNotExist();
    try (var files = Files.walk(tempDir)) {
      assertThat(files.filter(Files::isRegularFile).toList())
          .isNotEmpty()
          .allSatisfy(file -> assertThat(file).startsWith(tempDir));
    }
  }

  private static ai.narrativetrace.api.render.NarrativeRenderer stubRenderer(String output) {
    return tree -> output;
  }
}
