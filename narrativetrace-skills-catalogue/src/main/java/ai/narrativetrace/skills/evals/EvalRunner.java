/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
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
import java.util.UUID;

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
 * {@link CaseTurns}, {@link AgentTurns}, {@link TrialEnvironment}, {@link QuotaMarkdown}, {@link
 * TierPrecondition}, {@link Platform}, {@link RunLedgerRow}. Mirrors the TypeScript reference's
 * {@code evals/run.ts}, which carries no test of its own for the same reason.
 */
public final class EvalRunner {

  private EvalRunner() {}

  /**
   * What every trial of the requested case needs and what no trial of it changes — resolved once,
   * so the loop below reads as the loop it is.
   *
   * @param preStep the registry that delivers this case's skill pages, or {@code null} when the
   *     harness copies its own rendered pages in (every case but a registry one)
   */
  private record TrialPlan(
      EvalRunnerArgs args,
      Path repoRoot,
      Path caseDir,
      Path fixtureDir,
      String prompt,
      List<String> scriptedReplies,
      Path runsPath,
      RegistryPreStep preStep) {}

  /**
   * What drives one trial of this case: the platform's templates, the case's turns, and a session
   * id of this TRIAL's own — so three trials of a multi-turn case are three conversations rather
   * than one long one.
   */
  private static AgentTurns turnsFor(TrialPlan plan) {
    return AgentTurns.of(
        plan.args().platform(),
        plan.args().model(),
        plan.args().skill(),
        plan.args().agentCommandOverride().orElse(null),
        plan.prompt(),
        plan.scriptedReplies(),
        UUID.randomUUID().toString());
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    EvalRunnerArgs parsed = EvalRunnerArgs.parse(args);
    Path repoRoot = RepoRoot.locate();
    Path ledgerDir = repoRoot.resolve("narrativetrace-skills-catalogue/ledger");
    Path quotaPath = ledgerDir.resolve("quota.md");
    TrialPlan plan = planFor(parsed, repoRoot, ledgerDir.resolve("runs.jsonl"));
    logTheConversationsShape(plan);

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
      boolean pass = runOneTrial(plan, t);
      System.out.println("trial " + t + "/" + parsed.trials() + ": " + (pass ? "pass" : "fail"));
      if (parsed.platform().isSporadic()) {
        sessionSpend.add(recordSporadicSpend(quotaPath, parsed));
      }
    }
  }

  /** Reads the case directory: its prompt, its fixture, its registry, and the agent to drive. */
  private static TrialPlan planFor(EvalRunnerArgs parsed, Path repoRoot, Path runsPath)
      throws IOException {
    Path caseDir =
        repoRoot
            .resolve("narrativetrace-skills-catalogue/evals")
            .resolve(parsed.skill())
            .resolve(parsed.caseName());
    return new TrialPlan(
        parsed,
        repoRoot,
        caseDir,
        repoRoot.resolve(CaseFixture.fixtureFor(caseDir)),
        readPrompt(caseDir),
        CaseTurns.scriptedRepliesFor(caseDir),
        runsPath,
        CaseRegistry.registryFor(caseDir).orElse(null));
  }

  /**
   * One trial, in two throwaway directories outside every repository tree: the scratch project the
   * agent works in, and — for a registry case — a work directory holding the staged snapshot and
   * the isolated vendor-tool configuration the registry's own commands install into. Both are
   * deleted when the trial ends, whatever its outcome.
   *
   * @sideEffects creates and deletes two temp directories; for a registry case, copies the
   *     subscription login into the isolated configuration so the agent can start at all.
   */
  private static boolean runOneTrial(TrialPlan plan, int trialNumber)
      throws IOException, InterruptedException {
    Path scratch = Files.createTempDirectory("nt-eval-");
    Path work = Files.createTempDirectory("nt-eval-work-");
    try {
      TrialEnvironment environment = TrialEnvironment.under(work, plan.repoRoot());
      seedTheLogin(work);
      System.out.println("transcript: " + environment.transcript());
      EvalTrial trial =
          new EvalTrial(
              EvalTrial.REAL_PROCESS_RUNNER,
              Clock.systemUTC(),
              plan.runsPath(),
              deliveryFor(plan, work),
              environment);
      boolean pass =
          trial.run(
              plan.args(),
              plan.repoRoot(),
              plan.fixtureDir(),
              plan.caseDir(),
              scratch,
              turnsFor(plan),
              trialNumber);
      if (!pass) {
        System.out.println(
            "evidence kept: " + environment.keepEvidenceUnder(keptFor(plan, trialNumber)));
      }
      return pass;
    } finally {
      deleteRecursively(scratch);
      deleteRecursively(work);
    }
  }

  /**
   * Where a FAILED trial's evidence is kept, under {@code build/} so it is ignored by git and swept
   * by a clean. One directory per skill, case and trial, so a three-trial run leaves three records
   * rather than three copies of the last one.
   */
  private static Path keptFor(TrialPlan plan, int trialNumber) {
    return plan.repoRoot()
        .resolve("narrativetrace-skills-catalogue/build/evals")
        .resolve(plan.args().skill())
        .resolve(plan.args().caseName())
        .resolve("trial-" + trialNumber);
  }

  /**
   * The throwaway configuration every trial runs against, carrying the one thing a fresh one cannot
   * do without: the subscription login. Said out loud, because a trial with no login does not fail
   * here — it fails three steps later when the agent answers "Not logged in", a long way from the
   * cause.
   */
  private static void seedTheLogin(Path work) throws IOException {
    Path realConfig = IsolatedAgentConfig.realConfigDir();
    boolean seeded = IsolatedAgentConfig.seedLogin(realConfig, work);
    System.out.println(
        "isolated agent configuration under "
            + IsolatedAgentConfig.configDir(work)
            + (seeded
                ? " (subscription login seeded from " + realConfig + ")"
                : " (NO login found in " + realConfig + " — the agent may refuse to start)"));
  }

  /** A registry case's own delivery, in the same work directory the configuration lives in. */
  private static RegistryDelivery deliveryFor(TrialPlan plan, Path work) {
    if (plan.preStep() == null) {
      return RegistryDelivery.none();
    }
    System.out.println("registry pre-step: " + plan.preStep().id());
    return RegistryDelivery.through(plan.preStep(), work);
  }

  /**
   * How many turns this case drives, before a trial is spent on it — the one line that tells an
   * operator whether the case they named is the conversation they meant.
   */
  private static void logTheConversationsShape(TrialPlan plan) {
    int replies = plan.scriptedReplies().size();
    System.out.println(
        replies == 0
            ? "one turn: the case's prompt.md"
            : (replies + 1) + " turns: the case's prompt.md, then " + plan.scriptedReplies());
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
