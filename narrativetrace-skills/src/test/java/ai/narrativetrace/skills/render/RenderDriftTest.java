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
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import ai.narrativetrace.skills.catalogue.ProListings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code SKILL.md} and the {@code AGENTS.md} managed section are committed BUILD OUTPUT — this test
 * is their drift check: what {@link CatalogueIndex#ALL}'s typed source renders TODAY must
 * byte-match what is committed. A failure here means someone hand-edited a rendered file, or
 * changed the catalogue without re-rendering — both are the same bug (documentation/
 * what-to-commit.md's rule: regenerated, reviewed in diffs, checked against drift, never
 * hand-edited).
 */
class RenderDriftTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));

  static List<Skill> skills() {
    return CatalogueIndex.ALL;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("skills")
  void renderedSkillMdMatchesTheTypedCatalogue(Skill skill) {
    Path onDisk = RenderPaths.claudeSkillMd(REPO_ROOT, skill);
    assertThat(onDisk)
        .as(onDisk + " must exist — run the skills renderer and commit its output")
        .exists();
    assertThat(readText(onDisk)).isEqualTo(ClaudeSkillRenderer.render(skill));
  }

  @Test
  void agentsMdManagedSectionMatchesTheTypedCatalogue() {
    Path agentsMd = RenderPaths.agentsMd(REPO_ROOT);
    String onDisk = readText(agentsMd);
    String freshlySpliced =
        AgentsMdRenderer.splice(
            onDisk, AgentsMdRenderer.renderSection(CatalogueIndex.ALL, ProListings.ALL));

    assertThat(onDisk).isEqualTo(freshlySpliced);
  }

  private static String readText(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + path, e);
    }
  }
}
