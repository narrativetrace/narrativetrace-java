/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.spi.NamedTrace;
import ai.narrativetrace.api.spi.ReportContributor;
import ai.narrativetrace.api.spi.RunListener;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.clarity.ClarityAnalyzer;
import ai.narrativetrace.clarity.ClarityJsonExporter;
import ai.narrativetrace.clarity.ClarityReportRenderer;
import ai.narrativetrace.clarity.ClarityResult;
import ai.narrativetrace.clarity.DomainVocabulary;
import ai.narrativetrace.core.output.RunIdentity;
import ai.narrativetrace.core.output.RunIndex;
import ai.narrativetrace.core.output.RunPointers;
import ai.narrativetrace.core.output.ScenarioDelta;
import ai.narrativetrace.core.output.ScenarioManifest;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.spi.ExtensionRegistry;
import ai.narrativetrace.glossary.GlossaryVocabulary;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One TestNG execution's run-level state: its identity, the manifest rows, the traces the clarity
 * report scores, the structural deltas and the summed loss — written once, when the execution ends.
 *
 * <p>INTENT: The per-run grain of the JUnit 5 extension's {@code GlobalTraceAccumulator}. The
 * pipeline outlives a run ({@link SharedPipeline}); this does not: {@link #begin()} starts a fresh
 * one at {@code onExecutionStart}, {@link #finish(PrintStream)} writes and clears it at {@code
 * onExecutionFinish}, and {@link #current()} begins one lazily for a listener that joined a run
 * already under way (a {@code @Listeners} registration).
 *
 * <p><b>@llmNote</b> Static "current run" state, swapped atomically, because TestNG may hold two
 * listener instances in one execution (service loader and {@code @Listeners}) and they must report
 * into one run. A TestNG execution started from inside a TestNG test therefore shares the outer
 * execution's run — a documented limitation, the JUnit 4 rule's static accumulator has it too.
 *
 * <p><b>@sideEffects</b> Appends are synchronized: TestNG calls listeners on its worker threads.
 */
final class TestNgRun {

  /** The run under way, or empty between executions. */
  private static final AtomicReference<TestNgRun> CURRENT = new AtomicReference<>();

  private final RunIdentity runIdentity = RunIdentity.generate();

  /** Discovered once per run, alongside {@link #runIdentity} — see {@link RunListener}. */
  private final List<RunListener> runListeners = new ExtensionRegistry().load(RunListener.class);

  private final List<Map.Entry<String, TraceTree>> traces = new ArrayList<>();
  private final List<ScenarioManifest.Entry> rows = new ArrayList<>();
  private final List<ScenarioDelta> deltas = new ArrayList<>();
  private final Map<String, Integer> invocations = new HashMap<>();
  private TraceLoss loss = TraceLoss.none();
  private Path outputDir;

  private TestNgRun() {}

  /** Starts a fresh run, replacing any run a previous execution left unfinished. */
  static void begin() {
    CURRENT.set(new TestNgRun());
  }

  /** The run under way, beginning one when no execution start was seen. */
  static TestNgRun current() {
    return CURRENT.updateAndGet(run -> run == null ? new TestNgRun() : run);
  }

  /**
   * Ends the run under way: writes its artifacts and footer to {@code out}, then forgets it.
   * Nothing at all when no run is under way — a second listener instance's finish is a no-op.
   */
  static void finish(PrintStream out) {
    Optional.ofNullable(CURRENT.getAndSet(null)).ifPresent(ended -> ended.write(out));
  }

  RunIdentity runIdentity() {
    return runIdentity;
  }

  /**
   * Reports this run's identity to every discovered {@link RunListener} — SLF4J's MDC, when {@code
   * narrativetrace-slf4j} is on the classpath — on the thread about to run a test. Called as every
   * test's scope opens because MDC is thread-local; one listener that throws is reported and
   * skipped, never fails the test.
   */
  void notifyRunStarted() {
    for (var listener : runListeners) {
      isolated(
          listener, "runStarted", () -> listener.runStarted(runIdentity.id(), runIdentity.name()));
    }
  }

  private void notifyRunEnded() {
    for (var listener : runListeners) {
      isolated(listener, "runEnded", listener::runEnded);
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException") // one bad listener must not fail a run
  private static void isolated(RunListener listener, String hook, Runnable call) {
    try {
      call.run();
    } catch (RuntimeException e) {
      System.err.println(
          "narrative-trace: run listener "
              + listener.getClass().getName()
              + " failed on "
              + hook
              + " and was skipped ("
              + e
              + ")");
    }
  }

  /** The next 1-based invocation number of {@code methodKey} in this run. */
  synchronized int nextInvocation(String methodKey) {
    return invocations.merge(methodKey, 1, Integer::sum);
  }

  synchronized void recordLoss(TraceLoss testLoss) {
    loss = loss.plus(testLoss);
  }

  synchronized void recordDelta(ScenarioDelta delta) {
    deltas.add(delta);
  }

  /** Records one traced scenario for the report and its row for the manifest, in order. */
  synchronized void recordScenario(
      String scenario, TraceTree trace, ScenarioManifest.Entry row, Path testOutputDir) {
    traces.add(new AbstractMap.SimpleImmutableEntry<>(scenario, trace));
    rows.add(row);
    if (outputDir == null) {
      outputDir = testOutputDir;
    }
  }

  private synchronized void write(PrintStream out) {
    notifyRunEnded();
    if (traces.isEmpty()) {
      return;
    }
    var vocabulary = projectVocabulary();
    try {
      TraceTestSupport.writeClarityReport(
          traces,
          outputDir,
          list -> new ClarityReportRenderer().renderSuiteReport(analyze(list, vocabulary)),
          list -> new ClarityJsonExporter().export(analyze(list, vocabulary)));
      ScenarioManifest.write(rows, outputDir, runIdentity);
      RunIndex.write(rows, outputDir);
    } catch (IOException e) {
      System.err.println("Failed to write clarity report: " + e.getMessage());
    }
    runReportContributors();
    RunPointers.printFailingFirst(rows, outputDir, out);
    TraceTestSupport.printConsoleSummary(
        traces,
        outputDir,
        out,
        tree -> new ClarityAnalyzer(vocabulary).analyze(tree).overallScore(),
        deltas,
        loss,
        runIdentity);
  }

  /**
   * Hands every traced scenario to the discovered {@link ReportContributor}s, after this run's own
   * artifacts exist and before the footer. Each is isolated: one that throws is reported and
   * skipped. Scenario names are humanized method names, never blank, so every one is a valid {@link
   * NamedTrace} name.
   */
  @SuppressWarnings("PMD.AvoidCatchingGenericException") // one bad contributor must not fail a run
  private void runReportContributors() {
    var contributors = new ExtensionRegistry().load(ReportContributor.class);
    if (contributors.isEmpty()) {
      return;
    }
    var named = traces.stream().map(e -> new NamedTrace(e.getKey(), e.getValue())).toList();
    for (var contributor : contributors) {
      try {
        contributor.contribute(named, outputDir);
      } catch (RuntimeException e) {
        System.err.println(
            "narrative-trace: report contributor "
                + contributor.getClass().getName()
                + " failed and was skipped ("
                + e
                + ")");
      }
    }
  }

  private static List<Map.Entry<String, ClarityResult>> analyze(
      List<Map.Entry<String, TraceTree>> list, DomainVocabulary vocabulary) {
    var analyzer = new ClarityAnalyzer(vocabulary);
    var results = new ArrayList<Map.Entry<String, ClarityResult>>();
    for (var entry : list) {
      results.add(
          new AbstractMap.SimpleImmutableEntry<>(
              entry.getKey(), analyzer.analyze(entry.getValue())));
    }
    return results;
  }

  /**
   * The repository's committed glossary as the vocabulary clarity scores with — the JUnit
   * integrations' rule, so a repository scores the same under any of the three. {@code
   * narrativetrace.glossaryDir} names the directory, defaulting to the working directory.
   *
   * <p><b>@edgeCase</b> A glossary that cannot be read degrades to the built-in dictionaries with a
   * warning: a reporting artifact must never fail the suite that produced it.
   */
  @SuppressWarnings("PMD.AvoidCatchingGenericException") // a bad glossary must not fail the suite
  static DomainVocabulary projectVocabulary() {
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
}
