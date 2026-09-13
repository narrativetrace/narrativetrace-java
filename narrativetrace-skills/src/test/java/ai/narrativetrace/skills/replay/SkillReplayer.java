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
package ai.narrativetrace.skills.replay;

import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import ai.narrativetrace.skills.catalogue.DoctorCommands;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;

/**
 * Tier A2: mechanically executes a skill's own step data against the sixty-seconds fixture, no LLM.
 * Green means the instructions are literally executable today against this commit's code. Mirrors
 * {@code packages/skills/src/replay.ts} in the TypeScript reference, adapted for a safety concern
 * that reference never had: a Java skill's replayable commands are {@code ./gradlew} invocations
 * against THIS SAME multi-module build, and shelling out to a second, independent {@code ./gradlew}
 * process while the first is already running risks daemon/lock contention. Every known command is
 * instead dispatched through {@link GradleRunner} (Gradle's own supported "run a nested build from
 * a test" mechanism — already proven safe elsewhere in this repo, see {@code
 * narrativetrace-build-tests}) or, for {@code git}/{@code find}, a real subprocess.
 *
 * <p>{@link DoctorCommands}' constants are what an ADOPTER runs, in their own project — this class
 * is the translation layer: every key below is one of those adopter-facing strings, and its value
 * is the real, fixture-scoped invocation that actually proves the underlying capability works today
 * ({@code :sixty-seconds:...} task paths, a {@code sixty-seconds}-scoped working directory). The
 * rendered page never sees this mapping; it exists only so replay stays honest without asking the
 * committed fixture to carry a second copy of the real Gradle plugin.
 *
 * <p>Consequently this is a small closed registry rather than a fully generic shell interpreter:
 * every command string a {@link Skill} in this catalogue can name must be registered in {@link
 * #KNOWN_COMMANDS} (a command replay finds unregistered fails loudly, naming itself, rather than
 * silently no-op'ing). Limits are the same as the reference's: this proves the steps work, not that
 * an agent finds or follows them, and not steps that are inherently judgmental (a step with no
 * commands and no mechanically-checkable verify is reported {@code ran=false, ok=true} — nothing to
 * replay, not a failure).
 */
public final class SkillReplayer {

  private static final String FIXTURE = "sixty-seconds";

  private SkillReplayer() {}

  private static final Map<String, Function<Path, Boolean>> KNOWN_COMMANDS =
      Map.of(
          DoctorCommands.BUILD_PROJECT, root -> gradleTask(root, ":sixty-seconds:build"),
          DoctorCommands.RUN_PROJECT, root -> gradleTask(root, ":sixty-seconds:run"),
          // sixty-seconds deliberately does not apply the ai.narrativetrace plugin (it depends on
          // the in-tree modules by source, not published coordinates — see its own build.gradle.kts
          // comment) — the fixture-scoped real invocation of the same doctor classes the plugin
          // task calls in-process is the CLI module's own printDoctorReport task instead.
          DoctorCommands.RUN_DOCTOR_GRADLE,
              root ->
                  gradleTask(
                      root,
                      ":narrativetrace-cli:printDoctorReport",
                      "-PnarrativetraceDoctorTarget=" + FIXTURE),
          DoctorCommands.NO_STALE_RECEIVED_FILE,
              root -> gitStatusIsClean(root, FIXTURE + "/src/test/narratives"),
          DoctorCommands.FIND_RENDERED_TRACES, SkillReplayer::renderedTraceExists);

  /** Prose verify strings this replayer additionally knows how to check mechanically. */
  private static final Map<String, Function<Path, Boolean>> KNOWN_VERIFIES =
      Map.of(
          DoctorCommands.VERIFY_ELEVEN_FINDINGS,
          SkillReplayer::reportHasElevenFindings,
          DoctorCommands.VERIFY_REDACTION_FINDING_PRESENT,
          root -> reportContains(root, "trap.redaction-proof"));

  public static List<StepReplay> replay(Skill skill, Path repoRoot) {
    List<StepReplay> results = new ArrayList<>();
    for (SkillStep step : skill.steps()) {
      results.add(replayStep(step, repoRoot));
    }
    return List.copyOf(results);
  }

  private static StepReplay replayStep(SkillStep step, Path repoRoot) {
    List<String> commands =
        step.body() instanceof StepBody.CommandStep cs ? cs.commands() : List.of();
    boolean ran = false;
    boolean ok = true;
    for (String command : commands) {
      ran = true;
      ok &= execute(command, repoRoot);
    }
    if (step.verify() != null) {
      if (step.hasReplayableVerify()) {
        ran = true;
        ok &= execute(step.verify(), repoRoot);
      } else if (KNOWN_VERIFIES.containsKey(step.verify())) {
        ran = true;
        ok &= KNOWN_VERIFIES.get(step.verify()).apply(repoRoot);
      }
      // Otherwise: prose describing done-ness with no mechanical check attached — descriptive
      // only, exactly like a judgmental step's absent verify.
    }
    return new StepReplay(step.title(), ran, ok, "");
  }

  private static boolean execute(String command, Path repoRoot) {
    Function<Path, Boolean> known = KNOWN_COMMANDS.get(command);
    if (known == null) {
      throw new IllegalStateException(
          "SkillReplayer has no registered executor for: "
              + command
              + " — register it in SkillReplayer.KNOWN_COMMANDS");
    }
    return known.apply(repoRoot);
  }

  private static boolean gradleTask(Path repoRoot, String... args) {
    try {
      GradleRunner.create().withProjectDir(repoRoot.toFile()).withArguments(args).build();
      return true;
    } catch (UnexpectedBuildFailure failure) {
      return false;
    }
  }

  private static boolean gitStatusIsClean(Path repoRoot, String scopedPath) {
    try {
      Process process =
          new ProcessBuilder("git", "status", "--short", scopedPath)
              .directory(repoRoot.toFile())
              .redirectErrorStream(true)
              .start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      boolean finished = process.waitFor(30, TimeUnit.SECONDS);
      return finished && process.exitValue() == 0 && output.isBlank();
    } catch (IOException e) {
      throw new UncheckedIOException("could not run git status", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted running git status", e);
    }
  }

  /**
   * Proves the adopter-facing {@code find build/narrativetrace -name "*.md"} genuinely lists a
   * rendered trace: builds the fixture first (its test run is what writes one), then runs a real
   * {@code find} subprocess, scoped to the fixture's own directory, exactly as an adopter's own
   * find would be scoped to their project.
   */
  private static boolean renderedTraceExists(Path repoRoot) {
    if (!gradleTask(repoRoot, ":" + FIXTURE + ":test")) {
      return false;
    }
    try {
      Process process =
          new ProcessBuilder("find", "build/narrativetrace", "-name", "*.md")
              .directory(repoRoot.resolve(FIXTURE).toFile())
              .redirectErrorStream(true)
              .start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      boolean finished = process.waitFor(30, TimeUnit.SECONDS);
      return finished && process.exitValue() == 0 && !output.isBlank();
    } catch (IOException e) {
      throw new UncheckedIOException("could not run find", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted running find", e);
    }
  }

  private static boolean reportContains(Path repoRoot, String needle) {
    return reportText(repoRoot).map(text -> text.contains(needle)).orElse(false);
  }

  private static boolean reportHasElevenFindings(Path repoRoot) {
    return reportText(repoRoot)
        .map(text -> Pattern.compile("\"id\":").matcher(text).results().count() == 11)
        .orElse(false);
  }

  /** The fixture's own doctor report, at the path the fixture-scoped replay actually wrote it. */
  private static java.util.Optional<String> reportText(Path repoRoot) {
    Path report = repoRoot.resolve(FIXTURE).resolve(DoctorCommands.DOCTOR_REPORT_PATH);
    if (!Files.isRegularFile(report)) {
      return java.util.Optional.empty();
    }
    try {
      return java.util.Optional.of(Files.readString(report));
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + report, e);
    }
  }
}
