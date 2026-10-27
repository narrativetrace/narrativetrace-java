/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

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
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * The opt-in deep mode in the JUnit 4 rules: {@code @DeepTrace} (or the run-wide {@code
 * narrativetrace.depth}) AND an agent started with {@code depth=internal}; applies to the trace
 * file and the approval baseline.
 */
class DeepDepthJunit4Test {

  @BeforeEach
  void armTheFakeAgent() {
    FakeAgentRuntime.reset();
    FakeAgentRuntime.armed = true;
    AgentContextBridge.useRuntimeClass("ai.narrativetrace.junit4.FakeAgentRuntime");
  }

  @AfterEach
  void restore() {
    AgentContextBridge.useRuntimeClass(null);
    FakeAgentRuntime.reset();
  }

  @Test
  void anAnnotatedTestIsDeepAndAnUnannotatedOneIsNot(@TempDir Path out) throws Exception {
    run(
        out,
        null,
        null,
        selectMethod(DeepJunit4Fixture.class, "deep"),
        selectMethod(DeepJunit4Fixture.class, "plain"));

    assertThat(Files.readString(out.resolve("traces/DeepJunit4Fixture/deep.md")))
        .contains("Depth: internal")
        .contains("· **Pricing.withTax**");
    assertThat(Files.readString(out.resolve("structural/DeepJunit4Fixture/deep.nt")))
        .contains("depth: internal\n");
    assertThat(Files.readString(out.resolve("traces/DeepJunit4Fixture/plain.md")))
        .doesNotContain("Depth: internal");
    assertThat(FakeAgentRuntime.begun.get()).isEqualTo(1);
    assertThat(FakeAgentRuntime.open).isFalse();
  }

  @Test
  void aClassLevelAnnotationDeepensTheLinkedRule(@TempDir Path out) throws Exception {
    FakeAgentRuntime.dropped = 5;

    run(out, null, null, selectClass(DeepLinkedJunit4Fixture.class));

    assertThat(Files.readString(out.resolve("traces/DeepLinkedJunit4Fixture/linked.md")))
        .contains("Depth: internal")
        .contains("5 deep spans were dropped");
    assertThat(FakeAgentRuntime.open).isFalse();
  }

  @Test
  void theRunWideSettingDeepensEveryTest(@TempDir Path out) throws Exception {
    run(out, null, "internal", selectMethod(DeepJunit4Fixture.class, "plain"));

    assertThat(Files.readString(out.resolve("traces/DeepJunit4Fixture/plain.md")))
        .contains("Depth: internal");
  }

  @Test
  void depthWithoutTheAgentOptionStaysPublicAndSaysSoOnStderr(@TempDir Path out) throws Exception {
    FakeAgentRuntime.armed = false;
    var captured = new ByteArrayOutputStream();
    var original = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      run(out, null, null, selectMethod(DeepJunit4Fixture.class, "deep"));
    } finally {
      System.setErr(original);
    }

    assertThat(Files.readString(out.resolve("traces/DeepJunit4Fixture/deep.md")))
        .doesNotContain("Depth: internal");
    assertThat(FakeAgentRuntime.begun.get()).isZero();
  }

  @Test
  void aDeepBaselineIsNotSilentlyPassedByAShallowRun(@TempDir Path out, @TempDir Path approved)
      throws Exception {
    var deep = selectMethod(DeepJunit4Fixture.class, "deep");
    run(out, approved, null, deep); // no baseline yet: fails, writes received
    NarrativeApproval.promoteReceived(approved);
    assertThat(run(out, approved, null, deep).getTestsFailedCount()).as("deep vs deep").isZero();

    FakeAgentRuntime.armed = false;
    var shallow = run(out, approved, null, deep);

    assertThat(shallow.getTestsFailedCount()).isEqualTo(1);
    assertThat(shallow.getFailures().get(0).getException())
        .hasMessageContaining("Depth mismatch")
        .hasMessageContaining("baseline was recorded at depth internal");
  }

  private static TestExecutionSummary run(
      Path out, Path approvedDir, String runWideDepth, DiscoverySelector... selectors) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", out.toString());
    if (runWideDepth != null) {
      System.setProperty("narrativetrace.depth", runWideDepth);
    }
    if (approvedDir != null) {
      System.setProperty("narrativetrace.approval", "true");
      System.setProperty("narrativetrace.approvedDir", approvedDir.toString());
    }
    try {
      var listener = new SummaryGeneratingListener();
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request().selectors(selectors).build(), listener);
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
}
