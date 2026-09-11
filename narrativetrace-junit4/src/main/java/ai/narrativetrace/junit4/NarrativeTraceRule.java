/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.output.NarrativeApproval;
import ai.narrativetrace.core.output.ScenarioDelta;
import ai.narrativetrace.core.output.ScenarioFramer;
import ai.narrativetrace.core.output.TemplateWarningCollector;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.diagrams.MermaidSequenceDiagramRenderer;
import ai.narrativetrace.diagrams.PlantUmlSequenceDiagramRenderer;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * JUnit 4 per-test rule that creates and captures one test-scoped trace context.
 *
 * <p>INTENT: Use this as a {@code @Rule} when individual test methods need direct access to a
 * {@link NarrativeContext} for wrapping services or asserting captured traces.
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

  private NarrativeContext context;
  private boolean testFailed;
  private NarrativeTraceClassRule classRule;
  private PrintStream out = System.out;
  private PrintStream err = System.err;
  private NarrativeRenderer mermaidRenderer = new MermaidSequenceDiagramRenderer()::render;
  private NarrativeRenderer plantumlRenderer = new PlantUmlSequenceDiagramRenderer()::render;

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

  @Override
  protected void starting(Description description) {
    context = newContext(System.getProperty("narrativetrace.level", TracingLevel.DETAIL.name()));
    testFailed = false;
  }

  /**
   * Builds the per-test context at the given {@code narrativetrace.level} name. An unknown, blank,
   * or null level degrades to {@link TracingLevel#DETAIL} rather than failing the test run.
   */
  static ThreadLocalNarrativeContext newContext(String levelName) {
    var level = TracingLevel.fromName(levelName, TracingLevel.DETAIL);
    return new ThreadLocalNarrativeContext(new NarrativeTraceConfig(level));
  }

  @Override
  protected void failed(Throwable e, Description description) {
    testFailed = true;
  }

  @Override
  protected void finished(Description description) {
    if (context == null) {
      return;
    }
    printTemplateWarnings();
    // The verdict has to be known BEFORE anything is written: an approval rejection fails the test
    // too, and a run that ends red must not advance the last-green artifact.
    var rejection = testFailed ? null : approvalRejection(description);
    // Output before the report so the failure report can speak in terms of the structural delta the
    // write computed — TestWatcher guarantees finished() runs after failed().
    var delta = writeTraceIfEnabled(description, testFailed || rejection != null);
    printFailureReport(description, delta);
    accumulateIfLinked(description);
    if (rejection != null) {
      throw rejection;
    }
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
    var approvedFile =
        NarrativeApproval.approvedFile(approvedDir, testClassName, description.getMethodName());
    try {
      var note =
          NarrativeApproval.verify(
              trace,
              ScenarioFramer.humanize(description.getMethodName()),
              approvedFile,
              context.traceLoss());
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
      var delta =
          TraceTestSupport.writeTraceFile(
              testClassName,
              testMethodName,
              testMethodName,
              trace,
              verdictFailed,
              outputDir,
              out,
              format,
              mermaidRenderer,
              plantumlRenderer);
      if (classRule != null) {
        delta.ifPresent(classRule::accumulateDelta);
      }
      return delta;
    } catch (IOException ex) {
      err.println("Failed to write trace file: " + ex.getMessage());
      return Optional.empty();
    }
  }

  private void accumulateIfLinked(Description description) {
    if (classRule == null) {
      return;
    }
    classRule.accumulateLoss(context.traceLoss());
    var trace = context.captureTrace();
    if (!trace.isEmpty()) {
      var scenario = ScenarioFramer.humanize(description.getMethodName());
      classRule.accumulate(scenario, trace);
    }
  }

  static boolean isOutputEnabled() {
    return "true".equalsIgnoreCase(System.getProperty("narrativetrace.output", "false"));
  }
}
