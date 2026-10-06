/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adversarial coverage for the coordinate a carrier's own file name yields ({@code
 * Carrier#coordinateOf} and everything that feeds it: {@code artifactOf}, {@code versionOf}, {@code
 * versionDash}, {@code stem}). Every fixture here is hand-built and carries exactly one skill, so a
 * case is about the name-to-coordinate split alone and needs neither {@code -DprojectDir} nor
 * {@code -Dnarrativetrace.buildVersion}.
 */
class CarrierAdversarialM3Test {

  private static final Map<String, String> MINIMAL_ENTRIES =
      Map.of(
          "catalogue.json",
          "{\"runtime\": \"java\", \"skills\": [{\"name\": \"x\", \"description\": \"d\","
              + " \"agents\": \"agents/x/SKILL.md\", \"claude\": \"claude/x/SKILL.md\"}]}",
          "agents/x/SKILL.md",
          "---\nname: x\n---\n\nagents body\n",
          "claude/x/SKILL.md",
          "---\nname: x\n---\n\nclaude body\n");

  private static Path explodedCarrier(Path directory) {
    MINIMAL_ENTRIES.forEach(
        (name, content) -> write(directory.resolve(Carrier.CARRIER_ROOT + name), content));
    return directory;
  }

  private static Path jarCarrier(Path jar) {
    try {
      Files.createDirectories(jar.getParent());
      try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
        for (var entry : MINIMAL_ENTRIES.entrySet()) {
          zip.putNextEntry(new ZipEntry(Carrier.CARRIER_ROOT + entry.getKey()));
          zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
          zip.closeEntry();
        }
      }
      return jar;
    } catch (IOException e) {
      throw new UncheckedIOException("cannot build a test carrier jar at " + jar, e);
    }
  }

  private static void write(Path file, String content) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, content);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot build a test carrier at " + file, e);
    }
  }

  /** A trailing {@code -} with nothing after it never starts a version — nothing is a digit. */
  @Test
  void aTrailingDashWithNoDigitAfterItNeverStartsAVersion(@TempDir Path dir) {
    Carrier carrier = Carrier.open(explodedCarrier(dir.resolve("x-")));

    assertThat(carrier.coordinate()).isEqualTo("ai.narrativetrace:x-:unknown");
  }

  /**
   * A name that is only the separator character itself: not blank, so it is accepted as the whole
   * artifact. Pinned because it is surprising, not because it looks wrong — nothing downstream can
   * mistake it for the coordinate separator, since the stamped coordinate is still exactly three
   * {@code :}-joined parts.
   */
  @Test
  void aNameThatIsOnlyTheDashCharacterIsAcceptedAsTheArtifact(@TempDir Path dir) {
    Carrier carrier = Carrier.open(explodedCarrier(dir.resolve("-")));

    assertThat(carrier.coordinate()).isEqualTo("ai.narrativetrace:-:unknown");
  }

  /**
   * {@code versionDash} returns the FIRST {@code -} immediately followed by a digit; a double dash
   * before the digit must not shift the split by one.
   */
  @Test
  void splitsAtTheFirstDashImmediatelyFollowedByADigitEvenAfterADoubleDash(@TempDir Path dir) {
    Carrier carrier = Carrier.open(explodedCarrier(dir.resolve("x--1.0")));

    assertThat(carrier.coordinate()).isEqualTo("ai.narrativetrace:x-:1.0");
  }

  /** A {@code -SNAPSHOT} suffix has its own dash; the FIRST dash-digit boundary must still win. */
  @Test
  void keepsASnapshotSuffixInsideTheVersionRatherThanSplittingOnItsOwnDash(@TempDir Path dir) {
    Carrier carrier = Carrier.open(explodedCarrier(dir.resolve("x-1.0.0-SNAPSHOT")));

    assertThat(carrier.coordinate()).isEqualTo("ai.narrativetrace:x:1.0.0-SNAPSHOT");
  }

  /** A name with no dash at all, digits included, is the whole artifact — never a version. */
  @Test
  void aNameThatIsOnlyDigitsIsAcceptedAsTheArtifactRatherThanAVersion(@TempDir Path dir) {
    Carrier carrier = Carrier.open(explodedCarrier(dir.resolve("1234")));

    assertThat(carrier.coordinate()).isEqualTo("ai.narrativetrace:1234:unknown");
  }

  /** The {@code .jar} suffix is stripped before the split runs, same as for a directory's name. */
  @Test
  void aJarNameWithNoDashFallsBackToAnUnknownVersion(@TempDir Path dir) {
    Carrier carrier = Carrier.open(jarCarrier(dir.resolve("abcdef.jar")));

    assertThat(carrier.coordinate()).isEqualTo("ai.narrativetrace:abcdef:unknown");
  }
}
