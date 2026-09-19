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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit-tests {@code scripts/plugin-portal-published.sh} without touching the real Gradle Plugin
 * Portal: the pure URL/extraction/classification functions (sourced, no network — same idiom as
 * {@link VerifyPublicationScriptTest}), and the real {@code main()} path against a loopback {@code
 * HttpServer} fixture standing in for the Portal, covering the three shapes the guard must tell
 * apart: a real published-version page (200, body names the plugin), a redirect answering the way
 * the {@code /m2} proxy this replaces did (303 — the exact shape that made the old guard misread
 * "not published" as "published"), and no response at all (network failure).
 */
class PluginPortalPublishedScriptTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));
  private static final File SCRIPT = new File(PROJECT_DIR, "scripts/plugin-portal-published.sh");

  private record ScriptResult(String output, int exitCode) {}

  /**
   * Sources the script (main() never runs — see the script's own bottom guard) then evaluates one
   * call.
   */
  private ScriptResult sourced(String call) throws IOException, InterruptedException {
    var body = "set -e\nsource '" + SCRIPT.getAbsolutePath() + "'\n" + call + "\n";
    var processBuilder = new ProcessBuilder("bash", "-c", body).redirectErrorStream(true);
    return run(processBuilder);
  }

  /** Runs the script for real (main() executes) with environment overrides. */
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

  // --- pure functions: URL construction, field extraction --------------------------------

  @Test
  void portalCheckUrlIsTheVersionSpecificPluginPageNeverTheM2Proxy() throws Exception {
    var result = sourced("portal_check_url https://plugins.gradle.org ai.narrativetrace 0.2.3");

    assertThat(result.output().strip())
        .isEqualTo("https://plugins.gradle.org/plugin/ai.narrativetrace/0.2.3")
        .doesNotContain("/m2/");
  }

  @Test
  void extractFieldReadsTheHiddenPluginIdField() throws Exception {
    var result =
        sourced(
            "extract_field '<p id=\"pluginIdValue\" style=\"display:none\">ai.narrativetrace</p>'"
                + " pluginIdValue");

    assertThat(result.output().strip()).isEqualTo("ai.narrativetrace");
  }

  @Test
  void extractFieldReadsTheHiddenVersionField() throws Exception {
    var result =
        sourced(
            "extract_field '<p id=\"plugin-id-version\" style=\"display:none;\">0.2.3</p>' "
                + "plugin-id-version");

    assertThat(result.output().strip()).isEqualTo("0.2.3");
  }

  @Test
  void extractFieldReturnsEmptyWhenTheFieldIsAbsent() throws Exception {
    var result = sourced("extract_field '<p id=\"somethingElse\">x</p>' pluginIdValue; echo END");

    assertThat(result.output().strip()).isEqualTo("END");
  }

  // --- pure classification: a redirect, a real published page, and a network failure -----

  /** (a) A 303 — the exact shape the /m2 proxy answers with — is NOT_PUBLISHED, never followed. */
  @Test
  void classifyResponseA303RedirectIsNotPublished() throws Exception {
    var result = sourced("classify_response 0 303 '' ai.narrativetrace 0.2.3");

    assertThat(result.output().strip()).isEqualTo("NOT_PUBLISHED");
  }

  /** (b) A 200 from the Portal's own page, body naming the plugin id and version, is PUBLISHED. */
  @Test
  void classifyResponseB200WithMatchingBodyIsPublished() throws Exception {
    var body =
        "<p id=\"pluginIdValue\" style=\"display:none\">ai.narrativetrace</p>"
            + "<p id=\"plugin-id-version\" style=\"display:none;\">0.2.3</p>";
    var result = sourced("classify_response 0 200 '" + body + "' ai.narrativetrace 0.2.3");

    assertThat(result.output().strip()).isEqualTo("PUBLISHED");
  }

  /** (c) curl's own nonzero exit (network failure) is ERROR, never NOT_PUBLISHED or PUBLISHED. */
  @Test
  void classifyResponseCNetworkFailureIsError() throws Exception {
    var result = sourced("classify_response 7 000 '' ai.narrativetrace 0.2.3");

    assertThat(result.output().strip()).isEqualTo("ERROR");
  }

  @Test
  void classifyResponse400UnknownPluginOrVersionIsNotPublished() throws Exception {
    var result = sourced("classify_response 0 400 'Plugin Not Found' ai.narrativetrace 0.2.3");

    assertThat(result.output().strip()).isEqualTo("NOT_PUBLISHED");
  }

  /** A 200 whose body names a DIFFERENT plugin id (a routing accident) is never PUBLISHED. */
  @Test
  void classifyResponse200WithMismatchedPluginIdIsNotPublished() throws Exception {
    var body =
        "<p id=\"pluginIdValue\" style=\"display:none\">some.other.plugin</p>"
            + "<p id=\"plugin-id-version\" style=\"display:none;\">0.2.3</p>";
    var result = sourced("classify_response 0 200 '" + body + "' ai.narrativetrace 0.2.3");

    assertThat(result.output().strip()).isEqualTo("NOT_PUBLISHED");
  }

  /** A 200 whose body names the right plugin but the WRONG version is never PUBLISHED. */
  @Test
  void classifyResponse200WithMismatchedVersionIsNotPublished() throws Exception {
    var body =
        "<p id=\"pluginIdValue\" style=\"display:none\">ai.narrativetrace</p>"
            + "<p id=\"plugin-id-version\" style=\"display:none;\">0.1.0</p>";
    var result = sourced("classify_response 0 200 '" + body + "' ai.narrativetrace 0.2.3");

    assertThat(result.output().strip()).isEqualTo("NOT_PUBLISHED");
  }

  // --- argument handling -------------------------------------------------------------------

  @Test
  void noArgumentsFailsWithExitTwo() throws Exception {
    var result = run(Map.of());

    assertThat(result.exitCode()).isEqualTo(2);
  }

  @Test
  void helpFlagPrintsUsageAndExitsZero() throws Exception {
    var result = run(Map.of(), "--help");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("Usage: scripts/plugin-portal-published.sh <version>");
  }

  // --- end to end against a loopback fixture standing in for the Portal --------------------

  private record PortalServer(HttpServer httpServer, String baseUrl) {}

  /**
   * A loopback server with two endpoints: {@code /plugin/<id>/<publishedVersion>} answers 200 with
   * the real page's two hidden fields, exactly as the live Portal does for a version it holds;
   * every other {@code /plugin/...} path (a version the fixture was not told about) answers 303 to
   * a fake Central URL — the exact shape {@code /m2} answers with, reproduced here so the fix is
   * proven against the failure mode that broke the old guard, not just described.
   */
  private PortalServer startPortalFixture(String pluginId, String publishedVersion)
      throws IOException {
    var loopback = java.net.InetAddress.getLoopbackAddress();
    var server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
    var publishedPath = "/plugin/" + pluginId + "/" + publishedVersion;
    var body =
        ("<html><body>"
                + "<p id=\"pluginIdValue\" style=\"display:none\">"
                + pluginId
                + "</p>"
                + "<p id=\"plugin-id-version\" style=\"display:none;\">"
                + publishedVersion
                + "</p>"
                + "</body></html>")
            .getBytes(StandardCharsets.UTF_8);
    server.createContext(
        "/plugin/",
        exchange -> {
          if (exchange.getRequestURI().getPath().equals(publishedPath)) {
            exchange.sendResponseHeaders(200, body.length);
            try (var responseBody = exchange.getResponseBody()) {
              responseBody.write(body);
            }
          } else {
            exchange
                .getResponseHeaders()
                .add("Location", "https://repo.maven.apache.org/maven2/not-really-there");
            exchange.sendResponseHeaders(303, -1);
            exchange.close();
          }
        });
    server.start();
    return new PortalServer(
        server, "http://" + loopback.getHostAddress() + ":" + server.getAddress().getPort());
  }

  @Test
  void publishedVersionExitsZeroAndSaysPublished() throws Exception {
    var fixture = startPortalFixture("ai.narrativetrace", "0.2.3");
    try {
      var result = run(Map.of("GRADLE_PLUGIN_PORTAL_BASE", fixture.baseUrl()), "0.2.3");

      assertThat(result.exitCode()).isZero();
      assertThat(result.output()).contains("is published on the Gradle Plugin Portal");
    } finally {
      fixture.httpServer().stop(0);
    }
  }

  /**
   * The reproduction case: the fixture answers a version nobody asked about with the same 303 shape
   * {@code /m2} used to fool the old guard. This must exit 1 (NOT_PUBLISHED, publish should run) —
   * the exact case release rule 2 requires a real test for, not just a description.
   */
  @Test
  void unpublishedVersionAnsweredWithA303ExitsOneAndSaysNotPublished() throws Exception {
    var fixture = startPortalFixture("ai.narrativetrace", "0.2.3");
    try {
      var result =
          run(Map.of("GRADLE_PLUGIN_PORTAL_BASE", fixture.baseUrl()), "9.9.9-never-published");

      assertThat(result.exitCode()).isEqualTo(1);
      assertThat(result.output()).contains("is NOT on the Gradle Plugin Portal yet");
    } finally {
      fixture.httpServer().stop(0);
    }
  }

  @Test
  void networkFailureExitsTwoAndSaysError() throws Exception {
    var result =
        run(
            Map.of(
                "GRADLE_PLUGIN_PORTAL_BASE", "http://127.0.0.1:1",
                "CURL_MAX_TIME_SECONDS", "3"),
            "0.2.3");

    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.output()).contains("ERROR");
  }
}
