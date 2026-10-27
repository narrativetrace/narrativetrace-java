/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.output.NarrativeApproval;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The opt-in deep mode in the TestNG integration: off unless asked for ({@code @DeepTrace}, or
 * {@code narrativetrace.depth} for a whole run) AND the agent was started with {@code
 * depth=internal}; applies to the test trace and to the approval baseline.
 */
class DeepDepthTest {

  private static final String DEEP_TRACE = "traces/DeepFixture/deep.md";
  private static final String PLAIN_TRACE = "traces/DeepFixture/plain.md";

  private AgentContextBridge realBridge;

  @BeforeEach
  void armTheFakeAgent() {
    FakeAgentRuntime.reset();
    FakeAgentRuntime.armed = true;
    realBridge =
        SharedPipeline.shared()
            .useAgentBridge(
                AgentContextBridge.attach(
                    SharedPipeline.shared().context(),
                    "ai.narrativetrace.testng.FakeAgentRuntime"));
    NarrativeTraceListener.forgetDeepRefusalNote();
  }

  @AfterEach
  void restore() {
    SharedPipeline.shared().useAgentBridge(realBridge);
    FakeAgentRuntime.reset();
  }

  @Test
  void anAnnotatedTestIsRecordedDeepAndAnUnannotatedOneIsNot(@TempDir Path out) throws Exception {
    TestNgRuns.run(out, DeepFixture.class);

    var deep = Files.readString(out.resolve(DEEP_TRACE));
    assertThat(deep).contains("Depth: internal").contains("· **Pricing.withTax**");
    assertThat(Files.readString(out.resolve("structural/DeepFixture/deep.nt")))
        .contains("depth: internal\n");
    assertThat(Files.readString(out.resolve(PLAIN_TRACE))).doesNotContain("Depth: internal");
    assertThat(Files.readString(out.resolve("structural/DeepFixture/plain.nt")))
        .doesNotContain("depth:");
    assertThat(FakeAgentRuntime.begun.get())
        .as("one scope, for the annotated test only")
        .isEqualTo(1);
    assertThat(FakeAgentRuntime.open).as("the scope is closed after the test").isFalse();
  }

  @Test
  void theRunWideSettingDeepensEveryTest(@TempDir Path out) throws Exception {
    TestNgRuns.run(out, Map.of("narrativetrace.depth", "internal"), t -> {}, DeepFixture.class);

    assertThat(Files.readString(out.resolve(PLAIN_TRACE))).contains("Depth: internal");
    assertThat(FakeAgentRuntime.begun.get()).isEqualTo(2);
    assertThat(FakeAgentRuntime.open).isFalse();
  }

  @Test
  void depthWithoutTheAgentOptionStaysPublicAndSaysSoOnStderr(@TempDir Path out) throws Exception {
    FakeAgentRuntime.armed = false;

    var run = TestNgRuns.run(out, DeepFixture.class);

    assertThat(Files.readString(out.resolve(DEEP_TRACE))).doesNotContain("Depth: internal");
    assertThat(Files.readString(out.resolve("structural/DeepFixture/deep.nt")))
        .doesNotContain("depth:");
    assertThat(FakeAgentRuntime.begun.get()).isZero();
    assertThat(run.err()).contains("depth=internal").contains("PUBLIC");
  }

  @Test
  void aClassLevelAnnotationDeepensItsTestsIncludingTheirSetUp(@TempDir Path out) throws Exception {
    TestNgRuns.run(out, ClassDeepFixture.class);

    assertThat(FakeAgentRuntime.begun.get()).isEqualTo(1);
    assertThat(Files.readString(out.resolve("traces/ClassDeepFixture/any_test.md")))
        .contains("Depth: internal");
  }

  @Test
  void theDeepSpansTheCapDroppedAreNamedInTheTraceHeader(@TempDir Path out) throws Exception {
    FakeAgentRuntime.dropped = 12;

    TestNgRuns.run(out, DeepFixture.class);

    assertThat(Files.readString(out.resolve(DEEP_TRACE))).contains("12 deep spans were dropped");
  }

  @Test
  void aDeepBaselineIsNotSilentlyPassedByAShallowRun(@TempDir Path out, @TempDir Path approved)
      throws Exception {
    var approving =
        Map.of(
            "narrativetrace.approval", "true", "narrativetrace.approvedDir", approved.toString());
    TestNgRuns.run(out, approving, t -> {}, DeepFixture.class); // no baseline yet: fails
    NarrativeApproval.promoteReceived(approved);
    assertThat(Files.readString(approved.resolve("DeepFixture/deep.approved.nt")))
        .contains("depth: internal");
    assertThat(TestNgRuns.run(out, approving, t -> {}, DeepFixture.class).failures())
        .as("deep vs deep")
        .isEmpty();

    FakeAgentRuntime.armed = false; // the same test, run without the agent's deep option
    var shallow = TestNgRuns.run(out, approving, t -> {}, DeepFixture.class);

    assertThat(shallow.failureMessages())
        .singleElement()
        .asString()
        .contains("Depth mismatch")
        .contains("baseline was recorded at depth internal")
        .contains("this run at depth public");
  }
}
