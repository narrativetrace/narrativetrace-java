/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit-tests {@code scripts/verify-publication.sh} without a release: the pure coordinate/URL
 * functions (sourced, no network), argument parsing, the local-repository presence check {@code
 * --local-rehearsal} relies on, and a real {@code --dry-run} against this checkout's own build.
 * What is deliberately NOT here: a real poll against Maven Central / the Gradle Plugin Portal, and
 * a real consumer-smoke-test build — both make network calls and belong to a human running the
 * script itself against an actual release, never a per-commit gate (see the script's own header
 * comment and the publish checklist).
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
    return sourced(call, null);
  }

  private ScriptResult sourced(String call, String localMavenRepo)
      throws IOException, InterruptedException {
    var body = "set -e\nsource '" + SCRIPT.getAbsolutePath() + "'\n" + call + "\n";
    var command = new java.util.ArrayList<String>(List.of("bash", "-c", body));
    var processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
    if (localMavenRepo != null) {
      processBuilder.environment().put("LOCAL_MAVEN_REPO", localMavenRepo);
    }
    return run(processBuilder);
  }

  private ScriptResult run(String... args) throws IOException, InterruptedException {
    var command = new java.util.ArrayList<String>();
    command.add(SCRIPT.getAbsolutePath());
    command.addAll(List.of(args));
    return run(new ProcessBuilder(command).redirectErrorStream(true).directory(PROJECT_DIR));
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
    assertThat(result.output()).contains("Usage: scripts/verify-publication.sh <version>");
  }

  @Test
  void missingVersionArgumentFailsWithUsageAndNonZeroExit() throws Exception {
    var result = run();

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("ERROR: missing <version>.");
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
}
