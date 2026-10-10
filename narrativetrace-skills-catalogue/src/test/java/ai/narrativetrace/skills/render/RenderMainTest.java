/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.StepBody;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
  void writesBothCarrierFlavoursByteIdenticalToTheRenderedPages(@TempDir Path repoRoot)
      throws IOException {
    Files.writeString(repoRoot.resolve("AGENTS.md"), "# A repo\n");
    seedSnippetSources(repoRoot);

    RenderMain.render(repoRoot);

    for (var skill : CatalogueIndex.ALL) {
      assertThat(Files.readString(RenderPaths.carrierAgentsSkillMd(repoRoot, skill)))
          .isEqualTo(Files.readString(RenderPaths.codexSkillMd(repoRoot, skill)));
      assertThat(Files.readString(RenderPaths.carrierClaudeSkillMd(repoRoot, skill)))
          .isEqualTo(Files.readString(RenderPaths.claudeSkillMd(repoRoot, skill)));
    }
  }

  @Test
  void writesTheCarrierCatalogue(@TempDir Path repoRoot) throws IOException {
    Files.writeString(repoRoot.resolve("AGENTS.md"), "# A repo\n");
    seedSnippetSources(repoRoot);

    RenderMain.render(repoRoot);

    assertThat(Files.readString(RenderPaths.carrierCatalogueJson(repoRoot)))
        .isEqualTo(CatalogueJsonRenderer.render(CatalogueIndex.ALL));
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
          // Two skills may embed the same listing (verify and debug both run PlaceOrderFlowTest).
          Files.copy(
              realRepoRoot.resolve(snippet.path()), target, StandardCopyOption.REPLACE_EXISTING);
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
