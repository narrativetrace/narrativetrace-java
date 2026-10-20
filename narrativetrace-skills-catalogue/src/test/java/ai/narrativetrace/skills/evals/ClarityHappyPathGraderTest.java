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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-107: the happy-path clarity grader ({@code verify.sh}) demanded the scan artifacts it was about
 * to regenerate itself were ALREADY on disk, as a precondition of its first verifier call — so any
 * legitimate later step of the same trial that ran its own {@code clean} (the page's JUnit
 * enforcement path, say) erased them first and failed a trial that had done nothing wrong.
 *
 * <p>Exercised against the real {@code verify.sh} and {@code verify_report.py}, with a stand-in
 * {@code gradlew} in place of the real plugin task — never a real nested Gradle build from inside
 * this module's own test, which is exactly the daemon/lock contention and composite-build cost
 * {@link ai.narrativetrace.skills.replay.SkillReplayer}'s own class doc warns against. The stand-in
 * regenerates the scan artifacts fresh (the real schema, element/note included — the exact shape a
 * scan of this fixture has produced before) and checks the clarity policy in the fixture's own
 * {@code build.gradle.kts}, the same two things the real {@code clarityScan}/{@code clarityCheck}
 * tasks do.
 */
class ClarityHappyPathGraderTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));
  private static final Path FIXTURE =
      REPO_ROOT.resolve("narrativetrace-skills-catalogue/evals/fixtures/clarity-consumer");
  private static final Path VERIFY_SH =
      REPO_ROOT.resolve(
          "narrativetrace-skills-catalogue/evals/add-narrativetrace-clarity/happy-path/graders"
              + "/verify.sh");

  /** This fixture's own real scan has produced exactly this element and note before. */
  private static final String ELEMENT = "OrderService.placeOrder";

  private static final String NOTE = "Standard verb 'place' + broad noun 'order'";

  private static final String FAKE_GRADLEW =
      """
#!/bin/sh
case " $* " in
  *" clarityScan "*)
    rm -rf build
    mkdir -p build/narrativetrace
    cat > build/narrativetrace/clarity-scan-results.json <<'JSON'
{"scenarios":[{"overallScore":0.89,"issues":[],"elements":[{"element":"ELEMENT_PLACEHOLDER","note":"NOTE_PLACEHOLDER"}]}]}
JSON
    cat > build/narrativetrace/clarity-scan-report.md <<'MD'
## Elements
MD
    ;;
  *" verifyClarityPolicy "*)
    ok=1
    grep -q 'minScore.set(0.0)' build.gradle.kts || ok=0
    grep -q 'maxHighIssues.set(Int.MAX_VALUE)' build.gradle.kts || ok=0
    grep -q 'warnOnly' build.gradle.kts && ok=0
    [ "$ok" = 1 ]
    ;;
  *)
    echo "fake gradlew: unexpected args: $*" >&2
    exit 1
    ;;
esac
"""
          .replace("ELEMENT_PLACEHOLDER", ELEMENT)
          .replace("NOTE_PLACEHOLDER", NOTE);

  /** A scratch copy of the committed fixture — never its local, gitignored {@code build/}. */
  private static Path scratchCopyOfFixture(Path tempDir) throws IOException {
    Path scratch = tempDir.resolve("scratch");
    try (var stream = Files.walk(FIXTURE)) {
      for (Path source : (Iterable<Path>) stream::iterator) {
        Path relative = FIXTURE.relativize(source);
        if (relative.getNameCount() > 0
            && List.of("build", ".gradle", ".git").contains(relative.getName(0).toString())) {
          continue;
        }
        Path dest = scratch.resolve(relative);
        if (Files.isDirectory(source)) {
          Files.createDirectories(dest);
        } else {
          Files.createDirectories(dest.getParent());
          Files.copy(source, dest);
        }
      }
    }
    return scratch;
  }

  private static void writeFakeGradlew(Path scratch) throws IOException {
    Path gradlew = scratch.resolve("gradlew");
    Files.writeString(gradlew, FAKE_GRADLEW);
    assertThat(gradlew.toFile().setExecutable(true)).isTrue();
  }

  /** The agent's own write, present before the grader runs and before any scan artifact is. */
  private static void writeExplanation(Path scratch, String note) throws IOException {
    Path explanation = scratch.resolve("build/narrativetrace/clarity-explanation.md");
    Files.createDirectories(explanation.getParent());
    Files.writeString(explanation, "## Elements\n\n### placeOrder\n\n" + note + "\n");
  }

  private static int runVerify(Path scratch) throws IOException, InterruptedException {
    Process process =
        new ProcessBuilder("sh", VERIFY_SH.toAbsolutePath().toString())
            .directory(scratch.toFile())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes());
    int exit = process.waitFor();
    if (exit != 0) {
      System.err.println(output);
    }
    return exit;
  }

  @Test
  void anExplanationWrittenBeforeTheArtifactsWereRegeneratedPasses(@TempDir Path tempDir)
      throws Exception {
    Path scratch = scratchCopyOfFixture(tempDir);
    writeFakeGradlew(scratch);
    writeExplanation(scratch, NOTE);

    assertThat(runVerify(scratch)).isZero();
  }

  @Test
  void anExplanationMisquotingAnElementsNoteStillFails(@TempDir Path tempDir) throws Exception {
    Path scratch = scratchCopyOfFixture(tempDir);
    writeFakeGradlew(scratch);
    writeExplanation(scratch, "a note that does not match the scan at all");

    assertThat(runVerify(scratch)).isNotZero();
  }

  @Test
  void aChangedMinScoreStillFails(@TempDir Path tempDir) throws Exception {
    Path scratch = scratchCopyOfFixture(tempDir);
    writeFakeGradlew(scratch);
    writeExplanation(scratch, NOTE);
    Path buildFile = scratch.resolve("build.gradle.kts");
    Files.writeString(
        buildFile, Files.readString(buildFile).replace("minScore.set(0.0)", "minScore.set(0.5)"));

    assertThat(runVerify(scratch)).isNotZero();
  }
}
