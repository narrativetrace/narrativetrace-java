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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

  /**
   * The published site is the product surface the init prompt names ("read llms.txt first"), so a
   * trial must let the agent read it with {@code curl} — through the real curl behind the stand-in,
   * with the request still on the record. Every other host still gets curl's own "could not resolve
   * host", exit 6.
   */
  @Test
  void theCurlStandInServesThePublishedSiteThroughTheRealCurlBehindIt(@TempDir Path dir)
      throws Exception {
    TrialEnvironment environment = under(dir.resolve("work"), dir);
    Path realCurl = fakeRealCurl(dir);

    Process process =
        curlThroughTheStandIn(
            environment, realCurl, "-sS", "https://narrativetrace.ai/java/llms.txt");

    assertThat(process.waitFor()).isZero();
    assertThat(new String(process.getInputStream().readAllBytes()))
        .contains("REAL -sS https://narrativetrace.ai/java/llms.txt");
    assertThat(environment.ghInvocations())
        .content()
        .contains("curl -sS https://narrativetrace.ai/java/llms.txt");
  }

  @Test
  void theCurlStandInStillAnswersCouldNotResolveForAnyOtherHost(@TempDir Path dir)
      throws Exception {
    TrialEnvironment environment = under(dir.resolve("work"), dir);
    Path realCurl = fakeRealCurl(dir);

    Process process =
        curlThroughTheStandIn(environment, realCurl, "https://api.github.com/repos/x/y/issues");

    assertThat(process.waitFor()).isEqualTo(6);
    assertThat(new String(process.getInputStream().readAllBytes())).doesNotContain("REAL");
    assertThat(environment.ghInvocations())
        .content()
        .contains("curl https://api.github.com/repos/x/y/issues");
  }

  @Test
  void oneForeignUrlInTheSameInvocationBlocksTheWholeRequest(@TempDir Path dir) throws Exception {
    TrialEnvironment environment = under(dir.resolve("work"), dir);
    Path realCurl = fakeRealCurl(dir);

    Process process =
        curlThroughTheStandIn(
            environment, realCurl, "https://narrativetrace.ai/llms.txt", "https://evil.example/x");

    assertThat(process.waitFor()).isEqualTo(6);
    assertThat(new String(process.getInputStream().readAllBytes())).doesNotContain("REAL");
  }

  @Test
  void aCurlInvocationNamingNoPublishedUrlIsNotServed(@TempDir Path dir) throws Exception {
    TrialEnvironment environment = under(dir.resolve("work"), dir);
    Path realCurl = fakeRealCurl(dir);

    Process process = curlThroughTheStandIn(environment, realCurl, "--version");

    assertThat(process.waitFor()).isEqualTo(6);
    assertThat(new String(process.getInputStream().readAllBytes())).doesNotContain("REAL");
  }

  /**
   * A web-framework case's program is a server, and "run the program" means requesting its own
   * endpoint — the Spring Boot case's agent would otherwise hit exit 6 on {@code
   * http://localhost:8080/…} and could not see the trace it was asked to paste. Loopback reaches no
   * network, so it is served like the published site; written with or without a scheme, as people
   * type it.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://localhost:8080/accounts/ACC-17",
        "http://localhost",
        "https://localhost/x",
        "http://127.0.0.1:8080/accounts/ACC-17",
        "localhost:8080/accounts/ACC-17",
        "localhost",
        "127.0.0.1:8080",
        "127.0.0.1/x"
      })
  void theCurlStandInServesLoopbackThroughTheRealCurlBehindIt(String url, @TempDir Path dir)
      throws Exception {
    TrialEnvironment environment = under(dir.resolve("work"), dir);
    Path realCurl = fakeRealCurl(dir);

    Process process = curlThroughTheStandIn(environment, realCurl, "-s", url);

    assertThat(process.waitFor()).isZero();
    assertThat(new String(process.getInputStream().readAllBytes())).contains("REAL -s " + url);
    assertThat(environment.blockedInvocations()).content().contains("curl -s " + url);
  }

  /**
   * Near misses: a host that merely STARTS with a loopback name, and the userinfo form, where
   * everything before the {@code @} is credentials and the host curl actually contacts follows it.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://localhost.evil.example/x",
        "http://127.0.0.1.evil.example/x",
        "localhost.evil.example",
        "http://localhost:8080@evil.example/x",
        "localhost:8080@evil.example",
        "https://narrativetrace.ai@evil.example/llms.txt"
      })
  void aHostThatOnlyLooksServedIsNotServed(String url, @TempDir Path dir) throws Exception {
    TrialEnvironment environment = under(dir.resolve("work"), dir);
    Path realCurl = fakeRealCurl(dir);

    Process process = curlThroughTheStandIn(environment, realCurl, url);

    assertThat(process.waitFor()).as(url).isEqualTo(6);
    assertThat(new String(process.getInputStream().readAllBytes())).doesNotContain("REAL");
  }

  /**
   * The stand-in classifies EVERY argument, so the lines agents really type have to stay served:
   * the first is verbatim from a 2026-10-09 trial fetching llms.txt; the rest are how an agent
   * requests its own endpoint.
   */
  static List<List<String>> curlLinesAgentsType() {
    return List.of(
        List.of(
            "-sS",
            "-L",
            "--max-time",
            "30",
            "-o",
            "llms.txt",
            "-w",
            "HTTP %{http_code} %{content_type} %{size_download} bytes\\n",
            "https://narrativetrace.ai/java/llms.txt"),
        List.of("-fsSL", "https://narrativetrace.ai/java/llms.txt"),
        List.of("-s", "http://localhost:8080/accounts/ACC-17"),
        List.of("-i", "localhost:8080/accounts/ACC-17"),
        List.of("-X", "GET", "http://localhost:8080/x", "-H", "Accept: application/json"),
        List.of("-s", "-o", "/dev/null", "-w", "%{http_code}", "http://127.0.0.1:8080/x"),
        List.of("--silent", "--show-error", "--url", "http://localhost:8080/x"),
        List.of("--max-time=5", "--url=http://[::1]:8080/x"));
  }

  @ParameterizedTest
  @MethodSource("curlLinesAgentsType")
  void theCurlLinesAgentsTypeAreServed(List<String> argv, @TempDir Path dir) throws Exception {
    TrialEnvironment environment = under(dir.resolve("work"), dir);
    Path realCurl = fakeRealCurl(dir);

    Process process = curlThroughTheStandIn(environment, realCurl, argv.toArray(String[]::new));

    assertThat(process.waitFor()).as(String.join(" ", argv)).isZero();
    assertThat(new String(process.getInputStream().readAllBytes())).startsWith("REAL");
  }

  /** A stand-in for the real curl, placed BEHIND the trial's stub on PATH by the caller. */
  private static Path fakeRealCurl(Path dir) throws IOException {
    Path bin = Files.createDirectories(dir.resolve("real-bin"));
    Path curl = bin.resolve("curl");
    Files.writeString(
        curl,
        "#!/bin/sh\nprintf 'REAL'\nfor a in \"$@\"; do printf ' %s' \"$a\"; done\nprintf '\\n'\n");
    assertThat(curl.toFile().setExecutable(true)).isTrue();
    return curl;
  }

  private static Process curlThroughTheStandIn(
      TrialEnvironment environment, Path realCurl, String... args) throws IOException {
    String stubDir = environment.agentEnvironment().get("PATH").split(":")[0];
    List<String> command = new java.util.ArrayList<>(List.of(stubDir + "/curl"));
    command.addAll(List.of(args));
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    builder.environment().put("PATH", stubDir + ":" + realCurl.getParent());
    return builder.start();
  }
}
