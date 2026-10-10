/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Adversarial probes for the Phase 6 M2 changes: the curl stand-in's served hosts in {@link
 * TrialEnvironment}, and the root-README skip in {@link EvalTrial}.
 */
class AdversarialPhase6M2Test {

  private static final String SERVED = "https://narrativetrace.ai/llms.txt";
  private static final EvalRunnerArgs ARGS =
      new EvalRunnerArgs("narrativetrace-doctor", "happy-path", Platform.CODEX, "mini", null, 1);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC);

  // ---- curl stand-in ------------------------------------------------------------------------

  /** The served forms that must pass: port forms of the loopback names. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://localhost:0/x",
        "https://127.0.0.1:65535/x",
        "http://localhost:8080",
        "localhost:0"
      })
  void loopbackPortFormsAreServed(String url, @TempDir Path dir) throws Exception {
    assertServed(url, dir);
  }

  /**
   * Refused on purpose, the safe side: hosts are case-insensitive to curl, but POSIX {@code case}
   * has no portable lowercasing, and no agent types a loopback URL in capitals. Pinned so a later
   * "fix" that matches capitals has to match them everywhere, foreign hosts included.
   */
  @ParameterizedTest
  @ValueSource(strings = {"HTTP://LOCALHOST:8080/x", "https://NARRATIVETRACE.AI/llms.txt"})
  void aServedHostInCapitalsIsRefusedRatherThanLowercased(String url, @TempDir Path dir)
      throws Exception {
    assertBlocked(dir, url);
  }

  /**
   * SUSPECTED DEFECT (over-block): curl takes the host to end at {@code ?} or {@code #}, so these
   * name a served host, but the case patterns require {@code :} or {@code /} after the host.
   */
  @ParameterizedTest
  @ValueSource(strings = {"localhost?x=1", "127.0.0.1#frag", "https://narrativetrace.ai?ref=1"})
  void aServedHostFollowedByQueryOrFragmentIsServed(String url, @TempDir Path dir)
      throws Exception {
    assertServed(url, dir);
  }

  /**
   * SUSPECTED DEFECT (over-block): {@code [::1]} is the IPv6 loopback, which reaches no network any
   * more than {@code 127.0.0.1} does, yet the loopback list names only the two IPv4 forms.
   */
  @Test
  void theIpv6LoopbackIsServedLikeTheIpv4One(@TempDir Path dir) throws Exception {
    assertServed("http://[::1]:8080/x", dir);
  }

  /**
   * SUSPECTED DEFECT (over-block): the userinfo rule matches any {@code @} anywhere after the
   * scheme, but credentials can only precede the host, which ends at the first {@code /}, {@code ?}
   * or {@code #}. An {@code @} in the path or query of a published URL is not userinfo.
   */
  @Test
  void anAtSignInTheQueryOfAServedUrlDoesNotMakeItUserinfo(@TempDir Path dir) throws Exception {
    assertServed("https://narrativetrace.ai/llms.txt?email=a@b", dir);
  }

  /**
   * SUSPECTED DEFECT (egress): a served URL in the same invocation does not protect a foreign URL
   * written WITHOUT a scheme, since the foreign-URL check tests only for a leading {@code http://}
   * or {@code https://}. The real curl then contacts evil.example.
   */
  @Test
  void aSchemelessForeignUrlBesideAServedUrlBlocksTheRequest(@TempDir Path dir) throws Exception {
    assertBlocked(dir, SERVED, "evil.example/x");
  }

  /**
   * SUSPECTED DEFECT (egress): curl speaks many schemes; the stand-in only refuses foreign {@code
   * http(s)} URLs, so {@code ftp://} beside a served URL reaches the real curl.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {"ftp://evil.example/x", "sftp://evil.example/x", "gopher://evil.example/"})
  void aNonHttpForeignUrlBesideAServedUrlBlocksTheRequest(String foreign, @TempDir Path dir)
      throws Exception {
    assertBlocked(dir, SERVED, foreign);
  }

  /**
   * SUSPECTED DEFECT (egress): the scheme check is case-sensitive, so {@code HTTP://evil.example}
   * slips past it, while curl matches schemes case-insensitively and fetches it.
   */
  @Test
  void anUppercaseSchemeForeignUrlBesideAServedUrlBlocksTheRequest(@TempDir Path dir)
      throws Exception {
    assertBlocked(dir, SERVED, "HTTP://evil.example/x");
  }

  /**
   * SUSPECTED DEFECT (egress): {@code --url=VALUE} carries a URL in an argument that starts with
   * the option name, so the leading-scheme check never sees it. {@code --url VALUE} is blocked.
   */
  @Test
  void aForeignUrlGivenAsUrlEqualsBesideAServedUrlBlocksTheRequest(@TempDir Path dir)
      throws Exception {
    assertBlocked(dir, SERVED, "--url=https://evil.example/x");
  }

  /**
   * SUSPECTED DEFECT (egress): userinfo without a scheme. curl reads {@code localhost@evil.example}
   * as user {@code localhost} at host {@code evil.example}; the userinfo rule covers only {@code
   * localhost:PORT@} and {@code localhost/…@} forms, so this bare form passes beside a served URL.
   */
  @Test
  void aSchemelessUserinfoBesideAServedUrlBlocksTheRequest(@TempDir Path dir) throws Exception {
    assertBlocked(dir, SERVED, "localhost@evil.example/x");
  }

  /**
   * SUSPECTED DEFECT (egress): a proxy option names a host. The stand-in does not inspect options,
   * so {@code -x evil.example:3128} beside a served URL routes the served request through a foreign
   * proxy.
   */
  @Test
  void aForeignProxyBesideAServedUrlBlocksTheRequest(@TempDir Path dir) throws Exception {
    Path realCurl = fakeRealCurl(dir);
    TrialEnvironment environment = TrialEnvironment.under(dir.resolve("work"), dir);

    Process process =
        curlThroughTheStandIn(environment, realCurl, "-x", "evil.example:3128", SERVED);

    assertThat(process.waitFor()).isEqualTo(6);
    assertThat(stdout(process)).doesNotContain("REAL");
  }

  /**
   * SUSPECTED DEFECT (over-match): {@code :*} is a glob, so any text after the colon of a served
   * host is "a port". {@code narrativetrace.ai:evil.example} is not a served URL and must be
   * refused, not handed to the real curl.
   */
  @Test
  void aServedHostFollowedByANonNumericPortIsNotServed(@TempDir Path dir) throws Exception {
    assertBlocked(dir, "https://narrativetrace.ai:evil.example/llms.txt");
  }

  /** Guard, passes today: a served host with a longer name is refused on its own. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://narrativetrace.aix/llms.txt",
        "https://narrativetrace.ai:443@evil.example/llms.txt",
        "localhost@evil.example",
        "127.0.0.1.evil.example/x"
      })
  void aNearMissHostIsRefusedOnItsOwn(String url, @TempDir Path dir) throws Exception {
    assertBlocked(dir, url);
  }

  /** Guard, passes today: a userinfo form beside a served URL still blocks the request. */
  @Test
  void aLoopbackUserinfoBesideAServedUrlStillBlocksTheRequest(@TempDir Path dir) throws Exception {
    assertBlocked(dir, SERVED, "localhost:8080@evil.example/x");
  }

  // ---- EvalTrial root README ----------------------------------------------------------------

  /**
   * SUSPECTED DEFECT (leak): the skip matches the exact name {@code README.md}. The same
   * maintainer-facing document under another casing or extension, at the fixture root, is copied to
   * the agent, which can then read that it is being graded.
   */
  @ParameterizedTest
  @ValueSource(strings = {"readme.md", "Readme.md", "README.markdown", "README.txt", "README"})
  void aRootReadmeUnderAnyNameIsNeverCopiedToTheAgent(String name, @TempDir Path tempDir)
      throws Exception {
    Path fixture = fixtureWithMarker(tempDir);
    Files.writeString(fixture.resolve(name), "the grader reads the request trace");
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));

    runTrial(fixture, tempDir, scratch);

    assertThat(scratch.resolve(name)).as(name).doesNotExist();
    assertThat(scratch.resolve("marker.txt")).hasContent("fixture-content");
  }

  /** Passes today: a lowercase README one directory down is the project's own, so it is copied. */
  @Test
  void aNestedLowercaseReadmeIsTheProjectsOwnAndIsCopied(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarker(tempDir);
    Files.createDirectories(fixture.resolve("docs"));
    Files.writeString(fixture.resolve("docs/readme.md"), "the project's notes");
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));

    runTrial(fixture, tempDir, scratch);

    assertThat(scratch.resolve("docs/readme.md")).hasContent("the project's notes");
  }

  /** Passes today: a generated build directory is never copied, its README included. */
  @Test
  void aBuildDirectoryIsNotCopiedAndTheMarkerIsStillIs(@TempDir Path tempDir) throws Exception {
    Path fixture = fixtureWithMarker(tempDir);
    Files.createDirectories(fixture.resolve("build"));
    Files.writeString(fixture.resolve("build/README.md"), "generated");
    Path scratch = Files.createDirectory(tempDir.resolve("scratch"));

    runTrial(fixture, tempDir, scratch);

    assertThat(scratch.resolve("build")).doesNotExist();
    assertThat(scratch.resolve("marker.txt")).hasContent("fixture-content");
  }

  // ---- helpers ------------------------------------------------------------------------------

  private static void assertServed(String url, Path dir) throws Exception {
    TrialEnvironment environment = TrialEnvironment.under(dir.resolve("work"), dir);
    Path realCurl = fakeRealCurl(dir);

    Process process = curlThroughTheStandIn(environment, realCurl, "-s", url);

    assertThat(process.waitFor()).as(url).isZero();
    assertThat(stdout(process)).as(url).contains("REAL -s " + url);
  }

  private static void assertBlocked(Path dir, String... urls) throws Exception {
    TrialEnvironment environment = TrialEnvironment.under(dir.resolve("work"), dir);
    Path realCurl = fakeRealCurl(dir);
    List<String> args = new ArrayList<>(List.of(urls));

    Process process = curlThroughTheStandIn(environment, realCurl, args.toArray(String[]::new));

    assertThat(process.waitFor()).as(String.join(" ", urls)).isEqualTo(6);
    assertThat(stdout(process)).as(String.join(" ", urls)).doesNotContain("REAL");
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
    List<String> command = new ArrayList<>(List.of(stubDir + "/curl"));
    command.addAll(List.of(args));
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    builder.environment().put("PATH", stubDir + ":" + realCurl.getParent());
    return builder.start();
  }

  private static String stdout(Process process) throws IOException {
    return new String(process.getInputStream().readAllBytes());
  }

  private static Path fixtureWithMarker(Path tempDir) throws IOException {
    Path fixture = Files.createDirectory(tempDir.resolve("fixture"));
    Files.writeString(fixture.resolve("marker.txt"), "fixture-content");
    return fixture;
  }

  private static void runTrial(Path fixture, Path tempDir, Path scratch) throws Exception {
    Path repo = Files.createDirectory(tempDir.resolve("repo"));
    var trial = new EvalTrial(new FakeProcessRunner(0), FIXED_CLOCK, tempDir.resolve("runs.jsonl"));
    trial.run(ARGS, repo, fixture, tempDir, scratch, AgentTurns.graded("prompt"), 1);
  }

  /** Replays exit codes in call order; the trial's own agent and grader are never spawned. */
  private static final class FakeProcessRunner implements EvalTrial.ProcessRunner {
    private final Deque<Integer> exitCodes;

    FakeProcessRunner(Integer... codes) {
      this.exitCodes = new ArrayDeque<>(List.of(codes));
    }

    @Override
    public int run(List<String> argv, Path cwd, Map<String, String> env) {
      return exitCodes.pop();
    }
  }
}
