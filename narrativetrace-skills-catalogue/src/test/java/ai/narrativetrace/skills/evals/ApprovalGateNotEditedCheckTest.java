/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-108: {@code grade_the_approval_gate.py}'s {@code require_the_project_was_not_edited} walked the
 * fixture it ships and failed every untouched trial with "reporting a problem deleted README.md",
 * because {@link EvalTrial#run} never scaffolds the fixture's own root README (or a root {@code
 * build}/{@code .gradle}/{@code .git}) into the scaffolded copy in the first place — the grader
 * compared against a file the trial was never given.
 *
 * <p>Exercises the grader's {@code --check-only} mode (no transcript or draft needed) against the
 * REAL {@link EvalTrial} scaffold of the real {@code feedback-false-positive} fixture, so the
 * grader's skip list and {@code EvalTrial}'s own cannot drift apart silently again.
 */
class ApprovalGateNotEditedCheckTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));
  private static final Path FIXTURE =
      REPO_ROOT.resolve("narrativetrace-skills-catalogue/evals/fixtures/feedback-false-positive");
  private static final Path GRADER_SCRIPT =
      REPO_ROOT.resolve(
          "narrativetrace-skills-catalogue/evals/narrativetrace-feedback/grade_the_approval_gate.py");

  private static final EvalRunnerArgs ARGS =
      new EvalRunnerArgs(
          "narrativetrace-feedback", "approval-gate-approved", Platform.CODEX, "mini", null, 1);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC);

  /** A case whose own grader runs only the check this bug is about, against the real script. */
  private static Path caseDirWithCheckOnlyGrader(Path tempDir) throws IOException {
    Path caseDir = Files.createDirectories(tempDir.resolve("case"));
    Path graders = Files.createDirectories(caseDir.resolve("graders"));
    Files.writeString(
        graders.resolve("verify.sh"),
        "#!/bin/sh\nset -e\npython3 \"" + GRADER_SCRIPT + "\" --check-only\n");
    return caseDir;
  }

  @Test
  void theScaffoldedUntouchedCopyPassesTheNotEditedCheck(@TempDir Path tempDir) throws Exception {
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path emptyRepoRoot = Files.createDirectory(tempDir.resolve("empty-repo"));
    Path caseDir = caseDirWithCheckOnlyGrader(tempDir);
    var trial =
        new EvalTrial(EvalTrial.REAL_PROCESS_RUNNER, FIXED_CLOCK, tempDir.resolve("runs.jsonl"));

    boolean pass =
        trial.run(ARGS, emptyRepoRoot, FIXTURE, caseDir, scratch, AgentTurns.graded("prompt"), 1);

    assertThat(pass).isTrue();
  }

  /**
   * The check must still catch a REAL edit to a shipped file — proof that B-108's fix widened the
   * grader's exemption to exactly the scaffold's own skip list, not into a no-op.
   */
  @Test
  void deletingAShippedFileStillFailsTheCheck(@TempDir Path tempDir) throws Exception {
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path emptyRepoRoot = Files.createDirectory(tempDir.resolve("empty-repo"));
    Path caseDir = caseDirWithCheckOnlyGrader(tempDir);
    var trial =
        new EvalTrial(EvalTrial.REAL_PROCESS_RUNNER, FIXED_CLOCK, tempDir.resolve("runs.jsonl"));
    AgentTurns deletesAShippedFile =
        new AgentTurns("rm {prompt}", null, List.of("build.gradle.kts"));

    boolean pass =
        trial.run(ARGS, emptyRepoRoot, FIXTURE, caseDir, scratch, deletesAShippedFile, 1);

    assertThat(pass).isFalse();
  }
}
