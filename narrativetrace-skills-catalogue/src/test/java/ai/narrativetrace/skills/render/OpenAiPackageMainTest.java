/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenAiPackageMainTest {

  @Test
  void stagesTheManifestEverySkillPageAndTheAssets(@TempDir Path repoRoot, @TempDir Path staging)
      throws IOException {
    OpenAiPackageMainTestSupport.seedSources(repoRoot);

    OpenAiPackageMain.stage(repoRoot, staging, "1.2.3");

    assertThat(Files.readString(staging.resolve("plugin.json")))
        .isEqualTo(OpenAiPluginJsonRenderer.render(CatalogueIndex.OPENAI_DIRECTORY, "1.2.3"));
    for (var skill : CatalogueIndex.ALL) {
      Path staged = staging.resolve("skills/" + skill.canonicalName() + "/SKILL.md");
      assertThat(staged).exists();
      assertThat(Files.readString(staged)).isEqualTo(CodexSkillRenderer.render(skill, repoRoot));
    }
    assertThat(Files.readAllBytes(staging.resolve("assets/logo.png")))
        .isEqualTo(
            Files.readAllBytes(RenderPaths.openAiPluginAssets(repoRoot).resolve("logo.png")));
    assertThat(Files.readAllBytes(staging.resolve("assets/composer-icon.png")))
        .isEqualTo(
            Files.readAllBytes(
                RenderPaths.openAiPluginAssets(repoRoot).resolve("composer-icon.png")));
  }

  @Test
  void mainStagesIntoTheDirectoryNamedByItsSecondArgument(
      @TempDir Path repoRoot, @TempDir Path staging) throws IOException {
    OpenAiPackageMainTestSupport.seedSources(repoRoot);

    OpenAiPackageMain.main(new String[] {repoRoot.toString(), staging.toString(), "1.2.3"});

    assertThat(staging.resolve("plugin.json")).exists();
  }
}
