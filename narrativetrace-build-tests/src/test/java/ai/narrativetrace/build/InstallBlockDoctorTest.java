/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Docs-as-tests rule 8 applied to the one block every onboarding path funnels through: {@code
 * documentation/llms.txt}'s "Install and first trace (copy this)". A reader — or the agent the init
 * prompt drives — pastes that block and the sources beside it, and the prompt then sends them to
 * the doctor. This test IS that walk: it scaffolds a project out of the page's own fenced blocks
 * and runs the built CLI's doctor against it.
 *
 * <p>INTENT: the prompt's step 6 promises a doctor report, and the Tier B graders require it FULLY
 * green. That promise is a property of the PAGE — a missing test-dependency line or a missing
 * redaction listing makes it unreachable no matter how faithfully an agent follows along — so it is
 * pinned here, mechanically, instead of being re-verified by hand on each edit.
 *
 * <p>Zero network: the doctor reads declared build-file text and source text, and never resolves,
 * compiles or runs anything. The page's coordinates are therefore proven READABLE here, not
 * publishable — that is {@code verifyPublication}'s job.
 *
 * <p><b>@llmNote</b> The scaffold is driven by the page, not by a copy of it: the Kotlin block
 * whose first line is {@code // build.gradle.kts} becomes the build file, and every Java block
 * whose first line is a {@code // src/…} path comment is written at that path. Add a listing to the
 * block with that same first-line comment and this test picks it up; the two assertions in {@link
 * #theInstallBlockCarriesTheBuildFileAndTheSourcesItClaims()} fail loudly rather than vacuously if
 * the page ever stops carrying one.
 */
class InstallBlockDoctorTest {

  /** The section heading whose fenced blocks a reader is told to copy. */
  private static final String INSTALL_SECTION = "## Install and first trace (copy this)";

  /** A fenced block's first body line, when it names the file the block belongs in. */
  private static final Pattern PATH_COMMENT = Pattern.compile("^// (src/\\S+\\.java)$");

  private static final Pattern FAILING_FINDING = Pattern.compile("(?m)^\\[FAIL] (\\S+)");

  @Test
  void theInstallBlockCarriesTheBuildFileAndTheSourcesItClaims(@TempDir Path project)
      throws IOException {
    scaffoldFromTheInstallBlock(project);

    assertThat(project.resolve("build.gradle.kts")).isRegularFile();
    assertThat(sourcesUnder(project, "src/test/java"))
        .as("the install block must carry the redaction test the prompt's step 5 asks for")
        .isNotEmpty();
  }

  /**
   * Every check but one, on a project that has only ever read this page. {@code
   * config.skills-installed} is the single finding the block itself cannot answer — installing the
   * agent skills is {@code init}'s job, which is the prompt's step 3, not its install step.
   */
  @Test
  void theInstallBlockLeavesOnlyTheSkillsCheckForInitToAnswer(@TempDir Path project)
      throws IOException, InterruptedException {
    scaffoldFromTheInstallBlock(project);

    CliJar.Run doctor = CliJar.run(project, "doctor");

    assertThat(failingFindings(doctor.output()))
        .as(doctor.output())
        .containsExactly("config.skills-installed");
  }

  /** The whole faithful path: paste the block, run {@code init}, run the doctor — twenty green. */
  @Test
  void theFaithfulPathEndsAtAFullyGreenDoctorReport(@TempDir Path project)
      throws IOException, InterruptedException {
    scaffoldFromTheInstallBlock(project);

    CliJar.Run install = CliJar.run(project, "init");
    CliJar.Run doctor = CliJar.run(project, "doctor");

    assertThat(install.exitCode()).as(install.output()).isZero();
    assertThat(failingFindings(doctor.output())).as(doctor.output()).isEmpty();
    assertThat(doctor.output())
        .contains("20 check(s), 0 finding(s)")
        .contains("All checks passed.");
    assertThat(doctor.exitCode()).isZero();
  }

  /**
   * The prompt's step 6 says to run the doctor, and both entry points leave their JSON report under
   * the project's own output directory — so the SECOND run reads the first run's report. Every
   * report carries {@code arg0} inside the messages that discuss {@code arg0}-style parameter
   * names, which is how a clean project came to be told it had a {@code trap.parameter-arg0} defect
   * (found by a Tier B trial, 2026-09-25). The doctor is idempotent or it is not trustworthy.
   */
  @Test
  void runningTheDoctorTwiceReportsExactlyTheSameFindings(@TempDir Path project)
      throws IOException, InterruptedException {
    scaffoldFromTheInstallBlock(project);
    CliJar.run(project, "init");

    CliJar.Run first = CliJar.run(project, "doctor");
    write(
        project.resolve("build/narrativetrace/doctor-report.json"),
        CliJar.run(project, "doctor", "--json").output());
    CliJar.Run second = CliJar.run(project, "doctor");

    assertThat(failingFindings(first.output())).as(first.output()).isEmpty();
    assertThat(failingFindings(second.output())).as(second.output()).isEmpty();
  }

  /**
   * Writes the page's install section into {@code project}: the build file, and every source the
   * section's blocks name in their first line.
   */
  private void scaffoldFromTheInstallBlock(Path project) throws IOException {
    for (String block : fencedBlocksOfTheInstallSection()) {
      String firstLine = block.lines().findFirst().orElse("");
      Matcher path = PATH_COMMENT.matcher(firstLine);
      if (path.matches()) {
        write(project.resolve(path.group(1)), block);
      } else if (firstLine.equals("// build.gradle.kts")) {
        write(project.resolve("build.gradle.kts"), block);
      }
    }
  }

  /** Every fenced block between the install heading and the next heading, fences stripped. */
  private List<String> fencedBlocksOfTheInstallSection() throws IOException {
    List<String> lines = Files.readAllLines(CliJar.repoRoot().resolve("documentation/llms.txt"));
    int start = lines.indexOf(INSTALL_SECTION);
    assertThat(start).as("documentation/llms.txt must carry " + INSTALL_SECTION).isNotNegative();
    List<String> blocks = new ArrayList<>();
    List<String> current = new ArrayList<>();
    boolean inFence = false;
    for (String line : lines.subList(start + 1, lines.size())) {
      if (!inFence && line.startsWith("## ")) {
        break;
      }
      if (line.startsWith("```")) {
        if (inFence) {
          blocks.add(String.join("\n", current) + "\n");
          current.clear();
        }
        inFence = !inFence;
      } else if (inFence) {
        current.add(line);
      }
    }
    return List.copyOf(blocks);
  }

  private static List<Path> sourcesUnder(Path project, String relative) throws IOException {
    Path root = project.resolve(relative);
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    try (var stream = Files.walk(root)) {
      return stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
    }
  }

  private static List<String> failingFindings(String doctorOutput) {
    List<String> ids = new ArrayList<>();
    Matcher matcher = FAILING_FINDING.matcher(doctorOutput);
    while (matcher.find()) {
      ids.add(matcher.group(1));
    }
    return ids;
  }

  private static void write(Path target, String content) throws IOException {
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
  }
}
