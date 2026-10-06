/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.replay;

import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import ai.narrativetrace.skills.catalogue.ClarityCommands;
import ai.narrativetrace.skills.catalogue.DoctorCommands;
import ai.narrativetrace.skills.catalogue.FeedbackCommands;
import ai.narrativetrace.skills.catalogue.InstallerCommands;
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

  /**
   * System property {@code narrativetrace-skills-catalogue/build.gradle.kts} sets on every {@link
   * org.gradle.api.tasks.testing.Test} task, naming the local Maven file repository the outer build
   * published {@code publishSkillsTestRepo}'s modules into. {@link #clarityScan}/{@link
   * #clarityCheck} forward it to the nested build as {@code -PnarrativetraceTestMavenRepo=<path>}
   * so the {@code clarity-consumer} fixture's {@code settings.gradle.kts} resolves the plugin and
   * its libraries from that repository instead of {@code includeBuild}-ing this whole checkout as a
   * composite — the composite path pushed the standalone snapshot verify's JVM census high enough
   * to get the outer Gradle daemon OOM-killed. Absent (property unset or blank), the fixture falls
   * back to its own auto-detection, unchanged.
   */
  private static final String TEST_MAVEN_REPO_PROPERTY = "narrativetrace.testMavenRepo";

  private SkillReplayer() {}

  private static final Map<String, Function<Path, Boolean>> KNOWN_COMMANDS =
      Map.ofEntries(
          Map.entry(DoctorCommands.BUILD_PROJECT, root -> gradleTask(root, ":sixty-seconds:build")),
          Map.entry(DoctorCommands.RUN_PROJECT, root -> gradleTask(root, ":sixty-seconds:run")),
          // sixty-seconds deliberately does not apply the ai.narrativetrace plugin (it depends on
          // the in-tree modules by source, not published coordinates — see its own build.gradle.kts
          // comment) — the fixture-scoped real invocation of the same doctor classes the plugin
          // task calls in-process is the CLI module's own printDoctorReport task instead. The
          // installer preview is mapped the same way, onto printInitDiff.
          Map.entry(
              DoctorCommands.RUN_DOCTOR_GRADLE,
              root ->
                  gradleTask(
                      root,
                      ":narrativetrace-cli:printDoctorReport",
                      "-PnarrativetraceDoctorTarget=" + FIXTURE)),
          Map.entry(InstallerCommands.PREVIEW_INSTALL, SkillReplayer::installPreviewWroteADiffOnly),
          Map.entry(
              DoctorCommands.NO_STALE_RECEIVED_FILE,
              root -> gitStatusIsClean(root, FIXTURE + "/src/test/narratives")),
          Map.entry(DoctorCommands.FIND_RENDERED_TRACES, SkillReplayer::renderedTraceExists),
          Map.entry(ClarityCommands.CLEAN_STATIC_SCAN, SkillReplayer::clarityScan),
          Map.entry(ClarityCommands.CLEAN_CLARITY_CHECK, SkillReplayer::clarityCheck),
          Map.entry(
              ClarityCommands.FIND_JSON_REPORT,
              root -> clarityReportExists(root, "clarity-scan-results.json")),
          Map.entry(
              ClarityCommands.FIND_MARKDOWN_REPORT,
              root -> clarityReportExists(root, "clarity-scan-report.md")),
          Map.entry(FeedbackCommands.DRAFT_REPORT, root -> feedbackChannel(root, "draft")),
          Map.entry(FeedbackCommands.PRINT_URL, root -> feedbackChannel(root, "url")),
          Map.entry(FeedbackCommands.FIND_FEEDBACK_FILES, SkillReplayer::feedbackFilesExist));

  /** Prose verify strings this replayer additionally knows how to check mechanically. */
  private static final Map<String, Function<Path, Boolean>> KNOWN_VERIFIES =
      Map.of(
          DoctorCommands.VERIFY_TWELVE_FINDINGS,
          SkillReplayer::reportHasTwelveFindings,
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
  /**
   * The feedback verb, on one channel, against the fixture.
   *
   * <p>The exit value is the oracle: the verb exits 2 when a value-free rule refuses the report, so
   * a refusal fails the nested build and this returns false. The file check afterwards is the other
   * half — a verb that exited 0 without writing anything would be a verb that did nothing.
   */
  private static boolean feedbackChannel(Path repoRoot, String channel) {
    return gradleTask(
            repoRoot,
            ":narrativetrace-cli:printFeedbackDraft",
            "-PnarrativetraceDoctorTarget=" + FIXTURE,
            "-PnarrativetraceFeedbackChannel=" + channel)
        && feedbackFilesExist(repoRoot);
  }

  /** Both files the verb writes, where the skill's own steps tell a reader to look for them. */
  private static boolean feedbackFilesExist(Path repoRoot) {
    Path directory = repoRoot.resolve(FIXTURE).resolve("build/narrativetrace/feedback");
    return Files.isRegularFile(directory.resolve("feedback-draft.md"))
        && Files.isRegularFile(directory.resolve("feedback-body.md"));
  }

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

  /**
   * Replays the installer PREVIEW: the diff is produced, and the fixture is untouched.
   *
   * <p>Both halves matter, and the second is the one a green exit code cannot express — a preview
   * that quietly installed would pass a command-only replay. The skills root and the context file
   * are asserted absent afterwards, at the fixture's own paths.
   */
  private static boolean installPreviewWroteADiffOnly(Path repoRoot) {
    Path fixture = repoRoot.resolve(FIXTURE);
    Path diff = fixture.resolve("build/narrativetrace/init-diff.txt");
    if (!gradleTask(
        repoRoot, ":narrativetrace-cli:printInitDiff", "-PnarrativetraceDoctorTarget=" + FIXTURE)) {
      return false;
    }
    return Files.exists(diff)
        && readTextOrEmpty(diff).contains("+++ b/AGENTS.md")
        && !Files.exists(fixture.resolve("AGENTS.md"))
        && !Files.exists(fixture.resolve(".agents"));
  }

  /**
   * A file's text, or "" when it cannot be read — a missing diff is a failed replay, not a crash.
   */
  private static String readTextOrEmpty(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      return "";
    }
  }

  /** Runs the real plugin task in the standalone clarity consumer, never a root task. */
  private static boolean clarityScan(Path repoRoot) {
    Path consumer =
        repoRoot.resolve("narrativetrace-skills-catalogue/evals/fixtures/clarity-consumer");
    try {
      GradleRunner.create()
          .withProjectDir(consumer.toFile())
          .withArguments(clarityGradleArguments("clarityScan"))
          .build();
      return clarityReportExists(repoRoot, "clarity-scan-results.json")
          && clarityReportExists(repoRoot, "clarity-scan-report.md");
    } catch (UnexpectedBuildFailure failure) {
      return false;
    }
  }

  /**
   * The {@code ./gradlew clean <task>} arguments {@link #clarityScan}/{@link #clarityCheck} pass to
   * the nested build: {@code -PnarrativetraceTestMavenRepo=<path>} appended when the outer build
   * set {@link #TEST_MAVEN_REPO_PROPERTY}, omitted otherwise so the fixture's own auto-detection
   * (env var, then the composite checkout) still applies — e.g. a developer running the fixture by
   * hand, or the Tier B eval trials. Pure and unit-testable on purpose: the two call sites only
   * resolve the system property and hand it here.
   */
  static List<String> clarityGradleArguments(String task) {
    return clarityGradleArguments(task, System.getProperty(TEST_MAVEN_REPO_PROPERTY));
  }

  static List<String> clarityGradleArguments(String task, String testMavenRepoPath) {
    if (testMavenRepoPath == null || testMavenRepoPath.isBlank()) {
      return List.of("clean", task);
    }
    return List.of("clean", task, "-PnarrativetraceTestMavenRepo=" + testMavenRepoPath);
  }

  private static boolean clarityReportExists(Path repoRoot, String fileName) {
    Path report =
        repoRoot
            .resolve(
                "narrativetrace-skills-catalogue/evals/fixtures/clarity-consumer/build/narrativetrace")
            .resolve(fileName);
    try {
      return Files.isRegularFile(report) && Files.size(report) > 0;
    } catch (IOException e) {
      throw new UncheckedIOException("could not inspect " + report, e);
    }
  }

  private static boolean clarityCheck(Path repoRoot) {
    Path consumer =
        repoRoot.resolve("narrativetrace-skills-catalogue/evals/fixtures/clarity-consumer");
    try {
      GradleRunner.create()
          .withProjectDir(consumer.toFile())
          .withArguments(clarityGradleArguments("clarityCheck"))
          .build();
      return clarityReportExists(repoRoot, "clarity-results.json")
          && clarityReportExists(repoRoot, "clarity-report.md");
    } catch (UnexpectedBuildFailure failure) {
      return false;
    }
  }

  private static boolean reportContains(Path repoRoot, String needle) {
    return reportText(repoRoot).map(text -> text.contains(needle)).orElse(false);
  }

  private static boolean reportHasTwelveFindings(Path repoRoot) {
    return reportText(repoRoot)
        .map(text -> Pattern.compile("\"id\":").matcher(text).results().count() == 12)
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
