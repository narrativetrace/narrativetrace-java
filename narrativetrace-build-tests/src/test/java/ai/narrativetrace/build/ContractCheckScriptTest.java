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
 * Unit-tests {@code scripts/contract-check.sh} without a release and without a network: the
 * contract it hands {@code contract-probe} is {@code documentation/contract.yaml} from the WORKING
 * TREE, whatever version it resolved and whatever tags this clone carries. Development is
 * trunk-based and the public snapshot ships with the artifact, so {@code main} IS the published
 * code — there is no "main ahead of published" gap for a tag-time read to close, and a missing tag
 * is not an error the gate has any reason to raise (owner ruling 2026-09-25).
 *
 * <p>Every fixture is a throwaway git repository pointed at by {@code REPO_ROOT}, never this
 * checkout: a test that read "the newest tag here" would pass today and start reading a stale
 * answer the moment this history changes shape (a public snapshot export, a shallow clone, a
 * squash). The fixture's {@code contract-probe/gradlew} is a stub that echoes the contract file it
 * was handed, so the end-to-end path runs with no Gradle build and no registry call.
 */
class ContractCheckScriptTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));
  private static final File SCRIPT = new File(PROJECT_DIR, "scripts/contract-check.sh");

  /** The contract as of the tag — present in every fixture purely to prove it is NOT what runs. */
  private static final String TAGGED_CONTRACT = "entries: [] # tagged-contract\n";

  private static final String WORKING_TREE_CONTRACT = "entries: [] # working-tree-contract\n";

  private record ScriptResult(String output, int exitCode) {}

  private ScriptResult run(Path repoRoot, String... args) throws IOException, InterruptedException {
    return run(Map.of("REPO_ROOT", repoRoot.toString()), args);
  }

  private ScriptResult run(Map<String, String> env, String... args)
      throws IOException, InterruptedException {
    var command = new java.util.ArrayList<String>();
    command.add(SCRIPT.getAbsolutePath());
    command.addAll(List.of(args));
    var processBuilder =
        new ProcessBuilder(command).redirectErrorStream(true).directory(PROJECT_DIR);
    processBuilder.environment().putAll(env);
    var process = processBuilder.start();
    var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new ScriptResult(output, process.waitFor());
  }

  // --- the contract comes from the working tree ----------------------------------------------

  @Test
  void readsTheWorkingTreeContractEvenWhenATagCarriesADifferentOne(@TempDir Path repo)
      throws Exception {
    initRepoWithTaggedContract(repo, "v7.8.9");

    var result = run(repo);

    assertThat(result.exitCode()).isZero();
    assertThat(result.output())
        .contains("no <version> given")
        .contains("7.8.9")
        .contains("working-tree-contract")
        .doesNotContain("tagged-contract");
  }

  @Test
  void anExplicitVersionAlsoReadsTheWorkingTreeContract(@TempDir Path repo) throws Exception {
    initRepoWithTaggedContract(repo, "v7.8.9");

    var result = run(repo, "7.8.9");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output())
        .doesNotContain("no <version> given")
        .contains("working-tree-contract")
        .doesNotContain("tagged-contract");
  }

  // --- a tag this clone does not have is not the gate's business -------------------------------

  @Test
  void anExplicitVersionWithNoSuchTagStillRuns(@TempDir Path repo) throws Exception {
    initRepoWithTaggedContract(repo, "v7.8.9");

    var result = run(repo, "9.9.9");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("9.9.9").contains("working-tree-contract");
  }

  /**
   * The other way a version reaches the script: no tag reachable from HEAD at all, so resolution
   * falls through to Maven Central's {@code <latest>}. The contract read is the same file either
   * way — how the version was decided never changes which contract is checked.
   */
  @Test
  void aVersionResolvedFromTheRegistryReadsTheSameWorkingTreeContract(@TempDir Path repo)
      throws Exception {
    initRepoWithContractButNoTag(repo);
    var central = startMetadataServer("5.4.3");
    try {
      var result =
          run(
              Map.of("REPO_ROOT", repo.toString(), "MAVEN_CENTRAL_BASE", central.baseUrl()),
              new String[0]);

      assertThat(result.exitCode()).isZero();
      assertThat(result.output()).contains("5.4.3").contains("working-tree-contract");
    } finally {
      central.httpServer().stop(0);
    }
  }

  // --- dry run ------------------------------------------------------------------------------

  @Test
  void dryRunNamesTheWorkingTreeContractItWouldHandTheProbe(@TempDir Path repo) throws Exception {
    initRepoWithTaggedContract(repo, "v7.8.9");

    var result = run(repo, "--dry-run");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output())
        .contains("Dry run")
        .contains("-PcontractVersion=7.8.9")
        .contains("-PcontractYaml=" + repo + "/documentation/contract.yaml")
        .doesNotContain("working-tree-contract")
        .doesNotContain("read from tag");
  }

  // --- fixtures -----------------------------------------------------------------------------

  /**
   * One commit carrying {@code documentation/contract.yaml}, tagged; then the working tree's copy
   * is overwritten with different text, so a run that read the TAG instead of the working tree says
   * so in its own output. The tag is also what version resolution finds when no version is passed.
   */
  private void initRepoWithTaggedContract(Path repo, String tag)
      throws IOException, InterruptedException {
    initGitRepo(repo);
    var contract = repo.resolve("documentation/contract.yaml");
    Files.createDirectories(contract.getParent());
    Files.writeString(contract, TAGGED_CONTRACT);
    runGit(repo, "add", "documentation/contract.yaml");
    runGit(repo, "commit", "-q", "-m", "contract");
    runGit(repo, "tag", tag);
    writeWorkingTreeContract(repo);
    writeStubGradlewEchoingTheContract(repo);
  }

  private void initRepoWithContractButNoTag(Path repo) throws IOException, InterruptedException {
    initGitRepo(repo);
    writeWorkingTreeContract(repo);
    writeStubGradlewEchoingTheContract(repo);
  }

  private void writeWorkingTreeContract(Path repo) throws IOException {
    var contract = repo.resolve("documentation/contract.yaml");
    Files.createDirectories(contract.getParent());
    Files.writeString(contract, WORKING_TREE_CONTRACT);
  }

  /** Stands in for {@code contract-probe/gradlew}: echoes whatever {@code -PcontractYaml} names. */
  private void writeStubGradlewEchoingTheContract(Path repo) throws IOException {
    var gradlew = repo.resolve("contract-probe/gradlew");
    Files.createDirectories(gradlew.getParent());
    Files.writeString(
        gradlew,
        """
        #!/usr/bin/env bash
        for arg in "$@"; do
          case "$arg" in
            -PcontractYaml=*) cat "${arg#-PcontractYaml=}" ;;
          esac
        done
        """);
    gradlew.toFile().setExecutable(true);
  }

  private void initGitRepo(Path dir) throws IOException, InterruptedException {
    runGit(dir, "init", "-q");
    runGit(dir, "config", "user.email", "contract-check-test@example.com");
    runGit(dir, "config", "user.name", "contract-check-test");
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
    if (process.waitFor() != 0) {
      throw new IOException("git " + String.join(" ", args) + " failed: " + output);
    }
  }

  private record MetadataServer(HttpServer httpServer, String baseUrl) {}

  /** A loopback stand-in for Maven Central's {@code maven-metadata.xml}, no real network. */
  private MetadataServer startMetadataServer(String latestVersion) throws IOException {
    var loopback = java.net.InetAddress.getLoopbackAddress();
    var server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
    var body =
        ("<metadata><versioning><latest>" + latestVersion + "</latest></versioning></metadata>\n")
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
}
