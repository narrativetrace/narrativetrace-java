/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Turning the {@code narrativeTraceSkills} configuration's one file into an opened carrier — and,
 * more to the point, turning every way that can fail into a REASON rather than a stack trace, so
 * one caller can fail the build with it and the other can warn and carry on.
 */
class SkillsCarrierTest {

  private static final String ROOT = "META-INF/narrativetrace/skills/";

  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:7.7.7";

  @Test
  void opensTheOneJarTheConfigurationResolvedTo(@TempDir Path dir) {
    File jar = carrierJar(dir.resolve("narrativetrace-skills-7.7.7.jar"));

    var resolution = SkillsCarrier.resolve(() -> Set.of(jar), COORDINATE);

    assertThat(resolution.failure()).isNull();
    assertThat(resolution.carrier().coordinate())
        .isEqualTo("ai.narrativetrace:narrativetrace-skills:7.7.7");
  }

  /**
   * Nothing resolved is what OFFLINE looks like through the lenient artifact view the plugin reads
   * the carrier with — not an exception, just an empty set. It has to name the coordinate, because
   * the lenient view has swallowed Gradle's own "searched in" report by then.
   */
  @Test
  void reportsAResolutionThatFoundNothing() {
    var resolution = SkillsCarrier.resolve(Set::of, COORDINATE);

    assertThat(resolution.carrier()).isNull();
    assertThat(resolution.failure())
        .isEqualTo("no repository in this build provides " + COORDINATE);
  }

  @Test
  void reportsAResolutionThatFoundMoreThanOneJar(@TempDir Path dir) {
    File one = carrierJar(dir.resolve("narrativetrace-skills-7.7.7.jar"));
    File two = carrierJar(dir.resolve("narrativetrace-skills-8.8.8.jar"));

    var resolution = SkillsCarrier.resolve(() -> Set.of(one, two), COORDINATE);

    assertThat(resolution.failure()).contains(COORDINATE).contains("2 files");
  }

  /** Offline, or no repository declares it: Gradle throws, and that throw becomes the reason. */
  @Test
  void reportsAResolutionThatThrewInsteadOfLettingItEscape() {
    var resolution =
        SkillsCarrier.resolve(
            () -> {
              throw new IllegalStateException("Could not resolve all files");
            },
            COORDINATE);

    assertThat(resolution.carrier()).isNull();
    assertThat(resolution.failure()).isEqualTo("Could not resolve all files");
  }

  @Test
  void reportsAFailureWithNoMessageOfItsOwnByItsType() {
    var resolution =
        SkillsCarrier.resolve(
            () -> {
              throw new IllegalStateException();
            },
            COORDINATE);

    assertThat(resolution.failure()).contains("IllegalStateException");
  }

  @Test
  void reportsAJarThatIsNotACarrierAtAll(@TempDir Path dir) throws IOException {
    File notACarrier = Files.writeString(dir.resolve("empty-1.0.0.jar"), "not a jar").toFile();

    var resolution = SkillsCarrier.resolve(() -> Set.of(notACarrier), COORDINATE);

    assertThat(resolution.carrier()).isNull();
    assertThat(resolution.failure()).isNotBlank();
  }

  private static File carrierJar(Path jar) {
    Map<String, String> entries =
        Map.of(
            "catalogue.json",
            "{\"runtime\": \"java\", \"skills\": [{\"name\": \"doctor\", \"description\": \"d\","
                + " \"agents\": \"agents/doctor/SKILL.md\", \"claude\":"
                + " \"claude/doctor/SKILL.md\"}]}",
            "agents/doctor/SKILL.md",
            "---\nname: doctor\n---\n\nagents\n",
            "claude/doctor/SKILL.md",
            "---\nname: doctor\n---\n\nclaude\n");
    try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
      for (var entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(ROOT + entry.getKey()));
        zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("cannot build a test carrier jar at " + jar, e);
    }
    return jar.toFile();
  }
}
