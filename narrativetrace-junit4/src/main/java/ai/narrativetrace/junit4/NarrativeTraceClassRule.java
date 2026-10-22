/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.spi.RunListener;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.clarity.ClarityAnalyzer;
import ai.narrativetrace.clarity.ClarityJsonExporter;
import ai.narrativetrace.clarity.ClarityReportRenderer;
import ai.narrativetrace.clarity.ClarityResult;
import ai.narrativetrace.clarity.DomainVocabulary;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.output.RunIdentity;
import ai.narrativetrace.core.output.ScenarioDelta;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.pipeline.EventPipeline;
import ai.narrativetrace.core.spi.ExtensionRegistry;
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
 * JUnit 4 class-level rule that hands every test in its class the test JVM's shared context and
 * accumulates their traces across its test methods.
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
 * <p><b>@llmNote</b> §176: this rule adopts the test JVM's one {@link ThreadLocalNarrativeContext}
 * and {@link EventPipeline} — see {@link SuiteContext}, the lifecycle owner that builds them on the
 * first class rule to start and closes the pipeline once, at JVM shutdown. The linked {@link
 * NarrativeTraceRule} resets that same context per test rather than building its own. When the Java
 * agent is attached to this JVM, the context is also what {@code AgentRuntime}'s shared capture
 * publishes into for the life of the class (see {@code AgentContextBridge}), so a class the agent
 * wove — no proxy, no annotation — produces per-test files too.
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

  /**
   * This JVM's test-suite execution identity — generated once, at class load, alongside the other
   * {@code GLOBAL_*} suite-wide state (2026-09-13 ruling, item 2). {@link #resetGlobalAccumulator}
   * regenerates it for the same reason it clears the others: a test asserting behavior across two
   * separate "runs" within one JVM needs two distinct identities, not one reused.
   */
  private static volatile RunIdentity GLOBAL_RUN_IDENTITY = RunIdentity.generate();

  /** Discovered once per JVM, alongside {@link #GLOBAL_RUN_IDENTITY} — see {@link RunListener}. */
  private static final List<RunListener> RUN_LISTENERS =
      new ExtensionRegistry().load(RunListener.class);

  private final Map<String, TraceTree> accumulatedTraces = new LinkedHashMap<>();
  private final List<ScenarioDelta> accumulatedDeltas = new ArrayList<>();
  private PrintStream out = System.out;
  private PrintStream err = System.err;

  /**
   * The suite's context and config, adopted in {@link #apply} when the class starts, and this
   * class's own agent bridge, detached when it ends. Re-adopted on every {@link #apply} call rather
   * than once per instance: a {@code @ClassRule} static field is one object for the whole JVM, and
   * a test harness that runs the same class more than once in one process (nested launches, as this
   * module's own suite does) applies this rule more than once on it.
   */
  private ThreadLocalNarrativeContext context;

  private NarrativeTraceConfig config;
  private AgentContextBridge agentBridge;

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

  /**
   * The context every linked {@link NarrativeTraceRule} resets and captures per test.
   *
   * <p><b>@edgeCase</b> Adopted on first access rather than only in {@link #apply}: a test that
   * drives this rule directly (calling {@code starting()}/{@code finished()} without the real JUnit
   * 4 runner ever invoking {@code apply()}) still gets a working context. Under ordinary
   * {@code @ClassRule} execution {@link #apply} has already adopted it before any test's {@code
   * starting()} runs.
   */
  ThreadLocalNarrativeContext context() {
    if (context == null) {
      startContext();
    }
    return context;
  }

  /** The mutable config backing {@link #context()} — a linked rule sets the level per test. */
  NarrativeTraceConfig config() {
    if (context == null) {
      startContext();
    }
    return config;
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
    GLOBAL_RUN_IDENTITY = RunIdentity.generate();
  }

  /** This JVM's current test-suite run identity — see {@link #GLOBAL_RUN_IDENTITY}. */
  RunIdentity runIdentity() {
    return GLOBAL_RUN_IDENTITY;
  }

  /**
   * Reports the current run's identity to every discovered {@link RunListener} — SLF4J's MDC, when
   * {@code narrativetrace-slf4j} is on the classpath — on the thread about to run a test. Isolated
   * per listener: one that throws is reported and skipped, never fails the test.
   */
  @SuppressWarnings("PMD.AvoidCatchingGenericException") // one bad listener must not fail a test
  static void notifyRunStarted() {
    var run = GLOBAL_RUN_IDENTITY;
    for (var listener : RUN_LISTENERS) {
      try {
        listener.runStarted(run.id(), run.name());
      } catch (Exception e) { // NOPMD
        System.err.println(
            "narrative-trace: run listener "
                + listener.getClass().getName()
                + " failed on runStarted and was skipped ("
                + e
                + ")");
      }
    }
  }

  /** Reports the run's end to every discovered {@link RunListener}, isolated per listener. */
  @SuppressWarnings("PMD.AvoidCatchingGenericException") // one bad listener must not fail the run
  static void notifyRunEnded() {
    for (var listener : RUN_LISTENERS) {
      try {
        listener.runEnded();
      } catch (Exception e) { // NOPMD
        System.err.println(
            "narrative-trace: run listener "
                + listener.getClass().getName()
                + " failed on runEnded and was skipped ("
                + e
                + ")");
      }
    }
  }

  /**
   * Wraps the class statement so the context lives for the class's whole run and the suite's report
   * is written whether or not it passed.
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
        startContext();
        try {
          base.evaluate();
        } finally {
          afterAll();
          closeContext();
        }
      }
    };
  }

  /** Adopts the suite's context, and routes the agent into it if one is attached. */
  private void startContext() {
    var suite = SuiteContext.shared();
    config = suite.config();
    context = suite.context();
    agentBridge = AgentContextBridge.attach(context);
  }

  /**
   * Restores the agent's previous context (if any). The pipeline belongs to {@link SuiteContext},
   * which closes it once for the whole test JVM — a class that has finished must not close what the
   * classes after it still publish into.
   */
  private void closeContext() {
    agentBridge.detach();
  }

  private void afterAll() {
    notifyRunEnded();
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
        GLOBAL_LOSS.getAndSet(TraceLoss.none()),
        GLOBAL_RUN_IDENTITY);
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
