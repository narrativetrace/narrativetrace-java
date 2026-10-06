/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import ai.narrativetrace.skills.catalogue.ProListings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regenerates every rendered artifact from the typed catalogue: the Claude and Codex {@code
 * SKILL.md} pages this repository discovers its own skills through, the {@code AGENTS.md} managed
 * section, {@code .claude-plugin/marketplace.json} (what makes the repository itself a plugin
 * marketplace), and the CARRIER — the same two flavours plus {@code catalogue.json} under {@code
 * narrativetrace-skills}' resources, which is what the published jar hands an installer. Run via
 * {@code ./gradlew :narrativetrace-skills-catalogue:renderSkills}; {@code RenderDriftTest} is the
 * drift gate all of it is checked against on every {@code ./gradlew check}.
 *
 * @llmNote The carrier's pages are byte-identical to the repository's own rendered pages on purpose
 *     — one renderer per flavour, two destinations. A second renderer for the carrier would be a
 *     second thing to keep in step, which is exactly what the drift gate exists to forbid.
 */
public final class RenderMain {

  private RenderMain() {}

  public static void main(String[] args) throws IOException {
    render(resolveRepoRoot(args));
  }

  /** The repo root to render into: {@code args[0]} when given, else the current directory. */
  static Path resolveRepoRoot(String[] args) {
    return Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
  }

  static void render(Path repoRoot) throws IOException {
    for (var skill : CatalogueIndex.ALL) {
      String claude = ClaudeSkillRenderer.render(skill, repoRoot);
      String agents = CodexSkillRenderer.render(skill, repoRoot);
      writePage(RenderPaths.claudeSkillMd(repoRoot, skill), claude);
      writePage(RenderPaths.codexSkillMd(repoRoot, skill), agents);
      writePage(RenderPaths.carrierClaudeSkillMd(repoRoot, skill), claude);
      writePage(RenderPaths.carrierAgentsSkillMd(repoRoot, skill), agents);
    }
    writePage(
        RenderPaths.carrierCatalogueJson(repoRoot),
        CatalogueJsonRenderer.render(CatalogueIndex.ALL));
    writePage(
        RenderPaths.marketplaceJson(repoRoot),
        MarketplaceJsonRenderer.render(CatalogueIndex.MARKETPLACE));
    Path agentsMd = RenderPaths.agentsMd(repoRoot);
    String onDisk = Files.readString(agentsMd);
    String spliced =
        AgentsMdRenderer.splice(
            onDisk, AgentsMdRenderer.renderSection(CatalogueIndex.ALL, ProListings.ALL));
    Files.writeString(agentsMd, spliced);
  }

  private static void writePage(Path target, String content) throws IOException {
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
  }
}
