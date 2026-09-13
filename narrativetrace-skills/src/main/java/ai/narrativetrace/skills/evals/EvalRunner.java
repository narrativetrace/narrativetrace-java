/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tier B trial runner (skill-harness-design.md §4.3, §5.1). NEVER invoked by {@code ./gradlew
 * check} — Tier A (lints) and Tier A2 (oracle replay) ride {@code check} every commit; this runs
 * through the subscription CLIs, by hand or from the nightly job, never the metered API. Scaffolds
 * the case's fixture into a scratch directory (a fresh temp copy outside every repo tree), drives
 * the requested agent CLI against the prompt with the catalogue loaded, runs the case's grader, and
 * appends one row to {@code ledger/runs.jsonl}.
 *
 * <p>This class is the thin CLI entry point — argument parsing, the trial loop, temp-directory
 * lifecycle and stdout progress lines — deliberately excluded from the module's jacoco coverage
 * gate the same way {@code narrativetrace-cli}'s {@code Main} is. Every decision worth a unit test
 * (arg parsing, repo-root resolution, argv-safe agent commands, the per-trial scaffold/drive/grade/
 * ledger flow, the sporadic-lane precondition, the quota decision, the ledger row shape, the
 * platform command templates) lives in a plain, injectable class this one only calls: {@link
 * RepoRoot}, {@link AgentArgv}, {@link EvalTrial}, {@link SporadicPolicy}, {@link CaseFixture},
 * {@link QuotaMarkdown}, {@link TierPrecondition}, {@link Platform}, {@link RunLedgerRow}. Mirrors
 * the TypeScript reference's {@code evals/run.ts}, which carries no test of its own for the same
 * reason.
 */
public final class EvalRunner {

  private EvalRunner() {}

  public static void main(String[] args) throws IOException, InterruptedException {
    EvalRunnerArgs parsed = EvalRunnerArgs.parse(args);
    Path repoRoot = RepoRoot.locate();
    Path ledgerDir = repoRoot.resolve("narrativetrace-skills/ledger");
    Path runsPath = ledgerDir.resolve("runs.jsonl");
    Path quotaPath = ledgerDir.resolve("quota.md");

    Path caseDir =
        repoRoot
            .resolve("narrativetrace-skills/evals")
            .resolve(parsed.skill())
            .resolve(parsed.caseName());
    String prompt = readPrompt(caseDir);
    Path fixtureDir = repoRoot.resolve(CaseFixture.fixtureFor(caseDir));
    String agentCommand =
        parsed
            .agentCommandOverride()
            .orElse(parsed.platform().presetAgentCommand(parsed.model(), parsed.skill()));
    logWhenNoAgentCommand(agentCommand);

    EvalTrial trial = new EvalTrial(EvalTrial.REAL_PROCESS_RUNNER, Clock.systemUTC(), runsPath);
    List<QuotaSpendRow> sessionSpend = new ArrayList<>();
    for (int t = 1; t <= parsed.trials(); t++) {
      if (parsed.platform().isSporadic()) {
        SporadicPolicy.beforeTrial(
            parsed.platform(),
            repoRoot,
            TierPrecondition::runViaProcessBuilder,
            mergedQuotaLedger(quotaPath, sessionSpend),
            LocalDate.now());
      }
      boolean pass =
          runOneTrial(trial, parsed, repoRoot, fixtureDir, caseDir, prompt, agentCommand, t);
      System.out.println("trial " + t + "/" + parsed.trials() + ": " + (pass ? "pass" : "fail"));
      if (parsed.platform().isSporadic()) {
        sessionSpend.add(recordSporadicSpend(quotaPath, parsed));
      }
    }
  }

  private static boolean runOneTrial(
      EvalTrial trial,
      EvalRunnerArgs args,
      Path repoRoot,
      Path fixtureDir,
      Path caseDir,
      String prompt,
      String agentCommand,
      int trialNumber)
      throws IOException, InterruptedException {
    Path scratch = Files.createTempDirectory("nt-eval-");
    try {
      return trial.run(
          args, repoRoot, fixtureDir, caseDir, scratch, prompt, agentCommand, trialNumber);
    } finally {
      deleteRecursively(scratch);
    }
  }

  private static void logWhenNoAgentCommand(String agentCommand) {
    if (agentCommand == null) {
      System.out.println(
          "(no --agent-command given — skipping the agent step, grading the fixture as-is)");
    }
  }

  private static String readPrompt(Path caseDir) throws IOException {
    Path promptPath = caseDir.resolve("prompt.md");
    if (!Files.isRegularFile(promptPath)) {
      throw new IllegalArgumentException("No case found at " + caseDir);
    }
    return Files.readString(promptPath);
  }

  /**
   * The on-disk allowance table plus every spend row already on disk, plus this run's own so far.
   */
  private static QuotaLedger mergedQuotaLedger(Path quotaPath, List<QuotaSpendRow> sessionSpend)
      throws IOException {
    QuotaLedger onDisk = QuotaMarkdown.parse(Files.readString(quotaPath));
    List<QuotaSpendRow> spend = new ArrayList<>(onDisk.spend());
    spend.addAll(sessionSpend);
    return new QuotaLedger(onDisk.allowances(), spend);
  }

  private static QuotaSpendRow recordSporadicSpend(Path quotaPath, EvalRunnerArgs args)
      throws IOException {
    LocalDate now = LocalDate.now();
    QuotaSpendRow row =
        new QuotaSpendRow(
            now.toString(),
            args.platform(),
            args.skill(),
            args.caseName(),
            QuotaMarkdown.isoWeek(now));
    Files.writeString(
        quotaPath, QuotaMarkdown.appendedSpendRowLine(row), StandardOpenOption.APPEND);
    return row;
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var stream = Files.walk(root)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (IOException e) {
                  // Best-effort cleanup of a scratch temp directory — never fails the trial over
                  // it.
                }
              });
    }
  }
}
