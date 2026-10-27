/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.output.NarrativeApproval;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * The opt-in deep mode in the JUnit 5 integration: off unless asked for (the {@code @DeepTrace}
 * annotation, or the {@code narrativetrace.depth} setting for a whole run) AND the agent was
 * started with {@code depth=internal}; applies to the test trace and to the approval baseline.
 */
class DeepDepthTest {

  private static final String DEEP_TRACE = "traces/DeepFixture/deep.md";
  private static final String PLAIN_TRACE = "traces/DeepFixture/plain.md";
  private static final String DEEP_NT = "structural/DeepFixture/deep.nt";

  @BeforeEach
  void armTheFakeAgent() {
    FakeAgentRuntime.reset();
    FakeAgentRuntime.armed = true;
    AgentContextBridge.useRuntimeClass("ai.narrativetrace.junit5.FakeAgentRuntime");
  }

  @AfterEach
  void restore() {
    AgentContextBridge.useRuntimeClass(null);
    FakeAgentRuntime.reset();
  }

  @Test
  void anAnnotatedTestIsRecordedDeepAndAnUnannotatedOneIsNot(@TempDir Path out) throws Exception {
    run(out, null, null, "deep", "plain");

    var deep = Files.readString(out.resolve(DEEP_TRACE));
    assertThat(deep).contains("Depth: internal").contains("· **Pricing.withTax**");
    assertThat(Files.readString(out.resolve(DEEP_NT))).contains("depth: internal\n");
    var plain = Files.readString(out.resolve(PLAIN_TRACE));
    assertThat(plain).doesNotContain("Depth: internal");
    assertThat(Files.readString(out.resolve("structural/DeepFixture/plain.nt")))
        .doesNotContain("depth:");
    assertThat(FakeAgentRuntime.begun.get())
        .as("one scope, for the annotated test only")
        .isEqualTo(1);
    assertThat(FakeAgentRuntime.open).as("the scope is closed after the test").isFalse();
  }

  @Test
  void theRunWideSettingDeepensEveryTest(@TempDir Path out) throws Exception {
    run(out, null, "internal", "deep", "plain");

    assertThat(Files.readString(out.resolve(PLAIN_TRACE))).contains("Depth: internal");
    assertThat(FakeAgentRuntime.begun.get()).isEqualTo(2);
    assertThat(FakeAgentRuntime.open).isFalse();
  }

  @Test
  void depthWithoutTheAgentOptionStaysPublicAndSaysSoOnStderr(@TempDir Path out) throws Exception {
    FakeAgentRuntime.armed = false;

    var stderr = capturingStderr(() -> run(out, null, null, "deep"));

    assertThat(Files.readString(out.resolve(DEEP_TRACE))).doesNotContain("Depth: internal");
    assertThat(Files.readString(out.resolve(DEEP_NT))).doesNotContain("depth:");
    assertThat(FakeAgentRuntime.begun.get()).isZero();
    assertThat(stderr).contains("depth=internal").contains("PUBLIC");
  }

  @Test
  void aClassLevelAnnotationDeepensItsTests(@TempDir Path out) {
    var request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(ClassDeepFixture.class));
    System.setProperty("narrativetrace.outputDir", out.toString());
    try {
      LauncherFactory.create().execute(request.build());
    } finally {
      System.clearProperty("narrativetrace.outputDir");
    }

    assertThat(FakeAgentRuntime.begun.get()).isEqualTo(1);
  }

  @Test
  void theDeepSpansTheCapDroppedAreNamedInTheTraceHeader(@TempDir Path out) throws Exception {
    FakeAgentRuntime.dropped = 12;

    run(out, null, null, "deep");

    assertThat(Files.readString(out.resolve(DEEP_TRACE))).contains("12 deep spans were dropped");
  }

  @Test
  void aDeepBaselineIsNotSilentlyPassedByAShallowRun(@TempDir Path out, @TempDir Path approved)
      throws Exception {
    run(out, approved, null, "deep"); // no baseline yet: fails, writes received
    NarrativeApproval.promoteReceived(approved);
    assertThat(Files.readString(approved.resolve("DeepFixture/deep.approved.nt")))
        .contains("depth: internal");
    assertThat(run(out, approved, null, "deep").getTestsFailedCount()).as("deep vs deep").isZero();

    FakeAgentRuntime.armed = false; // the same test, run without the agent's deep option
    var shallow = run(out, approved, null, "deep");

    assertThat(shallow.getTestsFailedCount()).isEqualTo(1);
    assertThat(shallow.getFailures().get(0).getException())
        .hasMessageContaining("Depth mismatch")
        .hasMessageContaining("baseline was recorded at depth internal")
        .hasMessageContaining("this run at depth public");
  }

  private TestExecutionSummary run(
      Path out, Path approvedDir, String runWideDepth, String... tests) {
    var approval = approvedDir != null;
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", out.toString());
    if (runWideDepth != null) {
      System.setProperty("narrativetrace.depth", runWideDepth);
    }
    if (approval) {
      System.setProperty("narrativetrace.approval", "true");
      System.setProperty("narrativetrace.approvedDir", approvedDir.toString());
    }
    try {
      var request = LauncherDiscoveryRequestBuilder.request();
      for (var test : tests) {
        request.selectors(
            DiscoverySelectors.selectMethod(
                DeepFixture.class, test, "ai.narrativetrace.core.context.NarrativeContext"));
      }
      var listener = new SummaryGeneratingListener();
      LauncherFactory.create().execute(request.build(), listener);
      return listener.getSummary();
    } finally {
      for (var key :
          new String[] {
            "narrativetrace.output",
            "narrativetrace.outputDir",
            "narrativetrace.depth",
            "narrativetrace.approval",
            "narrativetrace.approvedDir"
          }) {
        System.clearProperty(key);
      }
    }
  }

  private static String capturingStderr(ThrowingRunnable body) throws Exception {
    var captured = new ByteArrayOutputStream();
    var original = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      body.run();
    } finally {
      System.setErr(original);
    }
    return captured.toString(StandardCharsets.UTF_8);
  }

  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
