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
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit-tests {@code scripts/verify-publication.sh} without a release: the pure coordinate/URL
 * functions (sourced, no network), argument parsing, the local-repository presence check {@code
 * --local-rehearsal} relies on, version resolution for an omitted {@code <version>} (a throwaway
 * git fixture for the tag path, a loopback {@code HttpServer} fixture for the Maven Central
 * metadata fallback — real git and real curl, no real network), and a real {@code --dry-run}
 * against this checkout's own build. What is deliberately NOT here: a real poll against Maven
 * Central / the Gradle Plugin Portal, and a real consumer-smoke-test build — both make real network
 * calls and belong to a human running the script itself against an actual release, never a
 * per-commit gate (see the script's own header comment and the publish checklist).
 */
class VerifyPublicationScriptTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));
  private static final File SCRIPT = new File(PROJECT_DIR, "scripts/verify-publication.sh");

  private record ScriptResult(String output, int exitCode) {}

  /**
   * Sources the script (main() never runs — see the script's own bottom guard) then evaluates one
   * call.
   */
  private ScriptResult sourced(String call) throws IOException, InterruptedException {
    return sourced(call, Map.of());
  }

  private ScriptResult sourced(String call, String localMavenRepo)
      throws IOException, InterruptedException {
    Map<String, String> env =
        localMavenRepo == null ? Map.of() : Map.of("LOCAL_MAVEN_REPO", localMavenRepo);
    return sourced(call, env);
  }

  /**
   * Sources the script with additional environment overrides — {@code REPO_ROOT} and {@code
   * MAVEN_CENTRAL_BASE} are overridable the same way {@code LOCAL_MAVEN_REPO} always has been, so a
   * test can point either at a fixture instead of this real checkout / the real Maven Central.
   */
  private ScriptResult sourced(String call, Map<String, String> env)
      throws IOException, InterruptedException {
    var body = "set -e\nsource '" + SCRIPT.getAbsolutePath() + "'\n" + call + "\n";
    var command = new java.util.ArrayList<String>(List.of("bash", "-c", body));
    var processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
    processBuilder.environment().putAll(env);
    return run(processBuilder);
  }

  private ScriptResult run(String... args) throws IOException, InterruptedException {
    return run(Map.of(), args);
  }

  /**
   * Runs the script for real (not sourced — {@code main()} executes) with environment overrides,
   * the same {@code REPO_ROOT} idiom {@code sourced} uses: a test points it at a throwaway git repo
   * so version resolution never reads this checkout's own tags.
   */
  private ScriptResult run(Map<String, String> env, String... args)
      throws IOException, InterruptedException {
    var command = new java.util.ArrayList<String>();
    command.add(SCRIPT.getAbsolutePath());
    command.addAll(List.of(args));
    var processBuilder =
        new ProcessBuilder(command).redirectErrorStream(true).directory(PROJECT_DIR);
    processBuilder.environment().putAll(env);
    return run(processBuilder);
  }

  private ScriptResult run(ProcessBuilder processBuilder) throws IOException, InterruptedException {
    var process = processBuilder.start();
    var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    var exitCode = process.waitFor();
    return new ScriptResult(output, exitCode);
  }

  // --- pure functions: coordinate/URL construction --------------------------------------

  @Test
  void groupPathReplacesDotsWithSlashes() throws Exception {
    var result = sourced("group_path ai.narrativetrace");

    assertThat(result.output().strip()).isEqualTo("ai/narrativetrace");
  }

  @Test
  void mavenJarUrlBuildsTheCentralLayout() throws Exception {
    var result =
        sourced(
            "maven_jar_url https://repo1.maven.org/maven2 ai.narrativetrace narrativetrace-core"
                + " 0.2.0");

    assertThat(result.output().strip())
        .isEqualTo(
            "https://repo1.maven.org/maven2/ai/narrativetrace/narrativetrace-core/0.2.0/narrativetrace-core-0.2.0.jar");
  }

  @Test
  void mavenPomUrlBuildsTheCentralLayout() throws Exception {
    var result =
        sourced(
            "maven_pom_url https://repo1.maven.org/maven2 ai.narrativetrace narrativetrace-core"
                + " 0.2.0");

    assertThat(result.output().strip())
        .isEqualTo(
            "https://repo1.maven.org/maven2/ai/narrativetrace/narrativetrace-core/0.2.0/narrativetrace-core-0.2.0.pom");
  }

  @Test
  void pluginMarkerUrlUsesThePortalBaseAndTheMarkerArtifactId() throws Exception {
    var result =
        sourced(
            "maven_pom_url https://plugins.gradle.org/m2 ai.narrativetrace"
                + " ai.narrativetrace.gradle.plugin 0.2.0");

    assertThat(result.output().strip())
        .isEqualTo(
            "https://plugins.gradle.org/m2/ai/narrativetrace/ai.narrativetrace.gradle.plugin/0.2.0"
                + "/ai.narrativetrace.gradle.plugin-0.2.0.pom");
  }

  @Test
  void classifyHttpStatus200IsPresent() throws Exception {
    var result = sourced("classify_http_status 200");

    assertThat(result.output().strip()).isEqualTo("PRESENT");
  }

  @Test
  void classifyHttpStatus404IsLagging() throws Exception {
    var result = sourced("classify_http_status 404");

    assertThat(result.output().strip()).isEqualTo("LAGGING");
  }

  @Test
  void classifyHttpStatus500IsMissing() throws Exception {
    var result = sourced("classify_http_status 500");

    assertThat(result.output().strip()).isEqualTo("MISSING");
  }

  /** curl's own placeholder for "never got a response at all" (DNS failure, connection refused). */
  @Test
  void classifyHttpStatus000ConnectionFailureIsMissing() throws Exception {
    var result = sourced("classify_http_status 000");

    assertThat(result.output().strip()).isEqualTo("MISSING");
  }

  @Test
  void resourceUrlsForLibraryIncludesBothJarAndPom() throws Exception {
    var result = sourced("resource_urls_for LIBRARY ai.narrativetrace narrativetrace-core 0.2.0");

    assertThat(result.output().lines())
        .hasSize(2)
        .anyMatch(line -> line.endsWith("narrativetrace-core-0.2.0.pom"))
        .anyMatch(line -> line.endsWith("narrativetrace-core-0.2.0.jar"));
  }

  /**
   * Regression test: {@code resource_urls_for}'s original implementation ended with a bare {@code [
   * "$kind" = "LIBRARY" ] && maven_jar_url ...} as its last statement. Under {@code set -e}, a
   * false guard in that position returns non-zero from the function itself, and every caller — a
   * bare statement or the left side of a {@code pipefail}-checked pipe — then aborted the whole
   * script. A PLUGIN coordinate (no jar, marker POM only) hit exactly this path.
   */
  @Test
  void resourceUrlsForPluginIncludesOnlyThePomAndDoesNotAbort() throws Exception {
    var result =
        sourced(
            "resource_urls_for PLUGIN ai.narrativetrace ai.narrativetrace.gradle.plugin 0.2.0; echo"
                + " REACHED_END");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output().lines()).hasSize(2);
    assertThat(result.output())
        .contains("ai.narrativetrace.gradle.plugin-0.2.0.pom")
        .doesNotContain(".jar")
        .contains("REACHED_END");
  }

  // --- local-rehearsal presence detection (offline, no real publish needed) -------------

  @Test
  void localCheckOneReportsPresentWhenJarAndPomBothExist(@TempDir Path repo) throws Exception {
    writeFakeLocalArtifact(repo, "ai.narrativetrace", "narrativetrace-api", "9.9.9-test", true);

    var result =
        sourced(
            "local_check_one LIBRARY ai.narrativetrace narrativetrace-api 9.9.9-test",
            repo.toString());

    assertThat(result.output().strip()).isEqualTo("PRESENT");
  }

  @Test
  void localCheckOneReportsMissingWhenPomAbsent(@TempDir Path repo) throws Exception {
    var result =
        sourced(
            "local_check_one LIBRARY ai.narrativetrace narrativetrace-core 9.9.9-test",
            repo.toString());

    assertThat(result.output().strip()).isEqualTo("MISSING");
  }

  @Test
  void localCheckOneReportsMissingWhenJarAbsentForLibrary(@TempDir Path repo) throws Exception {
    writeFakeLocalArtifact(repo, "ai.narrativetrace", "narrativetrace-core", "9.9.9-test", false);

    var result =
        sourced(
            "local_check_one LIBRARY ai.narrativetrace narrativetrace-core 9.9.9-test",
            repo.toString());

    assertThat(result.output().strip()).isEqualTo("MISSING");
  }

  /** A plugin marker artifact has no jar — only its POM is checked. */
  @Test
  void localCheckOnePluginMarkerNeedsOnlyThePom(@TempDir Path repo) throws Exception {
    writeFakeLocalArtifact(
        repo, "ai.narrativetrace", "ai.narrativetrace.gradle.plugin", "9.9.9-test", false);

    var result =
        sourced(
            "local_check_one PLUGIN ai.narrativetrace ai.narrativetrace.gradle.plugin 9.9.9-test",
            repo.toString());

    assertThat(result.output().strip()).isEqualTo("PRESENT");
  }

  private void writeFakeLocalArtifact(
      Path repo, String group, String artifact, String version, boolean withJar)
      throws IOException {
    var dir = repo.resolve(group.replace('.', '/')).resolve(artifact).resolve(version);
    Files.createDirectories(dir);
    Files.writeString(dir.resolve(artifact + "-" + version + ".pom"), "<project/>");
    if (withJar) {
      Files.writeString(dir.resolve(artifact + "-" + version + ".jar"), "not a real jar");
    }
  }

  // --- scaffold generation (offline: files only, no Gradle invocation) ------------------

  @Test
  void writeSmokeProjectGeneratesTheDocumentedFirstTenMinutesRecipe(@TempDir Path dir)
      throws Exception {
    var result =
        sourced(
            "write_smoke_project '" + dir + "' 9.9.9-test 'gradlePluginPortal()' 'mavenCentral()'");

    assertThat(result.exitCode()).isZero();
    assertThat(dir.resolve("settings.gradle.kts")).content().contains("gradlePluginPortal()");
    assertThat(dir.resolve("build.gradle.kts"))
        .content()
        .contains("id(\"ai.narrativetrace\") version \"9.9.9-test\"")
        .contains("mavenCentral()");
    assertThat(dir.resolve("src/main/java/com/example/orders/OrderService.java"))
        .content()
        .contains("interface OrderService");
    assertThat(dir.resolve("src/main/java/com/example/orders/DefaultOrderService.java"))
        .content()
        .contains("implements OrderService");
    assertThat(dir.resolve("src/test/java/com/example/orders/OrderServiceTest.java"))
        .content()
        .contains("NarrativeTraceProxy.trace")
        .contains("customerPlacesOrder");
  }

  @Test
  void writeSmokeProjectSwapsInMavenLocalUnderLocalRehearsal(@TempDir Path dir) throws Exception {
    var result =
        sourced("write_smoke_project '" + dir + "' 9.9.9-test 'mavenLocal()' 'mavenLocal()'");

    assertThat(result.exitCode()).isZero();
    assertThat(dir.resolve("settings.gradle.kts")).content().contains("mavenLocal()");
    assertThat(dir.resolve("build.gradle.kts")).content().contains("mavenLocal()");
  }

  // --- sourcing does not run main(); argument parsing on a real invocation --------------

  @Test
  void sourcingTheScriptRunsNoNetworkCallsAndPrintsNothingOnItsOwn() throws Exception {
    var result = sourced("echo SOURCED_OK");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output().strip()).isEqualTo("SOURCED_OK");
  }

  @Test
  void helpFlagPrintsUsageAndExitsZero() throws Exception {
    var result = run("--help");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("Usage: scripts/verify-publication.sh [<version>]");
  }

  @Test
  void unknownFlagFailsWithNonZeroExit() throws Exception {
    var result = run("0.2.0", "--bogus-flag");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("ERROR: unknown argument '--bogus-flag'");
  }

  @Test
  void nonNumericTimeoutFailsWithNonZeroExit() throws Exception {
    var result = run("0.2.0", "--timeout=soon");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("--timeout must be a whole number of seconds");
  }

  @Test
  void extraPositionalArgumentFailsWithNonZeroExit() throws Exception {
    var result = run("0.2.0", "0.3.0");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("ERROR: unexpected extra argument '0.3.0'");
  }

  // --- version resolution when <version> is omitted ---------------------------------------
  //
  // The family finding this fixes: a scheduled run with no version input must never default to
  // the working tree's own gradle.properties version — that moves on to the next -SNAPSHOT the
  // instant a release is cut, so a run reading it polls for artifacts that were never going to
  // exist. Every fixture below gives gradle.properties a version deliberately AHEAD of / different
  // from the correct answer, so a resolve_version that ever consulted it would give itself away.

  @Test
  void latestTagVersionReturnsTheNewestReachableTagWithoutTheVPrefix(@TempDir Path repo)
      throws Exception {
    initGitRepoWithTag(repo, "v1.2.3");

    var result = sourced("latest_tag_version", Map.of("REPO_ROOT", repo.toString()));

    assertThat(result.exitCode()).isZero();
    assertThat(result.output().strip()).isEqualTo("1.2.3");
  }

  @Test
  void latestTagVersionFailsWhenNoTagIsReachable(@TempDir Path repo) throws Exception {
    initGitRepo(repo);

    // `if`, not a bare statement: under `set -e` a bare failing call would abort the sourcing
    // shell before the echo ever ran — the same footgun resource_urls_for's own comment (above)
    // documents.
    var result =
        sourced(
            "if latest_tag_version >/dev/null 2>&1; then echo exit=0; else echo \"exit=$?\"; fi",
            Map.of("REPO_ROOT", repo.toString()));

    assertThat(result.output().strip()).isEqualTo("exit=1");
  }

  // The 2026-09-15 family finding: `publish-public.sh --tag` mints the public tag object with its
  // OWN embedded name set to "public/$TAG" while the ref it ends up at (after the push) is
  // "refs/tags/$TAG" -- ref and embedded name disagree. `git tag -a <name>` always makes the two
  // match, so a fixture has to build the object under its embedded name first, then re-home the
  // SAME object onto the target ref (the object is untouched; only which ref points at it
  // changes) -- exactly the shape a consumer clone of a real public release ends up with. Every
  // test below is red against today's `latest_tag_version`, which trusts `git describe`'s printed
  // name (the OBJECT's embedded name) instead of the ref it actually resolved through; git itself
  // warns "tag '$TAG' is externally known as 'public/$TAG'" on such a repo.

  private void tagWithMismatchedEmbeddedName(
      Path dir, String refName, String embeddedName, String message)
      throws IOException, InterruptedException {
    runGit(dir, "tag", "-a", embeddedName, "-m", message, "HEAD");
    if (!embeddedName.equals(refName)) {
      runGit(dir, "update-ref", "refs/tags/" + refName, "refs/tags/" + embeddedName);
      runGit(dir, "update-ref", "-d", "refs/tags/" + embeddedName);
    }
  }

  @Test
  void latestTagVersionUsesTheRefNameNeverTheTagObjectsEmbeddedName(@TempDir Path repo)
      throws Exception {
    initGitRepo(repo);
    tagWithMismatchedEmbeddedName(repo, "v1.2.3", "public/v1.2.3", "v1.2.3");

    var result = sourced("latest_tag_version", Map.of("REPO_ROOT", repo.toString()));

    assertThat(result.exitCode()).isZero();
    assertThat(result.output().strip()).isEqualTo("1.2.3");
  }

  @Test
  void latestTagVersionStillFindsTheMismatchedTagWhenHeadIsSeveralCommitsAhead(@TempDir Path repo)
      throws Exception {
    initGitRepo(repo);
    tagWithMismatchedEmbeddedName(repo, "v1.2.3", "public/v1.2.3", "v1.2.3");
    for (int i = 0; i < 3; i++) {
      Files.writeString(repo.resolve("f" + i + ".txt"), "c" + i + "\n");
      runGit(repo, "add", "f" + i + ".txt");
      runGit(repo, "commit", "-q", "-m", "commit " + i);
    }

    var result = sourced("latest_tag_version", Map.of("REPO_ROOT", repo.toString()));

    assertThat(result.exitCode()).isZero();
    assertThat(result.output().strip()).isEqualTo("1.2.3");
  }

  /**
   * Two {@code v*} tags reachable from HEAD, the newer one carrying the embedded-name mismatch:
   * proves the fix picks the newest by ancestry through the ref, not whatever {@code git describe}
   * happens to print for it.
   */
  @Test
  void latestTagVersionPicksTheNewestOfTwoReachableTagsByAncestry(@TempDir Path repo)
      throws Exception {
    initGitRepoWithTag(repo, "v1.0.0");
    Files.writeString(repo.resolve("f.txt"), "c\n");
    runGit(repo, "add", "f.txt");
    runGit(repo, "commit", "-q", "-m", "commit after v1.0.0");
    tagWithMismatchedEmbeddedName(repo, "v2.0.0", "public/v2.0.0", "v2.0.0");

    var result = sourced("latest_tag_version", Map.of("REPO_ROOT", repo.toString()));

    assertThat(result.exitCode()).isZero();
    assertThat(result.output().strip()).isEqualTo("2.0.0");
  }

  // Not duplicated here: a lightweight tag (initGitRepoWithTag, no -a) is already exercised by
  // latestTagVersionReturnsTheNewestReachableTagWithoutTheVPrefix, and "no v* tag reachable" by
  // latestTagVersionFailsWhenNoTagIsReachable (both above) -- neither behaviour changes under
  // this fix, and both already pass today, so a fresh copy of either would be a defective (green
  // before implementation) test.

  @Test
  void resolveVersionWithAReachableTagUsesItAndNeverConsultsGradleProperties(@TempDir Path repo)
      throws Exception {
    initGitRepoWithTag(repo, "v3.4.5");
    Files.writeString(repo.resolve("gradle.properties"), "narrativetraceVersion=99.9.9-SNAPSHOT\n");

    var result =
        sourced(
            "resolve_version; echo \"$VERSION|$VERSION_SOURCE\"",
            Map.of("REPO_ROOT", repo.toString()));

    assertThat(result.exitCode()).isZero();
    assertThat(result.output().strip()).startsWith("3.4.5|");
    assertThat(result.output())
        .contains("newest v* tag reachable from HEAD")
        .doesNotContain("99.9.9");
  }

  @Test
  void resolveVersionWithoutATagFallsBackToMavenCentralMetadataLatest(@TempDir Path repo)
      throws Exception {
    initGitRepo(repo);
    Files.writeString(repo.resolve("gradle.properties"), "narrativetraceVersion=42.0.0-SNAPSHOT\n");
    var server = startMetadataServer("2.7.1");
    try {
      var result =
          sourced(
              "resolve_version; echo \"$VERSION|$VERSION_SOURCE\"",
              Map.of("REPO_ROOT", repo.toString(), "MAVEN_CENTRAL_BASE", server.baseUrl()));

      assertThat(result.exitCode()).isZero();
      assertThat(result.output().strip()).startsWith("2.7.1|");
      assertThat(result.output())
          .contains("Maven Central maven-metadata.xml <latest>")
          .contains("no v* tag found")
          .doesNotContain("42.0.0");
    } finally {
      server.httpServer().stop(0);
    }
  }

  @Test
  void resolveVersionFailsWhenNoTagAndMetadataUnreachable(@TempDir Path repo) throws Exception {
    initGitRepo(repo);

    var result =
        sourced(
            "if resolve_version; then echo \"exit=0 $VERSION\"; else echo \"exit=$?\"; fi",
            Map.of("REPO_ROOT", repo.toString(), "MAVEN_CENTRAL_BASE", "http://127.0.0.1:1"));

    assertThat(result.output()).contains("exit=1").contains("ERROR:");
  }

  /**
   * {@code --dry-run} exercises the real {@code main()} path end to end: no version on the command
   * line, resolved via the newest {@code v*} tag reachable from HEAD, never against
   * gradle.properties. The tag lives in a throwaway git repo built by this test, never this
   * checkout's own history — a real release tags HEAD, so a test reading "the newest tag in this
   * checkout" would pass today and start reading a stale answer, or nothing at all, the moment this
   * history changes shape (a public snapshot export, a shallow clone, a squash). {@code REPO_ROOT}
   * points {@code main()}'s own git call at the fixture the same way {@code sourced}'s fixtures
   * above do; the fixture's stub {@code gradlew} stands in for the real build's {@code
   * printPublishedCoordinates} task so this stays offline and independent of this checkout's own
   * published module list too.
   */
  @Test
  void noVersionArgumentWithDryRunResolvesTheNewestReachableTagFromAThrowawayRepo(
      @TempDir Path repo) throws Exception {
    initGitRepoWithTag(repo, "v7.8.9");
    writeStubGradlewPrintingPublishedCoordinates(repo);

    var result = run(Map.of("REPO_ROOT", repo.toString()), "--dry-run");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output())
        .contains("no <version> given")
        .contains("verifying the last published version: 7.8.9")
        .contains(
            "LIBRARY https://repo1.maven.org/maven2/ai/narrativetrace/narrativetrace-core/7.8.9");
  }

  /** Requirement #3 of the fix: an explicit version always wins, and resolution never runs. */
  @Test
  void explicitVersionArgumentBypassesResolutionEntirely() throws Exception {
    var result = run("9.9.9-explicit", "--dry-run");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output())
        .doesNotContain("no <version> given")
        .contains(
            "LIBRARY https://repo1.maven.org/maven2/ai/narrativetrace/narrativetrace-core/"
                + "9.9.9-explicit");
  }

  /**
   * The only end-to-end path safe for a per-commit gate: {@code --dry-run} derives real coordinates
   * from this checkout's own build (offline: {@code printPublishedCoordinates} is a local Gradle
   * task) and prints the URLs it would poll, making no network call and starting no smoke-test
   * build.
   */
  @Test
  void dryRunListsRealCoordinatesFromThisCheckoutWithoutNetworkOrSmokeTest() throws Exception {
    var version = declaredVersion();

    var result = run(version, "--dry-run");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output())
        .contains("Dry run")
        .contains(
            "LIBRARY https://repo1.maven.org/maven2/ai/narrativetrace/narrativetrace-core/"
                + version)
        .contains(
            "PLUGIN"
                + " https://plugins.gradle.org/m2/ai/narrativetrace/ai.narrativetrace.gradle.plugin/"
                + version)
        .doesNotContain("Smoke test: PASSED")
        .doesNotContain("Smoke test: FAILED");
  }

  /** Reads `narrativetraceVersion` out of gradle.properties without going through Gradle. */
  private String declaredVersion() throws IOException {
    var props = new Properties();
    try (var in = new java.io.FileInputStream(new File(PROJECT_DIR, "gradle.properties"))) {
      props.load(in);
    }
    return props.getProperty("narrativetraceVersion");
  }

  /**
   * A stub {@code gradlew} at the fixture repo's root, executable, that answers {@code -q
   * printPublishedCoordinates} with a fixed coordinate list — the real task's output shape (see
   * {@code printPublishedCoordinates} in the root {@code build.gradle.kts}) without invoking a real
   * Gradle build. Only {@code noVersionArgumentWithDryRunResolvesTheNewestReachableTagFromA
   * ThrowawayRepo} needs this: every other {@code run(...)} test either passes an explicit version
   * (resolution never runs) or targets the real checkout directly.
   */
  private void writeStubGradlewPrintingPublishedCoordinates(Path repo) throws IOException {
    var gradlew = repo.resolve("gradlew");
    Files.writeString(
        gradlew,
        "#!/usr/bin/env bash\n"
            + "echo 'LIBRARY ai.narrativetrace narrativetrace-core'\n"
            + "echo 'PLUGIN ai.narrativetrace ai.narrativetrace.gradle.plugin'\n");
    if (!gradlew.toFile().setExecutable(true)) {
      throw new IOException("could not mark fixture gradlew executable: " + gradlew);
    }
  }

  // --- Gradle Plugin Portal LISTING check: the /m2 proxy blind spot this adds an independent
  // signal for. version_le is pure (no network); run_plugin_portal_listing_check shells out to
  // scripts/plugin-portal-published.sh, so these fixtures point REPO_ROOT at a throwaway
  // directory carrying a STUB of that script (exit code fixed by the test) rather than making a
  // real Portal call — the real script has its own dedicated test coverage
  // (PluginPortalPublishedScriptTest); this file only proves the classification wrapped around it.

  @Test
  void versionLeOrdersDottedVersionsNumerically() throws Exception {
    assertThat(sourced("version_le 0.2.3 0.2.3 && echo yes || echo no").output().strip())
        .isEqualTo("yes");
    assertThat(sourced("version_le 0.2.0 0.2.3 && echo yes || echo no").output().strip())
        .isEqualTo("yes");
    assertThat(sourced("version_le 0.2.4 0.2.3 && echo yes || echo no").output().strip())
        .isEqualTo("no");
    // Numeric, not lexicographic: "0.10.0" sorts before "0.2.3" as strings but is the newer
    // version.
    assertThat(sourced("version_le 0.10.0 0.2.3 && echo yes || echo no").output().strip())
        .isEqualTo("no");
  }

  private void writeStubPluginPortalPublishedScript(Path repo, int exitCode, String stdout)
      throws IOException {
    var scriptsDir = repo.resolve("scripts");
    Files.createDirectories(scriptsDir);
    var script = scriptsDir.resolve("plugin-portal-published.sh");
    Files.writeString(
        script,
        "#!/usr/bin/env bash\n"
            + "echo '"
            + stdout.replace("'", "'\\''")
            + "'\n"
            + "exit "
            + exitCode
            + "\n");
    if (!script.toFile().setExecutable(true)) {
      throw new IOException("could not mark fixture script executable: " + script);
    }
  }

  @Test
  void listingCheckExitZeroIsPresent(@TempDir Path repo) throws Exception {
    writeStubPluginPortalPublishedScript(repo, 0, "ai.narrativetrace 0.2.3 is published");

    var result =
        sourced(
            "LOCAL_REHEARSAL=0; run_plugin_portal_listing_check 0.2.3; echo"
                + " \"$PLUGIN_PORTAL_LISTING_STATUS\"",
            Map.of("REPO_ROOT", repo.toString()));

    assertThat(result.output().strip()).isEqualTo("PRESENT");
  }

  /**
   * A version through {@code PLUGIN_PORTAL_KNOWN_GAP_THROUGH_VERSION} (0.2.3, the last release
   * built under the /m2 guard bug) reads NOT_YET_PUBLISHED with the softened, explained wording —
   * still a failing status (the canary stays honestly red), never silently PRESENT.
   */
  @Test
  void listingCheckExitOneThroughKnownGapVersionIsNotYetPublishedWithExplanation(@TempDir Path repo)
      throws Exception {
    writeStubPluginPortalPublishedScript(repo, 1, "not on the Portal yet");

    var result =
        sourced(
            "LOCAL_REHEARSAL=0; run_plugin_portal_listing_check 0.2.3; echo"
                + " \"$PLUGIN_PORTAL_LISTING_STATUS|$PLUGIN_PORTAL_LISTING_DETAIL\"",
            Map.of("REPO_ROOT", repo.toString()));

    assertThat(result.output().strip())
        .startsWith("NOT_YET_PUBLISHED|")
        .contains("expected")
        .doesNotContain("not on the Portal yet");
  }

  /** A version AFTER the known-gap version gets no softening: a missing listing is real. */
  @Test
  void listingCheckExitOneAfterKnownGapVersionIsMissing(@TempDir Path repo) throws Exception {
    writeStubPluginPortalPublishedScript(repo, 1, "not on the Portal yet");

    var result =
        sourced(
            "LOCAL_REHEARSAL=0; run_plugin_portal_listing_check 0.2.4; echo"
                + " \"$PLUGIN_PORTAL_LISTING_STATUS|$PLUGIN_PORTAL_LISTING_DETAIL\"",
            Map.of("REPO_ROOT", repo.toString()));

    assertThat(result.output().strip()).startsWith("MISSING|").contains("not on the Portal yet");
  }

  /** The guard's own ERROR verdict (exit 2 — network failure) is never read as PRESENT. */
  @Test
  void listingCheckExitTwoIsMissingNeverPresent(@TempDir Path repo) throws Exception {
    writeStubPluginPortalPublishedScript(repo, 2, "ERROR: could not reach the Portal");

    var result =
        sourced(
            "LOCAL_REHEARSAL=0; run_plugin_portal_listing_check 0.2.4; echo"
                + " \"$PLUGIN_PORTAL_LISTING_STATUS\"",
            Map.of("REPO_ROOT", repo.toString()));

    assertThat(result.output().strip()).isEqualTo("MISSING");
  }

  /** --local-rehearsal has no local stand-in for the Portal, so the check is skipped, not run. */
  @Test
  void listingCheckSkipsUnderLocalRehearsalWithoutInvokingTheStub(@TempDir Path repo)
      throws Exception {
    // No stub script written at all: if the check ran it anyway, sourcing would fail loudly
    // rather than silently reporting SKIPPED.
    var result =
        sourced(
            "LOCAL_REHEARSAL=1; run_plugin_portal_listing_check 0.2.3;"
                + " echo \"$PLUGIN_PORTAL_LISTING_STATUS\"",
            Map.of("REPO_ROOT", repo.toString()));

    assertThat(result.exitCode()).isZero();
    assertThat(result.output().strip()).isEqualTo("SKIPPED");
  }

  // --- B-62: PENDING_APPROVAL — a new plugin id submitted for approval leaves the Portal's own
  // listing page (and its /m2 proxy) answering "not here" while Central already has the release.
  // These populate the CHECK_* arrays by hand (main()'s load_checks/poll_artifacts never run in a
  // sourced call) exactly as poll_artifacts would have left them after polling the PLUGIN row.

  private String pluginRowFixture(String status) {
    return "CHECK_KIND=(PLUGIN); CHECK_GROUP=(ai.narrativetrace);"
        + " CHECK_ARTIFACT=(ai.narrativetrace.gradle.plugin); CHECK_STATUS=("
        + status
        + "); ";
  }

  /**
   * The exact B-65 shape: the Portal's own listing page says NOT_YET_PUBLISHED (stub exit 1), the
   * PLUGIN row's /m2 proxy check already read LAGGING (its documented 404 reading), and Central's
   * own copy of the same marker is 200 — the release shipped; only the Portal's approval of the id
   * is pending. This must read as a distinct, non-red state, never MISSING.
   */
  @Test
  void listingCheckIsPendingApprovalWhenThePortalProxyIs404AndCentralHasTheMarker(
      @TempDir Path repo) throws Exception {
    writeStubPluginPortalPublishedScript(repo, 1, "not on the Portal yet");
    var central = startMarkerServer(true);
    try {
      var result =
          sourced(
              pluginRowFixture("LAGGING")
                  + "LOCAL_REHEARSAL=0; run_plugin_portal_listing_check 0.2.4; echo"
                  + " \"$PLUGIN_PORTAL_LISTING_STATUS|$PLUGIN_PORTAL_LISTING_DETAIL\"",
              Map.of("REPO_ROOT", repo.toString(), "MAVEN_CENTRAL_BASE", central.baseUrl()));

      assertThat(result.output().strip())
          .startsWith("PENDING_APPROVAL|")
          .contains("submitted to the Portal, awaiting Gradle's approval of the new plugin id")
          .contains("nothing to do");
    } finally {
      central.httpServer().stop(0);
    }
  }

  /**
   * The /m2 proxy is 404 (LAGGING) but Central ALSO lacks the marker: a real gap, stays MISSING.
   */
  @Test
  void listingCheckStaysMissingWhenCentralAlsoLacksTheMarker(@TempDir Path repo) throws Exception {
    writeStubPluginPortalPublishedScript(repo, 1, "not on the Portal yet");
    var central = startMarkerServer(false);
    try {
      var result =
          sourced(
              pluginRowFixture("LAGGING")
                  + "LOCAL_REHEARSAL=0; run_plugin_portal_listing_check 0.2.4; echo"
                  + " \"$PLUGIN_PORTAL_LISTING_STATUS|$PLUGIN_PORTAL_LISTING_DETAIL\"",
              Map.of("REPO_ROOT", repo.toString(), "MAVEN_CENTRAL_BASE", central.baseUrl()));

      assertThat(result.output().strip()).startsWith("MISSING|").contains("not on the Portal yet");
    } finally {
      central.httpServer().stop(0);
    }
  }

  /**
   * The /m2 proxy row itself read MISSING (a 5xx or connection failure), never LAGGING — a
   * different failure shape than "still awaiting approval", so PENDING_APPROVAL never fires even
   * when Central happens to have the marker.
   */
  @Test
  void listingCheckStaysMissingWhenThePluginRowItselfIsNotLagging(@TempDir Path repo)
      throws Exception {
    writeStubPluginPortalPublishedScript(repo, 1, "not on the Portal yet");
    var central = startMarkerServer(true);
    try {
      var result =
          sourced(
              pluginRowFixture("MISSING")
                  + "LOCAL_REHEARSAL=0; run_plugin_portal_listing_check 0.2.4; echo"
                  + " \"$PLUGIN_PORTAL_LISTING_STATUS\"",
              Map.of("REPO_ROOT", repo.toString(), "MAVEN_CENTRAL_BASE", central.baseUrl()));

      assertThat(result.output().strip()).isEqualTo("MISSING");
    } finally {
      central.httpServer().stop(0);
    }
  }

  // --- B-62 residue: the PLUGIN artifact-presence row (print_report's own table, not the
  // separate LISTING row above) must read the same PENDING_APPROVAL relief under the identical
  // condition — a new plugin id's /m2 proxy check reading LAGGING while Central already has the
  // marker. Same fake-endpoint style as the six LISTING fixtures above, reusing pluginRowFixture
  // and startMarkerServer so both rows are provably asking one shared detection function.

  /**
   * The PLUGIN row itself read LAGGING (the /m2 proxy's clean 404) and Central already has the
   * marker: the row must read PENDING_APPROVAL, carry the same "awaiting Gradle's approval" text,
   * and must not flip ALL_PRESENT to red.
   */
  @Test
  void printReportPluginRowIsPendingApprovalWhenPortalProxyIs404AndCentralHasTheMarker()
      throws Exception {
    var central = startMarkerServer(true);
    try {
      var result =
          sourced(
              pluginRowFixture("LAGGING")
                  + "PLUGIN_PORTAL_LISTING_STATUS=PRESENT; SMOKE_VERDICT=PASSED;"
                  + " print_report 0.2.4; echo \"ALL_PRESENT=$ALL_PRESENT\"",
              Map.of("MAVEN_CENTRAL_BASE", central.baseUrl()));

      assertThat(result.output())
          .contains("PLUGIN")
          .contains("PENDING_APPROVAL")
          .contains("submitted to the Portal, awaiting Gradle's approval of the new plugin id")
          .doesNotContain("LAGGING");
      assertThat(result.output()).contains("ALL_PRESENT=1");
    } finally {
      central.httpServer().stop(0);
    }
  }

  /**
   * The /m2 proxy is 404 but Central ALSO lacks the marker: a real gap, the PLUGIN row stays red.
   */
  @Test
  void printReportPluginRowStaysRedWhenCentralAlsoLacksTheMarker() throws Exception {
    var central = startMarkerServer(false);
    try {
      var result =
          sourced(
              pluginRowFixture("LAGGING")
                  + "PLUGIN_PORTAL_LISTING_STATUS=PRESENT; SMOKE_VERDICT=PASSED;"
                  + " print_report 0.2.4; echo \"ALL_PRESENT=$ALL_PRESENT\"",
              Map.of("MAVEN_CENTRAL_BASE", central.baseUrl()));

      assertThat(result.output()).contains("LAGGING").doesNotContain("PENDING_APPROVAL");
      assertThat(result.output()).contains("ALL_PRESENT=0");
    } finally {
      central.httpServer().stop(0);
    }
  }

  /**
   * A PLUGIN row that already reads PRESENT (a direct 200, or a redirect curl's {@code -L} resolved
   * to 200) is never touched by the pending-approval override — and, provably, Central is never
   * even asked: {@code MAVEN_CENTRAL_BASE} points at a closed port that fails instantly, so if the
   * row were wrongly re-checked this test would fail (or hang) rather than pass.
   */
  @Test
  void printReportPluginRowStaysPresentAndNeverQueriesCentralWhenAlreadyPresent() throws Exception {
    var result =
        sourced(
            pluginRowFixture("PRESENT")
                + "PLUGIN_PORTAL_LISTING_STATUS=PRESENT; SMOKE_VERDICT=PASSED;"
                + " print_report 0.2.4; echo \"ALL_PRESENT=$ALL_PRESENT\"",
            Map.of("MAVEN_CENTRAL_BASE", "http://127.0.0.1:1"));

    assertThat(result.output()).contains("PRESENT").doesNotContain("PENDING_APPROVAL");
    assertThat(result.output()).contains("ALL_PRESENT=1");
  }

  /** print_report's red/not-red verdict: PENDING_APPROVAL never flips ALL_PRESENT to red. */
  @Test
  void printReportTreatsPendingApprovalAsNotRed() throws Exception {
    var result =
        sourced(
            "CHECK_KIND=(); CHECK_GROUP=(); CHECK_ARTIFACT=(); CHECK_STATUS=();"
                + " PLUGIN_PORTAL_LISTING_STATUS=PENDING_APPROVAL; SMOKE_VERDICT=PASSED;"
                + " print_report 0.2.4 >/dev/null; echo \"$ALL_PRESENT\"");

    assertThat(result.output().strip()).isEqualTo("1");
  }

  /** Same verdict function: MISSING still flips ALL_PRESENT to red. */
  @Test
  void printReportKeepsMissingAsRed() throws Exception {
    var result =
        sourced(
            "CHECK_KIND=(); CHECK_GROUP=(); CHECK_ARTIFACT=(); CHECK_STATUS=();"
                + " PLUGIN_PORTAL_LISTING_STATUS=MISSING; SMOKE_VERDICT=PASSED;"
                + " print_report 0.2.4 >/dev/null; echo \"$ALL_PRESENT\"");

    assertThat(result.output().strip()).isEqualTo("0");
  }

  // --- fixtures: a throwaway git repo (usually no gradlew — only latest_tag_version /
  // resolve_version ever run against most of these; one test above adds a stub gradlew for the
  // full --dry-run path), and a loopback HTTP server standing in for Maven Central ------------

  private void initGitRepo(Path dir) throws IOException, InterruptedException {
    runGit(dir, "init", "-q");
    runGit(dir, "config", "user.email", "verify-publication-test@example.com");
    runGit(dir, "config", "user.name", "verify-publication-test");
    runGit(dir, "config", "commit.gpgsign", "false");
    Files.writeString(dir.resolve("seed.txt"), "seed\n");
    runGit(dir, "add", "seed.txt");
    runGit(dir, "commit", "-q", "-m", "seed");
  }

  private void initGitRepoWithTag(Path dir, String tag) throws IOException, InterruptedException {
    initGitRepo(dir);
    runGit(dir, "tag", tag);
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

  private record MetadataServer(HttpServer httpServer, String baseUrl) {}

  /**
   * A loopback HTTP server serving {@code ai/narrativetrace/narrativetrace-core/maven-metadata.xml}
   * with the given {@code <latest>} — the real curl calls {@code latest_metadata_version} makes
   * (HEAD then GET), with no real network involved. The caller stops the server when done.
   */
  private MetadataServer startMetadataServer(String latestVersion) throws IOException {
    var loopback = java.net.InetAddress.getLoopbackAddress();
    var server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
    var body =
        ("<metadata>\n"
                + "  <groupId>ai.narrativetrace</groupId>\n"
                + "  <artifactId>narrativetrace-core</artifactId>\n"
                + "  <versioning>\n"
                + "    <latest>"
                + latestVersion
                + "</latest>\n"
                + "    <release>"
                + latestVersion
                + "</release>\n"
                + "  </versioning>\n"
                + "</metadata>\n")
            .getBytes(StandardCharsets.UTF_8);
    server.createContext(
        "/ai/narrativetrace/narrativetrace-core/maven-metadata.xml",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "application/xml");
          exchange.sendResponseHeaders(200, body.length);
          if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.getResponseBody().close();
          } else {
            try (var responseBody = exchange.getResponseBody()) {
              responseBody.write(body);
            }
          }
        });
    server.start();
    return new MetadataServer(
        server, "http://" + loopback.getHostAddress() + ":" + server.getAddress().getPort());
  }

  /**
   * A loopback HTTP server standing in for Maven Central's copy of the plugin MARKER pom at version
   * 0.2.4 (the version every {@code pending_portal_approval} test above verifies against) — {@code
   * present} controls whether the marker path answers 200 or 404, the two halves of the B-62
   * PENDING_APPROVAL condition this class's {@code pending_portal_approval} reads.
   */
  private MetadataServer startMarkerServer(boolean present) throws IOException {
    var loopback = java.net.InetAddress.getLoopbackAddress();
    var server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
    server.createContext(
        "/ai/narrativetrace/ai.narrativetrace.gradle.plugin/0.2.4/"
            + "ai.narrativetrace.gradle.plugin-0.2.4.pom",
        exchange -> {
          int status = present ? 200 : 404;
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    server.start();
    return new MetadataServer(
        server, "http://" + loopback.getHostAddress() + ":" + server.getAddress().getPort());
  }
}
