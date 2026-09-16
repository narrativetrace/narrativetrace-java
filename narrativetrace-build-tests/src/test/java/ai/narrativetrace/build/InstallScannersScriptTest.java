/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit-tests {@code scripts/install-scanners.sh}: the version file is the ONE place a scanner
 * version lives (thin-CI rule), Semgrep is installed by the exact pinned version, and OSV-Scanner
 * is only ever trusted after its downloaded binary is verified against that release's own published
 * SHA256SUMS file — never against a hash typed into this script or the test.
 *
 * <p>INTENT (build-automation assessment 2026-09-14, Priority 1/4): before this, CI installed
 * whatever {@code pip install semgrep} resolved that run and downloaded OSV-Scanner's {@code
 * latest} release with no integrity check at all. Driven with a loopback {@code HttpServer} fixture
 * standing in for GitHub's release-asset host — real curl, real {@code sha256sum -c}, no real
 * network — the same idiom {@code VerifyPublicationScriptTest} uses for Maven Central.
 */
class InstallScannersScriptTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));
  private static final File SCRIPT = new File(PROJECT_DIR, "scripts/install-scanners.sh");
  private static final File VERSIONS_FILE = new File(PROJECT_DIR, "scripts/scanner-versions.env");

  private record ScriptResult(String output, int exitCode) {}

  private ScriptResult sourced(String call, Map<String, String> env)
      throws IOException, InterruptedException {
    var body = "source '" + SCRIPT.getAbsolutePath() + "'\n" + call + "\n";
    var processBuilder =
        new ProcessBuilder("bash", "-c", body).redirectErrorStream(true).directory(PROJECT_DIR);
    processBuilder.environment().putAll(env);
    return run(processBuilder);
  }

  private ScriptResult run(String... args) throws IOException, InterruptedException {
    return run(Map.of(), args);
  }

  private ScriptResult run(Map<String, String> env, String... args)
      throws IOException, InterruptedException {
    var command = new ArrayList<String>();
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

  // --- the versions file: the one place a version lives ----------------------------------

  @Test
  void bothScannerVersionsAreDeclaredAndPinned() throws IOException {
    var props = new Properties();
    // scanner-versions.env is `key=value` per line, valid Properties syntax.
    try (var in = Files.newInputStream(VERSIONS_FILE.toPath())) {
      props.load(in);
    }

    assertThat(props.getProperty("SEMGREP_VERSION")).isNotBlank().doesNotContain("latest");
    assertThat(props.getProperty("OSV_SCANNER_VERSION"))
        .isNotBlank()
        .doesNotContain("latest")
        .as("OSV-Scanner tags are v-prefixed releases")
        .startsWith("v");
  }

  // --- semgrep: exact pinned version -------------------------------------------------------

  @Test
  void installSemgrepInvokesPipWithExactlyThePinnedVersion(@TempDir Path fakeBin)
      throws IOException, InterruptedException {
    var pinned = readPinnedVersion("SEMGREP_VERSION");
    var argsFile = fakeBin.resolve("pip-args.txt");
    writeFakePip(fakeBin, argsFile);

    var result = sourced("install_semgrep", withFakeBinFirst(fakeBin, Map.of()));

    assertThat(result.exitCode()).as(result.output()).isZero();
    assertThat(Files.readString(argsFile).strip())
        .isEqualTo("install --break-system-packages --quiet semgrep==" + pinned);
  }

  // --- osv-scanner: checksum-verified download ---------------------------------------------

  @Test
  void installOsvScannerAcceptsABinaryMatchingItsReleaseChecksum(@TempDir Path tmp)
      throws IOException, InterruptedException {
    var binaryContent = "fake-osv-scanner-binary-content\n".getBytes(StandardCharsets.UTF_8);
    var pinnedVersion = readPinnedVersion("OSV_SCANNER_VERSION");
    var server = startAssetServer(pinnedVersion, binaryContent, sha256Hex(binaryContent));
    var installPath = tmp.resolve("osv-scanner").toString();
    try {
      var env = new HashMap<String, String>();
      env.put("OSV_SCANNER_RELEASE_BASE", server.baseUrl());
      env.put("OSV_SCANNER_INSTALL_PATH", installPath);

      var result = sourced("install_osv_scanner", env);

      assertThat(result.exitCode()).as(result.output()).isZero();
      assertThat(Files.readAllBytes(Path.of(installPath))).isEqualTo(binaryContent);
      assertThat(Path.of(installPath)).isExecutable();
    } finally {
      server.httpServer().stop(0);
    }
  }

  @Test
  void installOsvScannerRejectsABinaryThatDoesNotMatchItsReleaseChecksum(@TempDir Path tmp)
      throws IOException, InterruptedException {
    var binaryContent = "tampered-content".getBytes(StandardCharsets.UTF_8);
    // The checksums file names a DIFFERENT hash than the binary actually served — the exact
    // shape of a corrupted or substituted download.
    var wrongHash = sha256Hex("something-else".getBytes(StandardCharsets.UTF_8));
    var pinnedVersion = readPinnedVersion("OSV_SCANNER_VERSION");
    var server = startAssetServer(pinnedVersion, binaryContent, wrongHash);
    var installPath = tmp.resolve("osv-scanner").toString();
    try {
      var env = new HashMap<String, String>();
      env.put("OSV_SCANNER_RELEASE_BASE", server.baseUrl());
      env.put("OSV_SCANNER_INSTALL_PATH", installPath);

      var result = sourced("install_osv_scanner", env);

      assertThat(result.exitCode()).as("a checksum mismatch must fail, not warn").isNotZero();
      assertThat(Path.of(installPath))
          .as("a failed verification must never leave a binary at the install path")
          .doesNotExist();
    } finally {
      server.httpServer().stop(0);
    }
  }

  // --- CLI surface -----------------------------------------------------------------------

  @Test
  void unknownTargetFailsLoudlyRatherThanSilentlyInstallingEverything()
      throws IOException, InterruptedException {
    var result = run("bogus-target");

    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.output()).contains("unknown target 'bogus-target'");
  }

  @Test
  void missingVersionsFileFailsLoudlyWithItsPath(@TempDir Path tmp)
      throws IOException, InterruptedException {
    var missing = tmp.resolve("nope.env").toString();

    var result = sourced("load_versions", Map.of("SCANNER_VERSIONS_FILE", missing));

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains(missing);
  }

  // --- CI wiring: thin-CI rule --------------------------------------------------------------

  /**
   * A CI definition found by location and shape — never by a hardcoded filename. This tree carries
   * a private pipeline definition at the repository root (stripped from the public snapshot by the
   * publish gate, the same gate that would reject this test naming it); the public snapshot carries
   * only the GitHub Actions workflows. Discovering both the same way lets one test stay honest —
   * and pass unmodified — in either tree.
   */
  private record CiDefinition(String label, String content) {}

  private List<CiDefinition> discoverCiDefinitions() throws IOException {
    var found = new ArrayList<CiDefinition>();
    collectYamlFiles(PROJECT_DIR.toPath(), "", found);
    var workflowsDir = new File(PROJECT_DIR, ".github/workflows").toPath();
    if (Files.isDirectory(workflowsDir)) {
      collectYamlFiles(workflowsDir, ".github/workflows/", found);
    }
    return found;
  }

  private void collectYamlFiles(Path dir, String labelPrefix, List<CiDefinition> found)
      throws IOException {
    try (var entries = Files.list(dir)) {
      for (var entry : (Iterable<Path>) entries::iterator) {
        var name = entry.getFileName().toString();
        if (Files.isRegularFile(entry) && (name.endsWith(".yml") || name.endsWith(".yaml"))) {
          found.add(new CiDefinition(labelPrefix + name, Files.readString(entry)));
        }
      }
    }
  }

  @Test
  void everyCiDefinitionThatInstallsScannersDelegatesToTheScriptAndCarriesNoVersionOfItsOwn()
      throws IOException {
    var scannerDefinitions = new ArrayList<CiDefinition>();
    for (var ci : discoverCiDefinitions()) {
      if (ci.content().contains("install-scanners.sh")
          || ci.content().contains("semgrep")
          || ci.content().contains("osv-scanner")) {
        scannerDefinitions.add(ci);
      }
    }

    // Retro rule 2: a check that finds nothing to check is red, not green. If every CI
    // definition this discovers stopped mentioning the scanners at all, the assertions below
    // would vacuously pass over an empty list — this is what actually catches that.
    assertThat(scannerDefinitions)
        .as(
            "no CI definition installs the scanners — expected at least the private pipeline"
                + " definition (this tree) or a public GitHub Actions workflow (either tree) to"
                + " call scripts/install-scanners.sh")
        .isNotEmpty();

    for (var ci : scannerDefinitions) {
      assertThat(ci.content())
          .as("%s: no scanner version literal — scanner-versions.env is the one place", ci.label())
          .doesNotContain("semgrep==")
          .doesNotContain("osv-scanner/releases/latest");
      if (ci.content().contains("semgrep")) {
        assertThat(ci.content())
            .as("%s: Semgrep must be installed through the pinned script, not inline", ci.label())
            .contains("scripts/install-scanners.sh semgrep");
      }
      if (ci.content().contains("osv-scanner")) {
        assertThat(ci.content())
            .as(
                "%s: OSV-Scanner must be installed through the pinned script, not inline",
                ci.label())
            .contains("scripts/install-scanners.sh osv-scanner");
      }
    }
  }

  // --- fixtures ------------------------------------------------------------------------------

  private String readPinnedVersion(String key) throws IOException {
    var props = new Properties();
    try (var in = Files.newInputStream(VERSIONS_FILE.toPath())) {
      props.load(in);
    }
    return props.getProperty(key);
  }

  private Map<String, String> withFakeBinFirst(Path fakeBin, Map<String, String> extra) {
    var env = new HashMap<>(extra);
    var path = fakeBin + File.pathSeparator + System.getenv("PATH");
    env.put("PATH", path);
    return env;
  }

  private void writeFakePip(Path fakeBin, Path argsFile) throws IOException {
    var script = fakeBin.resolve("pip");
    Files.writeString(script, "#!/bin/sh\necho \"$*\" > '" + argsFile + "'\nexit 0\n");
    Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  private String sha256Hex(byte[] content) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(content);
      var sb = new StringBuilder();
      for (var b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private record AssetServer(HttpServer httpServer, String baseUrl) {}

  /**
   * A loopback HTTP server standing in for a GitHub release's asset host, at the SAME {@code
   * /<tag>/<asset>} path shape the script requests: serves {@code osv-scanner_linux_amd64} ({@code
   * binaryContent}) and {@code osv-scanner_SHA256SUMS} — a real multi-platform-shaped checksums
   * file naming {@code declaredHash} for the linux_amd64 line, exactly the format {@code sha256sum
   * -c} expects and the shape the script's {@code grep 'osv-scanner_linux_amd64$'} must pick the
   * right line out of.
   */
  private AssetServer startAssetServer(String tag, byte[] binaryContent, String declaredHash)
      throws IOException {
    var loopback = java.net.InetAddress.getLoopbackAddress();
    var server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
    var checksums =
        (declaredHash
                + "  osv-scanner_linux_amd64\n"
                + "0000000000000000000000000000000000000000000000000000000000000000 "
                + " osv-scanner_darwin_amd64\n"
                + "1111111111111111111111111111111111111111111111111111111111111111 "
                + " osv-scanner_windows_amd64.exe\n")
            .getBytes(StandardCharsets.UTF_8);
    server.createContext(
        "/" + tag + "/osv-scanner_linux_amd64", exchange -> respond(exchange, binaryContent));
    server.createContext(
        "/" + tag + "/osv-scanner_SHA256SUMS", exchange -> respond(exchange, checksums));
    server.start();
    return new AssetServer(
        server, "http://" + loopback.getHostAddress() + ":" + server.getAddress().getPort());
  }

  private void respond(HttpExchange exchange, byte[] body) throws IOException {
    exchange.sendResponseHeaders(200, body.length);
    try (var responseBody = exchange.getResponseBody()) {
      responseBody.write(body);
    }
  }
}
