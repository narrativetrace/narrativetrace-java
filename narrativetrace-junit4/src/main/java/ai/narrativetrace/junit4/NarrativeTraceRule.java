/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import ai.narrativetrace.api.annotation.DeepTrace;
import ai.narrativetrace.api.config.TraceDepth;
import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.output.ArtifactIdentity;
import ai.narrativetrace.core.output.NarrativeApproval;
import ai.narrativetrace.core.output.RunPointers;
import ai.narrativetrace.core.output.ScenarioDelta;
import ai.narrativetrace.core.output.ScenarioFramer;
import ai.narrativetrace.core.output.ScenarioManifest;
import ai.narrativetrace.core.output.TemplateWarningCollector;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.pipeline.EventPipeline;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import ai.narrativetrace.core.render.ExceptionMessage;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.diagrams.SequenceDiagramRenderers;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * JUnit 4 per-test rule that resets and captures one test-scoped trace context.
 *
 * <p>INTENT: Use this as a {@code @Rule} when individual test methods need direct access to a
 * {@link NarrativeContext} for wrapping services or asserting captured traces.
 *
 * <p><b>@llmNote</b> §176: linked to a {@link NarrativeTraceClassRule} (the usual pairing), this
 * rule resets and reconfigures the class rule's own context per test rather than building one — the
 * class rule is the lifecycle owner, builds the pipeline once when the class starts and closes it
 * when the class ends. Used standalone (no {@code @ClassRule}), this rule builds and owns its own
 * context and pipeline for the one test it runs, routes the agent into it if one is attached, and
 * closes both in {@code finished} — the same per-test shape every {@code @Rule} field already has
 * in JUnit 4.
 *
 * <p>For class-level output (clarity reports, console summaries), pair with {@link
 * NarrativeTraceClassRule}:
 *
 * <pre>{@code
 * public class OrderServiceTest {
 *     @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();
 *     @Rule public NarrativeTraceRule rule = classRule.testRule();
 *
 *     @Test
 *     public void placesOrder() {
 *         var service = NarrativeTraceProxy.trace(impl, OrderService.class, rule.context());
 *         service.placeOrder("item-1", 3);
 *     }
 * }
 * }</pre>
 *
 * @see NarrativeTraceClassRule
 * @see ai.narrativetrace.core.context.NarrativeContext
 */
public class NarrativeTraceRule extends TestWatcher {

  private ThreadLocalNarrativeContext context;
  private boolean testFailed;

  /** What failed the test, for the manifest row's message; the approval rejection when that did. */
  private Optional<Throwable> failure = Optional.empty();

  private NarrativeTraceClassRule classRule;
  private PrintStream out = System.out;
  private PrintStream err = System.err;
  private NarrativeRenderer mermaidRenderer = SequenceDiagramRenderers.mermaid();
  private NarrativeRenderer plantumlRenderer = SequenceDiagramRenderers.plantUml();

  /** This test's own loss, read at {@link #starting} and bracketed against at {@link #finished}. */
  private TraceLoss lossBaseline;

  /**
   * This test's own pipeline and agent bridge — only set when this rule stands alone (no linked
   * {@link NarrativeTraceClassRule}), since a linked rule resets and reports through the class
   * rule's context without owning a pipeline of its own.
   */
  private EventPipeline ownPipeline;

  private AgentContextBridge ownAgentBridge;

  /** Reported once per JVM: depth was requested but the agent never wove private methods. */
  private static final java.util.concurrent.atomic.AtomicBoolean DEEP_REFUSAL_REPORTED =
      new java.util.concurrent.atomic.AtomicBoolean();

  private TraceDepth depth = TraceDepth.PUBLIC;
  private long deepSpansDropped;

  /**
   * Returns the current test's {@link NarrativeContext}.
   *
   * @return Context created in {@code starting()} for the current test method.
   */
  public NarrativeContext context() {
    return context;
  }

  void setClassRule(NarrativeTraceClassRule classRule) {
    this.classRule = classRule;
  }

  void setOut(PrintStream out) {
    this.out = out;
  }

  void setErr(PrintStream err) {
    this.err = err;
  }

  void setMermaidRenderer(NarrativeRenderer mermaidRenderer) {
    this.mermaidRenderer = mermaidRenderer;
  }

  void setPlantumlRenderer(NarrativeRenderer plantumlRenderer) {
    this.plantumlRenderer = plantumlRenderer;
  }

  /**
   * §176: linked to a class rule, this resets and reconfigures the class's own context rather than
   * building one; standalone (no {@code @ClassRule}), it builds and owns its own context and
   * pipeline for this one test, exactly as every {@code @Rule} field already does in JUnit 4 (a new
   * instance per test method) — closed in {@link #finished}.
   */
  @Override
  protected void starting(Description description) {
    var level =
        TracingLevel.fromName(
            System.getProperty("narrativetrace.level", TracingLevel.DETAIL.name()),
            TracingLevel.DETAIL);
    if (classRule != null) {
      context = classRule.context();
      context.reset();
      classRule.config().setLevel(level);
    } else {
      context = buildOwnContext(level);
    }
    testFailed = false;
    failure = Optional.empty();
    lossBaseline = context.traceLoss();
    depth = openDepthScope(description);
    deepSpansDropped = 0;
    // Only when linked to a class rule: a lone @Rule with no aggregator names no run, the same
    // way it produces no console footer or manifest today (2026-09-13 ruling, item 2).
    if (classRule != null) {
      NarrativeTraceClassRule.notifyRunStarted();
    }
  }

  private AgentContextBridge bridge() {
    return classRule != null ? classRule.agentBridge() : ownAgentBridge;
  }

  /**
   * Decides this test's depth and, when it is {@link TraceDepth#INTERNAL}, opens the agent's deep
   * scope on the test thread. Requested by {@code @DeepTrace} (test or class) or the run-wide
   * {@code narrativetrace.depth=internal}; granted only when the agent was started with {@code
   * depth=internal}. A refused request runs public and says so once on standard error. A scope a
   * previous test left open is closed first.
   */
  private TraceDepth openDepthScope(Description description) {
    var bridge = bridge();
    var armed = bridge.deepArmed();
    if (armed) {
      bridge.endDeepScope();
    }
    if (!requestsDepth(description)) {
      return TraceDepth.PUBLIC;
    }
    if (!armed) {
      if (DEEP_REFUSAL_REPORTED.compareAndSet(false, true)) {
        err.println(
            "narrative-trace: depth=internal was requested (@DeepTrace or narrativetrace.depth)"
                + " but the Java agent was not started with depth=internal, so private methods"
                + " were never woven; tests run at depth PUBLIC.");
      }
      return TraceDepth.PUBLIC;
    }
    bridge.beginDeepScope();
    return TraceDepth.INTERNAL;
  }

  private static boolean requestsDepth(Description description) {
    return TraceDepth.INTERNAL
            == TraceDepth.fromName(System.getProperty("narrativetrace.depth"), TraceDepth.PUBLIC)
        || description.getAnnotation(DeepTrace.class) != null
        || (description.getTestClass() != null
            && description.getTestClass().isAnnotationPresent(DeepTrace.class));
  }

  /**
   * Builds a standalone context at the given {@code narrativetrace.level} name, independent of this
   * rule's own lifecycle fields — a direct-construction test utility, like {@code
   * NarrativeTraceExtension.newContext} in the JUnit 5 module. An unknown, blank, or null level
   * degrades to {@link TracingLevel#DETAIL} rather than failing the test run.
   */
  static ThreadLocalNarrativeContext newContext(String levelName) {
    var level = TracingLevel.fromName(levelName, TracingLevel.DETAIL);
    return new ThreadLocalNarrativeContext(
        new NarrativeTraceConfig(level),
        PipelineBootstrap.createDefault(PipelineBootstrap.DEFAULT_LOGGER_NAME));
  }

  /**
   * Builds the context and pipeline this rule owns when it stands alone (no linked {@link
   * NarrativeTraceClassRule}), and routes the agent into it — closed in {@link #finished}.
   */
  private ThreadLocalNarrativeContext buildOwnContext(TracingLevel level) {
    ownPipeline = PipelineBootstrap.createDefault(PipelineBootstrap.DEFAULT_LOGGER_NAME);
    var built = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(level), ownPipeline);
    ownAgentBridge = AgentContextBridge.attach(built);
    return built;
  }

  @Override
  protected void failed(Throwable e, Description description) {
    testFailed = true;
    failure = Optional.of(e);
  }

  @Override
  protected void finished(Description description) {
    if (context == null) {
      return;
    }
    try {
      if (depth == TraceDepth.INTERNAL) {
        deepSpansDropped = bridge().endDeepScope();
      }
      printTemplateWarnings();
      // The verdict has to be known BEFORE anything is written: an approval rejection fails the
      // test too, and a run that ends red must not advance the last-green artifact.
      var rejection = testFailed ? null : approvalRejection(description);
      // Output before the report so the failure report can speak in terms of the structural delta
      // the write computed — TestWatcher guarantees finished() runs after failed().
      var delta = writeTraceIfEnabled(description, testFailed || rejection != null);
      printFailureReport(description, delta);
      if (rejection != null) {
        failure = Optional.of(rejection);
      }
      var row = accumulateIfLinked(description, testFailed || rejection != null);
      // After the failure report, so it is the last thing under the failing test.
      row.filter(ScenarioManifest.Entry::failed)
          .flatMap(
              entry ->
                  RunPointers.pointerLine(
                      entry,
                      Path.of(
                          System.getProperty("narrativetrace.outputDir", "build/narrativetrace"))))
          .ifPresent(err::println);
      if (rejection != null) {
        throw rejection;
      }
    } finally {
      closeOwnContextIfStandalone();
    }
  }

  /**
   * Closes the context and pipeline this rule owns, and restores the agent's previous context —
   * only when this rule stands alone. A rule linked to a {@link NarrativeTraceClassRule} resets and
   * reports through the class rule's context but never owns or closes a pipeline; the class rule
   * closes its own when the class ends.
   */
  private void closeOwnContextIfStandalone() {
    if (classRule != null) {
      return;
    }
    ownAgentBridge.detach();
    ownPipeline.close();
  }

  /**
   * This test's own share of the context's loss, bracketed against the reading {@link #starting}
   * took — always non-null there, in the same step that sets {@link #context}, so a test that
   * reaches this (i.e. with {@link #context} non-null) always has one.
   */
  private TraceLoss currentLoss() {
    return context.traceLoss().since(lossBaseline);
  }

  /**
   * Runs approval verification and hands back the failure it would raise, instead of raising it.
   *
   * <p>INTENT: The last-green artifact advances only on a run that is green <em>in full</em>, and
   * that includes the approval verdict — a rejected structure that advanced the baseline poisoned
   * it, and the next (reverted, correct) run then reported a removal that never happened
   * (2026-09-08 agent evaluation). The error is thrown after the write, so the test fails exactly
   * as it did before, with the same message.
   */
  private AssertionError approvalRejection(Description description) {
    try {
      verifyApprovalIfEnabled(description);
      return null;
    } catch (AssertionError e) {
      return e;
    }
  }

  /**
   * Approval mode ({@code narrativetrace.approval=true}): verifies the scenario's structure against
   * its committed {@code *.approved.nt} baseline and fails the test on any unapproved change —
   * TestWatcher folds an exception thrown here into the test's failures. Runs only for tests that
   * passed, so a red test's mid-flight structure never churns the received files.
   */
  private void verifyApprovalIfEnabled(Description description) {
    if (!"true".equalsIgnoreCase(System.getProperty("narrativetrace.approval", "false"))) {
      return;
    }
    var trace = context.captureTrace();
    if (trace.isEmpty()) {
      return;
    }
    var approvedDir =
        Path.of(System.getProperty("narrativetrace.approvedDir", "src/test/narratives"));
    var testClassName =
        description.getTestClass() != null
            ? description.getTestClass().getName()
            : description.getClassName();
    var identity = ArtifactIdentity.ofMethod(testClassName, description.getMethodName());
    var approvedFile = NarrativeApproval.approvedFile(approvedDir, identity);
    try {
      // The baseline is a structural artifact: titled the value-free way, so the label
      // Parameterized appends to a method name stays out of it.
      var note =
          NarrativeApproval.verify(
              trace,
              identity.structuralScenario(description.getMethodName()),
              approvedFile,
              currentLoss(),
              depth);
      if (!note.isEmpty()) {
        System.out.println("  Approval: " + note);
      }
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(
          "Narrative approval could not access the baseline " + approvedFile, e);
    }
  }

  /**
   * On failure, prints the report last so it is the most visible console block; with a structural
   * delta available it localizes change against last green instead of dumping the full trace.
   */
  private void printFailureReport(Description description, Optional<ScenarioDelta> delta) {
    if (!testFailed) {
      return;
    }
    var trace = context.captureTrace();
    if (trace.isEmpty()) {
      return;
    }
    var scenario = ScenarioFramer.frame(description.getMethodName());
    var rendered = new IndentedTextRenderer().render(trace);
    out.println(
        delta
            .map(d -> TraceTestSupport.buildFailureReport(scenario, rendered, d))
            .orElseGet(() -> TraceTestSupport.buildFailureReport(scenario, rendered)));
  }

  private void printTemplateWarnings() {
    var trace = context.captureTrace();
    if (trace.isEmpty()) {
      return;
    }
    var warnings = TemplateWarningCollector.collect(trace);
    var formatted = TemplateWarningCollector.format(warnings);
    if (!formatted.isEmpty()) {
      out.print(formatted);
    }
  }

  /**
   * @param verdictFailed the run's verdict: the test failed, or its approval was rejected. A
   *     non-green run compares against the last-green artifact and never advances it
   */
  private Optional<ScenarioDelta> writeTraceIfEnabled(
      Description description, boolean verdictFailed) {
    if (!isOutputEnabled()) {
      return Optional.empty();
    }
    var trace = context.captureTrace();
    var outputDir = Path.of(System.getProperty("narrativetrace.outputDir", "build/narrativetrace"));
    var format = System.getProperty("narrativetrace.format", "markdown");
    var testClassName =
        description.getTestClass() != null
            ? description.getTestClass().getName()
            : description.getClassName();
    var testMethodName = description.getMethodName();
    try {
      var identity = ArtifactIdentity.ofMethod(testClassName, testMethodName);
      var delta =
          TraceTestSupport.writeTraceFile(
              identity,
              testMethodName,
              trace,
              verdictFailed,
              outputDir,
              out,
              format,
              mermaidRenderer,
              plantumlRenderer,
              true,
              classRule == null ? null : classRule.runIdentity().name(),
              depth,
              deepSpansDropped);
      if (classRule != null) {
        delta.ifPresent(classRule::accumulateDelta);
      }
      return delta;
    } catch (IOException ex) {
      err.println("Failed to write trace file: " + ex.getMessage());
      return Optional.empty();
    }
  }

  private Optional<ScenarioManifest.Entry> accumulateIfLinked(
      Description description, boolean verdictFailed) {
    if (classRule == null) {
      return Optional.empty();
    }
    classRule.accumulateLoss(currentLoss());
    var trace = context.captureTrace();
    if (trace.isEmpty()) {
      return Optional.empty();
    }
    var scenario = ScenarioFramer.humanize(description.getMethodName());
    classRule.accumulate(scenario, trace);
    if (!isOutputEnabled()) {
      return Optional.empty();
    }
    var testClassName =
        description.getTestClass() != null
            ? description.getTestClass().getName()
            : description.getClassName();
    var row =
        ScenarioManifest.entryFor(
            Path.of(System.getProperty("narrativetrace.outputDir", "build/narrativetrace")),
            ArtifactIdentity.ofMethod(testClassName, description.getMethodName()),
            scenario,
            verdictFailed ? ScenarioManifest.Entry.FAILED : ScenarioManifest.Entry.PASSED,
            verdictFailed ? failure.map(ExceptionMessage::of).orElse(null) : null);
    classRule.accumulateRow(row);
    return Optional.of(row);
  }

  /**
   * Whether this test writes its trace artifacts to disk — on by default (2026-09-11 ruling):
   * capture was always on, only file-writing was opt-in, and adopters wrapping services saw no
   * artifacts and no payoff. {@code narrativetrace.output=false} is the one-line opt-out; any other
   * value, including the historical {@code true}, changes nothing.
   */
  static boolean isOutputEnabled() {
    return !"false".equalsIgnoreCase(System.getProperty("narrativetrace.output", "true"));
  }
}
