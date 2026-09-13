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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
    private final Deque<Integer> exitCodes;

    FakeProcessRunner(Integer... codes) {
      this.exitCodes = new ArrayDeque<>(List.of(codes));
    }

    @Override
    public int run(List<String> argv, Path cwd, Map<String, String> env) {
      argvCalls.add(argv);
      envCalls.add(env);
      return exitCodes.pop();
    }
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

    trial.run(ARGS, tempDir, fixture, tempDir, scratch, "prompt", null, 1);

    assertThat(scratch.resolve("marker.txt")).hasContent("fixture-content");
  }

  @Test
  void passesWhenTheAgentSucceedsAndTheGraderSucceeds(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var trial = new EvalTrial(new FakeProcessRunner(0, 0), FIXED_CLOCK, runsPath);

    boolean pass =
        trial.run(ARGS, tempDir, fixture, tempDir, scratch, "prompt", "echo {prompt}", 1);

    assertThat(pass).isTrue();
  }

  @Test
  void failsWhenTheAgentSucceedsButTheGraderFails(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var trial = new EvalTrial(new FakeProcessRunner(0, 1), FIXED_CLOCK, runsPath);

    boolean pass =
        trial.run(ARGS, tempDir, fixture, tempDir, scratch, "prompt", "echo {prompt}", 1);

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
            () -> trial.run(ARGS, tempDir, fixture, tempDir, scratch, "prompt", "echo {prompt}", 1))
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

    boolean pass = trial.run(ARGS, tempDir, fixture, tempDir, scratch, "prompt", null, 1);

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

    trial.run(ARGS, tempDir, fixture, caseDir, scratch, "prompt", null, 1);

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

    trial.run(ARGS, repoRoot, fixture, tempDir, scratch, "prompt", null, 1);

    assertThat(runner.envCalls.get(0)).containsEntry("NARRATIVETRACE_CLI_JAR", jar.toString());
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

    trial.run(ARGS, repoRoot, fixture, tempDir, scratch, "prompt", null, 1);

    assertThat(runner.envCalls.get(0)).doesNotContainKey("NARRATIVETRACE_CLI_JAR");
  }

  @Test
  void appendsALedgerRowShapedFromTheArgsTrialNumberOutcomeAndClock(@TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarkerFile(tempDir);
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));
    Path runsPath = tempDir.resolve("runs.jsonl");
    var trial = new EvalTrial(new FakeProcessRunner(0), FIXED_CLOCK, runsPath);

    trial.run(ARGS, tempDir, fixture, tempDir, scratch, "prompt", null, 2);

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

    trial.run(ARGS, tempDir, fixture, tempDir, scratch1, "prompt", null, 1);
    trial.run(ARGS, tempDir, fixture, tempDir, scratch2, "prompt", null, 2);

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
    boolean pass = trial.run(ARGS, tempDir, fixture, caseDir, scratch, hostilePrompt, template, 1);

    assertThat(pass).isTrue();
    assertThat(Files.readString(outFile)).isEqualTo(hostilePrompt);
  }
}
