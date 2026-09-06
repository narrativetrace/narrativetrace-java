/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.clarity.ClarityAnalyzer;
import ai.narrativetrace.clarity.ClarityJsonExporter;
import ai.narrativetrace.clarity.ClarityReportRenderer;
import ai.narrativetrace.clarity.ClarityResult;
import ai.narrativetrace.clarity.DomainVocabulary;
import ai.narrativetrace.core.output.ScenarioDelta;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.glossary.GlossaryVocabulary;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit 4 class-level rule that accumulates traces across multiple test methods.
 *
 * <p>INTENT: Use this with {@link NarrativeTraceRule} when you want aggregated clarity reports and
 * console summaries in addition to per-test traces.
 *
 * <p>Use as a {@code @ClassRule} static field. Call {@link #testRule()} to create the linked
 * per-test rule:
 *
 * <pre>{@code
 * public class OrderServiceTest {
 *     @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();
 *     @Rule public NarrativeTraceRule rule = classRule.testRule();
 * }
 * }</pre>
 *
 * <p>After all tests in a class complete, accumulated traces are contributed to a static global
 * accumulator ({@code GLOBAL_TRACES}) that combines results across multiple test classes. The last
 * class to finish writes the complete clarity report.
 *
 * @see NarrativeTraceRule
 */
public class NarrativeTraceClassRule implements TestRule {

  private static final List<Map.Entry<String, TraceTree>> GLOBAL_TRACES =
      Collections.synchronizedList(new ArrayList<>());
  private static final List<ScenarioDelta> GLOBAL_DELTAS =
      Collections.synchronizedList(new ArrayList<>());

  /** Suite-wide loss, summed across tests; each test's context reports its own. */
  private static final AtomicReference<TraceLoss> GLOBAL_LOSS =
      new AtomicReference<>(TraceLoss.none());

  private final Map<String, TraceTree> accumulatedTraces = new LinkedHashMap<>();
  private final List<ScenarioDelta> accumulatedDeltas = new ArrayList<>();
  private PrintStream out = System.out;
  private PrintStream err = System.err;

  /**
   * Creates a linked per-test rule that reports traces back to this class rule.
   *
   * @return New per-test rule linked to this class accumulator.
   */
  public NarrativeTraceRule testRule() {
    var rule = new NarrativeTraceRule();
    rule.setClassRule(this);
    return rule;
  }

  void accumulate(String scenario, TraceTree trace) {
    accumulatedTraces.put(scenario, trace);
  }

  /** Adds one test's loss to the suite total behind the footer's "Incomplete" line. */
  void accumulateLoss(TraceLoss loss) {
    GLOBAL_LOSS.updateAndGet(total -> total.plus(loss));
  }

  /** Collects one test's structural delta for the end-of-run "Since last green" console line. */
  void accumulateDelta(ScenarioDelta delta) {
    accumulatedDeltas.add(delta);
  }

  Map<String, TraceTree> accumulatedTraces() {
    return accumulatedTraces;
  }

  void setOut(PrintStream out) {
    this.out = out;
  }

  void setErr(PrintStream err) {
    this.err = err;
  }

  static void resetGlobalAccumulator() {
    GLOBAL_TRACES.clear();
    GLOBAL_DELTAS.clear();
  }

  /**
   * Wraps the class statement so the suite's report is written whether or not it passed.
   *
   * <p><b>@edgeCase</b> {@code afterAll()} used to run only when {@code base.evaluate()} returned
   * normally — and JUnit 4 reports a failing test by letting that statement throw. The aggregate
   * report was therefore skipped exactly on the runs where it is most useful. The original failure
   * is what the runner still sees; the report is a side effect of the suite ending, not of it
   * passing.
   */
  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          base.evaluate();
        } finally {
          afterAll();
        }
      }
    };
  }

  private void afterAll() {
    if (!NarrativeTraceRule.isOutputEnabled()) {
      return;
    }
    if (accumulatedTraces.isEmpty()) {
      return;
    }
    for (var entry : accumulatedTraces.entrySet()) {
      GLOBAL_TRACES.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
    }
    accumulatedTraces.clear();
    GLOBAL_DELTAS.addAll(accumulatedDeltas);
    accumulatedDeltas.clear();
    var outputDir = Path.of(System.getProperty("narrativetrace.outputDir", "build/narrativetrace"));
    var vocabulary = projectVocabulary();
    try {
      TraceTestSupport.writeClarityReport(
          GLOBAL_TRACES,
          outputDir,
          traces -> renderClarityReport(traces, vocabulary),
          traces -> exportClarityJson(traces, vocabulary));
    } catch (IOException e) {
      err.println("Failed to write clarity report: " + e.getMessage());
    }
    TraceTestSupport.printConsoleSummary(
        GLOBAL_TRACES,
        outputDir,
        out,
        tree -> new ClarityAnalyzer(vocabulary).analyze(tree).overallScore(),
        GLOBAL_DELTAS,
        GLOBAL_LOSS.getAndSet(TraceLoss.none()));
  }

  /**
   * Reads the repository's committed glossary as the vocabulary clarity scores with, matching the
   * JUnit 5 extension so the same repository scores the same under either framework.
   *
   * <p>{@code narrativetrace.glossaryDir} names the directory, defaulting to the working directory.
   * A glossary that cannot be read degrades to the built-in dictionaries with a warning — a
   * reporting artifact must never fail the suite that produced it.
   */
  @SuppressWarnings("PMD.AvoidCatchingGenericException") // a bad glossary must not fail the suite
  private static DomainVocabulary projectVocabulary() {
    var glossaryDir =
        Path.of(System.getProperty("narrativetrace.glossaryDir", System.getProperty("user.dir")));
    try {
      return GlossaryVocabulary.from(glossaryDir);
    } catch (Exception e) { // NOPMD
      System.err.println(
          "narrative-trace: committed glossary at "
              + glossaryDir
              + " could not be read, scoring with the built-in dictionaries only ("
              + e
              + ")");
      return DomainVocabulary.empty();
    }
  }

  private String renderClarityReport(
      List<Map.Entry<String, TraceTree>> traces, DomainVocabulary vocabulary) {
    return new ClarityReportRenderer().renderSuiteReport(analyzeClarityResults(traces, vocabulary));
  }

  private String exportClarityJson(
      List<Map.Entry<String, TraceTree>> traces, DomainVocabulary vocabulary) {
    return new ClarityJsonExporter().export(analyzeClarityResults(traces, vocabulary));
  }

  private List<Map.Entry<String, ClarityResult>> analyzeClarityResults(
      List<Map.Entry<String, TraceTree>> traces, DomainVocabulary vocabulary) {
    var analyzer = new ClarityAnalyzer(vocabulary);
    var results = new ArrayList<Map.Entry<String, ClarityResult>>();
    for (var entry : traces) {
      results.add(
          new AbstractMap.SimpleEntry<>(entry.getKey(), analyzer.analyze(entry.getValue())));
    }
    return results;
  }
}
