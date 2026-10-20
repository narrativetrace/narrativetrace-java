/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.StepBody;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Seeds a fake repository root with everything {@link OpenAiPackageMain} needs to stage a package
 * from it — the embedded snippet sources {@code CodexSkillRenderer} reads, the rendered Codex
 * pages, and the two placeholder assets — so tests of the staging and checking mains never touch
 * the real repository.
 */
final class OpenAiPackageMainTestSupport {

  private OpenAiPackageMainTestSupport() {}

  static void seedSources(Path repoRoot) throws IOException {
    Path realRepoRoot = Path.of(System.getProperty("projectDir"));
    for (var skill : CatalogueIndex.ALL) {
      for (var step : skill.steps()) {
        if (step.body() instanceof StepBody.SnippetStep snippet) {
          Path target = repoRoot.resolve(snippet.path());
          Files.createDirectories(target.getParent());
          Files.copy(
              realRepoRoot.resolve(snippet.path()), target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
      Path page = RenderPaths.codexSkillMd(repoRoot, skill);
      Files.createDirectories(page.getParent());
      Files.writeString(page, CodexSkillRenderer.render(skill, repoRoot));
    }
    Path assets = RenderPaths.openAiPluginAssets(repoRoot);
    Files.createDirectories(assets);
    Files.write(assets.resolve("logo.png"), PngFixtures.flat(64, 64, (byte) 0x4a));
    Files.write(assets.resolve("composer-icon.png"), PngFixtures.flat(64, 64, (byte) 0x4a));
  }
}
