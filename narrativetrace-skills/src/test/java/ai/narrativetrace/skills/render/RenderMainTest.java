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

import ai.narrativetrace.skills.StepBody;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderMainTest {

  @Test
  void rendersEverySkillPageAndSplicesAgentsMd(@TempDir Path repoRoot) throws IOException {
    Files.writeString(repoRoot.resolve("AGENTS.md"), "# A repo\n\nSome prose.\n");
    seedSnippetSources(repoRoot);

    RenderMain.render(repoRoot);

    for (var skill : CatalogueIndex.ALL) {
      Path rendered = RenderPaths.claudeSkillMd(repoRoot, skill);
      assertThat(rendered).exists();
      assertThat(Files.readString(rendered)).isEqualTo(ClaudeSkillRenderer.render(skill, repoRoot));

      Path codexRendered = RenderPaths.codexSkillMd(repoRoot, skill);
      assertThat(codexRendered).exists();
      assertThat(Files.readString(codexRendered))
          .isEqualTo(CodexSkillRenderer.render(skill, repoRoot));
    }
    assertThat(Files.readString(repoRoot.resolve("AGENTS.md")))
        .contains(AgentsMdRenderer.BEGIN_MARKER)
        .contains(AgentsMdRenderer.END_MARKER);
  }

  @Test
  void mainRendersIntoTheDirectoryNamedByItsFirstArgument(@TempDir Path repoRoot)
      throws IOException {
    Files.writeString(repoRoot.resolve("AGENTS.md"), "# A repo\n");
    seedSnippetSources(repoRoot);

    RenderMain.main(new String[] {repoRoot.toString()});

    assertThat(RenderPaths.claudeSkillMd(repoRoot, CatalogueIndex.ALL.get(0))).exists();
    assertThat(RenderPaths.codexSkillMd(repoRoot, CatalogueIndex.ALL.get(0))).exists();
  }

  /**
   * Copies every {@link StepBody.SnippetStep} source this catalogue names into the fake {@code
   * repoRoot} so a render against it can read them, exactly as a render against the real repo root
   * would — {@code projectDir} is the same system property {@link RenderDriftTest} and {@code
   * TierA2ReplayTest} already rely on to find the real repo root from this module's test JVM.
   */
  private static void seedSnippetSources(Path repoRoot) throws IOException {
    Path realRepoRoot = Path.of(System.getProperty("projectDir"));
    for (var skill : CatalogueIndex.ALL) {
      for (var step : skill.steps()) {
        if (step.body() instanceof StepBody.SnippetStep snippet) {
          Path target = repoRoot.resolve(snippet.path());
          Files.createDirectories(target.getParent());
          Files.copy(realRepoRoot.resolve(snippet.path()), target);
        }
      }
    }
  }

  @Test
  void resolveRepoRootUsesTheFirstArgumentWhenGiven(@TempDir Path repoRoot) {
    assertThat(RenderMain.resolveRepoRoot(new String[] {repoRoot.toString()})).isEqualTo(repoRoot);
  }

  @Test
  void resolveRepoRootDefaultsToTheCurrentDirectoryWhenNoArgumentIsGiven() {
    assertThat(RenderMain.resolveRepoRoot(new String[0]))
        .isEqualTo(Path.of(".").toAbsolutePath().normalize());
  }
}
