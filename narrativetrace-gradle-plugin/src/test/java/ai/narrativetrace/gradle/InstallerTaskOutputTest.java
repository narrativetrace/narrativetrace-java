/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.init.Action;
import ai.narrativetrace.tooling.init.InitPlan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What an installer task prints, what it writes, and what it fails with. Everything but the logging
 * itself lives here, so the two task classes are a handful of lines each and a change to the
 * reporting is a unit test rather than a nested Gradle build.
 */
class InstallerTaskOutputTest {

  private static final String CARRIER = "ai.narrativetrace:narrativetrace-skills:0.2.4";

  private static final Path AGENTS_MD = Path.of("AGENTS.md");

  private static InitPlan plan(boolean dryRun, Action... actions) {
    return new InitPlan(CARRIER, dryRun, List.of(actions));
  }

  private static Action create() {
    return new Action.CreateFile(AGENTS_MD, "# Agents\n");
  }

  private static Action refuse() {
    return new Action.Refuse(
        Path.of(".agents/skills/doctor"), "not ours — re-run with --force to overwrite it");
  }

  @Test
  void aPreviewShowsTheSummaryAndTheDiffAndWritesNothing(@TempDir Path project) {
    var outcome =
        InstallerTaskOutput.run(plan(true, create()), project, "narrativetraceInit", false);

    assertThat(outcome.text())
        .contains(CARRIER)
        .contains("1 action(s)")
        .contains("+++ b/AGENTS.md");
    assertThat(outcome.refusal()).isNull();
    assertThat(project.resolve("AGENTS.md")).doesNotExist();
  }

  @Test
  void aPreviewNeverFailsEvenWhenItShowsARefusal(@TempDir Path project) {
    var outcome =
        InstallerTaskOutput.run(plan(true, refuse()), project, "narrativetraceInit", false);

    assertThat(outcome.refusal()).isNull();
    assertThat(outcome.text()).contains("--force");
  }

  @Test
  void aPreviewAsJsonIsTheEnvelopeAndNothingElse(@TempDir Path project) {
    var outcome =
        InstallerTaskOutput.run(plan(true, create()), project, "narrativetraceInit", true);

    assertThat(outcome.text())
        .startsWith("{")
        .contains("\"status\": \"planned\"")
        .doesNotContain("+++");
  }

  @Test
  void anAppliedPlanWritesAndSaysSo(@TempDir Path project) throws IOException {
    var outcome =
        InstallerTaskOutput.run(plan(false, create()), project, "narrativetraceInit", false);

    assertThat(Files.readString(project.resolve("AGENTS.md"))).isEqualTo("# Agents\n");
    assertThat(outcome.text()).contains("1 applied, 0 refused");
    assertThat(outcome.refusal()).isNull();
  }

  @Test
  void anAppliedPlanAsJsonIsTheSameEnvelopeTheDoctorPrints(@TempDir Path project) {
    var outcome =
        InstallerTaskOutput.run(plan(false, create()), project, "narrativetraceInit", true);

    assertThat(outcome.text()).contains("\"status\": \"applied\"").contains("\"exitCode\": 0");
  }

  /**
   * A refusal fails the task: a person is at the keyboard, and a half-applied install reported only
   * in a log line is worse than a red build naming the flag that would allow it.
   */
  @Test
  void arefusalFailsTheTaskWithTheFlagThatWouldAllowIt(@TempDir Path project) {
    var outcome =
        InstallerTaskOutput.run(
            plan(false, create(), refuse()), project, "narrativetraceInit", false);

    assertThat(outcome.refusal())
        .contains("narrativetraceInit")
        .contains(".agents/skills/doctor")
        .contains("--force");
    assertThat(outcome.text()).contains("1 applied, 1 refused");
  }

  @Test
  void theOtherActionsAreStillAppliedWhenOneIsRefused(@TempDir Path project) {
    InstallerTaskOutput.run(plan(false, create(), refuse()), project, "narrativetraceInit", false);

    assertThat(project.resolve("AGENTS.md")).exists();
  }

  @Test
  void anEmptyPlanSaysThereIsNothingToDo(@TempDir Path project) {
    var outcome = InstallerTaskOutput.run(plan(false), project, "narrativetraceUninstall", false);

    assertThat(outcome.text()).contains("0 applied, 0 refused");
    assertThat(outcome.refusal()).isNull();
  }
}
