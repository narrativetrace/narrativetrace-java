/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Stages the OpenAI plugin package tree the {@code openAiPluginZip} Gradle task zips: {@code
 * plugin.json} rendered WITH the release version, the drift-pinned {@code .agents/skills} pages
 * under {@code skills/}, and the committed listing assets under {@code assets/}. {@code
 * build/openai-plugin/} is never committed itself, like every staging directory under {@code
 * build/}.
 */
public final class OpenAiPackageMain {

  private static final List<String> ASSET_NAMES = List.of("logo.png", "composer-icon.png");

  private OpenAiPackageMain() {}

  public static void main(String[] args) throws IOException {
    stage(Path.of(args[0]), Path.of(args[1]), args[2]);
  }

  static void stage(Path repoRoot, Path stagingDir, String version) throws IOException {
    Files.createDirectories(stagingDir);
    Files.writeString(
        stagingDir.resolve("plugin.json"),
        OpenAiPluginJsonRenderer.render(CatalogueIndex.OPENAI_DIRECTORY, version));
    stageSkills(repoRoot, stagingDir);
    stageAssets(repoRoot, stagingDir);
  }

  private static void stageSkills(Path repoRoot, Path stagingDir) throws IOException {
    for (var skill : CatalogueIndex.ALL) {
      Path target = stagingDir.resolve("skills/" + skill.canonicalName() + "/SKILL.md");
      Files.createDirectories(target.getParent());
      Files.copy(
          RenderPaths.codexSkillMd(repoRoot, skill), target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void stageAssets(Path repoRoot, Path stagingDir) throws IOException {
    Path assetsSource = RenderPaths.openAiPluginAssets(repoRoot);
    Path assetsTarget = stagingDir.resolve("assets");
    Files.createDirectories(assetsTarget);
    for (String name : ASSET_NAMES) {
      Files.copy(
          assetsSource.resolve(name),
          assetsTarget.resolve(name),
          StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
