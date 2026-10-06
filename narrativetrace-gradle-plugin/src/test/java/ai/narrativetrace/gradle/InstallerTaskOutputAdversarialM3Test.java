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
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Feature-combination coverage for {@link InstallerTaskOutput}: a preview, JSON output, and a
 * refusal, all at once — the same combination {@code --dry-run --json} exercises on the CLI, here
 * at the Gradle task's own output layer, which {@code InstallerTaskOutputTest} exercises for JSON
 * and for a refusal separately but never together.
 */
class InstallerTaskOutputAdversarialM3Test {

  private static final String CARRIER = "ai.narrativetrace:narrativetrace-skills:0.2.4";

  @Test
  void aDryRunAsJsonStillShowsARefusalWithoutFailingTheTask(@TempDir Path project) {
    InitPlan plan =
        new InitPlan(
            CARRIER,
            true,
            List.of(
                new Action.CreateFile(Path.of("AGENTS.md"), "# Agents\n"),
                new Action.Refuse(Path.of(".agents/skills/doctor"), "not ours")));

    var outcome = InstallerTaskOutput.run(plan, project, "narrativetraceInit", true);

    assertThat(outcome.refusal()).isNull();
    assertThat(outcome.text())
        .startsWith("{")
        .contains("\"status\": \"refused\"")
        .contains("\"status\": \"planned\"");
    assertThat(project.resolve("AGENTS.md")).doesNotExist();
  }
}
