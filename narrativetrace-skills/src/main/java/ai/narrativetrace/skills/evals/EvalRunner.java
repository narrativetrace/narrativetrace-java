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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Tier B trial runner (skill-harness-design.md §4.3, §5.1). NEVER invoked by {@code ./gradlew
 * check} — Tier A (lints) and Tier A2 (oracle replay) ride {@code check} every commit; this runs
 * through the subscription CLIs, by hand or from the nightly job, never the metered API. Scaffolds
 * the case's fixture into a scratch directory (a fresh temp copy outside every repo tree), drives
 * the requested agent CLI against the prompt with the catalogue loaded, runs the case's grader, and
 * appends one row to {@code ledger/runs.jsonl}.
 *
 * <p>This class is the untestable integration glue — real subprocess and filesystem orchestration —
 * deliberately excluded from the module's jacoco coverage gate the same way {@code
 * narrativetrace-cli}'s {@code Main} is: every decision worth a unit test (arg parsing, fixture
 * resolution, the quota decision, the ledger row shape, the platform command templates) lives in a
 * plain class this one only calls. Mirrors the TypeScript reference's {@code evals/run.ts}, which
 * carries no test of its own for the same reason.
 */
public final class EvalRunner {

  private EvalRunner() {}

  public static void main(String[] args) throws IOException, InterruptedException {
    EvalRunnerArgs parsed = EvalRunnerArgs.parse(args);
    Path repoRoot = repoRoot();
    Path ledgerDir = repoRoot.resolve("narrativetrace-skills/ledger");
    Path runsPath = ledgerDir.resolve("runs.jsonl");
    Path quotaPath = ledgerDir.resolve("quota.md");

    if (parsed.platform().isSporadic()) {
      TierPrecondition.assertDeterministicTiersGreen(
          repoRoot, TierPrecondition::runViaProcessBuilder);
    }

    Path caseDir =
        repoRoot
            .resolve("narrativetrace-skills/evals")
            .resolve(parsed.skill())
            .resolve(parsed.caseName());
    String prompt = readPrompt(caseDir);

    List<QuotaSpendRow> sessionSpend = new ArrayList<>();
    for (int trial = 1; trial <= parsed.trials(); trial++) {
      if (parsed.platform().isSporadic()) {
        assertQuotaAvailable(quotaPath, parsed.platform(), sessionSpend);
      }
      boolean pass = runOneTrial(parsed, repoRoot, caseDir, prompt, trial, runsPath);
      System.out.println(
          "trial " + trial + "/" + parsed.trials() + ": " + (pass ? "pass" : "fail"));
      if (parsed.platform().isSporadic()) {
        sessionSpend.add(recordSporadicSpend(quotaPath, parsed));
      }
    }
  }

  private static String readPrompt(Path caseDir) throws IOException {
    Path promptPath = caseDir.resolve("prompt.md");
    if (!Files.isRegularFile(promptPath)) {
      throw new IllegalArgumentException("No case found at " + caseDir);
    }
    return Files.readString(promptPath);
  }

  private static boolean runOneTrial(
      EvalRunnerArgs args, Path repoRoot, Path caseDir, String prompt, int trial, Path runsPath)
      throws IOException, InterruptedException {
    String fixture = CaseFixture.fixtureFor(caseDir);
    Path scratch = Files.createTempDirectory("nt-eval-");
    try {
      copyRecursively(repoRoot.resolve(fixture), scratch);
      String agentCommand =
          args.agentCommandOverride()
              .orElse(args.platform().presetAgentCommand(args.model(), args.skill()));
      if (agentCommand != null) {
        runShell(agentCommand.replace("{prompt}", prompt), scratch);
      } else {
        System.out.println(
            "(no --agent-command given — skipping the agent step, grading the fixture as-is)");
      }
      boolean pass = runGrader(caseDir, scratch, repoRoot);
      appendLedgerRow(
          runsPath,
          new RunLedgerRow(
              java.time.Instant.now().toString(),
              args.skill(),
              args.caseName(),
              args.platform(),
              args.model(),
              trial,
              pass));
      return pass;
    } finally {
      deleteRecursively(scratch);
    }
  }

  private static void assertQuotaAvailable(
      Path quotaPath, Platform platform, List<QuotaSpendRow> sessionSpend) throws IOException {
    QuotaLedger onDisk = QuotaMarkdown.parse(Files.readString(quotaPath));
    List<QuotaSpendRow> spend = new ArrayList<>(onDisk.spend());
    spend.addAll(sessionSpend);
    QuotaDecision decision =
        QuotaMarkdown.checkQuota(
            new QuotaLedger(onDisk.allowances(), spend), platform, LocalDate.now());
    if (!decision.allowed()) {
      throw new IllegalStateException(decision.reason());
    }
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
        quotaPath,
        QuotaMarkdown.appendedSpendRowLine(row),
        java.nio.file.StandardOpenOption.APPEND);
    return row;
  }

  private static void appendLedgerRow(Path runsPath, RunLedgerRow row) throws IOException {
    Files.writeString(runsPath, row.toJsonLine() + "\n", java.nio.file.StandardOpenOption.APPEND);
  }

  private static boolean runGrader(Path caseDir, Path cwd, Path repoRoot)
      throws IOException, InterruptedException {
    Path grader = caseDir.resolve("graders").resolve("verify.sh");
    ProcessBuilder builder =
        new ProcessBuilder("sh", grader.toAbsolutePath().toString())
            .directory(cwd.toFile())
            .inheritIO();
    cliJarPath(repoRoot)
        .ifPresent(jar -> builder.environment().put("NARRATIVETRACE_CLI_JAR", jar.toString()));
    return builder.start().waitFor() == 0;
  }

  /**
   * The built, zero-dependency `narrativetrace-cli` jar a grader invokes directly — `./gradlew
   * :narrativetrace-cli:jar` must have run first; empty when it hasn't.
   */
  private static java.util.Optional<Path> cliJarPath(Path repoRoot) throws IOException {
    Path libsDir = repoRoot.resolve("narrativetrace-cli/build/libs");
    if (!Files.isDirectory(libsDir)) {
      return java.util.Optional.empty();
    }
    try (var stream = Files.list(libsDir)) {
      return stream
          .filter(p -> p.getFileName().toString().matches("narrativetrace-cli-.*\\.jar"))
          .findFirst();
    }
  }

  private static void runShell(String command, Path cwd) throws IOException, InterruptedException {
    Process process =
        new ProcessBuilder("sh", "-c", command).directory(cwd.toFile()).inheritIO().start();
    int exit = process.waitFor();
    if (exit != 0) {
      throw new IOException("agent command exited " + exit + ": " + command);
    }
  }

  private static Path repoRoot() {
    // narrativetrace-skills/src/main/java/ai/narrativetrace/skills/evals -> repo root, but at
    // runtime this class lives on a built classpath, not in source form — the repo root is instead
    // wherever the process was launched from, exactly like `./gradlew` itself.
    return Path.of("").toAbsolutePath();
  }

  private static void copyRecursively(Path source, Path target) throws IOException {
    try (var stream = Files.walk(source)) {
      for (Path path : (Iterable<Path>) stream::iterator) {
        Path dest = target.resolve(source.relativize(path));
        if (Files.isDirectory(path)) {
          Files.createDirectories(dest);
        } else {
          Files.createDirectories(dest.getParent());
          Files.copy(path, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var stream = Files.walk(root)) {
      stream
          .sorted(java.util.Comparator.reverseOrder())
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
