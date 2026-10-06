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
package ai.narrativetrace.cli;

import ai.narrativetrace.tooling.init.Carrier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A carrier built by hand, for tests about WHERE a carrier comes from and what a verb does with
 * one.
 *
 * <p>The real built jars are opened by {@code narrativetrace-tooling}'s own {@code CarrierTest};
 * here a one-skill fixture keeps each case independent of which of this repository's archives
 * happens to have been assembled, and small enough that an assertion can name the whole tree.
 */
final class TestCarriers {

  static final String ROOT = "META-INF/narrativetrace/skills/";

  /** The one skill the fixture carries, and the directory name it installs under. */
  static final String SKILL = "doctor";

  private static final String CATALOGUE =
      "{\"runtime\": \"java\", \"skills\": [{\"name\": \"doctor\", \"description\": \"What the"
          + " doctor does.\", \"agents\": \"agents/doctor/SKILL.md\", \"claude\":"
          + " \"claude/doctor/SKILL.md\"}]}";

  private TestCarriers() {}

  /** A carrier jar at {@code jar}; its file name decides the coordinate it stamps with. */
  static Path jar(Path jar) {
    try {
      Files.createDirectories(jar.getParent());
      try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
        for (var entry : entries().entrySet()) {
          zip.putNextEntry(new ZipEntry(ROOT + entry.getKey()));
          zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
          zip.closeEntry();
        }
      }
      return jar;
    } catch (IOException e) {
      throw new UncheckedIOException("cannot build a test carrier jar at " + jar, e);
    }
  }

  /** An exploded carrier under {@code root}, laid out as a jar would be. */
  static Path directory(Path root) {
    entries().forEach((name, content) -> write(root.resolve(ROOT + name), content));
    return root;
  }

  /** An opened carrier stamped {@code ai.narrativetrace:narrativetrace-skills:1.2.3}. */
  static Carrier carrier(Path parent) {
    return Carrier.open(directory(parent.resolve("narrativetrace-skills-1.2.3")));
  }

  private static Map<String, String> entries() {
    return Map.of(
        "catalogue.json", CATALOGUE,
        "agents/doctor/SKILL.md", "---\nname: doctor\n---\n\nagents body\n",
        "claude/doctor/SKILL.md", "---\nname: doctor\n---\n\nclaude body\n");
  }

  private static void write(Path file, String content) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, content);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot build a test carrier at " + file, e);
    }
  }
}
