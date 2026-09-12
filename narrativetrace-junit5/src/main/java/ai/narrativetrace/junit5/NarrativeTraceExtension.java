/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.api.spi.NamedTrace;
import ai.narrativetrace.api.spi.ReportContributor;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.clarity.ClarityAnalyzer;
import ai.narrativetrace.clarity.ClarityIssue;
import ai.narrativetrace.clarity.ClarityJsonExporter;
import ai.narrativetrace.clarity.ClarityReportRenderer;
import ai.narrativetrace.clarity.ClarityResult;
import ai.narrativetrace.clarity.DomainVocabulary;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.output.ArtifactIdentity;
import ai.narrativetrace.core.output.NarrativeApproval;
import ai.narrativetrace.core.output.ScenarioDelta;
import ai.narrativetrace.core.output.ScenarioFramer;
import ai.narrativetrace.core.output.ScenarioManifest;
import ai.narrativetrace.core.output.TemplateWarningCollector;
import ai.narrativetrace.core.output.TraceFileWriter;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.spi.ExtensionRegistry;
import ai.narrativetrace.diagrams.MermaidSequenceDiagramRenderer;
import ai.narrativetrace.diagrams.PlantUmlSequenceDiagramRenderer;
import ai.narrativetrace.glossary.ClassPackageIndex;
import ai.narrativetrace.glossary.GlossaryVocabulary;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 5 extension that manages trace capture during test execution.
 *
 * <p>INTENT: Use this when tests should receive a fresh {@link NarrativeContext}, emit failure
 * traces, and optionally produce trace artifacts and clarity reports without custom boilerplate.
 *
 * <p>Register via {@code @ExtendWith(NarrativeTraceExtension.class)} on your test class. The
 * extension:
 *
 * <ul>
 *   <li><strong>BeforeEach:</strong> creates a fresh {@link ThreadLocalNarrativeContext}
 *   <li><strong>AfterTestExecution:</strong> captures the trace, prints failure reports, writes
 *       Markdown/Mermaid/PlantUML/JSON files (when output is enabled)
 *   <li><strong>AfterAll:</strong> accumulates traces for the combined clarity report and for the
 *       run's {@code manifest.json}, the scenario → file index over every artifact written
 *   <li><strong>ParameterResolver:</strong> injects {@link NarrativeContext} into test methods
 * </ul>
 *
 * <p><b>@llmNote</b> Output is on by default: every test writes its artifacts to the ephemeral
 * {@code build/narrativetrace} directory with no configuration at all. Set {@code
 * narrativetrace.output=false} as a system property or JUnit configuration parameter to opt back
 * out. Additional properties:
 *
 * <ul>
 *   <li>{@code narrativetrace.outputDir} — output directory (default: {@code build/narrativetrace})
 *   <li>{@code narrativetrace.format} — output format: {@code markdown}, {@code text}, {@code
 *       mermaid}, or {@code plantuml} (default: {@code markdown})
 *   <li>{@code narrativetrace.unfolded} — render every iteration of a loop in full instead of
 *       summarizing repeats as {@code ×k more …} (default: {@code false}, Markdown only). Turn it
 *       on when a folded run hides the per-iteration values you are looking for
 *   <li>{@code narrativetrace.bufferCapacity} — slots in this context's event ring (default {@value
 *       #DEFAULT_TEST_BUFFER_CAPACITY}). Raise it when a narrative reports shed events; the
 *       runtime-wide {@code narrativetrace.buffer.capacity} overrules it
 *   <li>{@code narrativetrace.level} — capture level: {@code OFF}, {@code ERRORS}, {@code SUMMARY},
 *       {@code NARRATIVE}, or {@code DETAIL} (default: {@code DETAIL})
 *   <li>{@code narrativetrace.glossary} — harvest the domain glossary at suite end (default: {@code
 *       false}). Rewrites {@code glossary.json} / {@code glossary.md} outside the build directory,
 *       so it is opt-in
 *   <li>{@code narrativetrace.glossaryDir} — directory holding those files (default: the working
 *       directory; the Gradle plugin sets it to the repository root)
 *   <li>{@code narrativetrace.canonicalJson} — additionally write a {@code <test>.canonical.json}
 *       schema-1.1 entry array beside each trace file (default: {@code false}); a machine artifact
 *       for canonical-schema consumers such as the other runtimes and conformance fixtures
 *   <li>{@code narrativetrace.structuralJson} — additionally write a {@code <test>.structural.json}
 *       AI-safe artifact beside each trace file (default: {@code false}): the same schema-1.1 entry
 *       array with every runtime-value field elided (ADR-002 Level 1, "Structure Only")
 *   <li>{@code narrativetrace.approval} — approval mode (default: {@code false}): a passing test
 *       whose traced structure differs from its committed {@code *.approved.nt} baseline fails with
 *       a readable diff; the current structure is written as {@code *.received.nt} for review and
 *       accepted via the Gradle {@code approveNarratives} task
 *   <li>{@code narrativetrace.approvedDir} — directory of committed baselines, {@code
 *       <dir>/<TestClass>/<scenario>.approved.nt} (default: {@code src/test/narratives}, resolved
 *       against the test working directory)
 * </ul>
 *
 * <pre>{@code
 * @ExtendWith(NarrativeTraceExtension.class)
 * class OrderServiceTest {
 *     @Test
 *     void placesOrder(NarrativeContext context) {
 *         var service = NarrativeTraceProxy.trace(new OrderServiceImpl(), OrderService.class, context);
 *         service.placeOrder("item-1", 3);
 *     }
 * }
 * }</pre>
 *
 * @see ai.narrativetrace.core.context.NarrativeContext
 * @see ai.narrativetrace.core.output.TraceTestSupport
 */
public class NarrativeTraceExtension
    implements BeforeEachCallback, AfterTestExecutionCallback, AfterAllCallback, ParameterResolver {

  /**
   * Slots in a test context's event ring, when nothing configures it.
   *
   * <p>8,192 rather than the runtime's 65,536: a test method traces tens of calls, not tens of
   * thousands, and the ring is allocated eagerly at construction — so the runtime default would
   * cost 1.75 MB and ~1.5 ms per test method for slots no test reaches. At 8,192 that is 224 kB. A
   * suite that does exceed it sheds events, and says so loudly in every narrative it writes.
   */
  public static final int DEFAULT_TEST_BUFFER_CAPACITY = 8192;

  /** JUnit configuration key overriding {@link #DEFAULT_TEST_BUFFER_CAPACITY}. */
  static final String BUFFER_CAPACITY_KEY = "narrativetrace.bufferCapacity";

  /** The unique-id segment JUnit gives one invocation of a test template. */
  private static final java.util.regex.Pattern INVOCATION_SEGMENT =
      java.util.regex.Pattern.compile("\\[test-template-invocation:#(\\d{1,9})]");

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(NarrativeTraceExtension.class);
  private static final String CONTEXT_KEY = "narrativeContext";
  private static final String TRACES_KEY = "accumulatedTraces";
  private static final String DELTAS_KEY = "accumulatedDeltas";
  private static final String GLOBAL_KEY = "globalTraceAccumulator";

  /**
   * Accumulates traces from all test classes in the suite and writes the combined clarity report.
   *
   * <p>Uses a {@code List} rather than a {@code Map} so that two test classes with identically
   * named scenarios (same {@code @DisplayName}) are both retained as separate entries. A {@code
   * Map} would silently overwrite the first entry when the second class contributes its traces.
   */
  static class GlobalTraceAccumulator implements ExtensionContext.Store.CloseableResource {
    private final List<Map.Entry<String, TraceTree>> allTraces = new ArrayList<>();
    private final List<ScenarioDelta> allDeltas = new ArrayList<>();
    private final List<ScenarioManifest.Entry> manifestRows = new ArrayList<>();
    private TraceLoss suiteLoss = TraceLoss.none();
    private Path outputDir;
    private GlossaryHarvestStep glossaryStep = GlossaryHarvestStep.disabled();
    private DomainVocabulary vocabulary = DomainVocabulary.empty();

    /** Adds one test's loss to the suite total — each test gets a fresh context, so these sum. */
    synchronized void recordLoss(TraceLoss loss) {
      suiteLoss = suiteLoss.plus(loss);
    }

    /**
     * Adds one scenario's row to the run manifest, in execution order.
     *
     * <p>Kept here rather than in the class-level store because the manifest is one file for the
     * whole run: a reader looking for a scenario should not have to know which class produced it.
     */
    synchronized void recordArtifacts(ScenarioManifest.Entry entry) {
      manifestRows.add(entry);
    }

    synchronized void contribute(
        Map<String, TraceTree> traces,
        List<ScenarioDelta> deltas,
        Path outputDir,
        GlossaryHarvestStep glossaryStep,
        DomainVocabulary vocabulary) {
      for (var entry : traces.entrySet()) {
        allTraces.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue()));
      }
      allDeltas.addAll(deltas);
      if (this.outputDir == null) {
        this.outputDir = outputDir;
        this.glossaryStep = glossaryStep;
        this.vocabulary = vocabulary;
      }
    }

    @Override
    public void close() {
      if (allTraces.isEmpty()) {
        return;
      }
      var extension = new NarrativeTraceExtension();
      var suiteIssues =
          glossaryStep.run(allTraces.stream().map(Map.Entry::getValue).toList(), System.out);
      try {
        writeClarityFiles(extension, suiteIssues);
        ScenarioManifest.write(manifestRows, outputDir);
      } catch (IOException e) {
        System.err.println("Failed to write clarity report: " + e.getMessage());
      }
      runReportContributors();
      printConsoleSummary();
    }

    /**
     * Hands every accumulated trace to the discovered {@link ReportContributor}s.
     *
     * <p>INTENT: The end-of-run seam for output that needs the whole suite rather than one test.
     * Contributors run after this extension's own artifacts exist, so a contributor may read them,
     * and before the console summary, so their output is already on disk when the run signs off.
     *
     * <p><b>@sideEffects</b> Single-threaded and isolated per contributor: one that throws is
     * reported and skipped, and neither the remaining contributors nor the test run fail because of
     * it. Nothing discovered means nothing happens — no registry consultation cost is paid twice.
     */
    private void runReportContributors() {
      var contributors = new ExtensionRegistry().load(ReportContributor.class);
      if (contributors.isEmpty()) {
        return;
      }
      List<NamedTrace> named;
      try {
        named = namedTraces();
      } catch (IllegalArgumentException e) {
        System.err.println("narrative-trace: could not assemble traces for contributors: " + e);
        return;
      }
      for (var contributor : contributors) {
        contribute(contributor, named);
      }
    }

    private List<NamedTrace> namedTraces() {
      return allTraces.stream()
          .map(entry -> new NamedTrace(entry.getKey(), entry.getValue()))
          .toList();
    }

    @SuppressWarnings(
        "PMD.AvoidCatchingGenericException") // one bad contributor must not fail a run
    private void contribute(ReportContributor contributor, List<NamedTrace> named) {
      try {
        contributor.contribute(named, outputDir);
      } catch (Exception e) { // NOPMD
        System.err.println(
            "narrative-trace: report contributor "
                + contributor.getClass().getName()
                + " failed and was skipped ("
                + e
                + ")");
      }
    }

    /**
     * Writes the suite clarity report and JSON, folding the glossary harvest's {@code
     * non-canonical-term} issues in as suite-level issues (empty when harvesting is off or no
     * committed glossary exists).
     */
    private void writeClarityFiles(
        NarrativeTraceExtension extension, List<ClarityIssue> suiteIssues) throws IOException {
      var clarityResults = extension.analyzeClarityResultsList(allTraces, vocabulary);
      var writer = new TraceFileWriter();
      writer.write(
          new ClarityReportRenderer().renderSuiteReport(clarityResults, suiteIssues),
          outputDir.resolve("clarity-report.md"));
      writer.write(
          new ClarityJsonExporter().export(clarityResults, suiteIssues),
          outputDir.resolve("clarity-results.json"));
    }

    private void printConsoleSummary() {
      TraceTestSupport.printConsoleSummary(
          allTraces,
          outputDir,
          System.out,
          tree -> new ClarityAnalyzer(vocabulary).analyze(tree).overallScore(),
          allDeltas,
          suiteLoss);
    }
  }

  /** The one suite-wide accumulator, created on first use and closed by the root store. */
  private static GlobalTraceAccumulator accumulator(ExtensionContext extensionContext) {
    return extensionContext
        .getRoot()
        .getStore(NAMESPACE)
        .getOrComputeIfAbsent(
            GLOBAL_KEY, k -> new GlobalTraceAccumulator(), GlobalTraceAccumulator.class);
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    var levelName =
        configParam(extensionContext, "narrativetrace.level", TracingLevel.DETAIL.name());
    var context = newContext(levelName, bufferCapacity(extensionContext));
    extensionContext.getStore(NAMESPACE).put(CONTEXT_KEY, context);
  }

  /** Ring size for a test's context, from {@code narrativetrace.bufferCapacity}. */
  private static int bufferCapacity(ExtensionContext extensionContext) {
    return bufferCapacityFrom(configParam(extensionContext, BUFFER_CAPACITY_KEY, ""));
  }

  /**
   * Reads a configured ring size, defaulting to {@link #DEFAULT_TEST_BUFFER_CAPACITY}.
   *
   * <p><b>@edgeCase</b> Absent, blank, non-numeric, zero and negative values all degrade to the
   * default rather than failing: a typo in an observability knob must not turn a green suite red.
   * An unreasonably large value is left to {@code PipelineBootstrap}, which rejects only what the
   * ring genuinely cannot build.
   *
   * <p>The runtime-wide {@code narrativetrace.buffer.capacity} still overrules whatever this
   * returns, because a deployment's setting outranks an integration's default.
   *
   * @param configured the raw configuration value, possibly {@code null} or blank
   */
  static int bufferCapacityFrom(String configured) {
    if (configured == null || configured.isBlank()) {
      return DEFAULT_TEST_BUFFER_CAPACITY;
    }
    try {
      var requested = Integer.parseInt(configured.trim());
      return requested >= 1 ? requested : DEFAULT_TEST_BUFFER_CAPACITY;
    } catch (NumberFormatException e) {
      return DEFAULT_TEST_BUFFER_CAPACITY;
    }
  }

  /**
   * Builds the per-test context at the configured {@code narrativetrace.level}. An unknown, blank,
   * or null level degrades to {@link TracingLevel#DETAIL} rather than failing the test run.
   */
  static ThreadLocalNarrativeContext newContext(String levelName) {
    return newContext(levelName, DEFAULT_TEST_BUFFER_CAPACITY);
  }

  /**
   * Builds the per-test context with an explicitly chosen ring size.
   *
   * <p>INTENT: This extension builds one context per <em>test method</em>, and a test uses a few
   * dozen buffer slots of it. The runtime's 65,536-slot default costs 1.75 MB and ~1.5 ms per test
   * for nothing, so this integration passes its own, smaller default — <em>explicitly</em>. Core
   * never detects JUnit, or any framework: a runtime that behaves differently because of what is on
   * the classpath is a runtime nobody can reason about. The integration knows its workload, so the
   * integration says so, in its own code, in a line a reader can find.
   *
   * @param levelName the configured capture level, or {@code null} for {@link TracingLevel#DETAIL}
   * @param bufferCapacity ring size for this context's best-effort path
   */
  static ThreadLocalNarrativeContext newContext(String levelName, int bufferCapacity) {
    var level = TracingLevel.fromName(levelName, TracingLevel.DETAIL);
    return new ThreadLocalNarrativeContext(
        new NarrativeTraceConfig(level),
        PipelineBootstrap.createDefault(PipelineBootstrap.DEFAULT_LOGGER_NAME, bufferCapacity));
  }

  @Override
  public void afterTestExecution(ExtensionContext extensionContext) {
    var context = extensionContext.getStore(NAMESPACE).get(CONTEXT_KEY, NarrativeContext.class);
    if (context == null) {
      return;
    }
    var trace = context.captureTrace();
    // Each test runs on its own context, so this reading is that test's loss, not the process's.
    var loss = context.traceLoss();
    accumulator(extensionContext).recordLoss(loss);
    var failed = extensionContext.getExecutionException().isPresent();
    var displayName = extensionContext.getDisplayName();
    // The verdict has to be known BEFORE anything is written: an approval rejection fails the test
    // too, and a run that ends red must not advance the last-green artifact (see #advanceable).
    var rejection =
        failed
            ? Optional.<AssertionError>empty()
            : approvalRejection(extensionContext, displayName, trace, loss);

    // Output before the report so the failure report can speak in terms of the structural delta the
    // write computed — and so the report is the last (most visible) block in the console.
    Optional<ScenarioDelta> delta = Optional.empty();
    if (outputEnabled(extensionContext)) {
      delta =
          writeOutputAndAccumulate(
              extensionContext, displayName, trace, failed || rejection.isPresent());
    }
    handleAfterTest(displayName, failed, trace, delta, System.out);
    if (rejection.isPresent()) {
      throw rejection.get();
    }
  }

  /**
   * Runs approval verification and hands back the failure it would raise, instead of raising it.
   *
   * <p>INTENT: The last-green artifact must advance only on a run that is green <em>in full</em>,
   * and "in full" includes the approval verdict — a rejected structure that advanced the baseline
   * poisoned it, and the next (reverted, correct) run then reported a removal that never happened
   * (2026-09-08 agent evaluation). Approval therefore runs first and its verdict is carried to the
   * write; the error is thrown afterwards, so the test still fails exactly as it did, with the same
   * message, after the artifacts a reviewer needs are on disk.
   *
   * <p><b>@sideEffects</b> Writes the {@code *.received.nt} / {@code *.incomplete.nt} file, and
   * prints the lossy-pass note. Deleting a stale received file on a clean pass happens here too.
   */
  private static Optional<AssertionError> approvalRejection(
      ExtensionContext extensionContext, String displayName, TraceTree trace, TraceLoss loss) {
    try {
      verifyApprovalIfEnabled(extensionContext, displayName, trace, loss);
      return Optional.empty();
    } catch (AssertionError e) {
      return Optional.of(e);
    }
  }

  /**
   * Approval mode ({@code narrativetrace.approval=true}): verifies the scenario's structure against
   * its committed {@code *.approved.nt} baseline and fails the test on any unapproved change. Runs
   * only for tests that passed — a red test already has the developer's attention, and its
   * mid-flight structure must not churn the received files. {@code narrativetrace.approvedDir}
   * locates the baselines (default {@code src/test/narratives}, resolved against the test working
   * directory).
   */
  private static void verifyApprovalIfEnabled(
      ExtensionContext extensionContext, String displayName, TraceTree trace, TraceLoss loss) {
    if (trace.isEmpty()
        || !"true"
            .equalsIgnoreCase(configParam(extensionContext, "narrativetrace.approval", "false"))) {
      return;
    }
    var approvedDir =
        Path.of(configParam(extensionContext, "narrativetrace.approvedDir", "src/test/narratives"));
    var identity = artifactIdentity(extensionContext);
    var approvedFile = NarrativeApproval.approvedFile(approvedDir, identity);
    try {
      // The baseline is a structural artifact, so it is titled the value-free way the generated
      // one is — an argument interpolated into a display name reaches neither.
      var note =
          NarrativeApproval.verify(
              trace, identity.structuralScenario(displayName), approvedFile, loss);
      if (!note.isEmpty()) {
        System.out.println("  Approval: " + note);
      }
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(
          "Narrative approval could not access the baseline " + approvedFile, e);
    }
  }

  private Optional<ScenarioDelta> writeOutputAndAccumulate(
      ExtensionContext extensionContext, String displayName, TraceTree trace, boolean failed) {
    var outputDir =
        Path.of(configParam(extensionContext, "narrativetrace.outputDir", "build/narrativetrace"));
    var format = configParam(extensionContext, "narrativetrace.format", "markdown");
    var identity = artifactIdentity(extensionContext);
    Optional<ScenarioDelta> delta = Optional.empty();
    try {
      NarrativeRenderer mermaid = new MermaidSequenceDiagramRenderer()::render;
      NarrativeRenderer plantuml = new PlantUmlSequenceDiagramRenderer()::render;
      delta =
          TraceTestSupport.writeTraceFile(
              identity,
              displayName,
              trace,
              failed,
              outputDir,
              System.out,
              format,
              mermaid,
              plantuml,
              !"true"
                  .equalsIgnoreCase(
                      configParam(extensionContext, "narrativetrace.unfolded", "false")));
      delta.ifPresent(d -> accumulatedDeltas(extensionContext).add(d));
      writeOptionalEntryArtifacts(extensionContext, identity, trace, outputDir);
    } catch (IOException e) {
      System.err.println("Failed to write trace file: " + e.getMessage());
    }
    accumulateScenario(extensionContext, identity, displayName, trace, outputDir);
    return delta;
  }

  /**
   * Which artifact this invocation owns.
   *
   * <p>A {@code @ParameterizedTest} or {@code @RepeatedTest} method runs more than once, and every
   * run used to write the same files, so only the last one survived. The engine's own invocation
   * number is what separates them — deterministic for a given argument source, and therefore the
   * only part of the name an approval baseline can safely be pinned to.
   */
  private static ArtifactIdentity artifactIdentity(ExtensionContext extensionContext) {
    var testClassName = extensionContext.getRequiredTestClass().getName();
    var testMethodName = extensionContext.getRequiredTestMethod().getName();
    var index = invocationIndex(extensionContext.getUniqueId());
    return index == 0
        ? ArtifactIdentity.ofMethod(testClassName, testMethodName)
        : ArtifactIdentity.ofInvocation(
            testClassName, testMethodName, index, extensionContext.getDisplayName());
  }

  /**
   * The 1-based invocation number carried by a test-template unique id, or {@code 0} for an
   * ordinary test method that runs exactly once.
   *
   * <p>The unique id is the engine's own record of which invocation this is — JUnit exposes no
   * accessor for it on {@code ExtensionContext} — and it is the last such segment that names this
   * invocation, since a nested template would contribute an outer one first. The digit count is
   * bounded so a hand-built id can never overflow the parse.
   */
  static int invocationIndex(String uniqueId) {
    var matcher = INVOCATION_SEGMENT.matcher(uniqueId);
    var index = 0;
    while (matcher.find()) {
      index = Integer.parseInt(matcher.group(1));
    }
    return index;
  }

  /** Records the scenario's trace for the suite report and its files for the run manifest. */
  private static void accumulateScenario(
      ExtensionContext extensionContext,
      ArtifactIdentity identity,
      String displayName,
      TraceTree trace,
      Path outputDir) {
    if (trace.isEmpty()) {
      return;
    }
    var scenario = ScenarioFramer.humanize(displayName);
    var classStore = classContext(extensionContext).getStore(NAMESPACE);
    @SuppressWarnings("unchecked")
    var accumulated =
        (Map<String, TraceTree>)
            classStore.getOrComputeIfAbsent(
                TRACES_KEY, k -> new LinkedHashMap<String, TraceTree>(), Map.class);
    accumulated.put(scenario, trace);
    accumulator(extensionContext)
        .recordArtifacts(ScenarioManifest.entryFor(outputDir, identity, scenario));
  }

  /**
   * The class-level context a per-test accumulation belongs in — the store {@link #afterAll} later
   * reads.
   *
   * <p><b>@edgeCase</b> Not simply {@code getParent()}: an invocation of a test template has the
   * template's own container as its parent, so a parameterized test's traces and deltas landed in a
   * store {@code afterAll} never looks at. A class holding nothing but parameterized tests
   * therefore contributed <em>nothing</em> to the suite — no clarity rows, no delta line, no
   * manifest — because the write path and the read path disagreed about which context is "the
   * class". Walking up until the context has no test method is that agreement, in one place.
   */
  private static ExtensionContext classContext(ExtensionContext extensionContext) {
    var current = extensionContext;
    while (current.getTestMethod().isPresent() && current.getParent().isPresent()) {
      current = current.getParent().orElseThrow();
    }
    return current;
  }

  /** The opt-in machine artifacts beside each trace: canonical and structural entry arrays. */
  private static void writeOptionalEntryArtifacts(
      ExtensionContext extensionContext, ArtifactIdentity identity, TraceTree trace, Path outputDir)
      throws IOException {
    if ("true"
        .equalsIgnoreCase(configParam(extensionContext, "narrativetrace.canonicalJson", "false"))) {
      TraceTestSupport.writeCanonicalTraceFile(identity, trace, outputDir);
    }
    if ("true"
        .equalsIgnoreCase(
            configParam(extensionContext, "narrativetrace.structuralJson", "false"))) {
      TraceTestSupport.writeStructuralTraceFile(identity, trace, outputDir);
    }
  }

  /** The class-level list collecting each test's structural delta for the suite footer line. */
  private static List<ScenarioDelta> accumulatedDeltas(ExtensionContext extensionContext) {
    var classStore = classContext(extensionContext).getStore(NAMESPACE);
    @SuppressWarnings("unchecked")
    var deltas =
        (List<ScenarioDelta>)
            classStore.getOrComputeIfAbsent(
                DELTAS_KEY, k -> new ArrayList<ScenarioDelta>(), List.class);
    return deltas;
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) {
    if (!outputEnabled(extensionContext)) {
      return;
    }
    @SuppressWarnings("unchecked")
    var accumulated =
        (Map<String, TraceTree>) extensionContext.getStore(NAMESPACE).get(TRACES_KEY, Map.class);
    if (accumulated == null || accumulated.isEmpty()) {
      return;
    }
    @SuppressWarnings("unchecked")
    var deltas =
        (List<ScenarioDelta>) extensionContext.getStore(NAMESPACE).get(DELTAS_KEY, List.class);
    var outputDir =
        Path.of(configParam(extensionContext, "narrativetrace.outputDir", "build/narrativetrace"));
    var accumulator = accumulator(extensionContext);
    accumulator.contribute(
        accumulated,
        deltas == null ? List.of() : deltas,
        outputDir,
        glossaryStep(extensionContext, outputDir),
        projectVocabulary(glossaryDir(extensionContext)));
  }

  /**
   * The directory holding the repository's committed glossary — both the harvest's target and the
   * vocabulary's source, so the two can never disagree about where the glossary lives.
   */
  private static Path glossaryDir(ExtensionContext context) {
    return Path.of(
        configParam(context, "narrativetrace.glossaryDir", System.getProperty("user.dir")));
  }

  /**
   * Reads the repository's committed glossary as the vocabulary clarity scores with.
   *
   * <p>Unlike harvesting, this is on whenever a {@code glossary.json} exists: reading a committed
   * file changes nothing on disk, and a project that curates its ubiquitous language should not
   * have to opt in to being scored in it. Only the committed file counts — nothing this run
   * harvests feeds back into its own scores.
   *
   * <p><b>@edgeCase</b> A glossary that cannot be read degrades to the built-in dictionaries with a
   * warning: a reporting artifact must never fail the suite that produced it.
   */
  @SuppressWarnings("PMD.AvoidCatchingGenericException") // a bad glossary must not fail the suite
  static DomainVocabulary projectVocabulary(Path glossaryDir) {
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

  /**
   * Builds the suite's glossary harvest from configuration.
   *
   * <p>Harvesting is off unless {@code narrativetrace.glossary=true}: it rewrites {@code
   * glossary.json} / {@code glossary.md} outside the build directory, which no test run may do
   * unasked. {@code narrativetrace.glossaryDir} names that directory — the Gradle plugin sets it to
   * the repository root, since a test task's working directory is the subproject, and the glossary
   * is one file per repository (ADR-012), not one per module.
   */
  private static GlossaryHarvestStep glossaryStep(ExtensionContext context, Path outputDir) {
    if (!"true".equalsIgnoreCase(configParam(context, "narrativetrace.glossary", "false"))) {
      return GlossaryHarvestStep.disabled();
    }
    return GlossaryHarvestStep.into(
        glossaryDir(context),
        outputDir.resolve("glossary-usage.json"),
        ClassPackageIndex.fromClasspath(System.getProperty("java.class.path", "")),
        Clock.systemUTC());
  }

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext) {
    return parameterContext.getParameter().getType() == NarrativeContext.class;
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext) {
    return extensionContext.getStore(NAMESPACE).get(CONTEXT_KEY, NarrativeContext.class);
  }

  /**
   * Prints template warnings and, on failure, the failure report. When the write path produced a
   * structural delta, the report localizes change against last green instead of dumping the trace
   * (see {@code TraceTestSupport.buildFailureReport(String, String, ScenarioDelta)}).
   */
  void handleAfterTest(
      String displayName,
      boolean failed,
      TraceTree trace,
      Optional<ScenarioDelta> delta,
      java.io.PrintStream out) {
    if (!trace.isEmpty()) {
      var templateWarnings = TemplateWarningCollector.collect(trace);
      var formatted = TemplateWarningCollector.format(templateWarnings);
      if (!formatted.isEmpty()) {
        out.print(formatted);
      }
    }
    if (!failed || trace.isEmpty()) {
      return;
    }
    var scenario = ScenarioFramer.frame(displayName);
    var rendered = new IndentedTextRenderer().render(trace);
    out.println(
        delta
            .map(d -> TraceTestSupport.buildFailureReport(scenario, rendered, d))
            .orElseGet(() -> TraceTestSupport.buildFailureReport(scenario, rendered)));
  }

  /**
   * Analyzes clarity for an ordered list of (scenarioName, trace) pairs, scoring in the project's
   * own vocabulary.
   *
   * <p>Preserves duplicate scenario names as separate entries, which is required for correct
   * suite-level aggregation when multiple test classes share the same display name.
   *
   * @param traces ordered (scenarioName, trace) pairs
   * @param vocabulary the repository's committed vocabulary, or {@link DomainVocabulary#empty()}
   */
  List<Map.Entry<String, ClarityResult>> analyzeClarityResultsList(
      List<Map.Entry<String, TraceTree>> traces, DomainVocabulary vocabulary) {
    var analyzer = new ClarityAnalyzer(vocabulary);
    var results = new ArrayList<Map.Entry<String, ClarityResult>>();
    for (var entry : traces) {
      results.add(
          new AbstractMap.SimpleImmutableEntry<>(
              entry.getKey(), analyzer.analyze(entry.getValue())));
    }
    return results;
  }

  private static String configParam(ExtensionContext context, String key, String defaultValue) {
    return context.getConfigurationParameter(key).orElse(defaultValue);
  }

  /**
   * Whether this test writes its trace artifacts to disk — on by default (2026-09-11 ruling):
   * capture was always on, only file-writing was opt-in, and adopters wrapping services saw no
   * artifacts and no payoff. {@code narrativetrace.output=false} is the one-line opt-out; any other
   * value, including the historical {@code true}, changes nothing.
   */
  private static boolean outputEnabled(ExtensionContext extensionContext) {
    return !"false"
        .equalsIgnoreCase(configParam(extensionContext, "narrativetrace.output", "true"));
  }
}
