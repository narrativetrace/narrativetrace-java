/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.output.ArtifactIdentity;
import ai.narrativetrace.core.output.NarrativeApproval;
import ai.narrativetrace.core.output.RunPointers;
import ai.narrativetrace.core.output.ScenarioDelta;
import ai.narrativetrace.core.output.ScenarioFramer;
import ai.narrativetrace.core.output.ScenarioManifest;
import ai.narrativetrace.core.output.TemplateWarningCollector;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.render.ExceptionMessage;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.diagrams.SequenceDiagramRenderers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import org.testng.IExecutionListener;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;
import org.testng.internal.TestResult;

/**
 * TestNG listener that gives every test method a trace of its own and writes the run's artifacts.
 *
 * <p>INTENT: The TestNG twin of the JUnit 5 {@code NarrativeTraceExtension}: add the {@code
 * narrativetrace-testng} dependency and nothing else — TestNG's service loader registers this
 * listener ({@code META-INF/services/org.testng.ITestNGListener}) — and each test method writes its
 * trace files, the run writes {@code manifest.json}, {@code INDEX.md}, the clarity report and the
 * console footer. {@code @Listeners(NarrativeTraceListener.class)} registers it explicitly; both at
 * once is safe.
 *
 * <ul>
 *   <li><strong>beforeInvocation</strong> of a test method (or of the {@code @BeforeMethod} before
 *       it): opens the test's scope on the shared context — {@code reset()}, the capture level
 *   <li><strong>afterInvocation</strong> of the test method: captures the trace, writes it, records
 *       its manifest row, and resets again so the test's events leave the shared store
 *   <li><strong>onExecutionFinish</strong>: writes the run's manifest, index, clarity report and
 *       footer. TestNG has no class-end hook the way the JUnit 4 class rule has one, so nothing is
 *       written per class; one execution may run several suites, so nothing is written per suite
 *       either
 * </ul>
 *
 * <p><b>@llmNote</b> A test reaches the context through {@link #context()}: TestNG cannot inject a
 * custom parameter type into a {@code @Test} method the way JUnit 5 does. The context is the JVM's
 * one shared {@code ThreadLocalNarrativeContext} over one pipeline ({@link SharedPipeline}); each
 * test sees only the calls made on its own thread during its own scope.
 *
 * <p><b>@llmNote</b> Output is on by default: every test writes its artifacts to {@code
 * build/narrativetrace} (relative to the test JVM's working directory). Configuration is by system
 * property, as for the JUnit 4 rule: {@code narrativetrace.output=false} opts out; {@code
 * narrativetrace.outputDir}, {@code .format} ({@code markdown}, {@code text}, {@code mermaid},
 * {@code plantuml}), {@code .level}, {@code .unfolded}, {@code .canonicalJson}, {@code
 * .structuralJson}, {@code .glossaryDir} and {@code .bufferCapacity} mean what they mean for the
 * JUnit 5 extension.
 *
 * <pre>{@code
 * public class OrderServiceTest {
 *     private final OrderService service =
 *         NarrativeTraceProxy.trace(new OrderServiceImpl(), OrderService.class,
 *             NarrativeTraceListener.context());
 *
 *     @Test
 *     public void placesOrder() {
 *         service.placeOrder("item-1", 3);
 *     }
 * }
 * }</pre>
 *
 * @see ai.narrativetrace.core.output.TraceTestSupport
 */
public class NarrativeTraceListener implements IInvokedMethodListener, IExecutionListener {

  /** The loss reading taken when the calling thread's current test scope opened. */
  private static final ThreadLocal<TraceLoss> OPEN_SCOPE = new ThreadLocal<>();

  /**
   * The JVM's shared narrative context — what a test wraps its services with.
   *
   * @return the context every test in this JVM traces into; never {@code null}
   */
  public static NarrativeContext context() {
    return SharedPipeline.shared().context();
  }

  @Override
  public void onExecutionStart() {
    TestNgRun.begin();
  }

  @Override
  public void onExecutionFinish() {
    TestNgRun.finish(System.out);
  }

  /**
   * Opens the test's scope at the first of its {@code @BeforeMethod}s, or at the test method when
   * it has none: what a set-up traces belongs to the test it prepares, as JUnit 5's {@code
   * beforeEach} runs before {@code @BeforeEach}.
   */
  @Override
  public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
    if (method.isTestMethod() || method.getTestMethod().isBeforeMethodConfiguration()) {
      openScope();
    }
  }

  /**
   * Finishes the test after its method — captured, written, reset — and resets again after each
   * {@code @AfterMethod}, so what a tear-down traces never stays in the shared store.
   */
  @Override
  public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
    if (method.isTestMethod()) {
      try {
        finishTest(testResult);
      } finally {
        closeScope();
      }
    } else if (method.getTestMethod().isAfterMethodConfiguration()) {
      closeScope();
    }
  }

  /** Opens the calling thread's test scope: a clean stack, this test's level, a loss baseline. */
  private static void openScope() {
    if (OPEN_SCOPE.get() == null) {
      var shared = SharedPipeline.shared();
      shared.context().reset();
      shared
          .config()
          .setLevel(
              TracingLevel.fromName(
                  System.getProperty("narrativetrace.level", TracingLevel.DETAIL.name()),
                  TracingLevel.DETAIL));
      OPEN_SCOPE.set(shared.context().traceLoss());
      // On the thread about to run the test, so a discovered RunListener (SLF4J's MDC) can attach
      // the run's identity there before the body runs.
      TestNgRun.current().notifyRunStarted();
    }
  }

  /** The loss reading the calling thread's scope opened with; none when no scope is open. */
  private static TraceLoss scopeBaseline() {
    return Optional.ofNullable(OPEN_SCOPE.get()).orElse(TraceLoss.none());
  }

  /** Removes the calling thread's spans and stored events, and closes its scope. */
  private static void closeScope() {
    SharedPipeline.shared().context().reset();
    OPEN_SCOPE.remove();
  }

  private void finishTest(ITestResult testResult) {
    // A skipped test writes nothing: its body never ran (a failed @BeforeMethod, a failed
    // dependency) or gave up (SkipException) — as a JUnit test aborted before it ran writes none.
    if (testResult.getStatus() == ITestResult.SKIP) {
      return;
    }
    var context = SharedPipeline.shared().context();
    var trace = context.captureTrace();
    var run = TestNgRun.current();
    var loss = context.traceLoss().since(scopeBaseline());
    run.recordLoss(loss);
    var identity = artifactIdentity(testResult, run);
    // An invocation is titled by its method and its number, never by its arguments.
    var displayName =
        identity.isInvocation()
            ? identity.structuralScenario(identity.methodName())
            : identity.methodName();
    // TestNG has settled the verdict by now, expectedExceptions included: only FAILURE is red.
    var failed = testResult.getStatus() == ITestResult.FAILURE;
    // The approval verdict is known BEFORE anything is written: a rejection fails the test too,
    // and a run that ends red must not advance the last-green artifact.
    var verdictFailed = failed || rejected(testResult, identity, displayName, trace, loss);
    printTemplateWarnings(trace);
    var written =
        outputEnabled() && !trace.isEmpty()
            ? writeAndRecord(
                run, identity, displayName, trace, verdictFailed, testResult.getThrowable())
            : Written.NOTHING;
    printFailureReport(displayName, failed, trace, written.delta());
    // Last, so it is the line directly under the failure: where this test's trace is.
    written
        .row()
        .filter(ScenarioManifest.Entry::failed)
        .flatMap(row -> RunPointers.pointerLine(row, outputDirectory()))
        .ifPresent(System.err::println);
  }

  /**
   * Which artifact this invocation owns.
   *
   * <p>A method TestNG runs more than once — a data provider's rows, {@code invocationCount}, the
   * instances a {@code @Factory} made — owns one artifact per invocation, numbered from 1: a
   * data-provider row by its row index (deterministic even under {@code parallel = true}), any
   * other repetition by the order it started in this run. The invocation label stays empty, so the
   * file is {@code <method>-00n}: TestNG's text for a row is its arguments' {@code toString}, and
   * rendering never runs a value's own code.
   *
   * <p><b>@edgeCase</b> The row index is read from TestNG's own result class, {@code
   * org.testng.internal.TestResult}; a result of any other class falls back to start order.
   */
  static ArtifactIdentity artifactIdentity(ITestResult testResult, TestNgRun run) {
    var method = testResult.getMethod();
    var testClassName = method.getRealClass().getName();
    var methodName = method.getMethodName();
    var factoryMade = method.getFactoryMethodParamsInfo() != null;
    if (!method.isDataDriven() && method.getInvocationCount() <= 1 && !factoryMade) {
      return ArtifactIdentity.ofMethod(testClassName, methodName);
    }
    var index =
        method.isDataDriven() && !factoryMade && testResult instanceof TestResult row
            ? row.getParameterIndex() + 1
            : run.nextInvocation(testClassName + "#" + methodName);
    return ArtifactIdentity.ofInvocation(testClassName, methodName, index, "");
  }

  /**
   * Approval mode ({@code narrativetrace.approval=true}) on a test that passed: whether its
   * structure was rejected against its committed {@code *.approved.nt} baseline. A rejection is set
   * on the TestNG result — FAILURE, with the readable diff as its throwable — rather than thrown,
   * so the artifacts a reviewer needs are written first and TestNG reports the test failed. {@code
   * narrativetrace.approvedDir} locates the baselines (default {@code src/test/narratives}, against
   * the working directory).
   *
   * <p><b>@sideEffects</b> Writes the {@code *.received.nt} file on a rejection and prints a lossy
   * pass's note; an inaccessible baseline is an unchecked I/O failure of the test.
   */
  private static boolean rejected(
      ITestResult testResult,
      ArtifactIdentity identity,
      String displayName,
      TraceTree trace,
      TraceLoss loss) {
    if (trace.isEmpty() || !flag("narrativetrace.approval")) {
      return false;
    }
    var approvedFile =
        NarrativeApproval.approvedFile(
            Path.of(System.getProperty("narrativetrace.approvedDir", "src/test/narratives")),
            identity);
    try {
      // The baseline is a structural artifact, titled the value-free way the generated one is.
      var note =
          NarrativeApproval.verify(
              trace, identity.structuralScenario(displayName), approvedFile, loss);
      if (!note.isEmpty()) {
        System.out.println("  Approval: " + note);
      }
      return false;
    } catch (AssertionError rejection) {
      testResult.setStatus(ITestResult.FAILURE);
      testResult.setThrowable(rejection);
      return true;
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Narrative approval could not access the baseline " + approvedFile, e);
    }
  }

  /** What writing one test's artifacts produced: its structural delta and its manifest row. */
  private record Written(Optional<ScenarioDelta> delta, Optional<ScenarioManifest.Entry> row) {
    static final Written NOTHING = new Written(Optional.empty(), Optional.empty());
  }

  /** Writes the test's files, then records its trace and manifest row in the run. */
  private static Written writeAndRecord(
      TestNgRun run,
      ArtifactIdentity identity,
      String displayName,
      TraceTree trace,
      boolean failed,
      Throwable failure) {
    var outputDir = outputDirectory();
    var delta = write(identity, displayName, trace, failed, outputDir);
    delta.ifPresent(run::recordDelta);
    var scenario = ScenarioFramer.humanize(displayName);
    var row =
        ScenarioManifest.entryFor(
            outputDir,
            identity,
            scenario,
            failed ? ScenarioManifest.Entry.FAILED : ScenarioManifest.Entry.PASSED,
            failed ? failureMessage(failure) : null);
    run.recordScenario(scenario, trace, row, outputDir);
    return new Written(delta, Optional.of(row));
  }

  /** The failure's message through the exception-message filter, or {@code null} without one. */
  private static String failureMessage(Throwable failure) {
    return failure == null ? null : ExceptionMessage.of(failure);
  }

  /**
   * On failure, the report: the scenario and its trace — or, when the write computed a structural
   * delta against last green, the delta, which says where behaviour moved.
   */
  private static void printFailureReport(
      String displayName, boolean failed, TraceTree trace, Optional<ScenarioDelta> delta) {
    if (!failed || trace.isEmpty()) {
      return;
    }
    var scenario = ScenarioFramer.frame(displayName);
    var rendered = new IndentedTextRenderer().render(trace);
    System.out.println(
        delta
            .map(d -> TraceTestSupport.buildFailureReport(scenario, rendered, d))
            .orElseGet(() -> TraceTestSupport.buildFailureReport(scenario, rendered)));
  }

  /**
   * @param failed the run's verdict: a non-green run compares against the last-green artifact and
   *     never advances it
   */
  private static Optional<ScenarioDelta> write(
      ArtifactIdentity identity,
      String displayName,
      TraceTree trace,
      boolean failed,
      Path outputDir) {
    try {
      var delta =
          TraceTestSupport.writeTraceFile(
              identity,
              displayName,
              trace,
              failed,
              outputDir,
              System.out,
              System.getProperty("narrativetrace.format", "markdown"),
              SequenceDiagramRenderers.mermaid(),
              SequenceDiagramRenderers.plantUml(),
              !flag("narrativetrace.unfolded"),
              TestNgRun.current().runIdentity().name());
      if (flag("narrativetrace.canonicalJson")) {
        TraceTestSupport.writeCanonicalTraceFile(identity, trace, outputDir);
      }
      if (flag("narrativetrace.structuralJson")) {
        TraceTestSupport.writeStructuralTraceFile(identity, trace, outputDir);
      }
      return delta;
    } catch (IOException e) {
      System.err.println("Failed to write trace file: " + e.getMessage());
      return Optional.empty();
    }
  }

  private static void printTemplateWarnings(TraceTree trace) {
    if (trace.isEmpty()) {
      return;
    }
    var formatted = TemplateWarningCollector.format(TemplateWarningCollector.collect(trace));
    if (!formatted.isEmpty()) {
      System.out.print(formatted);
    }
  }

  private static Path outputDirectory() {
    return Path.of(System.getProperty("narrativetrace.outputDir", "build/narrativetrace"));
  }

  private static boolean flag(String key) {
    return "true".equalsIgnoreCase(System.getProperty(key, "false"));
  }

  /**
   * Whether tests write their artifacts — on by default; {@code narrativetrace.output=false} is the
   * one-line opt-out, and any other value changes nothing.
   */
  private static boolean outputEnabled() {
    return !"false".equalsIgnoreCase(System.getProperty("narrativetrace.output", "true"));
  }
}
