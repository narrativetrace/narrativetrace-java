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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Tier B trial flow: scaffold the case fixture into a scratch directory, drive the agent
 * command against the prompt, run the case's grader, and append one ledger row — factored out of
 * {@link EvalRunner} so the flow runs under a JUnit test with a fake process spawner, a fixed clock
 * and a temp ledger file, never a real subprocess or a real CLI. {@link EvalRunner#main} wires
 * {@link #REAL_PROCESS_RUNNER}, {@link Clock#systemUTC()} and the real {@code ledger/runs.jsonl}; a
 * test wires fakes.
 *
 * @llmNote the agent command and the grader script both run through the same {@link ProcessRunner}
 *     seam as an argv list, built by {@link AgentArgv} — never a shell string — so a prompt
 *     containing shell metacharacters can never be reinterpreted.
 */
public final class EvalTrial {

  /** Spawns {@code argv} in {@code cwd} with the given extra environment; returns its exit code. */
  @FunctionalInterface
  public interface ProcessRunner {
    int run(List<String> argv, Path cwd, Map<String, String> env)
        throws IOException, InterruptedException;
  }

  /** Production seam: a real OS process, argv passed directly — never through a shell. */
  public static final ProcessRunner REAL_PROCESS_RUNNER =
      (argv, cwd, env) -> {
        ProcessBuilder builder = new ProcessBuilder(argv).directory(cwd.toFile()).inheritIO();
        builder.environment().putAll(env);
        return builder.start().waitFor();
      };

  private final ProcessRunner processRunner;
  private final Clock clock;
  private final Path runsPath;

  public EvalTrial(ProcessRunner processRunner, Clock clock, Path runsPath) {
    if (processRunner == null) {
      throw new IllegalArgumentException("processRunner must not be null");
    }
    if (clock == null) {
      throw new IllegalArgumentException("clock must not be null");
    }
    if (runsPath == null) {
      throw new IllegalArgumentException("runsPath must not be null");
    }
    this.processRunner = processRunner;
    this.clock = clock;
    this.runsPath = runsPath;
  }

  /**
   * Runs one trial into {@code scratch} (created and deleted by the caller): copies {@code
   * fixtureDir}'s contents in, drives {@code agentCommand} (already carrying the literal {@code
   * {prompt}} placeholder, or {@code null} to skip the agent step) against {@code prompt}, runs the
   * case's grader from {@code caseDir}, and appends one row to the ledger. Returns whether the
   * grader passed.
   *
   * @throws IOException if the agent command exits non-zero (a crash, not a graded failure)
   * @sideEffects copies {@code fixtureDir} into {@code scratch}; appends one line to the ledger
   *     path this instance was built with; may spawn real subprocesses through the injected {@link
   *     ProcessRunner}.
   */
  public boolean run(
      EvalRunnerArgs args,
      Path repoRoot,
      Path fixtureDir,
      Path caseDir,
      Path scratch,
      String prompt,
      String agentCommand,
      int trial)
      throws IOException, InterruptedException {
    copyRecursively(fixtureDir, scratch);
    if (agentCommand != null) {
      runAgent(agentCommand, prompt, scratch);
    }
    boolean pass = runGrader(caseDir, scratch, repoRoot);
    appendLedgerRow(
        new RunLedgerRow(
            clock.instant().toString(),
            args.skill(),
            args.caseName(),
            args.platform(),
            args.model(),
            trial,
            pass));
    return pass;
  }

  private void runAgent(String agentCommand, String prompt, Path scratch)
      throws IOException, InterruptedException {
    List<String> argv = AgentArgv.build(agentCommand, prompt);
    int exit = processRunner.run(argv, scratch, Map.of());
    if (exit != 0) {
      throw new IOException("agent command exited " + exit + ": " + argv);
    }
  }

  private boolean runGrader(Path caseDir, Path cwd, Path repoRoot)
      throws IOException, InterruptedException {
    Path grader = caseDir.resolve("graders").resolve("verify.sh");
    Map<String, String> env =
        cliJarPath(repoRoot)
            .map(jar -> Map.of("NARRATIVETRACE_CLI_JAR", jar.toString()))
            .orElse(Map.of());
    List<String> argv = List.of("sh", grader.toAbsolutePath().toString());
    return processRunner.run(argv, cwd, env) == 0;
  }

  /**
   * The built, zero-dependency `narrativetrace-cli` jar a grader invokes directly — `./gradlew
   * :narrativetrace-cli:jar` must have run first; empty when it hasn't.
   */
  private static Optional<Path> cliJarPath(Path repoRoot) throws IOException {
    Path libsDir = repoRoot.resolve("narrativetrace-cli/build/libs");
    if (!Files.isDirectory(libsDir)) {
      return Optional.empty();
    }
    try (var stream = Files.list(libsDir)) {
      return stream
          .filter(p -> p.getFileName().toString().matches("narrativetrace-cli-.*\\.jar"))
          .findFirst();
    }
  }

  private void appendLedgerRow(RunLedgerRow row) throws IOException {
    Files.writeString(
        runsPath, row.toJsonLine() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  private static void copyRecursively(Path source, Path target) throws IOException {
    try (var stream = Files.walk(source)) {
      for (Path path : (Iterable<Path>) stream::iterator) {
        Path dest = target.resolve(source.relativize(path));
        if (Files.isDirectory(path)) {
          Files.createDirectories(dest);
        } else {
          Files.createDirectories(dest.getParent());
          Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }
}
