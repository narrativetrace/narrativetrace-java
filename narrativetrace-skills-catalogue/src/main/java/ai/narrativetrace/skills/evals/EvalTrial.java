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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
 * @llmNote every turn's agent command and the grader script run through the same {@link
 *     ProcessRunner} seam as an argv list, built by {@link AgentArgv} — never a shell string — so a
 *     prompt containing shell metacharacters can never be reinterpreted.
 * @llmNote a trial's TRANSCRIPT and its {@code gh} log live in {@link TrialEnvironment}'s work
 *     directory, outside the scaffolded project, and only the grader is told where. A record the
 *     subject could read or rewrite is not evidence.
 */
public final class EvalTrial {

  /** Spawns {@code argv} in {@code cwd} with the given extra environment; returns its exit code. */
  @FunctionalInterface
  public interface ProcessRunner {

    /** Runs {@code argv}, its output going wherever this runner's own output goes. */
    int run(List<String> argv, Path cwd, Map<String, String> env)
        throws IOException, InterruptedException;

    /**
     * Runs {@code argv} with its STANDARD OUTPUT appended to {@code stdoutTo} — the transcript a
     * grader reads — while its errors stay visible to whoever started the trial.
     *
     * <p><b>@llmNote</b> A seam that does not override this captures nothing, which is right for a
     * fake whose job is to record argv: the capture is proven against {@link #REAL_PROCESS_RUNNER}
     * at the actual OS process boundary, the same way the argv-safety property is, because a fake
     * that "captured" into a string would prove nothing about a child process's file descriptors.
     */
    default int run(List<String> argv, Path cwd, Map<String, String> env, Path stdoutTo)
        throws IOException, InterruptedException {
      return run(argv, cwd, env);
    }
  }

  /**
   * Production seam: a real OS process, argv passed directly — never through a shell.
   *
   * <p>A NAMED class rather than an anonymous one, deliberately. The security gate carries a
   * reviewed {@code COMMAND_INJECTION} exclusion for this runner (the "attacker" is the operator
   * launching their own CLI with their own arguments), and an exclusion matching an anonymous
   * class's generated {@code $1} name silently moves to a different class the moment another
   * anonymous class is declared ahead of it.
   */
  static final class RealProcessRunner implements ProcessRunner {

    @Override
    public int run(List<String> argv, Path cwd, Map<String, String> env)
        throws IOException, InterruptedException {
      return start(new ProcessBuilder(argv).directory(cwd.toFile()).inheritIO(), env);
    }

    /** Standard output to the transcript; errors stay where whoever started the trial sees them. */
    @Override
    public int run(List<String> argv, Path cwd, Map<String, String> env, Path stdoutTo)
        throws IOException, InterruptedException {
      return start(
          new ProcessBuilder(argv)
              .directory(cwd.toFile())
              .redirectInput(ProcessBuilder.Redirect.INHERIT)
              .redirectError(ProcessBuilder.Redirect.INHERIT)
              .redirectOutput(ProcessBuilder.Redirect.appendTo(stdoutTo.toFile())),
          env);
    }

    private static int start(ProcessBuilder builder, Map<String, String> env)
        throws IOException, InterruptedException {
      builder.environment().putAll(env);
      return builder.start().waitFor();
    }
  }

  /** The one instance of {@link RealProcessRunner}: it holds no state. */
  public static final ProcessRunner REAL_PROCESS_RUNNER = new RealProcessRunner();

  /** What the harness provides a fixture that carries no wrapper of its own. */
  private static final List<String> WRAPPER_FILES =
      List.of(
          "gradlew",
          "gradlew.bat",
          "gradle/wrapper/gradle-wrapper.jar",
          "gradle/wrapper/gradle-wrapper.properties");

  private final ProcessRunner processRunner;
  private final Clock clock;
  private final Path runsPath;
  private final RegistryDelivery delivery;
  private final TrialEnvironment environment;

  /** A case whose skill pages the harness itself copies in — every case but a registry one. */
  public EvalTrial(ProcessRunner processRunner, Clock clock, Path runsPath) {
    this(processRunner, clock, runsPath, RegistryDelivery.none());
  }

  /** A trial that keeps no transcript and installs no {@code gh} stand-in — the unit-test shape. */
  public EvalTrial(
      ProcessRunner processRunner, Clock clock, Path runsPath, RegistryDelivery delivery) {
    this(processRunner, clock, runsPath, delivery, TrialEnvironment.none());
  }

  public EvalTrial(
      ProcessRunner processRunner,
      Clock clock,
      Path runsPath,
      RegistryDelivery delivery,
      TrialEnvironment environment) {
    if (delivery == null) {
      throw new IllegalArgumentException("delivery must not be null");
    }
    if (environment == null) {
      throw new IllegalArgumentException("environment must not be null");
    }
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
    this.delivery = delivery;
    this.environment = environment;
  }

  /**
   * Runs one trial into {@code scratch} (created and deleted by the caller): copies {@code
   * fixtureDir}'s contents in, drives every turn {@code turns} declares, runs the case's grader
   * from {@code caseDir}, and appends one row to the ledger. Returns whether the grader passed.
   *
   * @throws IOException if any turn's command exits non-zero (a crash, not a graded failure)
   * @sideEffects copies {@code fixtureDir} into {@code scratch}; appends one line to the ledger
   *     path this instance was built with; appends to this trial's transcript; may spawn real
   *     subprocesses through the injected {@link ProcessRunner}.
   */
  public boolean run(
      EvalRunnerArgs args,
      Path repoRoot,
      Path fixtureDir,
      Path caseDir,
      Path scratch,
      AgentTurns turns,
      int trial)
      throws IOException, InterruptedException {
    copyRecursively(fixtureDir, scratch);
    if (!delivery.deliversTheSkills()) {
      copyRenderedSkills(repoRoot, scratch);
    }
    copyGradleWrapper(repoRoot, scratch);
    runPreStep(repoRoot, scratch);
    if (turns.drivesAnAgent()) {
      driveTheConversation(turns, scratch);
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

  /**
   * The registry's own delivery, before the agent starts: the staged snapshot, then the documented
   * commands a reader runs, in order, in the project itself.
   *
   * @throws IOException if any of them exits non-zero — the trial CRASHED rather than failed, so it
   *     leaves no ledger row: an absent vendor tool or an unreachable registry is not a verdict
   *     about the registry path.
   */
  private void runPreStep(Path repoRoot, Path scratch) throws IOException, InterruptedException {
    if (!delivery.deliversTheSkills()) {
      return;
    }
    Files.createDirectories(delivery.stagedSnapshot());
    for (List<String> command : delivery.commands(repoRoot)) {
      int exit = processRunner.run(command, scratch, delivery.environment());
      if (exit != 0) {
        throw new IOException("registry pre-step exited " + exit + ": " + command);
      }
    }
  }

  /**
   * Both rendered skill layouts, so the skill under trial can trigger at all. The pages are this
   * checkout's own rendered output — the same bytes the carrier jar ships — and they carry no
   * provenance line, which is why a grader exempts the installer's own check unless the agent ran
   * the installer itself.
   */
  private static void copyRenderedSkills(Path repoRoot, Path scratch) throws IOException {
    for (String layout : List.of(".agents", ".claude")) {
      Path skills = repoRoot.resolve(layout).resolve("skills");
      if (Files.isDirectory(skills)) {
        copyRecursively(skills, scratch.resolve(layout).resolve("skills"));
      }
    }
  }

  /**
   * A usable wrapper for a fixture that ships none — never over one the fixture brought itself,
   * because a fixture's own wrapper is part of what the case arranged.
   */
  private static void copyGradleWrapper(Path repoRoot, Path scratch) throws IOException {
    for (String relative : WRAPPER_FILES) {
      Path source = repoRoot.resolve(relative);
      Path destination = scratch.resolve(relative);
      if (Files.isRegularFile(source) && !Files.exists(destination)) {
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
      }
    }
  }

  /**
   * Every turn, in order: the user's words into the transcript first, then the agent's own turn
   * with its standard output appended after them.
   *
   * <p>A turn that exits non-zero stops the trial, so no later turn runs and the grader never does.
   * That is not tidiness: a second turn driven after a failed first is an approval answering a
   * question that was never asked, and its grader would read "nothing was filed" as a pass.
   *
   * @throws IOException if any turn's command exits non-zero
   */
  private void driveTheConversation(AgentTurns turns, Path scratch)
      throws IOException, InterruptedException {
    for (int turn = 1; turn <= turns.turnCount(); turn++) {
      String prompt = turns.promptForTurn(turn);
      if (environment.isRecording()) {
        environment.recordUserTurn(turn, prompt);
      }
      runOneTurn(turns.commandForTurn(turn), prompt, scratch, turn);
    }
  }

  private void runOneTurn(String command, String prompt, Path scratch, int turn)
      throws IOException, InterruptedException {
    List<String> argv = AgentArgv.build(command, prompt);
    Map<String, String> env = agentEnvironment();
    int exit =
        environment.isRecording()
            ? processRunner.run(argv, scratch, env, environment.transcript())
            : processRunner.run(argv, scratch, env);
    if (exit != 0) {
      throw new IOException("agent command for turn " + turn + " exited " + exit + ": " + argv);
    }
  }

  private Map<String, String> agentEnvironment() {
    Map<String, String> env = new HashMap<>(delivery.environment());
    env.putAll(environment.agentEnvironment());
    return Map.copyOf(env);
  }

  /**
   * The grader runs with the agent's environment PLUS what only it may know: where this trial's
   * transcript and {@code gh} log are, and the built CLI jar it invokes directly.
   */
  private boolean runGrader(Path caseDir, Path cwd, Path repoRoot)
      throws IOException, InterruptedException {
    Path grader = caseDir.resolve("graders").resolve("verify.sh");
    Map<String, String> env = new HashMap<>(delivery.environment());
    env.putAll(environment.graderEnvironment());
    cliJarPath(repoRoot).ifPresent(jar -> env.put("NARRATIVETRACE_CLI_JAR", jar.toString()));
    List<String> argv = List.of("sh", grader.toAbsolutePath().toString());
    return processRunner.run(argv, cwd, Map.copyOf(env)) == 0;
  }

  /**
   * The built, zero-dependency `narrativetrace-cli` jar a grader invokes directly — `./gradlew
   * :narrativetrace-cli:jar` must have run first; empty when it hasn't.
   *
   * <p>That one directory accumulates everything the module ever produced: the `-sources` and
   * `-javadoc` classifier jars, which carry no `Main-Class` at all, and one jar per released
   * version. So the choice is made twice over — runnable names only, and of those the most recently
   * built — never "whichever the filesystem happens to list first", which is how a grader ends up
   * running `java -jar` against a javadoc jar and failing for a reason the case never touched.
   */
  private static Optional<Path> cliJarPath(Path repoRoot) throws IOException {
    Path libsDir = repoRoot.resolve("narrativetrace-cli/build/libs");
    if (!Files.isDirectory(libsDir)) {
      return Optional.empty();
    }
    try (var stream = Files.list(libsDir)) {
      return stream
          .filter(EvalTrial::isRunnableCliJar)
          .max(Comparator.comparingLong(jar -> jar.toFile().lastModified()));
    }
  }

  /** `narrativetrace-cli-<version>.jar` — a classifier jar is not something `java -jar` can run. */
  private static boolean isRunnableCliJar(Path path) {
    String name = path.getFileName().toString();
    return name.startsWith("narrativetrace-cli-")
        && name.endsWith(".jar")
        && !name.endsWith("-sources.jar")
        && !name.endsWith("-javadoc.jar");
  }

  private void appendLedgerRow(RunLedgerRow row) throws IOException {
    Files.writeString(
        runsPath, row.toJsonLine() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  /**
   * A fixture's own documentation, never scaffolded: a ROOT file named {@code README}, in any case
   * and with any extension ({@code README.md}, {@code readme.txt}, {@code README}). It names the
   * case and what its grader reads. A README deeper in the tree is the project's own.
   */
  static boolean isFixtureDocumentation(Path relative) {
    if (relative.getNameCount() != 1) {
      return false;
    }
    String name = relative.getFileName().toString().toLowerCase(Locale.ROOT);
    return "readme".equals(name) || name.startsWith("readme.");
  }

  private static void copyRecursively(Path source, Path target) throws IOException {
    try (var stream = Files.walk(source)) {
      for (Path path : (Iterable<Path>) stream::iterator) {
        Path relative = source.relativize(path);
        // A trial must earn its reports: a previously built fixture is not evidence of success.
        if (List.of("build", ".gradle", ".git").contains(relative.getName(0).toString())) {
          continue;
        }
        // The root README documents the fixture for maintainers — its case, what the grader reads;
        // an agent that opened it would be told it is being tested, and how.
        if (isFixtureDocumentation(relative)) {
          continue;
        }
        Path dest = target.resolve(relative);
        if (Files.isDirectory(path)) {
          Files.createDirectories(dest);
        } else {
          Files.createDirectories(dest.getParent());
          Files.copy(
              path, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
      }
    }
  }
}
