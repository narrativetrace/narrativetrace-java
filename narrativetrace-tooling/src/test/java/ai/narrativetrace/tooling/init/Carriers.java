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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Carriers for tests: the REAL one built by this repo, and a small hand-built one whose skill list
 * a test controls.
 *
 * <p>The real carrier is the one every end-to-end case uses, because a fixture carrier proves only
 * that the fixture is consistent. The hand-built one exists so a planner case can say "two skills"
 * and assert on two actions instead of on the whole catalogue.
 */
public final class Carriers {

  public static final String FAKE_COORDINATE = "ai.narrativetrace:narrativetrace-skills:1.2.3";

  private static final Path PROJECT_DIR = Path.of(System.getProperty("projectDir"));

  private static final String VERSION = System.getProperty("narrativetrace.buildVersion");

  private static Carrier shared;

  private Carriers() {}

  /**
   * One hand-built carrier for the whole JVM — the generative properties build thousands of
   * projects and none of them cares which skills the carrier lists.
   */
  public static synchronized Carrier shared() {
    if (shared == null) {
      try {
        shared = fake(Files.createTempDirectory("narrativetrace-carrier"), "doctor", "clarity");
      } catch (IOException e) {
        throw new UncheckedIOException("cannot build the shared test carrier", e);
      }
    }
    return shared;
  }

  /** The carrier this build produced — the archive a consumer really gets. */
  public static Carrier real() {
    return Carrier.open(
        PROJECT_DIR.resolve(
            "narrativetrace-skills/build/libs/narrativetrace-skills-" + VERSION + ".jar"));
  }

  /** The coordinate {@link #real()} stamps with. */
  public static String realCoordinate() {
    return "ai.narrativetrace:narrativetrace-skills:" + VERSION;
  }

  /** An exploded carrier under {@code parent}, carrying one page per name. */
  public static Carrier fake(Path parent, String... names) {
    Path root = parent.resolve("narrativetrace-skills-1.2.3").resolve(Carrier.CARRIER_ROOT);
    StringBuilder catalogue = new StringBuilder("{\"runtime\": \"java\", \"skills\": [");
    for (int i = 0; i < names.length; i++) {
      catalogue.append(i == 0 ? "" : ",").append(entry(names[i]));
      for (SkillFlavour flavour : SkillFlavour.values()) {
        write(
            root.resolve(page(names[i], flavour)), page(names[i], flavour, "body of " + names[i]));
      }
    }
    write(root.resolve(Carrier.CATALOGUE_FILE), catalogue.append("]}").toString());
    return Carrier.open(parent.resolve("narrativetrace-skills-1.2.3"));
  }

  /** The page text the fake carrier carries for one skill and flavour. */
  public static String body(String name, SkillFlavour flavour) {
    return page(name, flavour, "body of " + name);
  }

  private static String page(String name, SkillFlavour flavour, String body) {
    return "---\nname: " + name + "\nflavour: " + flavour + "\n---\n\n" + body + "\n";
  }

  private static String page(String name, SkillFlavour flavour) {
    return (flavour == SkillFlavour.AGENTS ? "agents/" : "claude/") + name + "/SKILL.md";
  }

  private static String entry(String name) {
    return "{\"name\": \""
        + name
        + "\", \"description\": \"What "
        + name
        + " does.\", \"agents\": \"agents/"
        + name
        + "/SKILL.md\", \"claude\": \"claude/"
        + name
        + "/SKILL.md\"}";
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
