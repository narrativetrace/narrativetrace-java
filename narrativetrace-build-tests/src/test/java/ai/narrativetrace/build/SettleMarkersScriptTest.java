/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit- and end-to-end-tests {@code scripts/settle-markers.sh}: the post-release step that rewrites
 * {@code *(since X.Y.Z, unreleased)*} markers to {@code *(since X.Y.Z)*} once a release's registry
 * publish is confirmed live, the private-tree counterpart of the release-publish script's own
 * tag-time rewrite (which only ever touches its staged snapshot). Same technique {@link
 * VerifyPublicationScriptTest} uses for its own script: pure functions sourced (no network, no git,
 * {@code main()} never runs), a loopback {@code HttpServer} fixture standing in for the registry's
 * real HTTP surface, and a handful of real end-to-end runs against a throwaway git fixture with a
 * stub {@code gradlew}.
 */
class SettleMarkersScriptTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));
  private static final File SCRIPT = new File(PROJECT_DIR, "scripts/settle-markers.sh");

  private record ScriptResult(String output, int exitCode) {}

  /**
   * Sources the script ({@code main()} never runs — see the script's own bottom guard) then
   * evaluates one call.
   */
  private ScriptResult sourced(String call) throws IOException, InterruptedException {
    return sourced(call, Map.of());
  }

  private ScriptResult sourced(String call, Map<String, String> env)
      throws IOException, InterruptedException {
    var body = "set -e\nsource '" + SCRIPT.getAbsolutePath() + "'\n" + call + "\n";
    var command = new java.util.ArrayList<String>(List.of("bash", "-c", body));
    var processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
    processBuilder.environment().putAll(env);
    return run(processBuilder);
  }

  /** Runs the script for real (not sourced — {@code main()} executes), directory {@code repo}. */
  private ScriptResult run(Path repo, Map<String, String> env, String... args)
      throws IOException, InterruptedException {
    var command = new java.util.ArrayList<String>();
    command.add(SCRIPT.getAbsolutePath());
    command.addAll(List.of(args));
    var processBuilder =
        new ProcessBuilder(command).redirectErrorStream(true).directory(repo.toFile());
    processBuilder.environment().putAll(env);
    return run(processBuilder);
  }

  private ScriptResult run(ProcessBuilder processBuilder) throws IOException, InterruptedException {
    var process = processBuilder.start();
    var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    var exitCode = process.waitFor();
    return new ScriptResult(output, exitCode);
  }

  // -----------------------------------------------------------------------------------------
  // is_release_version
  // -----------------------------------------------------------------------------------------

  @Test
  void plainXyzVersionIsAReleaseVersion() throws Exception {
    var result = sourced("if is_release_version 0.2.3; then echo yes; else echo no; fi");

    assertThat(result.output().strip()).isEqualTo("yes");
  }

  @Test
  void aSnapshotQualifierIsNotAReleaseVersion() throws Exception {
    var result = sourced("if is_release_version 0.2.3-SNAPSHOT; then echo yes; else echo no; fi");

    assertThat(result.output().strip()).isEqualTo("no");
  }

  @Test
  void anEmptyOrMalformedVersionIsRejected() throws Exception {
    var result = sourced("if is_release_version '1.2'; then echo yes; else echo no; fi");

    assertThat(result.output().strip()).isEqualTo("no");
  }

  // -----------------------------------------------------------------------------------------
  // registry_has_version — loopback HTTP fixture, no real network
  // -----------------------------------------------------------------------------------------

  @Test
  void registryHasVersionIsTrueForA200Directory() throws Exception {
    var server = startRegistryServer("9.9.9");
    try {
      var result =
          sourced(
              "if registry_has_version 9.9.9; then echo present; else echo missing; fi",
              Map.of("MAVEN_CENTRAL_BASE", server.baseUrl()));

      assertThat(result.output().strip()).isEqualTo("present");
    } finally {
      server.httpServer().stop(0);
    }
  }

  @Test
  void registryHasVersionIsFalseForAVersionTheServerDoesNotServe() throws Exception {
    var server = startRegistryServer("9.9.9");
    try {
      var result =
          sourced(
              "if registry_has_version 0.0.1; then echo present; else echo missing; fi",
              Map.of("MAVEN_CENTRAL_BASE", server.baseUrl()));

      assertThat(result.output().strip()).isEqualTo("missing");
    } finally {
      server.httpServer().stop(0);
    }
  }

  @Test
  void registryHasVersionIsFalseWhenTheRegistryIsUnreachable() throws Exception {
    var result =
        sourced(
            "if registry_has_version 0.2.3; then echo present; else echo missing; fi",
            Map.of("MAVEN_CENTRAL_BASE", "http://127.0.0.1:1"));

    assertThat(result.output().strip()).isEqualTo("missing");
  }

  // -----------------------------------------------------------------------------------------
  // rewrite_markers — the brief fixture: one marker for X, one for a later version, one in a
  // translated mirror. Only X's settles; the later version survives; the mirror is reported
  // touched (restamp_translated_mirrors below picks that up).
  // -----------------------------------------------------------------------------------------

  private Path writeFixtureDocs(Path repo) throws IOException {
    Files.createDirectories(repo.resolve("documentation/es"));
    Files.writeString(
        repo.resolve("documentation/guide.md"),
        "Landed *(since 0.2.3, unreleased)*. Preview *(since 0.9.0, unreleased)*.\n");
    Files.writeString(
        repo.resolve("documentation/es/guide.md"),
        "<!-- source: documentation/guide.md blob 000000000000 | translated: 2026-09-01 |"
            + " reviewed: - -->\n"
            + "Disponible *(since 0.2.3, unreleased)*.\n");
    Files.writeString(repo.resolve("README.md"), "Root note *(since 0.2.3, unreleased)*.\n");
    return repo;
  }

  @Test
  void onlyTheCitedVersionsMarkersAreRewrittenALaterVersionSurvives(@TempDir Path repo)
      throws Exception {
    writeFixtureDocs(repo);

    var result =
        sourced(
            "rewrite_markers '"
                + repo
                + "' 0.2.3; echo \"SETTLED=$SETTLED\"; printf"
                + " 'TOUCHED=%s\\n' \"$MARKER_TOUCHED\"");

    assertThat(result.exitCode()).as("output: %s", result.output()).isZero();
    assertThat(result.output()).contains("SETTLED=3");
    assertThat(Files.readString(repo.resolve("documentation/guide.md")))
        .contains("Landed *(since 0.2.3)*.")
        .contains("Preview *(since 0.9.0, unreleased)*.");
    assertThat(Files.readString(repo.resolve("documentation/es/guide.md")))
        .contains("Disponible *(since 0.2.3)*.");
    assertThat(Files.readString(repo.resolve("README.md"))).contains("Root note *(since 0.2.3)*.");
  }

  @Test
  void everyTouchedFileIsListedInMarkerTouched(@TempDir Path repo) throws Exception {
    writeFixtureDocs(repo);

    var result = sourced("rewrite_markers '" + repo + "' 0.2.3; printf '%s' \"$MARKER_TOUCHED\"");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output())
        .contains(repo.resolve("documentation/guide.md").toString())
        .contains(repo.resolve("documentation/es/guide.md").toString())
        .contains(repo.resolve("README.md").toString());
  }

  @Test
  void aMarkerHardWrappedRightAfterSinceIsStillFound(@TempDir Path repo) throws Exception {
    Files.createDirectories(repo.resolve("documentation"));
    Files.writeString(
        repo.resolve("documentation/guide.md"), "Landed *(since\n0.2.3, unreleased)*.\n");

    var result = sourced("rewrite_markers '" + repo + "' 0.2.3; echo \"SETTLED=$SETTLED\"");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("SETTLED=1");
    assertThat(Files.readString(repo.resolve("documentation/guide.md")))
        .isEqualTo("Landed *(since 0.2.3)*.\n");
  }

  @Test
  void aMarkerHardWrappedAfterTheVersionCommaIsStillFound(@TempDir Path repo) throws Exception {
    Files.createDirectories(repo.resolve("documentation"));
    Files.writeString(
        repo.resolve("documentation/guide.md"), "Landed *(since 0.2.3,\nunreleased)*.\n");

    var result = sourced("rewrite_markers '" + repo + "' 0.2.3; echo \"SETTLED=$SETTLED\"");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("SETTLED=1");
  }

  @Test
  void aSourceOrTestFileOutsideDocumentationAndReadmeIsNeverTouched(@TempDir Path repo)
      throws Exception {
    Files.createDirectories(repo.resolve("documentation"));
    Files.writeString(
        repo.resolve("documentation/guide.md"), "Landed *(since 0.2.3, unreleased)*.\n");
    Files.createDirectories(repo.resolve("src/test/resources"));
    var fixture = repo.resolve("src/test/resources/marker-fixture.md");
    Files.writeString(fixture, "Fixture text *(since 0.2.3, unreleased)*.\n");

    sourced("rewrite_markers '" + repo + "' 0.2.3");

    assertThat(fixture).content().isEqualTo("Fixture text *(since 0.2.3, unreleased)*.\n");
  }

  /** Retro rule 2: nothing to settle is a mistake, not a success — the zero-marker case is red. */
  @Test
  void zeroMarkersForTheVersionIsARedRunNotASilentNoOp(@TempDir Path repo) throws Exception {
    Files.createDirectories(repo.resolve("documentation"));
    Files.writeString(repo.resolve("documentation/guide.md"), "Nothing for this version here.\n");

    var result =
        sourced(
            "if rewrite_markers '" + repo + "' 0.2.3; then echo exit=0; else echo \"exit=$?\"; fi");

    assertThat(result.output()).contains("exit=1").contains("ERROR:").contains("nothing to settle");
  }

  // -----------------------------------------------------------------------------------------
  // restamp_translated_mirrors — hash-only restamp of a touched mirror's header
  // -----------------------------------------------------------------------------------------

  @Test
  void aTranslatedMirrorOfATouchedSourceGetsItsHashRestampedNeverItsDates(@TempDir Path repo)
      throws Exception {
    writeFixtureDocs(repo);

    var result =
        sourced(
            "rewrite_markers '"
                + repo
                + "' 0.2.3 >/dev/null; restamp_translated_mirrors '"
                + repo
                + "' \"$MARKER_TOUCHED\"; echo \"RESTAMPED=$REMARK_RESTAMPED\"");

    assertThat(result.exitCode()).as("output: %s", result.output()).isZero();
    assertThat(result.output()).contains("RESTAMPED=1");
    var header =
        Files.readString(repo.resolve("documentation/es/guide.md"))
            .lines()
            .findFirst()
            .orElseThrow();
    assertThat(header)
        .startsWith("<!-- source: documentation/guide.md blob ")
        .doesNotContain("blob 000000000000")
        .contains("translated: 2026-09-01")
        .contains("reviewed: -");
  }

  @Test
  void aMirrorWhoseSourceTheRewriteNeverTouchedIsNotRestamped(@TempDir Path repo) throws Exception {
    Files.createDirectories(repo.resolve("documentation/es"));
    Files.writeString(repo.resolve("documentation/untouched.md"), "No marker here at all.\n");
    Files.writeString(
        repo.resolve("documentation/es/untouched.md"),
        "<!-- source: documentation/untouched.md blob 000000000000 | translated: 2026-09-01 |"
            + " reviewed: - -->\nSin marcador.\n");

    var result =
        sourced(
            "restamp_translated_mirrors '" + repo + "' ''; echo \"RESTAMPED=$REMARK_RESTAMPED\"");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("RESTAMPED=0");
    assertThat(Files.readString(repo.resolve("documentation/es/untouched.md")))
        .contains("blob 000000000000");
  }

  // -----------------------------------------------------------------------------------------
  // main() end-to-end: argument parsing, working-tree and registry preconditions
  // -----------------------------------------------------------------------------------------

  @Test
  void noArgumentPrintsUsageAndFailsNonZero(@TempDir Path repo) throws Exception {
    var result = run(repo, Map.of());

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("Usage: scripts/settle-markers.sh <version>");
  }

  @Test
  void aSnapshotVersionArgumentIsRefused(@TempDir Path repo) throws Exception {
    var result = run(repo, Map.of(), "0.2.3-SNAPSHOT");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("is not a plain X.Y.Z release version");
  }

  @Test
  void aDirtyWorkingTreeIsRefusedBeforeAnyNetworkCall(@TempDir Path repo) throws Exception {
    initGitRepo(repo);
    Files.writeString(repo.resolve("dirty.txt"), "uncommitted\n");

    var result =
        run(
            repo,
            Map.of("REPO_ROOT", repo.toString(), "MAVEN_CENTRAL_BASE", "http://127.0.0.1:1"),
            "0.2.3");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("working tree not clean");
  }

  @Test
  void aVersionNotOnTheRegistryIsRefusedWithAClearMessageAndNothingIsRewritten(@TempDir Path repo)
      throws Exception {
    initGitRepo(repo);
    writeFixtureDocs(repo);
    runGit(repo, "add", "-A");
    runGit(repo, "commit", "-q", "-m", "docs");
    var server = startRegistryServer("9.9.9"); // never 0.2.3
    try {
      var result =
          run(
              repo,
              Map.of("REPO_ROOT", repo.toString(), "MAVEN_CENTRAL_BASE", server.baseUrl()),
              "0.2.3");

      assertThat(result.exitCode()).isNotZero();
      assertThat(result.output())
          .contains("is not on the registry yet")
          .contains("A settle must never run ahead of the publish it settles");
      assertThat(Files.readString(repo.resolve("documentation/guide.md")))
          .contains("(since 0.2.3, unreleased)*");
    } finally {
      server.httpServer().stop(0);
    }
  }

  /**
   * Full happy path: registry present, markers rewritten, mirror restamped, a stub {@code gradlew}
   * stands in for the real {@code snippetSync} build task (never exercised here — that belongs to
   * the gate build, not this fast unit test).
   */
  @Test
  void aFullSettleRunRewritesRestampsAndInvokesTheBannerTask(@TempDir Path repo) throws Exception {
    initGitRepo(repo);
    writeFixtureDocs(repo);
    var gradlewLog = repo.resolve("gradlew-invocations.log");
    writeStubGradlew(repo, gradlewLog);
    runGit(repo, "add", "-A");
    runGit(repo, "commit", "-q", "-m", "docs");
    var server = startRegistryServer("0.2.3");
    try {
      var result =
          run(
              repo,
              Map.of("REPO_ROOT", repo.toString(), "MAVEN_CENTRAL_BASE", server.baseUrl()),
              "0.2.3");

      assertThat(result.exitCode()).as("output: %s", result.output()).isZero();
      assertThat(result.output())
          .contains("markers settled: 3")
          .contains("headers restamped: 1")
          .contains("Settle complete: 3 marker(s) settled for 0.2.3");
      assertThat(Files.readString(repo.resolve("documentation/guide.md")))
          .contains("Landed *(since 0.2.3)*.")
          .contains("Preview *(since 0.9.0, unreleased)*.");
      assertThat(gradlewLog).content().contains("snippetSync");
    } finally {
      server.httpServer().stop(0);
    }
  }

  private void writeStubGradlew(Path repo, Path log) throws IOException {
    var gradlew = repo.resolve("gradlew");
    Files.writeString(gradlew, "#!/usr/bin/env bash\necho \"$@\" >> '" + log + "'\nexit 0\n");
    gradlew.toFile().setExecutable(true);
  }

  /**
   * The morning-of-2026-09-17 regression: {@code snippetSync} re-embeds fresh content into an
   * English page that carries no since-marker of its own (the real incident was timing digits
   * landing back in {@code sixty-seconds.md}). A settle run must still restamp that page's
   * translated mirror — restamping only the MARKER-touched set misses it, because this page was
   * never in that set to begin with.
   */
  @Test
  void aPageOnlySnippetSyncTouchesWithNoMarkerOfItsOwnStillGetsItsMirrorRestamped(
      @TempDir Path repo) throws Exception {
    initGitRepo(repo);
    writeFixtureDocs(repo);
    Files.writeString(
        repo.resolve("documentation/unmarked.md"), "Some content, no marker at all.\n");
    Files.writeString(
        repo.resolve("documentation/es/unmarked.md"),
        "<!-- source: documentation/unmarked.md blob 000000000000 | translated: 2026-09-01 |"
            + " reviewed: - -->\nContenido sin marcador.\n");
    var gradlewLog = repo.resolve("gradlew-invocations.log");
    writeStubGradlewThatMutatesAnUnmarkedPage(repo, gradlewLog);
    runGit(repo, "add", "-A");
    runGit(repo, "commit", "-q", "-m", "docs");
    var server = startRegistryServer("0.2.3");
    try {
      var result =
          run(
              repo,
              Map.of("REPO_ROOT", repo.toString(), "MAVEN_CENTRAL_BASE", server.baseUrl()),
              "0.2.3");

      assertThat(result.exitCode()).as("output: %s", result.output()).isZero();
      var mirrorHeader =
          Files.readString(repo.resolve("documentation/es/unmarked.md"))
              .lines()
              .findFirst()
              .orElseThrow();
      assertThat(mirrorHeader)
          .as("output: %s", result.output())
          .startsWith("<!-- source: documentation/unmarked.md blob ")
          .doesNotContain("blob 000000000000");
      // The marker-touched mirror (guide.md's) is still restamped too — this is additive, never a
      // regression of the existing behaviour.
      var markerMirrorHeader =
          Files.readString(repo.resolve("documentation/es/guide.md"))
              .lines()
              .findFirst()
              .orElseThrow();
      assertThat(markerMirrorHeader).doesNotContain("blob 000000000000");
    } finally {
      server.httpServer().stop(0);
    }
  }

  /**
   * A stub {@code gradlew} that, on the {@code snippetSync} invocation, also mutates {@code
   * documentation/unmarked.md} — standing in for the real task re-embedding fresh content into a
   * page that carries no since-marker of its own.
   */
  private void writeStubGradlewThatMutatesAnUnmarkedPage(Path repo, Path log) throws IOException {
    var gradlew = repo.resolve("gradlew");
    Files.writeString(
        gradlew,
        "#!/usr/bin/env bash\n"
            + "echo \"$@\" >> '"
            + log
            + "'\n"
            + "echo 'Freshly re-embedded content.' >> '"
            + repo.resolve("documentation/unmarked.md")
            + "'\n"
            + "exit 0\n");
    gradlew.toFile().setExecutable(true);
  }

  // -----------------------------------------------------------------------------------------
  // fixtures
  // -----------------------------------------------------------------------------------------

  private record RegistryServer(HttpServer httpServer, String baseUrl) {}

  /**
   * A loopback HTTP server answering 200 to a HEAD on {@code
   * ai/narrativetrace/narrativetrace-core/<presentVersion>/} and 404 to every other path — the real
   * curl call {@code registry_has_version} makes, no real network involved.
   */
  private RegistryServer startRegistryServer(String presentVersion) throws IOException {
    var loopback = java.net.InetAddress.getLoopbackAddress();
    var server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
    var presentPath = "/ai/narrativetrace/narrativetrace-core/" + presentVersion + "/";
    server.createContext(
        "/",
        exchange -> {
          var status = exchange.getRequestURI().getPath().equals(presentPath) ? 200 : 404;
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    server.start();
    return new RegistryServer(
        server, "http://" + loopback.getHostAddress() + ":" + server.getAddress().getPort());
  }

  private void initGitRepo(Path dir) throws IOException, InterruptedException {
    runGit(dir, "init", "-q");
    runGit(dir, "config", "user.email", "settle-markers-test@example.com");
    runGit(dir, "config", "user.name", "settle-markers-test");
    runGit(dir, "config", "commit.gpgsign", "false");
    Files.writeString(dir.resolve("seed.txt"), "seed\n");
    runGit(dir, "add", "seed.txt");
    runGit(dir, "commit", "-q", "-m", "seed");
  }

  private void runGit(Path dir, String... args) throws IOException, InterruptedException {
    var command = new java.util.ArrayList<String>(List.of("git"));
    command.addAll(List.of(args));
    var process =
        new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
    var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    var exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IOException(
          "git " + String.join(" ", args) + " failed (" + exitCode + "): " + output);
    }
  }
}
