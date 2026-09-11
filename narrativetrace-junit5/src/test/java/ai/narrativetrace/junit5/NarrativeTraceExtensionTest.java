/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.clarity.ClarityAnalyzer;
import ai.narrativetrace.clarity.ClarityReportRenderer;
import ai.narrativetrace.clarity.ClarityResult;
import ai.narrativetrace.clarity.DomainVocabulary;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import ai.narrativetrace.diagrams.MermaidSequenceDiagramRenderer;
import ai.narrativetrace.diagrams.PlantUmlSequenceDiagramRenderer;
import ai.narrativetrace.glossary.BoundedContext;
import ai.narrativetrace.glossary.Glossary;
import ai.narrativetrace.glossary.GlossaryJsonWriter;
import ai.narrativetrace.glossary.GlossaryTerm;
import ai.narrativetrace.glossary.SynonymAlias;
import ai.narrativetrace.glossary.TermKind;
import ai.narrativetrace.glossary.TermStatus;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(NarrativeTraceExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NarrativeTraceExtensionTest {

  @Test
  void providesNarrativeContextViaParameterResolution(NarrativeContext context) {
    assertThat(context).isNotNull();
  }

  @Test
  @Order(1)
  void firstTestPopulatesTrace(NarrativeContext context) {
    context.enterMethod(new MethodSignature("A", "first", List.of()));
    context.exitMethodWithReturn("done");
    assertThat(context.captureTrace().roots()).hasSize(1);
  }

  @Test
  @Order(2)
  void secondTestStartsWithEmptyTrace(NarrativeContext context) {
    assertThat(context.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void supportsParameterReturnsFalseForNonContextType() throws Exception {
    var extension = new NarrativeTraceExtension();
    var param =
        getClass().getDeclaredMethod("methodWithStringParam", String.class).getParameters()[0];
    var paramContext = new StubParameterContext(param);

    assertThat(extension.supportsParameter(paramContext, null)).isFalse();
  }

  @SuppressWarnings("unused")
  private void methodWithStringParam(String value) {}

  @Test
  void newContextHonorsOffLevelSoNothingIsCaptured() {
    assertThat(NarrativeTraceExtension.newContext("OFF").isActive()).isFalse();
  }

  @Test
  void newContextDefaultsToDetailForNullOrUnknownLevel() {
    assertThat(NarrativeTraceExtension.newContext(null).isActive()).isTrue();
    assertThat(NarrativeTraceExtension.newContext("bogus").isActive()).isTrue();
  }

  @Test
  void handleAfterTestPrintsFailureReportWhenTestFailsWithTrace() {
    var trace = traceWithOneCall();
    var out = new ByteArrayOutputStream();

    new NarrativeTraceExtension()
        .handleAfterTest(
            "customer places order", true, trace, java.util.Optional.empty(), new PrintStream(out));

    var output = out.toString();
    assertThat(output).contains("Scenario: customer places order");
    assertThat(output).contains("Service.doWork");
  }

  @Test
  void handleAfterTestDoesNothingWhenTraceIsEmpty() {
    var out = new ByteArrayOutputStream();

    new NarrativeTraceExtension()
        .handleAfterTest(
            "test fails", true, emptyTrace(), java.util.Optional.empty(), new PrintStream(out));

    assertThat(out.toString()).isEmpty();
  }

  @Test
  void handleAfterTestDoesNothingWhenTestPasses() {
    var trace = traceWithOneCall();
    var out = new ByteArrayOutputStream();

    new NarrativeTraceExtension()
        .handleAfterTest(
            "test passes", false, trace, java.util.Optional.empty(), new PrintStream(out));

    assertThat(out.toString()).isEmpty();
  }

  @Test
  void writeTraceFileWritesMarkdownAndPrintsPath(@TempDir Path tempDir) throws Exception {
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
        "markdown",
        mermaid(),
        plantuml());

    var file = tempDir.resolve("traces/FooTest/test_something.md");
    assertThat(file).exists();
    var content = java.nio.file.Files.readString(file);
    assertThat(content).contains("Service.doWork");
    assertThat(content).contains("test something");
    var output = out.toString();
    assertThat(output).contains("Service.doWork");
    assertThat(output).doesNotContain("---\ntype: trace");
    assertThat(output).contains("Trace written: " + file.toUri());
  }

  @Test
  void markdownTraceContainsHumanizedScenarioName(@TempDir Path tempDir) throws Exception {
    var trace = traceWithOneCall();
    var out = new ByteArrayOutputStream();

    TraceTestSupport.writeTraceFile(
        "com.example.OrderServiceTest",
        "customerPlacesOrder",
        "customerPlacesOrder()",
        trace,
        false,
        tempDir,
        new PrintStream(out),
        "markdown",
        mermaid(),
        plantuml());

    var file = tempDir.resolve("traces/OrderServiceTest/customer_places_order.md");
    assertThat(file).exists();
    var content = java.nio.file.Files.readString(file);
    assertThat(content).contains("Customer places order");
    assertThat(content).doesNotContain("customerPlacesOrder()");
  }

  @Test
  void writeTraceFileWritesTextWhenFormatIsText(@TempDir Path tempDir) throws Exception {
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
        mermaid(),
        plantuml());

    var file = tempDir.resolve("traces/FooTest/test_something.txt");
    assertThat(file).exists();
    var content = java.nio.file.Files.readString(file);
    assertThat(content).contains("Service.doWork");
    assertThat(content).doesNotContain("**Service.doWork**");
  }

  @Test
  void writeTraceFileWritesMermaidWhenFormatIsMermaid(@TempDir Path tempDir) throws Exception {
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
        "mermaid",
        mermaid(),
        plantuml());

    var file = tempDir.resolve("traces/FooTest/test_something.mmd");
    assertThat(file).exists();
    var content = java.nio.file.Files.readString(file);
    assertThat(content).startsWith("sequenceDiagram");
    assertThat(content).contains("Service");
  }

  @Test
  void writeTraceFileWritesPlantUmlWhenFormatIsPlantUml(@TempDir Path tempDir) throws Exception {
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
        "plantuml",
        mermaid(),
        plantuml());

    var file = tempDir.resolve("traces/FooTest/test_something.puml");
    assertThat(file).exists();
    var content = java.nio.file.Files.readString(file);
    assertThat(content).startsWith("@startuml");
    assertThat(content).contains("Service");
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
        mermaid(),
        plantuml());

    assertThat(tempDir.resolve("traces")).doesNotExist();
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void afterTestExecutionPrintsFailureReportForFailingTest() {
    var oldOut = System.out;
    var captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured));
    try {
      runSuite(FailingTestFixture.class);
    } finally {
      System.setOut(oldOut);
    }

    var output = captured.toString();
    assertThat(output).contains("Scenario: failing test");
    assertThat(output).contains("Service.doWork");
  }

  @Test
  void failingTestPrintsTheDeltaAgainstLastGreenInsteadOfTheFullTrace(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    var oldOut = System.out;
    var captured = new ByteArrayOutputStream();
    try {
      EvolvingFlowFixture.grownAndFailing = false;
      runSuite(EvolvingFlowFixture.class);
      EvolvingFlowFixture.grownAndFailing = true;
      System.setOut(new PrintStream(captured));
      runSuite(EvolvingFlowFixture.class);
    } finally {
      System.setOut(oldOut);
      EvolvingFlowFixture.grownAndFailing = false;
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var output = captured.toString();
    assertThat(output).contains("Changed since last green (+1 call Ledger.record):");
    assertThat(output).contains("+  - Ledger.record()");
    assertThat(output).doesNotContain("Execution trace:");
  }

  @Test
  void outputSystemPropertyEnablesFileWriting(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runSuite(FailingTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var traceFile = tempDir.resolve("traces/FailingTestFixture/failing_test.md");
    assertThat(traceFile).exists();
    try {
      assertThat(java.nio.file.Files.readString(traceFile)).contains("Service.doWork");
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void outputDirSystemPropertyOverridesDefault(@TempDir Path tempDir) {
    var customDir = tempDir.resolve("custom-output");
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", customDir.toString());
    try {
      runSuite(FailingTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    assertThat(customDir.resolve("traces/FailingTestFixture/failing_test.md")).exists();
  }

  @Test
  void producesFullOutputDirectoryStructure(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    // Markdown traces
    assertThat(tempDir.resolve("traces/MultiTestFixture/customer_places_order.md")).exists();
    assertThat(tempDir.resolve("traces/MultiTestFixture/customer_cancels_order.md")).exists();

    // JSON exports
    assertThat(tempDir.resolve("traces/MultiTestFixture/customer_places_order.json")).exists();
    assertThat(tempDir.resolve("traces/MultiTestFixture/customer_cancels_order.json")).exists();

    // Mermaid diagrams
    assertThat(tempDir.resolve("diagrams/MultiTestFixture/customer_places_order.mmd")).exists();
    assertThat(tempDir.resolve("diagrams/MultiTestFixture/customer_cancels_order.mmd")).exists();

    // Suite-level clarity report
    assertThat(tempDir.resolve("clarity-report.md")).exists();
  }

  @Test
  void outputWritesByDefaultWithNoOutputConfiguration(@TempDir Path tempDir) {
    System.clearProperty("narrativetrace.output");
    runSuite(Map.of("narrativetrace.outputDir", tempDir.toString()), MultiTestFixture.class);

    assertThat(tempDir.resolve("traces/MultiTestFixture/customer_places_order.md")).exists();
    assertThat(tempDir.resolve("clarity-report.md")).exists();
  }

  @Test
  void outputWritesNothingWhenExplicitlyDisabled(@TempDir Path tempDir) {
    System.clearProperty("narrativetrace.output");
    runSuite(
        Map.of("narrativetrace.output", "false", "narrativetrace.outputDir", tempDir.toString()),
        MultiTestFixture.class);

    assertThat(tempDir.resolve("traces")).doesNotExist();
    assertThat(tempDir.resolve("clarity-report.md")).doesNotExist();
  }

  @Test
  void printsConsoleSummaryAfterAllTests(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    var oldOut = System.out;
    var captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured));
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.setOut(oldOut);
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var output = captured.toString();
    assertThat(output).contains("NarrativeTrace — Suite complete");
    assertThat(output).contains("2 scenarios recorded");
    assertThat(output).contains("Clarity:");
  }

  @Test
  void suiteSummaryReportsTheStructuralDeltaSinceLastGreen(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    var oldOut = System.out;
    var firstRun = new ByteArrayOutputStream();
    var secondRun = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(firstRun));
      runSuite(MultiTestFixture.class);
      System.setOut(new PrintStream(secondRun));
      runSuite(MultiTestFixture.class);
    } finally {
      System.setOut(oldOut);
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    assertThat(firstRun.toString()).contains("Since last green: 2 scenarios new");
    assertThat(secondRun.toString()).contains("Since last green: 2 scenarios unchanged");
  }

  @Test
  void generatesClarityReportAfterAllTests(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var reportFile = tempDir.resolve("clarity-report.md");
    assertThat(reportFile).exists();
    try {
      var content = java.nio.file.Files.readString(reportFile);
      assertThat(content).contains("customer places order");
      assertThat(content).contains("customer cancels order");
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void afterAllWritesClarityJson(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var jsonFile = tempDir.resolve("clarity-results.json");
    assertThat(jsonFile).exists();
    try {
      var content = java.nio.file.Files.readString(jsonFile);
      assertThat(content).contains("\"version\":\"1.2\"");
      assertThat(content).contains("\"scenarios\":");
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void clarityJsonContainsExpectedStructure(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    try {
      var content = java.nio.file.Files.readString(tempDir.resolve("clarity-results.json"));
      assertThat(content).contains("\"version\":\"1.2\"");
      assertThat(content).contains("\"overallScore\":");
      assertThat(content).contains("\"methodNameScore\":");
      assertThat(content).contains("\"classNameScore\":");
      assertThat(content).contains("\"parameterNameScore\":");
      assertThat(content).contains("\"structuralScore\":");
      assertThat(content).contains("\"cohesionScore\":");
      assertThat(content).contains("\"issues\":");
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void combinedReportContainsScenariosFromMultipleClasses(@TempDir Path tempDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runSuite(MultiTestFixture.class, SecondMultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var jsonContent = java.nio.file.Files.readString(tempDir.resolve("clarity-results.json"));
    assertThat(jsonContent).contains("customer places order");
    assertThat(jsonContent).contains("customer cancels order");
    assertThat(jsonContent).contains("customer checks inventory");

    var reportContent = java.nio.file.Files.readString(tempDir.resolve("clarity-report.md"));
    assertThat(reportContent).contains("customer places order");
    assertThat(reportContent).contains("customer checks inventory");
  }

  @Test
  void canonicalJsonParamWritesEntriesBesideTheTraceFile(@TempDir Path tempDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    System.setProperty("narrativetrace.canonicalJson", "true");
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
      System.clearProperty("narrativetrace.canonicalJson");
    }

    var jsonFiles =
        java.nio.file.Files.walk(tempDir.resolve("traces"))
            .filter(p -> p.toString().endsWith(".canonical.json"))
            .toList();
    assertThat(jsonFiles).isNotEmpty();
    var content = java.nio.file.Files.readString(jsonFiles.get(0));
    assertThat(content).contains("\"nt.schemaVersion\": \"1.2\"");
    assertThat(content).contains("\"nt.eventType\": \"method_enter\"");
    assertThat(content).contains("\"nt.eventType\": \"method_exit\"");
  }

  @Test
  void structuralJsonParamWritesValueFreeEntriesBesideTheTraceFile(@TempDir Path tempDir)
      throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    System.setProperty("narrativetrace.structuralJson", "true");
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
      System.clearProperty("narrativetrace.structuralJson");
    }

    var jsonFiles =
        java.nio.file.Files.walk(tempDir.resolve("traces"))
            .filter(p -> p.toString().endsWith(".structural.json"))
            .toList();
    assertThat(jsonFiles).isNotEmpty();
    var content = java.nio.file.Files.readString(jsonFiles.get(0));
    assertThat(content).contains("\"nt.schemaVersion\": \"1.2\"");
    assertThat(content).contains("\"nt.eventType\": \"method_enter\"");
    assertThat(content).doesNotContain("order-1").doesNotContain("cancelled");
  }

  @Test
  void structuralAndCanonicalJsonCombineToEmitBothArtifacts(@TempDir Path tempDir)
      throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    System.setProperty("narrativetrace.canonicalJson", "true");
    System.setProperty("narrativetrace.structuralJson", "true");
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
      System.clearProperty("narrativetrace.canonicalJson");
      System.clearProperty("narrativetrace.structuralJson");
    }

    var files = java.nio.file.Files.walk(tempDir.resolve("traces")).toList();
    assertThat(files.stream().filter(p -> p.toString().endsWith(".canonical.json"))).isNotEmpty();
    assertThat(files.stream().filter(p -> p.toString().endsWith(".structural.json"))).isNotEmpty();
  }

  @Test
  void structuralJsonIsOffByDefault(@TempDir Path tempDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var jsonFiles =
        java.nio.file.Files.walk(tempDir.resolve("traces"))
            .filter(p -> p.toString().endsWith(".structural.json"))
            .toList();
    assertThat(jsonFiles).isEmpty();
  }

  @Test
  void canonicalJsonIsOffByDefault(@TempDir Path tempDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var jsonFiles =
        java.nio.file.Files.walk(tempDir.resolve("traces"))
            .filter(p -> p.toString().endsWith(".canonical.json"))
            .toList();
    assertThat(jsonFiles).isEmpty();
  }

  @Test
  void globalAccumulatorCloseDoesNothingWhenEmpty(@TempDir Path tempDir) {
    var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();
    accumulator.close();

    assertThat(tempDir.resolve("clarity-report.md")).doesNotExist();
  }

  @Test
  void suiteRunHarvestsTheGlossaryIntoTheConfiguredDirectory(
      @TempDir Path tempDir, @TempDir Path glossaryDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    System.setProperty("narrativetrace.glossary", "true");
    System.setProperty("narrativetrace.glossaryDir", glossaryDir.toString());
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
      System.clearProperty("narrativetrace.glossary");
      System.clearProperty("narrativetrace.glossaryDir");
    }

    assertThat(glossaryDir.resolve("glossary.json")).exists();
    assertThat(glossaryDir.resolve("glossary.md")).exists();
    assertThat(tempDir.resolve("glossary-usage.json")).exists();
  }

  @Test
  void suiteRunLeavesTheGlossaryAloneUnlessExplicitlyEnabled(
      @TempDir Path tempDir, @TempDir Path glossaryDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    System.setProperty("narrativetrace.glossaryDir", glossaryDir.toString());
    try {
      runSuite(MultiTestFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
      System.clearProperty("narrativetrace.glossaryDir");
    }

    assertThat(glossaryDir.resolve("glossary.json")).doesNotExist();
    assertThat(tempDir.resolve("clarity-report.md")).exists();
  }

  @Test
  void approvalModeFailsUnapprovedScenariosUntilPromoted(
      @TempDir Path tempDir, @TempDir Path narrativesDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    System.setProperty("narrativetrace.approval", "true");
    System.setProperty("narrativetrace.approvedDir", narrativesDir.toString());
    try {
      var unapproved = runSuiteWithSummary(MultiTestFixture.class);
      assertThat(unapproved.getTestsFailedCount()).isEqualTo(2);
      assertThat(unapproved.getFailures().get(0).getException())
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("No approved narrative");
      assertThat(receivedFilesUnder(narrativesDir)).hasSize(2);

      ai.narrativetrace.core.output.NarrativeApproval.promoteReceived(narrativesDir);

      var approved = runSuiteWithSummary(MultiTestFixture.class);
      assertThat(approved.getTestsFailedCount()).isZero();
      assertThat(receivedFilesUnder(narrativesDir)).isEmpty();
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
      System.clearProperty("narrativetrace.approval");
      System.clearProperty("narrativetrace.approvedDir");
    }
  }

  private static List<Path> receivedFilesUnder(Path dir) throws java.io.IOException {
    try (var files = Files.walk(dir)) {
      return files.filter(f -> f.getFileName().toString().endsWith(".received.nt")).toList();
    }
  }

  private static org.junit.platform.launcher.listeners.TestExecutionSummary runSuiteWithSummary(
      Class<?>... fixtures) {
    var request =
        org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
            .selectors(
                java.util.Arrays.stream(fixtures)
                    .map(org.junit.platform.engine.discovery.DiscoverySelectors::selectClass)
                    .toArray(org.junit.platform.engine.DiscoverySelector[]::new));
    var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
    org.junit.platform.launcher.core.LauncherFactory.create().execute(request.build(), listener);
    return listener.getSummary();
  }

  private static void runSuite(Class<?>... fixtures) {
    runSuite(Map.of(), fixtures);
  }

  private static void runSuite(Map<String, String> configurationParameters, Class<?>... fixtures) {
    var request =
        org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
            .selectors(
                java.util.Arrays.stream(fixtures)
                    .map(org.junit.platform.engine.discovery.DiscoverySelectors::selectClass)
                    .toArray(org.junit.platform.engine.DiscoverySelector[]::new));
    configurationParameters.forEach(request::configurationParameter);
    org.junit.platform.launcher.core.LauncherFactory.create().execute(request.build());
  }

  @Test
  void globalAccumulatorHarvestsTheGlossaryBesideTheClarityReport(
      @TempDir Path outputDir, @TempDir Path glossaryDir) {
    var sig = new MethodSignature("OverdraftService", "openOverdraftAccount", List.of());
    var trace =
        new DefaultTraceTree(
            List.of(new TraceNode(sig, List.of(), new TraceOutcome.Returned("\"ok\""))));
    var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();
    var step =
        GlossaryHarvestStep.into(
            glossaryDir,
            outputDir.resolve("glossary-usage.json"),
            className -> "com.acme.billing",
            Clock.systemUTC());

    accumulator.contribute(
        Map.of("opens an overdraft account", trace),
        List.of(),
        outputDir,
        step,
        DomainVocabulary.empty());
    accumulator.close();

    assertThat(glossaryDir.resolve("glossary.json")).exists();
    assertThat(glossaryDir.resolve("glossary.md")).exists();
    assertThat(outputDir.resolve("clarity-report.md")).exists();
  }

  @Test
  void globalAccumulatorRoutesVocabularyIssuesIntoTheClarityReport(
      @TempDir Path outputDir, @TempDir Path glossaryDir) throws Exception {
    writeCuratedGlossaryWithSynonym(glossaryDir);
    var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();
    var step = billingGlossaryStep(outputDir, glossaryDir);

    accumulator.contribute(
        Map.of("opens an account with overdraft", billingTrace("openAccountWithOverdraft")),
        List.of(),
        outputDir,
        step,
        DomainVocabulary.empty());
    accumulator.close();

    var report = Files.readString(outputDir.resolve("clarity-report.md"));
    assertThat(report)
        .contains("## Suite Issues")
        .contains("non-canonical-term")
        .contains("openAccountWithOverdraft");
    var json = Files.readString(outputDir.resolve("clarity-results.json"));
    assertThat(json).contains("\"suiteIssues\":[{\"category\":\"non-canonical-term\"");
  }

  @Test
  void clarityReportCarriesNoSuiteIssuesWhenNoGlossaryFileExists(
      @TempDir Path outputDir, @TempDir Path glossaryDir) throws Exception {
    var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();
    var step = billingGlossaryStep(outputDir, glossaryDir);

    accumulator.contribute(
        Map.of("opens an account with overdraft", billingTrace("openAccountWithOverdraft")),
        List.of(),
        outputDir,
        step,
        DomainVocabulary.empty());
    accumulator.close();

    var report = Files.readString(outputDir.resolve("clarity-report.md"));
    assertThat(report).doesNotContain("## Suite Issues");
    var json = Files.readString(outputDir.resolve("clarity-results.json"));
    assertThat(json).contains("\"suiteIssues\":[]");
  }

  private static TraceTree billingTrace(String methodName) {
    var sig = new MethodSignature("AccountService", methodName, List.of());
    return new DefaultTraceTree(
        List.of(new TraceNode(sig, List.of(), new TraceOutcome.Returned("\"ok\""))));
  }

  private static GlossaryHarvestStep billingGlossaryStep(Path outputDir, Path glossaryDir) {
    return GlossaryHarvestStep.into(
        glossaryDir,
        outputDir.resolve("glossary-usage.json"),
        className -> "com.acme.billing",
        Clock.systemUTC());
  }

  private static void writeCuratedGlossaryWithSynonym(Path glossaryDir) throws Exception {
    var curated =
        new Glossary(
            1,
            Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
            List.of(
                new GlossaryTerm(
                    "overdraft account",
                    "billing",
                    TermKind.NOUN_PHRASE,
                    TermStatus.CURATED,
                    null,
                    Map.of(),
                    List.of(new SynonymAlias("account with overdraft", "legacy phrasing")),
                    List.of(),
                    LocalDate.of(2026, 8, 11))));
    Files.writeString(
        glossaryDir.resolve("glossary.json"), new GlossaryJsonWriter().write(curated));
  }

  @Test
  void serviceLoaderDiscoversExtension() {
    var extensions = ServiceLoader.load(Extension.class);
    var found = false;
    for (var ext : extensions) {
      if (ext instanceof NarrativeTraceExtension) {
        found = true;
        break;
      }
    }
    assertThat(found).as("ServiceLoader should discover NarrativeTraceExtension").isTrue();
  }

  @Test
  void handleAfterTestPrintsTemplateWarningsWhenUnresolvedPlaceholders() {
    var sig =
        new MethodSignature(
            "OrderService", "placeOrder", List.of(), "Placing order for {custmerId}", null);
    var node = new TraceNode(sig, List.of(), new TraceOutcome.Returned("\"ok\""));
    var trace = new DefaultTraceTree(List.of(node));
    var out = new ByteArrayOutputStream();

    new NarrativeTraceExtension()
        .handleAfterTest(
            "places order", false, trace, java.util.Optional.empty(), new PrintStream(out));

    var output = out.toString();
    assertThat(output).contains("Unresolved template placeholder");
    assertThat(output).contains("OrderService.placeOrder");
    assertThat(output).contains("{custmerId}");
  }

  @Test
  void handleAfterTestNoWarningsForCleanTrace() {
    var sig =
        new MethodSignature(
            "OrderService", "placeOrder", List.of(), "Placing order for C-123", null);
    var node = new TraceNode(sig, List.of(), new TraceOutcome.Returned("\"ok\""));
    var trace = new DefaultTraceTree(List.of(node));
    var out = new ByteArrayOutputStream();

    new NarrativeTraceExtension()
        .handleAfterTest(
            "places order", false, trace, java.util.Optional.empty(), new PrintStream(out));

    assertThat(out.toString()).isEmpty();
  }

  @Test
  void writeTraceFileAlsoGeneratesJsonExport(@TempDir Path tempDir) throws Exception {
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
        "markdown",
        mermaid(),
        plantuml());

    var jsonFile = tempDir.resolve("traces/FooTest/test_something.json");
    assertThat(jsonFile).exists();
    var content = java.nio.file.Files.readString(jsonFile);
    assertThat(content).contains("\"scenario\"");
    assertThat(content).contains("\"test something\"");
    assertThat(content).contains("\"Service\"");
    assertThat(content).contains("\"doWork\"");
  }

  @Test
  void writeTraceFileAlsoGeneratesMermaidDiagram(@TempDir Path tempDir) throws Exception {
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
        "markdown",
        mermaid(),
        plantuml());

    var diagram = tempDir.resolve("diagrams/FooTest/test_something.mmd");
    assertThat(diagram).exists();
    var content = java.nio.file.Files.readString(diagram);
    assertThat(content).startsWith("sequenceDiagram");
    assertThat(content).contains("Service");
  }

  @Test
  void writesClarityReportFromAccumulatedTraces(@TempDir Path tempDir) throws Exception {
    var traces = new LinkedHashMap<String, TraceTree>();
    traces.put("Customer places order", traceWithOneCall());
    traces.put("Customer cancels order", traceWithOneCall());

    TraceTestSupport.writeClarityReport(traces, tempDir, this::renderClarityReport);

    var reportFile = tempDir.resolve("clarity-report.md");
    assertThat(reportFile).exists();
    var content = java.nio.file.Files.readString(reportFile);
    assertThat(content).contains("Customer places order");
    assertThat(content).contains("Customer cancels order");
  }

  @Test
  void writeTraceFileIoErrorIsCaughtGracefully(@TempDir Path tempDir) throws Exception {
    // Create a file where a directory is expected, causing IOException on write
    var blockingFile = tempDir.resolve("traces");
    java.nio.file.Files.writeString(blockingFile, "blocker");

    var oldErr = System.err;
    var capturedErr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(capturedErr));
    try {
      System.setProperty("narrativetrace.output", "true");
      System.setProperty("narrativetrace.outputDir", tempDir.toString());
      try {
        runSuite(IoErrorFixture.class);
      } finally {
        System.clearProperty("narrativetrace.output");
        System.clearProperty("narrativetrace.outputDir");
      }
    } finally {
      System.setErr(oldErr);
    }

    assertThat(capturedErr.toString()).contains("Failed to write trace file:");
  }

  @Test
  void afterAllCatchesIoErrorOnClarityReportWrite(@TempDir Path tempDir) throws Exception {
    // Create a file where the clarity-report.md directory should go
    java.nio.file.Files.writeString(tempDir.resolve("clarity-report.md"), "blocker");
    // Make it a directory to block the file write
    java.nio.file.Files.delete(tempDir.resolve("clarity-report.md"));
    java.nio.file.Files.createDirectories(tempDir.resolve("clarity-report.md"));

    var oldErr = System.err;
    var capturedErr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(capturedErr));
    try {
      System.setProperty("narrativetrace.output", "true");
      System.setProperty("narrativetrace.outputDir", tempDir.toString());
      try {
        runSuite(MultiTraceIoErrorFixture.class);
      } finally {
        System.clearProperty("narrativetrace.output");
        System.clearProperty("narrativetrace.outputDir");
      }
    } finally {
      System.setErr(oldErr);
    }

    assertThat(capturedErr.toString()).contains("Failed to write clarity report:");
  }

  @Test
  void afterAllSkipsWhenNoTracesAccumulated(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runSuite(EmptyTraceFixture.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    assertThat(tempDir.resolve("clarity-report.md")).doesNotExist();
  }

  @Test
  void writeTraceFileHandlesUnqualifiedClassName(@TempDir Path tempDir) throws Exception {
    var trace = traceWithOneCall();
    var out = new ByteArrayOutputStream();

    TraceTestSupport.writeTraceFile(
        "FooTest",
        "testSomething",
        "test something",
        trace,
        false,
        tempDir,
        new PrintStream(out),
        "markdown",
        mermaid(),
        plantuml());

    var diagramFile = tempDir.resolve("diagrams/FooTest/test_something.mmd");
    assertThat(diagramFile).exists();
  }

  @Test
  void writeClarityReportSkipsWhenTracesAreEmpty(@TempDir Path tempDir) throws Exception {
    TraceTestSupport.writeClarityReport(new LinkedHashMap<>(), tempDir, t -> "report");

    assertThat(tempDir.resolve("clarity-report.md")).doesNotExist();
  }

  @Test
  void readsConfigViaConfigurationParameterInsteadOfSystemProperty(@TempDir Path tempDir) {
    // Ensure no system properties are set
    System.clearProperty("narrativetrace.output");
    System.clearProperty("narrativetrace.outputDir");
    System.clearProperty("narrativetrace.format");

    runSuite(
        Map.of(
            "narrativetrace.output", "true",
            "narrativetrace.outputDir", tempDir.toString(),
            "narrativetrace.format", "markdown"),
        FailingTestFixture.class);

    var traceFile = tempDir.resolve("traces/FailingTestFixture/failing_test.md");
    assertThat(traceFile).exists();
  }

  @Test
  void suiteLevelArtifactsShouldRetainDuplicateScenarioNamesFromDifferentClasses(
      @TempDir Path tempDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runSuite(DuplicateScenarioNameFixtureOne.class, DuplicateScenarioNameFixtureTwo.class);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var json = java.nio.file.Files.readString(tempDir.resolve("clarity-results.json"));
    assertThat(countOccurrences(json, "\"customer places order\"")).isEqualTo(2);
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int from = 0;
    while (true) {
      var idx = haystack.indexOf(needle, from);
      if (idx < 0) {
        return count;
      }
      count++;
      from = idx + needle.length();
    }
  }

  private String renderClarityReport(Map<String, TraceTree> traces) {
    var analyzer = new ClarityAnalyzer();
    var results = new LinkedHashMap<String, ClarityResult>();
    for (var entry : traces.entrySet()) {
      results.put(entry.getKey(), analyzer.analyze(entry.getValue()));
    }
    return new ClarityReportRenderer().renderSuiteReport(results);
  }

  private static NarrativeRenderer mermaid() {
    return new MermaidSequenceDiagramRenderer()::render;
  }

  private static NarrativeRenderer plantuml() {
    return new PlantUmlSequenceDiagramRenderer()::render;
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
  void suiteClarityReportScoresInTheProjectsCommittedVocabulary(
      @TempDir Path outputDir, @TempDir Path glossaryDir) throws Exception {
    var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();
    var trace =
        new DefaultTraceTree(
            List.of(
                new TraceNode(
                    new MethodSignature("TrancheService", "foldTranche", List.of()),
                    List.of(),
                    new TraceOutcome.Returned("\"ok\""),
                    1_000_000L)));

    accumulator.contribute(
        Map.of("folds a tranche", trace),
        List.of(),
        outputDir,
        GlossaryHarvestStep.disabled(),
        DomainVocabulary.of(Set.of("fold"), Set.of("tranche")));
    accumulator.close();

    assertThat(Files.readString(outputDir.resolve("clarity-report.md")))
        .contains("Domain verb 'fold' + domain noun 'tranche'");
  }

  @Test
  void suiteClarityReportFallsBackToTheBuiltInDictionariesWithoutAGlossary(@TempDir Path outputDir)
      throws Exception {
    var accumulator = new NarrativeTraceExtension.GlobalTraceAccumulator();
    var trace =
        new DefaultTraceTree(
            List.of(
                new TraceNode(
                    new MethodSignature("TrancheService", "foldTranche", List.of()),
                    List.of(),
                    new TraceOutcome.Returned("\"ok\""),
                    1_000_000L)));

    accumulator.contribute(
        Map.of("folds a tranche", trace),
        List.of(),
        outputDir,
        GlossaryHarvestStep.disabled(),
        DomainVocabulary.empty());
    accumulator.close();

    assertThat(Files.readString(outputDir.resolve("clarity-report.md")))
        .doesNotContain("Domain verb 'fold'");
  }

  @Test
  void projectVocabularyReadsTheCommittedGlossary(@TempDir Path glossaryDir) throws Exception {
    Files.writeString(
        glossaryDir.resolve("glossary.json"),
        """
        {
          "schemaVersion": 1,
          "contexts": {"trading": {"packages": ["com.acme.trading"]}},
          "terms": [
            {
              "term": "fold tranche",
              "context": "trading",
              "kind": "verb-phrase",
              "status": "curated",
              "firstSeen": "2020-01-01"
            }
          ]
        }
        """);

    var vocabulary = NarrativeTraceExtension.projectVocabulary(glossaryDir);

    assertThat(vocabulary.isDomainVerb("fold")).isTrue();
    assertThat(vocabulary.isDomainNoun("tranche")).isTrue();
  }

  @Test
  void projectVocabularyIsEmptyWithoutACommittedGlossary(@TempDir Path glossaryDir) {
    assertThat(NarrativeTraceExtension.projectVocabulary(glossaryDir))
        .isEqualTo(DomainVocabulary.empty());
  }

  @Test
  void aMalformedCommittedGlossaryDegradesToTheBuiltInDictionaries(@TempDir Path glossaryDir)
      throws Exception {
    Files.writeString(glossaryDir.resolve("glossary.json"), "{ not json");
    var capturedErr = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(capturedErr, true, java.nio.charset.StandardCharsets.UTF_8));
    try {
      assertThat(NarrativeTraceExtension.projectVocabulary(glossaryDir))
          .isEqualTo(DomainVocabulary.empty());
      assertThat(capturedErr.toString(java.nio.charset.StandardCharsets.UTF_8))
          .contains("could not be read")
          .contains("built-in dictionaries only");
    } finally {
      System.setErr(originalErr);
    }
  }
}
