/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link EvalTrial#run} is the scaffold-agent-grade-ledger flow, factored out of {@link
 * EvalRunner#main} so it runs here with a fake {@link EvalTrial.ProcessRunner}, a fixed {@link
 * Clock} and a temp ledger file — no real subprocess, no real CLI. The last two tests use the
 * production {@link EvalTrial#REAL_PROCESS_RUNNER} against a tiny local script (never a real agent
 * CLI) to prove the argv-safety fix at the actual OS process boundary.
 */
class EvalTrialTest {

  private static final EvalRunnerArgs ARGS =
      new EvalRunnerArgs("narrativetrace-doctor", "happy-path", Platform.CODEX, "mini", null, 1);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC);

  /** Records every argv/cwd/env it was called with; replays exit codes in call order. */
  private static final class FakeProcessRunner implements EvalTrial.ProcessRunner {
    final List<List<String>> argvCalls = new ArrayList<>();
    final List<Map<String, String>> envCalls = new ArrayList<>();
    final List<Path> cwdCalls = new ArrayList<>();
    private final Deque<Integer> exitCodes;

    FakeProcessRunner(Integer... codes) {
      this.exitCodes = new ArrayDeque<>(List.of(codes));
    }

    @Override
    public int run(List<String> argv, Path cwd, Map<String, String> env) {
      argvCalls.add(argv);
      envCalls.add(env);
      cwdCalls.add(cwd);
      return exitCodes.pop();
    }
  }

  /** One turn, driven by a command that needs no agent CLI. */
  private static AgentTurns echoing(String prompt) {
    return new AgentTurns("echo {prompt}", null, List.of(prompt));
  }

  private static Path fixtureWithMarkerFile(Path tempDir) throws IOException {
    Path fixture = Files.createDirectory(tempDir.resolve("fixture"));
    Files.writeString(fixture.resolve("marker.txt"), "fixture-content");
    return fixture;
  }

  @Test
  void constructorRejectsANullSeam(@TempDir Path tempDir) {
    Path runsPath = tempDir.resolve("runs.jsonl");
    assertThatThrownBy(() -> new EvalTrial(null, FIXED_CLOCK, runsPath))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvalTrial(EvalTrial.REAL_PROCESS_RUNNER, null, runsPath))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvalTrial(EvalTrial.REAL_PROCESS_RUNNER, FIXED_CLOCK, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void copiesTheFixtureIntoScratchBeforeGrading(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var trial = new EvalTrial(new FakeProcessRunner(0), FIXED_CLOCK, runsPath);

    trial.run(ARGS, tempDir, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 1);

    assertThat(scratch.resolve("marker.txt")).hasContent("fixture-content");
  }

  @Test
  void makesTheRenderedCatalogueAvailableBeforeTheAgentRuns(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path repo = Files.createDirectory(tempDir.resolve("repo"));
    for (String layout : List.of(".agents", ".claude")) {
      Path skill = Files.createDirectories(repo.resolve(layout + "/skills/example"));
      Files.writeString(skill.resolve("SKILL.md"), "---\nname: example\n---\nDo the work.");
    }
    var trial =
        new EvalTrial(
            (argv, cwd, env) -> {
              assertThat(cwd.resolve(".agents/skills/example/SKILL.md"))
                  .hasContent("---\nname: example\n---\nDo the work.");
              assertThat(cwd.resolve(".claude/skills/example/SKILL.md"))
                  .hasContent("---\nname: example\n---\nDo the work.");
              return 0;
            },
            FIXED_CLOCK,
            tempDir.resolve("runs.jsonl"));

    assertThat(trial.run(ARGS, repo, fixture, tempDir, scratch, echoing("prompt"), 1)).isTrue();
  }

  @Test
  void suppliesAnExecutableGradleWrapperToAStandaloneFixture(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path repo = Files.createDirectory(tempDir.resolve("repo"));
    Files.writeString(repo.resolve("gradlew"), "#!/bin/sh\nexit 0\n");
    assertThat(repo.resolve("gradlew").toFile().setExecutable(true)).isTrue();
    Files.createDirectories(repo.resolve("gradle/wrapper"));
    Files.writeString(repo.resolve("gradle/wrapper/gradle-wrapper.jar"), "wrapper-jar");
    Files.writeString(repo.resolve("gradle/wrapper/gradle-wrapper.properties"), "distribution");
    var trial = new EvalTrial(new FakeProcessRunner(0), FIXED_CLOCK, tempDir.resolve("runs.jsonl"));

    trial.run(ARGS, repo, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 1);

    assertThat(scratch.resolve("gradlew")).isExecutable();
    assertThat(scratch.resolve("gradle/wrapper/gradle-wrapper.jar")).hasContent("wrapper-jar");
    assertThat(scratch.resolve("gradle/wrapper/gradle-wrapper.properties"))
        .hasContent("distribution");
  }

  @Test
  void startsWithoutBuildArtifactsOrCachesAndPreservesTheFixturesOwnWrapper(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    for (String directory : List.of("build", ".gradle", ".git")) {
      Files.createDirectories(fixture.resolve(directory));
      Files.writeString(fixture.resolve(directory + "/stale.txt"), "old run");
    }
    Files.writeString(fixture.resolve("gradlew"), "fixture wrapper");
    assertThat(fixture.resolve("gradlew").toFile().setExecutable(true)).isTrue();
    Path repo = Files.createDirectory(tempDir.resolve("repo"));
    Files.writeString(repo.resolve("gradlew"), "repository wrapper");
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    var trial = new EvalTrial(new FakeProcessRunner(0), FIXED_CLOCK, tempDir.resolve("runs.jsonl"));

    trial.run(ARGS, repo, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 1);

    assertThat(scratch.resolve("build")).doesNotExist();
    assertThat(scratch.resolve(".gradle")).doesNotExist();
    assertThat(scratch.resolve(".git")).doesNotExist();
    assertThat(scratch.resolve("gradlew")).hasContent("fixture wrapper").isExecutable();
    assertThat(scratch.resolve("marker.txt")).hasContent("fixture-content");
  }

  @Test
  void passesWhenTheAgentSucceedsAndTheGraderSucceeds(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var trial = new EvalTrial(new FakeProcessRunner(0, 0), FIXED_CLOCK, runsPath);

    boolean pass = trial.run(ARGS, tempDir, fixture, tempDir, scratch, echoing("prompt"), 1);

    assertThat(pass).isTrue();
  }

  @Test
  void failsWhenTheAgentSucceedsButTheGraderFails(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var trial = new EvalTrial(new FakeProcessRunner(0, 1), FIXED_CLOCK, runsPath);

    boolean pass = trial.run(ARGS, tempDir, fixture, tempDir, scratch, echoing("prompt"), 1);

    assertThat(pass).isFalse();
  }

  @Test
  void throwsWhenTheAgentCommandCrashesAndNeverRunsTheGrader(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var runner = new FakeProcessRunner(17);
    var trial = new EvalTrial(runner, FIXED_CLOCK, runsPath);

    assertThatThrownBy(
            () -> trial.run(ARGS, tempDir, fixture, tempDir, scratch, echoing("prompt"), 1))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("17");
    assertThat(runner.argvCalls).hasSize(1); // the agent call only — the grader never ran
  }

  @Test
  void skipsTheAgentStepWhenNoAgentCommandIsGiven(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var runner = new FakeProcessRunner(0);
    var trial = new EvalTrial(runner, FIXED_CLOCK, runsPath);

    boolean pass =
        trial.run(ARGS, tempDir, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 1);

    assertThat(pass).isTrue();
    assertThat(runner.argvCalls).hasSize(1); // the grader call only
    assertThat(runner.argvCalls.get(0)).contains("sh");
  }

  @Test
  void theGraderRunsInTheScratchDirectoryWithTheCaseDirsVerifyScript(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path caseDir = Files.createDirectory(tempDir.resolve("case"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var runner = new FakeProcessRunner(0);
    var trial = new EvalTrial(runner, FIXED_CLOCK, runsPath);

    trial.run(ARGS, tempDir, fixture, caseDir, scratch, AgentTurns.graded("prompt"), 1);

    List<String> graderArgv = runner.argvCalls.get(0);
    assertThat(graderArgv)
        .containsExactly(
            "sh", caseDir.resolve("graders").resolve("verify.sh").toAbsolutePath().toString());
  }

  @Test
  void injectsTheCliJarEnvVarWhenTheCliJarHasBeenBuilt(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path repoRoot = Files.createDirectory(tempDir.resolve("repo"));
    Path libsDir = Files.createDirectories(repoRoot.resolve("narrativetrace-cli/build/libs"));
    Path jar = Files.createFile(libsDir.resolve("narrativetrace-cli-0.2.0.jar"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var runner = new FakeProcessRunner(0);
    var trial = new EvalTrial(runner, FIXED_CLOCK, runsPath);

    trial.run(ARGS, repoRoot, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 1);

    assertThat(runner.envCalls.get(0)).containsEntry("NARRATIVETRACE_CLI_JAR", jar.toString());
  }

  /**
   * The libs directory accumulates: a release build leaves the {@code -sources} and {@code
   * -javadoc} classifier jars beside the runnable one, and neither carries a {@code Main-Class}, so
   * a grader handed one runs {@code java -jar} against a jar that cannot start and fails for a
   * reason that has nothing to do with the case it was grading.
   */
  @Test
  void neverHandsAGraderAClassifierJar(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path repoRoot = Files.createDirectory(tempDir.resolve("repo"));
    Path libsDir = Files.createDirectories(repoRoot.resolve("narrativetrace-cli/build/libs"));
    Path runnable = Files.createFile(libsDir.resolve("narrativetrace-cli-0.2.4.jar"));
    Files.createFile(libsDir.resolve("narrativetrace-cli-0.2.4-javadoc.jar"));
    Files.createFile(libsDir.resolve("narrativetrace-cli-0.2.4-sources.jar"));
    var runner = new FakeProcessRunner(0);
    var trial = new EvalTrial(runner, FIXED_CLOCK, tempDir.resolve("runs.jsonl"));

    trial.run(ARGS, repoRoot, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 1);

    assertThat(runner.envCalls.get(0)).containsEntry("NARRATIVETRACE_CLI_JAR", runnable.toString());
  }

  /**
   * Every past release's jar is still there too; the one this checkout just built is the newest.
   */
  @Test
  void handsAGraderTheJarThisCheckoutBuiltLastWhenOlderVersionsAreStillOnDisk(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path repoRoot = Files.createDirectory(tempDir.resolve("repo"));
    Path libsDir = Files.createDirectories(repoRoot.resolve("narrativetrace-cli/build/libs"));
    Path stale = Files.createFile(libsDir.resolve("narrativetrace-cli-0.2.1.jar"));
    Path fresh = Files.createFile(libsDir.resolve("narrativetrace-cli-0.2.4.jar"));
    Files.setLastModifiedTime(stale, FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));
    Files.setLastModifiedTime(fresh, FileTime.from(Instant.parse("2026-09-25T00:00:00Z")));
    var runner = new FakeProcessRunner(0);
    var trial = new EvalTrial(runner, FIXED_CLOCK, tempDir.resolve("runs.jsonl"));

    trial.run(ARGS, repoRoot, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 1);

    assertThat(runner.envCalls.get(0)).containsEntry("NARRATIVETRACE_CLI_JAR", fresh.toString());
  }

  @Test
  void leavesTheCliJarEnvVarAbsentWhenTheCliJarHasNotBeenBuilt(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path repoRoot = Files.createDirectory(tempDir.resolve("repo"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var runner = new FakeProcessRunner(0);
    var trial = new EvalTrial(runner, FIXED_CLOCK, runsPath);

    trial.run(ARGS, repoRoot, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 1);

    assertThat(runner.envCalls.get(0)).doesNotContainKey("NARRATIVETRACE_CLI_JAR");
  }

  @Test
  void appendsALedgerRowShapedFromTheArgsTrialNumberOutcomeAndClock(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var trial = new EvalTrial(new FakeProcessRunner(0), FIXED_CLOCK, runsPath);

    trial.run(ARGS, tempDir, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 2);

    List<RunLedgerRow> rows = RunLedgerRow.parseJsonl(Files.readString(runsPath));
    assertThat(rows).hasSize(1);
    RunLedgerRow row = rows.get(0);
    assertThat(row.date()).isEqualTo("2026-09-13T00:00:00Z");
    assertThat(row.skill()).isEqualTo("narrativetrace-doctor");
    assertThat(row.caseName()).isEqualTo("happy-path");
    assertThat(row.platform()).isEqualTo(Platform.CODEX);
    assertThat(row.model()).isEqualTo("mini");
    assertThat(row.trial()).isEqualTo(2);
    assertThat(row.pass()).isTrue();
  }

  @Test
  void appendsOneRowPerTrialAcrossRepeatedCalls(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch1 = Files.createDirectory(tempDir.resolve("scratch1"));
    Path scratch2 = Files.createDirectory(tempDir.resolve("scratch2"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var trial = new EvalTrial(new FakeProcessRunner(0, 1), FIXED_CLOCK, runsPath);

    trial.run(ARGS, tempDir, fixture, tempDir, scratch1, AgentTurns.graded("prompt"), 1);
    trial.run(ARGS, tempDir, fixture, tempDir, scratch2, AgentTurns.graded("prompt"), 2);

    List<RunLedgerRow> rows = RunLedgerRow.parseJsonl(Files.readString(runsPath));
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).trial()).isEqualTo(1);
    assertThat(rows.get(0).pass()).isTrue();
    assertThat(rows.get(1).trial()).isEqualTo(2);
    assertThat(rows.get(1).pass()).isFalse();
  }

  @Test
  void realProcessRunnerDeliversAHostilePromptToTheChildProcessByteForByte(@TempDir Path tempDir)
      throws Exception {
    // The regression proof for the shell-splicing defect, at the actual OS process boundary: a
    // tiny local script stands in for a real agent CLI, invoked through the exact production seam
    // (EvalTrial.REAL_PROCESS_RUNNER) and the exact production template-substitution (AgentArgv),
    // never through "sh -c" string interpolation.
    Path echoScript = tempDir.resolve("echo-argv.sh");
    Files.writeString(echoScript, "#!/bin/sh\nprintf '%s' \"$1\" > \"$2\"\n");
    Path outFile = tempDir.resolve("out.txt");
    String hostilePrompt =
        "before `id` $(cat /etc/passwd) \"double quoted\" 'single quoted'\nsecond line\nend";
    String template = "sh " + echoScript + " \"{prompt}\" " + outFile;

    List<String> argv = AgentArgv.build(template, hostilePrompt);
    int exit = EvalTrial.REAL_PROCESS_RUNNER.run(argv, tempDir, Map.of());

    assertThat(exit).isZero();
    assertThat(Files.readString(outFile)).isEqualTo(hostilePrompt);
  }

  @Test
  void endToEndRunDrivesTheRealProcessRunnerWithAHostilePromptAndStillGrades(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path caseDir = Files.createDirectory(tempDir.resolve("case"));
    Path graders = Files.createDirectory(caseDir.resolve("graders"));
    Path verify = graders.resolve("verify.sh");
    Files.writeString(verify, "#!/bin/sh\nexit 0\n");
    Path runsPath = tempDir.resolve("runs.jsonl");
    Path echoScript = tempDir.resolve("echo-argv.sh");
    Files.writeString(echoScript, "#!/bin/sh\nprintf '%s' \"$1\" > \"$2\"\n");
    Path outFile = tempDir.resolve("out.txt");
    String hostilePrompt = "`rm -rf /` $(id)\nnewline";
    String template = "sh " + echoScript + " \"{prompt}\" " + outFile;

    var trial = new EvalTrial(EvalTrial.REAL_PROCESS_RUNNER, FIXED_CLOCK, runsPath);
    boolean pass =
        trial.run(
            ARGS,
            tempDir,
            fixture,
            caseDir,
            scratch,
            new AgentTurns(template, null, List.of(hostilePrompt)),
            1);

    assertThat(pass).isTrue();
    assertThat(Files.readString(outFile)).isEqualTo(hostilePrompt);
  }

  // ---------------------------------------------------------------------------------------------
  // A case whose pages a REGISTRY delivers. The harness copies none of its own in, the registry's
  // own commands run first, and every command of the trial sees the isolated configuration.
  // ---------------------------------------------------------------------------------------------

  /** A runner that lets {@code calls} commands succeed — a registry trial runs several. */
  private static FakeProcessRunner succeeding(int calls) {
    Integer[] codes = new Integer[calls];
    Arrays.fill(codes, 0);
    return new FakeProcessRunner(codes);
  }

  private static Path repoWithRenderedSkills(Path tempDir) throws IOException {
    Path repo = Files.createDirectory(tempDir.resolve("repo"));
    for (String layout : List.of(".agents", ".claude")) {
      Path skill = Files.createDirectories(repo.resolve(layout + "/skills/example"));
      Files.writeString(skill.resolve("SKILL.md"), "---\nname: example\n---\nDo the work.");
    }
    return repo;
  }

  /**
   * The whole point of a registry case: the pages in the project are the ones the registry tool put
   * there. A page the harness copied in on top would answer the case's own question for it — and
   * for the tool that symlinks one flavour at the other, it would answer it wrongly.
   */
  @Test
  void aRegistryCaseNeverGetsTheHarnessOwnCopyOfTheRenderedPages(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path repo = repoWithRenderedSkills(tempDir);
    var trial =
        new EvalTrial(
            succeeding(4),
            FIXED_CLOCK,
            tempDir.resolve("runs.jsonl"),
            RegistryDelivery.through(RegistryPreStep.NPX_SKILLS, tempDir.resolve("work")));

    trial.run(ARGS, repo, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 1);

    assertThat(scratch.resolve(".agents/skills/example/SKILL.md")).doesNotExist();
    assertThat(scratch.resolve(".claude/skills/example/SKILL.md")).doesNotExist();
    assertThat(scratch.resolve("marker.txt")).hasContent("fixture-content");
  }

  @Test
  void runsEveryPreStepCommandInOrderInTheProjectBeforeTheAgent(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path work = tempDir.resolve("work");
    var runner = succeeding(7);
    var delivery = RegistryDelivery.through(RegistryPreStep.CLAUDE_MARKETPLACE, work);
    var trial = new EvalTrial(runner, FIXED_CLOCK, tempDir.resolve("runs.jsonl"), delivery);

    trial.run(ARGS, tempDir, fixture, tempDir, scratch, echoing("prompt"), 1);

    List<List<String>> expected = delivery.commands(tempDir);
    assertThat(runner.argvCalls.subList(0, expected.size())).isEqualTo(expected);
    assertThat(runner.argvCalls).hasSize(expected.size() + 2); // the agent and the grader after
    assertThat(runner.cwdCalls.subList(0, expected.size())).containsOnly(scratch);
  }

  /** `git archive -o <staged>.tar` needs the parent, and `tar -C <staged>` needs the directory. */
  @Test
  void createsTheStagedTreesDirectoryBeforeAnyPreStepCommandRuns(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path work = tempDir.resolve("work");
    var delivery = RegistryDelivery.through(RegistryPreStep.NPX_SKILLS, work);
    var trial =
        new EvalTrial(
            (argv, cwd, env) -> {
              assertThat(delivery.stagedSnapshot()).isDirectory();
              return 0;
            },
            FIXED_CLOCK,
            tempDir.resolve("runs.jsonl"),
            delivery);

    assertThat(trial.run(ARGS, tempDir, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 1))
        .isTrue();
  }

  @Test
  void everyCommandOfARegistryTrialSeesTheIsolatedConfiguration(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path work = tempDir.resolve("work");
    var runner = succeeding(5);
    var delivery = RegistryDelivery.through(RegistryPreStep.NPX_SKILLS, work);
    var trial = new EvalTrial(runner, FIXED_CLOCK, tempDir.resolve("runs.jsonl"), delivery);

    trial.run(ARGS, tempDir, fixture, tempDir, scratch, echoing("prompt"), 1);

    assertThat(runner.envCalls)
        .allSatisfy(
            env ->
                assertThat(env)
                    .containsEntry(
                        "CLAUDE_CONFIG_DIR", IsolatedAgentConfig.configDir(work).toString()));
  }

  /**
   * A pre-step that failed delivered nothing, so there is no registry state to grade — the trial
   * CRASHED, the way a crashed agent command does, and must leave no ledger row claiming a verdict.
   * A red row here would read as "the registry path does not work" when what happened is that the
   * tool was absent or the network was down.
   */
  @Test
  void aFailedPreStepCrashesTheTrialAndLeavesNoLedgerRow(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var runner = new FakeProcessRunner(0, 42);
    var trial =
        new EvalTrial(
            runner,
            FIXED_CLOCK,
            runsPath,
            RegistryDelivery.through(RegistryPreStep.NPX_SKILLS, tempDir.resolve("work")));

    assertThatThrownBy(
            () -> trial.run(ARGS, tempDir, fixture, tempDir, scratch, echoing("prompt"), 1))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("42");
    assertThat(runner.argvCalls).hasSize(2); // the two staging commands, nothing after
    assertThat(runsPath).doesNotExist();
  }

  // ---------------------------------------------------------------------------------------------
  // A case the runner drives through SEVERAL turns, and the transcript it keeps of them. An
  // approval is only an approval if it arrives in a turn of the user's own, so the gate that
  // measures one needs both.
  // ---------------------------------------------------------------------------------------------

  private static AgentTurns twoTurns(String first, String reply) {
    return new AgentTurns("echo-first {prompt}", "echo-again {prompt}", List.of(first, reply));
  }

  @Test
  void drivesEveryTurnInOrderEachWithItsOwnPromptAndTemplate(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    var runner = new FakeProcessRunner(0, 0, 0);
    var trial = new EvalTrial(runner, FIXED_CLOCK, tempDir.resolve("runs.jsonl"));

    trial.run(ARGS, tempDir, fixture, tempDir, scratch, twoTurns("report it", "yes, file it"), 1);

    assertThat(runner.argvCalls)
        .containsExactly(
            List.of("echo-first", "report it"),
            List.of("echo-again", "yes, file it"),
            List.of("sh", tempDir.resolve("graders/verify.sh").toAbsolutePath().toString()));
  }

  /**
   * A turn that crashed never happened, so neither does any turn after it, and neither does the
   * grader: a second turn driven after a failed first would be an approval answering a question
   * that was never asked.
   */
  @Test
  void aCrashedTurnStopsTheTrialBeforeAnyLaterTurnOrTheGrader(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var runner = new FakeProcessRunner(9);
    var trial = new EvalTrial(runner, FIXED_CLOCK, runsPath);

    assertThatThrownBy(
            () ->
                trial.run(
                    ARGS, tempDir, fixture, tempDir, scratch, twoTurns("report it", "yes"), 1))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("9");
    assertThat(runner.argvCalls).hasSize(1);
    assertThat(runsPath).doesNotExist();
  }

  @Test
  void everyAgentTurnSeesTheTrialsOwnEnvironmentAndTheGraderSeesItsEvidenceToo(
      @TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    TrialEnvironment environment = TrialEnvironment.under(tempDir.resolve("work"), tempDir);
    var runner = new FakeProcessRunner(0, 0, 0);
    var trial =
        new EvalTrial(
            runner,
            FIXED_CLOCK,
            tempDir.resolve("runs.jsonl"),
            RegistryDelivery.none(),
            environment);

    trial.run(ARGS, tempDir, fixture, tempDir, scratch, twoTurns("report it", "yes"), 1);

    assertThat(runner.envCalls.subList(0, 2))
        .allSatisfy(
            env ->
                assertThat(env).containsKey("PATH").doesNotContainKey("NARRATIVETRACE_TRANSCRIPT"));
    assertThat(runner.envCalls.get(2))
        .containsEntry("NARRATIVETRACE_TRANSCRIPT", environment.transcript().toString())
        .containsEntry("NARRATIVETRACE_GH_LOG", environment.ghInvocations().toString());
  }

  /**
   * End to end through the production seam: each turn's user marker, then that turn's own standard
   * output, in order, in one file outside the project the agent worked in.
   */
  @Test
  void keepsATranscriptOfEveryTurnsMarkerAndItsOutputInOrder(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path caseDir = Files.createDirectory(tempDir.resolve("case"));
    Files.createDirectory(caseDir.resolve("graders"));
    Files.writeString(caseDir.resolve("graders/verify.sh"), "#!/bin/sh\nexit 0\n");
    TrialEnvironment environment = TrialEnvironment.under(tempDir.resolve("work"), tempDir);
    var trial =
        new EvalTrial(
            EvalTrial.REAL_PROCESS_RUNNER,
            FIXED_CLOCK,
            tempDir.resolve("runs.jsonl"),
            RegistryDelivery.none(),
            environment);

    boolean pass =
        trial.run(
            ARGS,
            tempDir,
            fixture,
            caseDir,
            scratch,
            new AgentTurns(
                "echo turn-one-said {prompt}",
                "echo turn-two-said {prompt}",
                List.of("report it", "yes, file it")),
            1);

    assertThat(pass).isTrue();
    assertThat(Files.readAllLines(environment.transcript()))
        .containsExactly(
            "{\"nt_turn\":1,\"role\":\"user\",\"text\":\"report it\"}",
            "turn-one-said report it",
            "{\"nt_turn\":2,\"role\":\"user\",\"text\":\"yes, file it\"}",
            "turn-two-said yes, file it");
  }

  /** Nothing the agent ran is written into the project it was working in. */
  @Test
  void theTranscriptIsNeverWrittenInsideTheProjectTheAgentWorkedIn(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    TrialEnvironment environment = TrialEnvironment.under(tempDir.resolve("work"), tempDir);
    var trial =
        new EvalTrial(
            new FakeProcessRunner(0, 0),
            FIXED_CLOCK,
            tempDir.resolve("runs.jsonl"),
            RegistryDelivery.none(),
            environment);

    trial.run(ARGS, tempDir, fixture, tempDir, scratch, echoing("report it"), 1);

    assertThat(environment.transcript()).exists();
    try (var walk = Files.walk(scratch)) {
      assertThat(walk.map(Path::getFileName).map(Path::toString))
          .doesNotContain("transcript.jsonl", "gh-invocations.log");
    }
  }

  /** A trial wired with no environment records nothing and still runs — the unit-test shape. */
  @Test
  void aTrialWiredWithNoEnvironmentKeepsNoTranscriptAndStillGrades(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    var trial =
        new EvalTrial(new FakeProcessRunner(0, 0), FIXED_CLOCK, tempDir.resolve("runs.jsonl"));

    assertThat(trial.run(ARGS, tempDir, fixture, tempDir, scratch, echoing("report it"), 1))
        .isTrue();
  }

  @Test
  void constructorRejectsANullEnvironment(@TempDir Path tempDir) {
    assertThatThrownBy(
            () ->
                new EvalTrial(
                    EvalTrial.REAL_PROCESS_RUNNER,
                    FIXED_CLOCK,
                    tempDir.resolve("runs.jsonl"),
                    RegistryDelivery.none(),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
