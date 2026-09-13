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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression coverage for three {@code scripts/publish-public.sh} mechanisms added 2026-09-13
 * (mirroring the TypeScript/Python ports' own fixes for the identical gaps):
 *
 * <ol>
 *   <li>the trace gate's staged-file-NAME check ({@code name_hits}) is filtered through {@code
 *       .publishallow} the same way its content check already was — before this fix, a cleanly
 *       worded file at an AI-tooling path (e.g. {@code .claude/skills/**}, {@code
 *       ClaudeSkillRenderer.java}) could never clear the gate by review, only by renaming or
 *       deletion;
 *   <li>{@code .publishignore}'s {@code !}-prefixed exception syntax, which restores a path a
 *       broader strip pattern removed (from a pristine pre-strip copy of the archive) — the
 *       mechanism {@code !.claude/skills/**} relies on to ship rendered {@code SKILL.md} pages
 *       despite the broader {@code .claude} strip;
 *   <li>the step that composes the public snapshot's {@code AGENTS.md} from just its {@code <!--
 *       narrativetrace:skills:start/end -->} managed section, since {@code .publishignore} strips
 *       the whole (private) file.
 * </ol>
 *
 * <p>Every snippet below is extracted out of the REAL script text (never a hand-copied duplicate)
 * so these tests cannot silently drift from what actually gates a publish, then executed in
 * isolation against a synthetic staged tree — the same technique {@link
 * VerifyPublicationScriptTest} uses for {@code verify-publication.sh}'s own functions.
 */
class PublishPublicScriptTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));
  private static final File SCRIPT = new File(PROJECT_DIR, "scripts/publish-public.sh");

  // scripts/publish-public.sh is itself private machinery (.publishignore strips it): the
  // --verify step of a real publish run builds and tests the STAGED (published) snapshot
  // standalone, where this file legitimately does not exist. `extract` below asserts the
  // assumption BEFORE reading the file, never as a static field initializer, so a missing script
  // aborts (skips) every test that needs it instead of failing the whole class at class-load time.
  private static String scriptText;

  private static String readScript() {
    if (scriptText == null) {
      try {
        scriptText = Files.readString(SCRIPT.toPath(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return scriptText;
  }

  /**
   * The first match of {@code pattern} in the real script text, failing loudly if it moved —
   * skipped (never failed) when the script itself is absent (see the field comment above).
   */
  private static String extract(String pattern) {
    Assumptions.assumeTrue(
        SCRIPT.isFile(),
        SCRIPT + " not present (private publish machinery, stripped from this snapshot)");
    Matcher matcher =
        Pattern.compile(pattern, Pattern.DOTALL | Pattern.MULTILINE).matcher(readScript());
    if (!matcher.find()) {
      throw new AssertionError("pattern not found in " + SCRIPT + ": " + pattern);
    }
    return matcher.group();
  }

  private record ScriptResult(String stdout, String stderr, int exitCode) {}

  private static ScriptResult runBash(String body, Map<String, String> env)
      throws IOException, InterruptedException {
    var command = new java.util.ArrayList<String>(List.of("bash", "-c", body));
    var processBuilder = new ProcessBuilder(command);
    processBuilder.environment().putAll(env);
    var process = processBuilder.start();
    var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    var exitCode = process.waitFor();
    return new ScriptResult(stdout, stderr, exitCode);
  }

  // -----------------------------------------------------------------------------------------
  // 1. name_hits filtered through .publishallow (2026-09-13 fix)
  // -----------------------------------------------------------------------------------------

  @Nested
  class NameHitsAllowlistFiltering {

    private final String tracePatternLine = extract("^TRACE_PATTERN='[^']*'$");
    // The two-line name_hits= assignment: from its opening "$(cd "$STAGE"" through the first
    // "|| true)\"" that closes it — the fallback-empty-string idiom every gate in this script
    // shares.
    private final String nameHitsSnippet =
        extract("name_hits=\"\\$\\(cd \"\\$STAGE\".*?\\|\\| true\\)\"");

    private String runNameHits(Path stage, String allowContents)
        throws IOException, InterruptedException {
      var allow = stage.resolveSibling("publishallow-" + stage.getFileName());
      Files.writeString(allow, allowContents);
      var body =
          String.join(
              "\n",
              "set -euo pipefail",
              "STAGE=\"" + stage + "\"",
              "allow=\"" + allow + "\"",
              tracePatternLine,
              nameHitsSnippet,
              "printf '%s' \"$name_hits\"");
      var result = runBash(body, Map.of());
      assertThat(result.exitCode()).as("script stderr: %s", result.stderr()).isZero();
      return result.stdout();
    }

    private Path stageWithClaudeSkills(Path tmp) throws IOException {
      var stage = tmp.resolve("stage");
      Files.createDirectories(stage.resolve(".claude/skills"));
      Files.writeString(stage.resolve(".claude/skills/add.md"), "hi");
      Files.writeString(stage.resolve("plain.md"), "hi");
      return stage;
    }

    @Test
    void anUnreviewedAiToolNamedPathIsStillCaught(@TempDir Path tmp) throws Exception {
      var hits = runNameHits(stageWithClaudeSkills(tmp), "");

      assertThat(hits).contains("./.claude");
    }

    @Test
    void aReviewedPublishallowEntryClearsTheSamePath(@TempDir Path tmp) throws Exception {
      var hits = runNameHits(stageWithClaudeSkills(tmp), "^\\./\\.claude(/.*)?$\n");

      assertThat(hits).doesNotContain("./.claude");
    }

    @Test
    void anUnrelatedPlainFileNeverHitsRegardlessOfTheAllowlist(@TempDir Path tmp) throws Exception {
      var hits = runNameHits(stageWithClaudeSkills(tmp), "");

      assertThat(hits).doesNotContain("plain.md");
    }

    /**
     * A reviewed entry for one path must not silently launder an unrelated hit — proves the filter
     * is a real regex match, not {@code [ -s "$allow" ]} alone short-circuiting the whole gate.
     */
    @Test
    void theAllowlistDoesNotBlanketClearEveryNameHit(@TempDir Path tmp) throws Exception {
      var hits = runNameHits(stageWithClaudeSkills(tmp), "^\\./nonexistent$\n");

      assertThat(hits).contains("./.claude");
    }
  }

  // -----------------------------------------------------------------------------------------
  // 2. .publishignore's `!`-exception restore (2026-09-13 addition)
  // -----------------------------------------------------------------------------------------

  @Nested
  class PublishignoreExceptionRestore {

    private final String restoreSnippet =
        extract(
            "restored=0\\n"
                + "while IFS= read -r pattern; do.*?\\n"
                + "done < \"\\$REPO_ROOT/\\.publishignore\"");

    private ScriptResult runRestore(Path repoRoot, Path stage, Path pristine, String ignoreText)
        throws IOException, InterruptedException {
      Files.writeString(repoRoot.resolve(".publishignore"), ignoreText);
      var body =
          String.join(
              "\n",
              "set -euo pipefail",
              "STAGE=\"" + stage + "\"",
              "PRISTINE=\"" + pristine + "\"",
              "REPO_ROOT=\"" + repoRoot + "\"",
              restoreSnippet,
              "printf '%s' \"$restored\"");
      return runBash(body, Map.of());
    }

    @Test
    void copiesADirectoryAStripPatternRemovedBackFromThePristineSnapshot(
        @TempDir Path repoRoot, @TempDir Path stage, @TempDir Path pristine) throws Exception {
      Files.createDirectories(pristine.resolve(".claude/skills/doctor"));
      Files.writeString(pristine.resolve(".claude/skills/doctor/SKILL.md"), "doctor content");
      assertThat(stage.resolve(".claude")).doesNotExist();

      var result = runRestore(repoRoot, stage, pristine, "!.claude/skills\n");

      assertThat(result.exitCode()).as("stderr: %s", result.stderr()).isZero();
      assertThat(result.stdout()).isEqualTo("1");
      assertThat(stage.resolve(".claude/skills/doctor/SKILL.md"))
          .content()
          .isEqualTo("doctor content");
    }

    @Test
    void aTrailingDoubleStarIsStrippedAsSugarForARestoredWholeDirectory(
        @TempDir Path repoRoot, @TempDir Path stage, @TempDir Path pristine) throws Exception {
      Files.createDirectories(pristine.resolve(".claude/skills/doctor"));
      Files.writeString(pristine.resolve(".claude/skills/doctor/SKILL.md"), "doctor content");

      var result = runRestore(repoRoot, stage, pristine, "!.claude/skills/**\n");

      assertThat(result.exitCode()).isZero();
      assertThat(stage.resolve(".claude/skills/doctor/SKILL.md")).exists();
    }

    @Test
    void leavesASiblingPathTheExceptionDidNotNameUntouched(
        @TempDir Path repoRoot, @TempDir Path stage, @TempDir Path pristine) throws Exception {
      Files.createDirectories(pristine.resolve(".claude/skills"));
      Files.writeString(pristine.resolve(".claude/skills/SKILL.md"), "content");
      Files.writeString(pristine.resolve(".claude/settings.local.json"), "{}");

      runRestore(repoRoot, stage, pristine, "!.claude/skills\n");

      assertThat(stage.resolve(".claude/skills/SKILL.md")).exists();
      assertThat(stage.resolve(".claude/settings.local.json")).doesNotExist();
    }

    @Test
    void reportsAPathAbsentFromThePristineSnapshotAsAHardError(
        @TempDir Path repoRoot, @TempDir Path stage, @TempDir Path pristine) throws Exception {
      var result = runRestore(repoRoot, stage, pristine, "!.claude/does-not-exist\n");

      assertThat(result.exitCode()).isNotZero();
      assertThat(result.stdout())
          .contains("ERROR: .publishignore exception '!.claude/does-not-exist'")
          .contains("not found in the pristine snapshot");
    }

    @Test
    void commentsAndOrdinaryStripLinesAreIgnored(
        @TempDir Path repoRoot, @TempDir Path stage, @TempDir Path pristine) throws Exception {
      var result =
          runRestore(
              repoRoot,
              stage,
              pristine,
              String.join("\n", "# a comment", "", "CLAUDE.md", "reports") + "\n");

      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).isEqualTo("0");
    }
  }

  // -----------------------------------------------------------------------------------------
  // 3. AGENTS.md composed from its managed section only (2026-09-13 addition)
  // -----------------------------------------------------------------------------------------

  @Nested
  class AgentsMdSectionComposition {

    private final String composeSnippet =
        extract(
            "agents_md_src=\"\\$REPO_ROOT/AGENTS\\.md\".*?\"\\$agents_md_src\" >"
                + " \"\\$STAGE/AGENTS\\.md\"");

    private ScriptResult runCompose(Path repoRoot, Path stage)
        throws IOException, InterruptedException {
      var body =
          String.join(
              "\n",
              "set -euo pipefail",
              "STAGE=\"" + stage + "\"",
              "REPO_ROOT=\"" + repoRoot + "\"",
              composeSnippet);
      return runBash(body, Map.of());
    }

    @Test
    void writesOnlyTheManagedSection(@TempDir Path repoRoot, @TempDir Path stage) throws Exception {
      Files.writeString(
          repoRoot.resolve("AGENTS.md"),
          "# Private briefing\n\nInternal-only prose.\n\n"
              + "<!-- narrativetrace:skills:start -->\nshared section\n"
              + "<!-- narrativetrace:skills:end -->\n\nMore internal prose.\n");

      var result = runCompose(repoRoot, stage);

      assertThat(result.exitCode()).as("stderr: %s", result.stdout()).isZero();
      var written = Files.readString(stage.resolve("AGENTS.md"));
      assertThat(written)
          .isEqualTo(
              "<!-- narrativetrace:skills:start -->\n"
                  + "shared section\n"
                  + "<!-- narrativetrace:skills:end -->\n")
          .doesNotContain("Private briefing")
          .doesNotContain("Internal-only prose")
          .doesNotContain("More internal prose");
    }

    @Test
    void matchesThisRepositoryOwnCommittedAgentsMd(@TempDir Path stage) throws Exception {
      var result = runCompose(PROJECT_DIR.toPath(), stage);

      assertThat(result.exitCode()).as("stderr: %s", result.stdout()).isZero();
      var written = Files.readString(stage.resolve("AGENTS.md"));
      assertThat(written)
          .startsWith("<!-- narrativetrace:skills:start -->\n")
          .contains("## NarrativeTrace agent skills")
          .endsWith("<!-- narrativetrace:skills:end -->\n");
    }

    @Test
    void failsNamingTheFileWhenNoSectionIsPresent(@TempDir Path repoRoot, @TempDir Path stage)
        throws Exception {
      Files.writeString(repoRoot.resolve("AGENTS.md"), "no markers here\n");

      var result = runCompose(repoRoot, stage);

      assertThat(result.exitCode()).isNotZero();
      assertThat(result.stdout())
          .contains("ERROR:")
          .contains("AGENTS.md")
          .contains("no narrativetrace:skills section to extract");
      assertThat(stage.resolve("AGENTS.md")).doesNotExist();
    }
  }
}
