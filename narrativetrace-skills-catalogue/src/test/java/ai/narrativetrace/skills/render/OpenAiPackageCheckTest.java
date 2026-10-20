/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenAiPackageCheckTest {

  private static final String MANIFEST_TEMPLATE =
      "{\n"
          + "  \"name\": \"narrativetrace-x\",\n"
          + "  \"version\": \"%s\",\n"
          + "  \"extensions\": {\n"
          + "    \"com.openai\": {\n"
          + "      \"interface\": {\n"
          + "        \"logo\": \"./assets/logo.png\",\n"
          + "        \"composerIcon\": \"./assets/composer-icon.png\"\n"
          + "      }\n"
          + "    }\n"
          + "  }\n"
          + "}\n";

  private static Map<String, byte[]> validEntries(String version) {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    entries.put("plugin.json", String.format(MANIFEST_TEMPLATE, version).getBytes());
    entries.put("skills/narrativetrace-doctor/SKILL.md", "page".getBytes());
    entries.put("assets/logo.png", PngFixtures.flat(64, 64, (byte) 0x4a));
    entries.put("assets/composer-icon.png", PngFixtures.flat(64, 64, (byte) 0x4a));
    return entries;
  }

  private static List<String> expectedFor(Map<String, byte[]> entries) {
    return entries.keySet().stream().sorted().toList();
  }

  private static Path zip(Path dir, Map<String, byte[]> entries) {
    return zip(dir, entries, false);
  }

  private static Path zip(Path dir, Map<String, byte[]> entries, boolean withDirectoryEntries) {
    Path target = dir.resolve("package.zip");
    try (var out = new ZipOutputStream(Files.newOutputStream(target))) {
      if (withDirectoryEntries) {
        out.putNextEntry(new ZipEntry("skills/"));
        out.closeEntry();
        out.putNextEntry(new ZipEntry("assets/"));
        out.closeEntry();
      }
      for (var entry : entries.entrySet()) {
        out.putNextEntry(new ZipEntry(entry.getKey()));
        out.write(entry.getValue());
        out.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return target;
  }

  @Test
  void aValidPackageHasNoViolations(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).isEmpty();
  }

  @Test
  void directoryEntriesAZipFormatArtifactAreNotTreatedAsUnexpectedContent(@TempDir Path dir)
      throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries, true), expectedFor(entries), "1.2.3");

    assertThat(violations).isEmpty();
  }

  @Test
  void flagsAMissingExpectedEntry(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    List<String> expected = expectedFor(entries);
    entries.remove("skills/narrativetrace-doctor/SKILL.md");

    List<String> violations = OpenAiPackageCheck.violations(zip(dir, entries), expected, "1.2.3");

    assertThat(violations).anyMatch(v -> v.contains("entries do not match"));
  }

  @Test
  void flagsAForbiddenHooksEntry(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    entries.put("hooks/pre-install.js", "x".getBytes());

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).anyMatch(v -> v.contains("hooks/pre-install.js"));
  }

  /**
   * Boundary-matching siblings for every forbidden check: a name sharing the prefix/suffix without
   * the delimiter that actually makes it a match must NOT be flagged.
   */
  @Test
  void doesNotFlagNearMissSiblingsOfEveryForbiddenName(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    entries.put("hooksworthy/file.js", "x".getBytes());
    entries.put("notmcp.json", "{}".getBytes());
    entries.put("widgetapp.json", "{}".getBytes());
    entries.put("NotesClass.txt", "x".getBytes());

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).noneMatch(v -> v.contains("forbidden entry present"));
  }

  @Test
  void flagsAForbiddenMcpJsonEntry(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    entries.put("mcp.json", "{}".getBytes());

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).anyMatch(v -> v.contains("mcp.json"));
  }

  @Test
  void flagsAForbiddenAppJsonEntry(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    entries.put("widget.app.json", "{}".getBytes());

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).anyMatch(v -> v.contains("widget.app.json"));
  }

  @Test
  void flagsAForbiddenClassEntry(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    entries.put("Hooks.class", "x".getBytes());

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).anyMatch(v -> v.contains("Hooks.class"));
  }

  @Test
  void flagsANonSemverVersion(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3-SNAPSHOT");

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3-SNAPSHOT");

    assertThat(violations).anyMatch(v -> v.contains("not semantic"));
  }

  @Test
  void flagsAManifestVersionThatDoesNotMatchTheExpectedVersion(@TempDir Path dir)
      throws IOException {
    Map<String, byte[]> entries = validEntries("9.9.9");

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).anyMatch(v -> v.contains("does not equal"));
  }

  @Test
  void flagsAManifestPathThatResolvesToNoEntry(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    entries.remove("assets/logo.png");
    List<String> expected = expectedFor(validEntries("1.2.3"));

    List<String> violations = OpenAiPackageCheck.violations(zip(dir, entries), expected, "1.2.3");

    assertThat(violations).anyMatch(v -> v.contains("./assets/logo.png"));
  }

  @Test
  void flagsAnIconLargerThanFiveMebibytes(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    entries.put("assets/logo.png", new byte[6 * 1024 * 1024]);

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).anyMatch(v -> v.contains("5 MiB"));
  }

  @Test
  void flagsANonSquareIcon(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    entries.put("assets/logo.png", PngFixtures.flat(64, 32, (byte) 0x4a));

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).anyMatch(v -> v.contains("not square"));
  }

  @Test
  void flagsAnIconSmallerThanFortyEightPixels(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    entries.put("assets/logo.png", PngFixtures.flat(32, 32, (byte) 0x4a));

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).anyMatch(v -> v.contains("smaller than 48"));
  }

  @Test
  void anIconExactlyFortyEightPixelsIsAccepted(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    entries.put("assets/logo.png", PngFixtures.flat(48, 48, (byte) 0x4a));

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).isEmpty();
  }

  @Test
  void anIconExactlyFiveMebibytesIsAccepted(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    byte[] exactlyFiveMebibytes = PngFixtures.flat(64, 64, (byte) 0x4a);
    entries.put("assets/logo.png", Arrays.copyOf(exactlyFiveMebibytes, 5 * 1024 * 1024));

    List<String> violations =
        OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3");

    assertThat(violations).noneMatch(v -> v.contains("5 MiB"));
  }

  @Test
  void throwsWhenThePackageCarriesNoManifestAtAll(@TempDir Path dir) throws IOException {
    Map<String, byte[]> entries = validEntries("1.2.3");
    entries.remove("plugin.json");

    assertThatThrownBy(
            () -> OpenAiPackageCheck.violations(zip(dir, entries), expectedFor(entries), "1.2.3"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("plugin.json");
  }
}
