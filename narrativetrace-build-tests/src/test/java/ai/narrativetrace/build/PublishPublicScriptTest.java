/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
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
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression coverage for four {@code scripts/publish-public.sh} mechanisms (mirroring the
 * TypeScript/Python/.NET ports' own fixes for the identical gaps):
 *
 * <ol>
 *   <li>(2026-09-13) the trace gate's staged-file-NAME check ({@code name_hits}) is filtered
 *       through {@code .publishallow} the same way its content check already was — before this fix,
 *       a cleanly worded file at an AI-tooling path (e.g. {@code .claude/skills/**}, {@code
 *       ClaudeSkillRenderer.java}) could never clear the gate by review, only by renaming or
 *       deletion;
 *   <li>(2026-09-13) {@code .publishignore}'s {@code !}-prefixed exception syntax, which restores a
 *       path a broader strip pattern removed (from a pristine pre-strip copy of the archive) — the
 *       mechanism {@code !.claude/skills/**} relies on to ship rendered {@code SKILL.md} pages
 *       despite the broader {@code .claude} strip;
 *   <li>(2026-09-13) the step that composes the public snapshot's {@code AGENTS.md} from just its
 *       {@code <!-- narrativetrace:skills:start/end -->} managed section, since {@code
 *       .publishignore} strips the whole (private) file;
 *   <li>(2026-09-13, family finding) the trace gate and the secrets &amp; identity gate each run a
 *       second pass over every staged BINARY asset — {@code grep -riIE}/{@code grep -riE}'s {@code
 *       -I} flag skips binary files outright, so a gated word or this maintainer's identity
 *       embedded in an image's {@code tEXt}/{@code iTXt} chunk, a PDF's XMP dictionary, or a font's
 *       name table shipped undetected (the .NET port's NuGet package icon carried exactly this: a
 *       C2PA provenance chunk naming the AI vendor, in every published package). Binaries are found
 *       by set difference — every staged file minus the ones {@code grep -rlI ''} is willing to
 *       treat as text — then re-scanned with {@code grep -a} for the same patterns, filtered
 *       through the same {@code .publishallow}.
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

  // -----------------------------------------------------------------------------------------
  // 4. Binary-asset re-scan for the trace and secrets & identity gates (2026-09-13 addition)
  // -----------------------------------------------------------------------------------------

  @Nested
  class BinaryAssetScan {

    private final String tracePatternLine = extract("^TRACE_PATTERN='[^']*'$");
    private final String secretsPatternLine = extract("^SECRETS_PATTERN=\"[^\\n]*\"$");
    // The binary-asset discovery block: computes $binary_staged_files by set difference (every
    // staged file minus the ones `grep -I` is willing to treat as text) and defines
    // scan_binary_assets_for(), which the trace and secrets gates both call. From its opening
    // "all_staged_files=" assignment through the function's closing "}".
    private final String binaryScanSnippet =
        extract("all_staged_files=\"\\$\\(cd \"\\$STAGE\".*?\\n\\}");
    // The trace gate's binary re-scan: binary_trace_hits= through its .publishallow filter.
    private final String binaryTraceHitsSnippet =
        extract("binary_trace_hits=\"\\$\\(scan_binary_assets_for.*?\\|\\| true\\)\"");
    // The secrets gate's binary re-scan: same shape, $SECRETS_PATTERN instead.
    private final String binarySecretHitsSnippet =
        extract("binary_secret_hits=\"\\$\\(scan_binary_assets_for.*?\\|\\| true\\)\"");

    private static void writeInt32BE(ByteArrayOutputStream out, long value) {
      out.write((int) ((value >>> 24) & 0xFF));
      out.write((int) ((value >>> 16) & 0xFF));
      out.write((int) ((value >>> 8) & 0xFF));
      out.write((int) (value & 0xFF));
    }

    private static byte[] pngChunk(byte[] tag, byte[] data) {
      var out = new ByteArrayOutputStream();
      writeInt32BE(out, data.length);
      out.writeBytes(tag);
      out.writeBytes(data);
      var crc = new CRC32();
      crc.update(tag);
      crc.update(data);
      writeInt32BE(out, crc.getValue());
      return out.toByteArray();
    }

    private static byte[] deflate(byte[] raw) {
      var deflater = new Deflater();
      deflater.setInput(raw);
      deflater.finish();
      var out = new ByteArrayOutputStream();
      var buf = new byte[256];
      while (!deflater.finished()) {
        int n = deflater.deflate(buf);
        out.write(buf, 0, n);
      }
      deflater.end();
      return out.toByteArray();
    }

    private static byte[] ihdrChunk() {
      var ihdr = new ByteArrayOutputStream();
      writeInt32BE(ihdr, 1); // width
      writeInt32BE(ihdr, 1); // height
      ihdr.writeBytes(
          new byte[] {8, 0, 0, 0, 0}); // depth, color type, compression, filter, interlace
      return pngChunk("IHDR".getBytes(StandardCharsets.US_ASCII), ihdr.toByteArray());
    }

    /** An {@code iTXt} chunk carrying {@code text} as an untranslated, uncompressed comment. */
    private static byte[] itxtChunk(String text) {
      var itxt = new ByteArrayOutputStream();
      itxt.writeBytes("Comment".getBytes(StandardCharsets.US_ASCII));
      itxt.write(0); // keyword terminator
      itxt.write(0); // compression flag
      itxt.write(0); // compression method
      itxt.write(0); // language tag terminator (empty language tag)
      itxt.write(0); // translated keyword terminator (empty translated keyword)
      itxt.writeBytes(text.getBytes(StandardCharsets.UTF_8));
      return pngChunk("iTXt".getBytes(StandardCharsets.US_ASCII), itxt.toByteArray());
    }

    /**
     * A real, valid 1x1 grayscale PNG, built byte-for-byte — no image library. With {@code
     * itxtText} given, carries it in an {@code iTXt} metadata chunk (the same chunk type a real
     * image editor or a provenance stamp — e.g. C2PA — would use), the way the family finding's
     * NuGet package icon carried its embedded vendor-naming block.
     */
    private static byte[] minimalPng(String itxtText) {
      var out = new ByteArrayOutputStream();
      out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
      out.writeBytes(ihdrChunk());
      byte[] rawScanline = {0, 0}; // filter-type byte + one gray pixel
      out.writeBytes(pngChunk("IDAT".getBytes(StandardCharsets.US_ASCII), deflate(rawScanline)));
      if (itxtText != null) {
        out.writeBytes(itxtChunk(itxtText));
      }
      out.writeBytes(pngChunk("IEND".getBytes(StandardCharsets.US_ASCII), new byte[0]));
      return out.toByteArray();
    }

    /**
     * Stages a planted PNG ({@code planted.png}, {@code iTXt} chunk carrying "claude") next to a
     * clean PNG ({@code clean.png}) and an ordinary text file ({@code plain.md}, contains "claude"
     * too, to prove the binary path is genuinely additive to the existing text-content gate rather
     * than a replacement for it), then runs the real script's binary-asset discovery plus one
     * gate's {@code binary_*_hits=} computation.
     */
    private ScriptResult runBinaryHits(Path tmp, String hitsSnippet, String allowContents)
        throws IOException, InterruptedException {
      var stage = tmp.resolve("stage");
      Files.createDirectories(stage);
      Files.write(stage.resolve("planted.png"), minimalPng("rendered by claude"));
      Files.write(stage.resolve("clean.png"), minimalPng(null));
      Files.writeString(stage.resolve("plain.md"), "claude");

      var allow = tmp.resolve(".publishallow");
      Files.writeString(allow, allowContents);

      var varName = hitsSnippet.split("=", 2)[0];
      var body =
          String.join(
              "\n",
              "set -euo pipefail",
              "STAGE=\"" + stage + "\"",
              "allow=\"" + allow + "\"",
              tracePatternLine,
              secretsPatternLine,
              binaryScanSnippet,
              hitsSnippet,
              "printf '%s' \"$" + varName + "\"");
      return runBash(body, Map.of());
    }

    @Test
    void aGatedWordInAPngITxtChunkIsCaught(@TempDir Path tmp) throws Exception {
      var result = runBinaryHits(tmp, binaryTraceHitsSnippet, "");

      assertThat(result.exitCode()).as("stderr: %s", result.stderr()).isZero();
      assertThat(result.stdout()).contains("planted.png");
    }

    @Test
    void aCleanPngPasses(@TempDir Path tmp) throws Exception {
      var result = runBinaryHits(tmp, binaryTraceHitsSnippet, "");

      assertThat(result.stdout()).doesNotContain("clean.png");
    }

    @Test
    void theTextFileIsLeftToTheExistingContentGateNotThisOne(@TempDir Path tmp) throws Exception {
      // plain.md is real TEXT (grep -I treats it as such), so it is outside $binary_staged_files
      // entirely -- this proves the binary scan is additive, not a wholesale replacement that
      // would double-report (or worse, mis-report) an ordinary text hit.
      var result = runBinaryHits(tmp, binaryTraceHitsSnippet, "");

      assertThat(result.stdout()).doesNotContain("plain.md");
    }

    @Test
    void aReviewedPublishallowEntryClearsThePlantedPng(@TempDir Path tmp) throws Exception {
      var result = runBinaryHits(tmp, binaryTraceHitsSnippet, "^\\./planted\\.png$\n");

      assertThat(result.stdout()).doesNotContain("planted.png");
    }

    @Test
    void theAllowlistDoesNotBlanketClearEveryBinaryHit(@TempDir Path tmp) throws Exception {
      var result = runBinaryHits(tmp, binaryTraceHitsSnippet, "^\\./nonexistent$\n");

      assertThat(result.stdout()).contains("planted.png");
    }

    @Test
    void theSecretsGateBinaryScanCatchesThePlantedIdentityMarker(@TempDir Path tmp)
        throws Exception {
      // Reuses the same fixture shape but plants an identity marker SECRETS_PATTERN actually
      // gates on, proving the secrets gate's own binary re-scan (independent code path from the
      // trace gate's) is wired up too.
      var stage = tmp.resolve("stage");
      Files.createDirectories(stage);
      Files.write(stage.resolve("planted.png"), minimalPng("built by danijel"));
      Files.write(stage.resolve("clean.png"), minimalPng(null));
      var allow = tmp.resolve(".publishallow");
      Files.writeString(allow, "");

      var body =
          String.join(
              "\n",
              "set -euo pipefail",
              "STAGE=\"" + stage + "\"",
              "allow=\"" + allow + "\"",
              tracePatternLine,
              secretsPatternLine,
              binaryScanSnippet,
              binarySecretHitsSnippet,
              "printf '%s' \"$binary_secret_hits\"");
      var result = runBash(body, Map.of());

      assertThat(result.exitCode()).as("stderr: %s", result.stderr()).isZero();
      assertThat(result.stdout()).contains("planted.png");
      assertThat(result.stdout()).doesNotContain("clean.png");
    }
  }
}
