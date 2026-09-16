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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves {@code semgrepScan} tells its three outcomes apart — clean, findings, scanner error — by
 * running the REAL task against a stand-in {@code semgrep} placed first on {@code PATH}.
 *
 * <p>INTENT: the task used to invoke Semgrep without {@code --error}, and Semgrep's scan mode exits
 * {@code 0} with findings unless that flag is given — so a scan that found something was a green
 * build. The classification now lives in {@code ScannerGateSupport} (unit-tested in buildSrc); this
 * class pins the task's side: the exact invocation ({@code scan --error}), the failure message that
 * names the findings, the distinct message for a scanner that could not run, and the recorded
 * status. A stand-in binary is the only way to exercise all three exit codes deterministically, and
 * it means these tests never skip — Semgrep itself is not installed in the dev container
 * (release-retrospective rule 2: a skip cannot prove the tool ever ran).
 *
 * <p>The one test that needs the real tool ({@link
 * #realSemgrepFailsAKnownPJavaFindingUnderErrorFlag}) pins the exit-code contract against reality
 * (rule 4) and skips LOUDLY when {@code semgrep} is absent from {@code PATH}.
 */
class SemgrepScanTaskTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));
  private static final File STATUS_FILE =
      new File(PROJECT_DIR, "build/reports/security-scans/semgrep.status");
  private static final File REPORT_FILE =
      new File(PROJECT_DIR, "build/reports/semgrep/results.json");

  private static final String TWO_FINDINGS =
      "{\"results\":[{\"check_id\":\"java.lang.security.audit.crypto.use-of-md5.use-of-md5\","
          + "\"path\":\"src/main/java/Hash.java\",\"start\":{\"line\":7,\"col\":5},"
          + "\"extra\":{\"message\":\"MD5\",\"severity\":\"WARNING\"}},"
          + "{\"check_id\":\"java.lang.security.audit.command-injection-process-builder\","
          + "\"path\":\"src/main/java/Shell.java\",\"start\":{\"line\":12,\"col\":9},"
          + "\"extra\":{\"message\":\"ProcessBuilder\",\"severity\":\"ERROR\"}}],\"errors\":[]}";
  private static final String NO_FINDINGS = "{\"results\":[],\"errors\":[]}";

  @TempDir Path fakeBin;

  private byte[] previousStatus;
  private byte[] previousReport;

  @BeforeEach
  void snapshotRealScanState() throws IOException {
    // NOPMD-worthy shape (ternary-to-null): a file that has never run has no "previous status" to
    // restore, and that absence IS the value these fields carry — restore() already treats null
    // as "delete, don't write".
    previousStatus =
        STATUS_FILE.isFile() ? Files.readAllBytes(STATUS_FILE.toPath()) : null; // NOPMD
    previousReport =
        REPORT_FILE.isFile() ? Files.readAllBytes(REPORT_FILE.toPath()) : null; // NOPMD
    // Clean slate: a status left by an earlier real run (e.g. `skipped: binary not on PATH`)
    // would otherwise be indistinguishable from what THIS build wrote.
    Files.deleteIfExists(STATUS_FILE.toPath());
    Files.deleteIfExists(REPORT_FILE.toPath());
  }

  /**
   * The stand-in must leave no trace: a {@code ran-clean} written by a fake would be exactly the
   * false "scan passed" evidence the status file exists to prevent.
   */
  @AfterEach
  void restoreRealScanState() throws IOException {
    restore(STATUS_FILE, previousStatus);
    restore(REPORT_FILE, previousReport);
  }

  private static void restore(File file, byte[] previous) throws IOException {
    if (previous == null) {
      Files.deleteIfExists(file.toPath());
    } else {
      Files.createDirectories(file.toPath().getParent());
      Files.write(file.toPath(), previous);
    }
  }

  // --- findings -----------------------------------------------------------

  @Test
  void findingsFailTheTaskAndAreNamedInTheMessage() throws IOException {
    installFakeSemgrep(1, TWO_FINDINGS);

    var result = runner().buildAndFail();

    assertThat(result.task(":semgrepScan").getOutcome()).isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput())
        .contains("semgrepScan: Semgrep reported 2 finding(s)")
        .contains(
            "java.lang.security.audit.crypto.use-of-md5.use-of-md5 — src/main/java/Hash.java:7")
        .contains(
            "java.lang.security.audit.command-injection-process-builder —"
                + " src/main/java/Shell.java:12")
        .doesNotContain("scan itself failed");
    assertThat(STATUS_FILE).as("findings are not a clean run").doesNotExist();
  }

  // --- clean --------------------------------------------------------------

  @Test
  void aCleanScanPassesAndRecordsRanClean() throws IOException {
    installFakeSemgrep(0, NO_FINDINGS);

    var result = runner().build();

    assertThat(result.task(":semgrepScan").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains("semgrepScan: clean");
    assertThat(Files.readString(STATUS_FILE.toPath()).strip()).isEqualTo("ran-clean");
  }

  // --- scanner error ------------------------------------------------------

  @Test
  void aScannerErrorFailsWithItsOwnMessageAndIsNotMistakenForFindings() throws IOException {
    installFakeSemgrep(2, "");

    var result = runner().buildAndFail();

    assertThat(result.task(":semgrepScan").getOutcome()).isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput())
        .contains("semgrepScan: the scan itself failed (exit 2)")
        .doesNotContain("finding(s)");
    assertThat(STATUS_FILE).as("a scanner that could not run is not a clean run").doesNotExist();
  }

  /**
   * Exit {@code 1} means findings only under {@code --error}; without the flag Semgrep exits {@code
   * 0} with findings. The invocation is therefore part of the contract, not an implementation
   * detail — the fake records every argument it was given.
   */
  @Test
  void theTaskInvokesScanModeWithTheErrorFlagAndTheJavaRuleset() throws IOException {
    installFakeSemgrep(0, NO_FINDINGS);

    runner().build();

    var recorded = Files.readAllLines(fakeBin.resolve("args.txt"));
    assertThat(recorded).startsWith("scan", "--error").contains("--config=p/java", "--json");
  }

  // --- the real tool, when present --------------------------------------

  /**
   * Pins the exit-code contract this task depends on against the real scanner: a fixture with a
   * known {@code p/java} finding exits {@code 1} under {@code --error}, a clean fixture exits
   * {@code 0}. Needs {@code semgrep} on {@code PATH} and the registry ruleset (network) — the same
   * preconditions {@code semgrepScan} itself has — and skips LOUDLY otherwise.
   */
  @Test
  void realSemgrepFailsAKnownPJavaFindingUnderErrorFlag(@TempDir Path fixture)
      throws IOException, InterruptedException {
    var semgrep = findOnPath("semgrep");
    if (semgrep == null) {
      System.err.println(
          "SKIPPED (loudly): semgrep is not on PATH — the real-tool exit-code contract was NOT"
              + " verified in this environment; the stand-in tests above cover the task itself.");
      Assumptions.abort("semgrep not on PATH");
    }
    var src = Files.createDirectories(fixture.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Hash.java"),
        "public class Hash {\n"
            + "  byte[] digest(byte[] in) throws Exception {\n"
            + "    return java.security.MessageDigest.getInstance(\"MD5\").digest(in);\n"
            + "  }\n"
            + "}\n");

    var withFinding = semgrep(semgrep, fixture);
    assertThat(withFinding.exitCode)
        .as("a known p/java finding exits 1 under --error")
        .isEqualTo(1);
    assertThat(withFinding.output).contains("use-of-md5");

    Files.writeString(src.resolve("Hash.java"), "public class Hash {}\n");
    assertThat(semgrep(semgrep, fixture).exitCode).as("a clean fixture exits 0").isZero();
  }

  private record Run(int exitCode, String output) {}

  private static Run semgrep(String semgrep, Path dir) throws IOException, InterruptedException {
    var process =
        new ProcessBuilder(
                semgrep, "scan", "--error", "--config=p/java", "--metrics=off", "--json", ".")
            .directory(dir.toFile())
            .redirectErrorStream(true)
            .start();
    var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Run(process.waitFor(), output);
  }

  private static String findOnPath(String name) {
    for (var dir : System.getenv("PATH").split(File.pathSeparator)) {
      var candidate = new File(dir, name);
      if (candidate.canExecute()) {
        return candidate.getAbsolutePath();
      }
    }
    return null;
  }

  // --- the stand-in -------------------------------------------------------

  /**
   * A {@code semgrep} that records its arguments, writes {@code report} to whatever {@code
   * --output=} names, and exits with {@code exitCode} — the three behaviours the task reads.
   */
  private void installFakeSemgrep(int exitCode, String report) throws IOException {
    var script = fakeBin.resolve("semgrep");
    var reportFile = fakeBin.resolve("report.json");
    Files.writeString(reportFile, report);
    Files.writeString(
        script,
        "#!/bin/sh\n"
            + "printf '%s\\n' \"$@\" > '"
            + fakeBin.resolve("args.txt")
            + "'\n"
            + "for a in \"$@\"; do case \"$a\" in --output=*) cp '"
            + reportFile
            + "' \"${a#--output=}\";; esac; done\n"
            + "exit "
            + exitCode
            + "\n");
    Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  private GradleRunner runner() {
    Map<String, String> env = new HashMap<>(System.getenv());
    env.put("PATH", fakeBin + File.pathSeparator + env.getOrDefault("PATH", ""));
    return GradleRunner.create()
        .withProjectDir(PROJECT_DIR)
        .withArguments(List.of("semgrepScan", "-q"))
        .withEnvironment(env);
  }
}
