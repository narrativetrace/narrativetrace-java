/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

class NarrativeTraceClassRuleTest {

  @BeforeEach
  void resetGlobal() {
    NarrativeTraceClassRule.resetGlobalAccumulator();
  }

  @Test
  void testRuleReturnsLinkedRule() {
    var classRule = new NarrativeTraceClassRule();
    var rule = classRule.testRule();

    assertThat(rule).isNotNull();
    assertThat(rule).isInstanceOf(NarrativeTraceRule.class);
  }

  @Test
  void linkedRuleAccumulatesTracesInClassRule() {
    var classRule = new NarrativeTraceClassRule();
    var rule = classRule.testRule();

    var desc = Description.createTestDescription(getClass(), "customerPlacesOrder");
    rule.starting(desc);
    rule.context().enterMethod(new MethodSignature("Service", "doWork", List.of()));
    rule.context().exitMethodWithReturn("ok");
    rule.finished(desc);

    assertThat(classRule.accumulatedTraces()).hasSize(1);
    assertThat(classRule.accumulatedTraces()).containsKey("Customer places order");
  }

  @Test
  void multipleTestsAccumulate() {
    var classRule = new NarrativeTraceClassRule();
    var rule = classRule.testRule();

    var desc1 = Description.createTestDescription(getClass(), "customerPlacesOrder");
    rule.starting(desc1);
    rule.context().enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    rule.context().exitMethodWithReturn("order-1");
    rule.finished(desc1);

    var desc2 = Description.createTestDescription(getClass(), "customerCancelsOrder");
    rule.starting(desc2);
    rule.context().enterMethod(new MethodSignature("OrderService", "cancelOrder", List.of()));
    rule.context().exitMethodWithReturn("cancelled");
    rule.finished(desc2);

    assertThat(classRule.accumulatedTraces()).hasSize(2);
    assertThat(classRule.accumulatedTraces())
        .containsKeys("Customer places order", "Customer cancels order");
  }

  @Test
  void applyWritesClarityReport(@TempDir Path tempDir) throws Throwable {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      var classRule = new NarrativeTraceClassRule();
      classRule.setOut(new PrintStream(new ByteArrayOutputStream()));
      var rule = configureTestRule(classRule);

      evaluateTwoScenarios(classRule, rule);

      var reportContent = Files.readString(tempDir.resolve("clarity-report.md"));
      assertThat(reportContent).contains("Customer places order");
      assertThat(reportContent).contains("Customer cancels order");
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  @Test
  void applyWritesConsoleSummary(@TempDir Path tempDir) throws Throwable {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      var classRule = new NarrativeTraceClassRule();
      var captured = new ByteArrayOutputStream();
      classRule.setOut(new PrintStream(captured));
      var rule = configureTestRule(classRule);

      evaluateTwoScenarios(classRule, rule);

      assertThat(captured.toString()).contains("NarrativeTrace — Suite complete");
      assertThat(captured.toString()).contains("2 scenarios recorded");
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  @Test
  void consoleSummaryReportsTheStructuralDeltaSinceLastGreen(@TempDir Path tempDir)
      throws Throwable {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      var firstRun = new ByteArrayOutputStream();
      var classRule = new NarrativeTraceClassRule();
      classRule.setOut(new PrintStream(firstRun));
      evaluateTwoScenarios(classRule, configureTestRule(classRule));

      NarrativeTraceClassRule.resetGlobalAccumulator();
      var secondRun = new ByteArrayOutputStream();
      var secondClassRule = new NarrativeTraceClassRule();
      secondClassRule.setOut(new PrintStream(secondRun));
      evaluateTwoScenarios(secondClassRule, configureTestRule(secondClassRule));

      assertThat(firstRun.toString()).contains("Since last green: 2 scenarios new");
      assertThat(secondRun.toString()).contains("Since last green: 2 scenarios unchanged");
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  @Test
  void applyWritesClarityJson(@TempDir Path tempDir) throws Throwable {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      var classRule = new NarrativeTraceClassRule();
      classRule.setOut(new PrintStream(new ByteArrayOutputStream()));

      classRule.accumulate("Customer places order", traceWithOneCall());
      var statement =
          classRule.apply(
              new Statement() {
                @Override
                public void evaluate() {}
              },
              Description.createSuiteDescription(getClass()));
      statement.evaluate();

      var jsonFile = tempDir.resolve("clarity-results.json");
      assertThat(jsonFile).exists();
      var content = Files.readString(jsonFile);
      assertThat(content).contains("\"version\":\"1.2\"");
      assertThat(content).contains("\"scenarios\":");
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  @Test
  void applySkipsReportWhenOutputDisabled() throws Throwable {
    System.clearProperty("narrativetrace.output");
    var classRule = new NarrativeTraceClassRule();
    var captured = new ByteArrayOutputStream();
    classRule.setOut(new PrintStream(captured));

    classRule.accumulate("test", traceWithOneCall());
    var statement =
        classRule.apply(
            new Statement() {
              @Override
              public void evaluate() {}
            },
            Description.createSuiteDescription(getClass()));
    statement.evaluate();

    assertThat(captured.toString()).isEmpty();
  }

  @Test
  void applyCatchesIoErrorOnClarityReportWrite(@TempDir Path tempDir) throws Throwable {
    Files.createDirectories(tempDir.resolve("clarity-report.md"));
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      var classRule = new NarrativeTraceClassRule();
      var errStream = new ByteArrayOutputStream();
      classRule.setOut(new PrintStream(new ByteArrayOutputStream()));
      classRule.setErr(new PrintStream(errStream));

      classRule.accumulate("test", traceWithOneCall());
      var statement =
          classRule.apply(
              new Statement() {
                @Override
                public void evaluate() {}
              },
              Description.createSuiteDescription(getClass()));
      statement.evaluate();

      assertThat(errStream.toString()).contains("Failed to write clarity report:");
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  @Test
  void applySkipsReportWhenNoTracesAccumulated(@TempDir Path tempDir) throws Throwable {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      var classRule = new NarrativeTraceClassRule();
      var captured = new ByteArrayOutputStream();
      classRule.setOut(new PrintStream(captured));
      var statement =
          classRule.apply(
              new Statement() {
                @Override
                public void evaluate() {}
              },
              Description.createSuiteDescription(getClass()));
      statement.evaluate();

      assertThat(captured.toString()).isEmpty();
      assertThat(tempDir.resolve("clarity-report.md")).doesNotExist();
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  @Test
  void standaloneRuleDoesNotAccumulate() {
    var rule = new NarrativeTraceRule();

    var desc = Description.createTestDescription(getClass(), "test");
    rule.starting(desc);
    rule.context().enterMethod(new MethodSignature("Service", "doWork", List.of()));
    rule.context().exitMethodWithReturn("ok");
    rule.finished(desc);

    // no exception, no accumulation (no class rule linked)
  }

  @Test
  void combinedReportContainsScenariosFromMultipleClasses(@TempDir Path tempDir) throws Throwable {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      simulateClassWithScenario("Customer places order", "FirstClass");
      simulateClassWithScenario("Customer checks inventory", "SecondClass");

      var jsonContent = Files.readString(tempDir.resolve("clarity-results.json"));
      assertThat(jsonContent).contains("Customer places order");
      assertThat(jsonContent).contains("Customer checks inventory");

      var reportContent = Files.readString(tempDir.resolve("clarity-report.md"));
      assertThat(reportContent).contains("Customer places order");
      assertThat(reportContent).contains("Customer checks inventory");
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  @Test
  void instanceTracesAreClearedAfterReportIsWritten(@TempDir Path tempDir) throws Throwable {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      var classRule = new NarrativeTraceClassRule();
      classRule.setOut(new PrintStream(new ByteArrayOutputStream()));

      classRule.accumulate("test scenario", traceWithOneCall());
      assertThat(classRule.accumulatedTraces()).hasSize(1);

      var statement =
          classRule.apply(
              new Statement() {
                @Override
                public void evaluate() {}
              },
              Description.createSuiteDescription(getClass()));
      statement.evaluate();

      // After afterAll() completes, instance traces should be cleared
      // to avoid holding duplicate references (data is in GLOBAL_TRACES)
      assertThat(classRule.accumulatedTraces()).isEmpty();
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  private NarrativeTraceRule configureTestRule(NarrativeTraceClassRule classRule) {
    var rule = classRule.testRule();
    rule.setOut(new PrintStream(new ByteArrayOutputStream()));
    rule.setMermaidRenderer(tree -> "sequenceDiagram");
    rule.setPlantumlRenderer(tree -> "@startuml\n@enduml");
    return rule;
  }

  private void simulateScenario(
      NarrativeTraceRule rule,
      String testClass,
      String testName,
      String className,
      String methodName,
      String returnValue) {
    var desc = Description.createTestDescription(testClass, testName);
    rule.starting(desc);
    rule.context().enterMethod(new MethodSignature(className, methodName, List.of()));
    rule.context().exitMethodWithReturn(returnValue);
    rule.finished(desc);
  }

  private void evaluateTwoScenarios(NarrativeTraceClassRule classRule, NarrativeTraceRule rule)
      throws Throwable {
    classRule
        .apply(
            new Statement() {
              @Override
              public void evaluate() {
                simulateScenario(
                    rule,
                    "com.example.Test",
                    "customerPlacesOrder",
                    "OrderService",
                    "placeOrder",
                    "order-1");
                simulateScenario(
                    rule,
                    "com.example.Test",
                    "customerCancelsOrder",
                    "OrderService",
                    "cancelOrder",
                    "cancelled");
              }
            },
            Description.createSuiteDescription(getClass()))
        .evaluate();
  }

  private void simulateClassWithScenario(String scenario, String suiteName) throws Throwable {
    var classRule = new NarrativeTraceClassRule();
    classRule.setOut(new PrintStream(new ByteArrayOutputStream()));
    classRule.accumulate(scenario, traceWithOneCall());
    classRule
        .apply(
            new Statement() {
              @Override
              public void evaluate() {}
            },
            Description.createSuiteDescription(suiteName))
        .evaluate();
  }

  private static TraceTree traceWithOneCall() {
    var node =
        new TraceNode(
            new MethodSignature("Service", "doWork", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    return new DefaultTraceTree(List.of(node));
  }

  private static final String TRADING_GLOSSARY =
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
      """;

  private String clarityReportFor(Path outputDir, Path glossaryDir, String methodName)
      throws Throwable {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", outputDir.toString());
    System.setProperty("narrativetrace.glossaryDir", glossaryDir.toString());
    try {
      var classRule = new NarrativeTraceClassRule();
      classRule.setOut(new PrintStream(new ByteArrayOutputStream()));
      var rule = configureTestRule(classRule);
      classRule
          .apply(
              new Statement() {
                @Override
                public void evaluate() {
                  simulateScenario(
                      rule, "com.acme.Test", "foldsATranche", "TrancheService", methodName, "ok");
                }
              },
              Description.createSuiteDescription(getClass()))
          .evaluate();
      return Files.readString(outputDir.resolve("clarity-report.md"));
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
      System.clearProperty("narrativetrace.glossaryDir");
    }
  }

  @Test
  void clarityReportScoresInTheProjectsCommittedVocabulary(
      @TempDir Path outputDir, @TempDir Path glossaryDir) throws Throwable {
    Files.writeString(glossaryDir.resolve("glossary.json"), TRADING_GLOSSARY);

    var report = clarityReportFor(outputDir, glossaryDir, "foldTranche");

    assertThat(report).contains("Domain verb 'fold' + domain noun 'tranche'");
  }

  @Test
  void clarityReportFallsBackToTheBuiltInDictionariesWithoutACommittedGlossary(
      @TempDir Path outputDir, @TempDir Path glossaryDir) throws Throwable {
    var report = clarityReportFor(outputDir, glossaryDir, "foldTranche");

    assertThat(report).doesNotContain("Domain verb 'fold'");
  }

  @Test
  void aMalformedCommittedGlossaryDegradesToTheBuiltInDictionaries(
      @TempDir Path outputDir, @TempDir Path glossaryDir) throws Throwable {
    Files.writeString(glossaryDir.resolve("glossary.json"), "{ not json");
    var capturedErr = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(capturedErr, true, java.nio.charset.StandardCharsets.UTF_8));
    try {
      var report = clarityReportFor(outputDir, glossaryDir, "foldTranche");

      assertThat(report).doesNotContain("Domain verb 'fold'");
      assertThat(capturedErr.toString(java.nio.charset.StandardCharsets.UTF_8))
          .contains("could not be read");
    } finally {
      System.setErr(originalErr);
    }
  }
}
