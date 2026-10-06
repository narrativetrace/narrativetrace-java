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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The environment every command of a trial runs with, and the evidence a grader reads afterwards:
 * the transcript, the recording {@code gh} stand-in and its log. All of it lives in a work
 * directory OUTSIDE the scaffolded project, so the agent cannot read what it is being graded on and
 * cannot write it either.
 */
class TrialEnvironmentTest {

  private static TrialEnvironment under(Path work, Path repoRoot) throws IOException {
    return TrialEnvironment.under(work, repoRoot);
  }

  @Test
  void aTrialThatRecordsNothingHasNoEvidenceAndAddsNoEnvironment() {
    TrialEnvironment environment = TrialEnvironment.none();

    assertThat(environment.isRecording()).isFalse();
    assertThat(environment.agentEnvironment()).isEmpty();
    assertThat(environment.graderEnvironment()).isEmpty();
  }

  @Test
  void theEvidenceLivesInTheWorkDirectoryAndNeverInTheProject(@TempDir Path dir)
      throws IOException {
    Path work = dir.resolve("work");
    Path project = Files.createDirectories(dir.resolve("scratch"));

    TrialEnvironment environment = under(work, dir);

    assertThat(environment.isRecording()).isTrue();
    assertThat(environment.transcript()).startsWithRaw(work).doesNotExist();
    assertThat(environment.ghInvocations()).startsWithRaw(work);
    assertThat(List.of(environment.transcript(), environment.ghInvocations()))
        .noneMatch(path -> path.startsWith(project));
  }

  /**
   * The whole reason a stub is installed rather than relying on {@code gh} being absent: absence
   * makes "tried to file" and "did not try" the same observation, and this is the evidence that
   * tells them apart. It also means no trial can create a real issue anywhere, whatever an agent
   * does.
   */
  @Test
  void installsAGhStandInThatRecordsWhatItWasAskedAndFilesNothing(@TempDir Path dir)
      throws Exception {
    TrialEnvironment environment = under(dir.resolve("work"), dir);

    int exit =
        new ProcessBuilder(
                environment.agentEnvironment().get("PATH").split(":")[0] + "/gh",
                "issue",
                "create",
                "--title",
                "doctor: trap.redaction-proof is wrong")
            .start()
            .waitFor();

    assertThat(exit).isZero();
    assertThat(environment.ghInvocations())
        .content()
        .contains("gh issue create --title doctor: trap.redaction-proof is wrong");
  }

  @Test
  void theGhStandInIsFirstOnTheAgentsPathWithTheInheritedPathBehindIt(@TempDir Path dir)
      throws IOException {
    TrialEnvironment environment = under(dir.resolve("work"), dir);

    String path = environment.agentEnvironment().get("PATH");

    assertThat(path).startsWith(dir.resolve("work").resolve("bin") + ":");
    assertThat(path).endsWith(System.getenv("PATH"));
  }

  /**
   * An agent told where its own transcript is could read what it is being graded on, and an agent
   * told where the {@code gh} log is could rewrite it. The grader is told; the agent is not.
   */
  @Test
  void theAgentIsNeverToldWhereItsOwnEvidenceIs(@TempDir Path dir) throws IOException {
    TrialEnvironment environment = under(dir.resolve("work"), dir);

    assertThat(environment.agentEnvironment())
        .doesNotContainKey("NARRATIVETRACE_TRANSCRIPT")
        .doesNotContainKey("NARRATIVETRACE_GH_LOG");
    assertThat(environment.agentEnvironment().values())
        .noneMatch(value -> value.contains(environment.transcript().toString()));
  }

  @Test
  void theGraderIsToldWhereBothPiecesOfEvidenceAre(@TempDir Path dir) throws IOException {
    TrialEnvironment environment = under(dir.resolve("work"), dir);

    assertThat(environment.graderEnvironment())
        .containsEntry("NARRATIVETRACE_TRANSCRIPT", environment.transcript().toString())
        .containsEntry("NARRATIVETRACE_GH_LOG", environment.ghInvocations().toString())
        .containsKey("PATH");
  }

  /**
   * A fixture scaffolded into a temp directory has nothing relative to it that can find this
   * checkout, so the one route to the plugin and the libraries is a local Maven repository named
   * from outside. {@code ORG_GRADLE_PROJECT_} is how a project property reaches a {@code ./gradlew}
   * line the AGENT types — a {@code -P} flag only reaches one the harness types.
   */
  @Test
  void theAgentCanResolveNarrativeTraceFromTheLocalTestRepositoryWhenItHasBeenPublished(
      @TempDir Path dir) throws IOException {
    Path repoRoot = Files.createDirectories(dir.resolve("repo"));
    Path testRepo =
        Files.createDirectories(
            repoRoot.resolve("narrativetrace-skills-catalogue/build/test-repo"));

    TrialEnvironment environment = under(dir.resolve("work"), repoRoot);

    assertThat(environment.agentEnvironment())
        .containsEntry("ORG_GRADLE_PROJECT_narrativetraceTestMavenRepo", testRepo.toString());
  }

  /**
   * Absent rather than empty: a property pointing at a repository that was never published resolves
   * nothing and reports it as "could not resolve the plugin", which reads like a broken fixture
   * instead of a forgotten `publishSkillsTestRepo`.
   */
  @Test
  void thePropertyIsAbsentWhenTheTestRepositoryHasNotBeenPublished(@TempDir Path dir)
      throws IOException {
    TrialEnvironment environment =
        under(dir.resolve("work"), Files.createDirectories(dir.resolve("repo")));

    assertThat(environment.agentEnvironment())
        .doesNotContainKey("ORG_GRADLE_PROJECT_narrativetraceTestMavenRepo");
  }

  @Test
  void recordsEachTurnAsOneJsonLineNamingTheTurnAndTheUsersOwnWords(@TempDir Path dir)
      throws IOException {
    TrialEnvironment environment = under(dir.resolve("work"), dir);

    environment.recordUserTurn(1, "report this to NarrativeTrace");
    environment.recordUserTurn(2, "yes, file it");

    assertThat(Files.readAllLines(environment.transcript()))
        .containsExactly(
            "{\"nt_turn\":1,\"role\":\"user\",\"text\":\"report this to NarrativeTrace\"}",
            "{\"nt_turn\":2,\"role\":\"user\",\"text\":\"yes, file it\"}");
  }

  /**
   * A prompt is a whole file and a reply is somebody's sentence, so both carry quotes, backslashes
   * and line breaks. A marker that let one through raw would end the line early, and a grader
   * reading the transcript line by line would attribute the rest of the prompt to the agent.
   */
  @Test
  void aMarkerStaysOneLineWhateverTheTurnsTextCarries(@TempDir Path dir) throws IOException {
    TrialEnvironment environment = under(dir.resolve("work"), dir);

    environment.recordUserTurn(1, "say \"yes\"\r\nor C:\\no\tnow\u0001");

    assertThat(Files.readAllLines(environment.transcript()))
        .containsExactly(
            "{\"nt_turn\":1,\"role\":\"user\","
                + "\"text\":\"say \\\"yes\\\"\\r\\nor C:\\\\no\\tnow\\u0001\"}");
  }

  @Test
  void aTrialThatRecordsNothingRefusesToBeAskedForItsEvidence() {
    TrialEnvironment environment = TrialEnvironment.none();

    assertThatThrownBy(environment::transcript).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(environment::ghInvocations).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> environment.recordUserTurn(1, "anything"))
        .isInstanceOf(IllegalStateException.class);
  }

  /**
   * The stub embeds its log path in a POSIX single-quoted string, so a path carrying a single quote
   * would close it and turn the rest into shell words.
   */
  @Test
  void refusesAWorkDirectoryWhosePathCouldBreakOutOfTheStubsOwnQuoting(@TempDir Path dir) {
    assertThatThrownBy(() -> under(dir.resolve("wo'rk"), dir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'");
  }

  /**
   * A trial's work directory is a temp directory the runner deletes when the trial ends, whatever
   * its outcome — which is right for a pass and useless for a fail: the grader's one-line reason is
   * on the console and the record it read is gone. Kept evidence is how a red row gets diagnosed.
   */
  @Test
  void keepsTheEvidenceSomewhereDurableWhenAskedTo(@TempDir Path dir) throws IOException {
    TrialEnvironment environment = under(dir.resolve("work"), dir);
    environment.recordUserTurn(1, "report it");
    Files.writeString(environment.ghInvocations(), "gh issue create\n");

    Path kept = environment.keepEvidenceUnder(dir.resolve("keep"));

    assertThat(kept.resolve("transcript.jsonl"))
        .hasContent("{\"nt_turn\":1,\"role\":\"user\",\"text\":\"report it\"}");
    assertThat(kept.resolve("gh-invocations.log")).hasContent("gh issue create");
  }

  /** A trial where `gh` was never run has no log to keep, and that is not a failure to keep it. */
  @Test
  void keepsWhatExistsAndDoesNotInventWhatDoesNot(@TempDir Path dir) throws IOException {
    TrialEnvironment environment = under(dir.resolve("work"), dir);
    environment.recordUserTurn(1, "report it");

    Path kept = environment.keepEvidenceUnder(dir.resolve("keep"));

    assertThat(kept.resolve("transcript.jsonl")).exists();
    assertThat(kept.resolve("gh-invocations.log")).doesNotExist();
  }

  @Test
  void aTrialThatRecordsNothingHasNoEvidenceToKeep(@TempDir Path dir) {
    assertThatThrownBy(() -> TrialEnvironment.none().keepEvidenceUnder(dir))
        .isInstanceOf(IllegalStateException.class);
  }

  /**
   * EVERY trial runs against a throwaway agent configuration, not only a registry one.
   *
   * <p>Until this held, a trial inherited the configuration of the person running it: the first
   * feedback trial's own `system/init` event listed FORTY skills and thirty tools belonging to this
   * machine, among which the skill under test was one line. A preset narrower than its prompt
   * measures the sandbox (the lesson round 2 of the init-prompt cases left in `evals/README.md`); a
   * CONFIGURATION wider than the product measures the operator's laptop, and a red row would not
   * reproduce anywhere else.
   */
  @Test
  void everyTrialRunsAgainstAThrowawayAgentConfiguration(@TempDir Path dir) throws IOException {
    Path work = dir.resolve("work");

    TrialEnvironment environment = under(work, dir);

    assertThat(environment.agentEnvironment())
        .containsEntry("CLAUDE_CONFIG_DIR", IsolatedAgentConfig.configDir(work).toString());
    assertThat(environment.graderEnvironment()).containsKey("CLAUDE_CONFIG_DIR");
  }

  /** A trial that records nothing isolates nothing either: it drives no agent. */
  @Test
  void aTrialThatRecordsNothingNamesNoConfigurationDirectory() {
    assertThat(TrialEnvironment.none().agentEnvironment()).doesNotContainKey("CLAUDE_CONFIG_DIR");
  }

  /**
   * `curl` is stubbed for the same reason `gh` is, and the reason was earned: a real trial of the
   * feedback skill spent a whole turn making about forty requests to `api.github.com` and
   * `raw.githubusercontent.com`, hunting for the repository to file into. A Tier B trial must reach
   * no network at all — it cannot file anything anywhere, and a result that depended on a live
   * third-party endpoint would not be a result about our skill.
   *
   * <p>It exits NON-ZERO, unlike the `gh` stand-in: 6 is curl's own "could not resolve host", which
   * is the truthful answer to a request in a sandbox with no network, where exiting 0 with empty
   * output would read as "that repository has nothing in it".
   */
  @Test
  void installsACurlStandInThatReachesNothingAndSaysSo(@TempDir Path dir) throws Exception {
    TrialEnvironment environment = under(dir.resolve("work"), dir);

    int exit =
        new ProcessBuilder(
                environment.agentEnvironment().get("PATH").split(":")[0] + "/curl",
                "-s",
                "https://api.github.com/repos/narrativetrace/narrativetrace-java")
            .start()
            .waitFor();

    assertThat(exit).isEqualTo(6);
    assertThat(environment.blockedInvocations())
        .content()
        .contains("curl -s https://api.github.com/repos/narrativetrace/narrativetrace-java");
  }

  /** One log for both stand-ins, so "what did this trial try to reach?" is one file. */
  @Test
  void bothStandInsRecordIntoTheSameLog(@TempDir Path dir) throws Exception {
    TrialEnvironment environment = under(dir.resolve("work"), dir);
    String bin = environment.agentEnvironment().get("PATH").split(":")[0];

    new ProcessBuilder(bin + "/gh", "issue", "create").start().waitFor();
    new ProcessBuilder(bin + "/curl", "https://example.com").start().waitFor();

    assertThat(environment.blockedInvocations())
        .content()
        .contains("gh issue create")
        .contains("curl https://example.com");
    assertThat(environment.ghInvocations()).isEqualTo(environment.blockedInvocations());
  }
}
