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
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RenderPathsTest {

  @Test
  void claudeSkillMdIsUnderDotClaudeSkillsByCanonicalName() {
    var skill = CatalogueIndex.ALL.get(0);
    Path resolved = RenderPaths.claudeSkillMd(Path.of("/repo"), skill);
    assertThat(resolved)
        .isEqualTo(Path.of("/repo/.claude/skills/" + skill.canonicalName() + "/SKILL.md"));
  }

  @Test
  void codexSkillMdIsUnderDotAgentsSkillsByCanonicalName() {
    var skill = CatalogueIndex.ALL.get(0);
    Path resolved = RenderPaths.codexSkillMd(Path.of("/repo"), skill);
    assertThat(resolved)
        .isEqualTo(Path.of("/repo/.agents/skills/" + skill.canonicalName() + "/SKILL.md"));
  }

  @Test
  void agentsMdIsAtTheRepoRoot() {
    assertThat(RenderPaths.agentsMd(Path.of("/repo"))).isEqualTo(Path.of("/repo/AGENTS.md"));
  }

  @Test
  void carrierAgentsSkillMdLandsUnderTheCarrierResourceRoot() {
    var skill = CatalogueIndex.ALL.get(0);
    assertThat(RenderPaths.carrierAgentsSkillMd(Path.of("/repo"), skill))
        .isEqualTo(
            Path.of(
                "/repo/narrativetrace-skills/src/main/resources/META-INF/narrativetrace/skills/"
                    + "agents/"
                    + skill.canonicalName()
                    + "/SKILL.md"));
  }

  @Test
  void carrierClaudeSkillMdLandsUnderTheCarrierResourceRoot() {
    var skill = CatalogueIndex.ALL.get(0);
    assertThat(RenderPaths.carrierClaudeSkillMd(Path.of("/repo"), skill))
        .isEqualTo(
            Path.of(
                "/repo/narrativetrace-skills/src/main/resources/META-INF/narrativetrace/skills/"
                    + "claude/"
                    + skill.canonicalName()
                    + "/SKILL.md"));
  }

  @Test
  void carrierCatalogueJsonSitsBesideTheFlavourDirectories() {
    assertThat(RenderPaths.carrierCatalogueJson(Path.of("/repo")))
        .isEqualTo(
            Path.of(
                "/repo/narrativetrace-skills/src/main/resources/META-INF/narrativetrace/skills/"
                    + "catalogue.json"));
  }

  /** The values `catalogue.json` carries are carrier-root-relative, never repo-relative. */
  @Test
  void carrierResourceNamesAreRelativeToTheCarrierRoot() {
    var skill = CatalogueIndex.ALL.get(0);
    assertThat(RenderPaths.carrierAgentsResource(skill))
        .isEqualTo("agents/" + skill.canonicalName() + "/SKILL.md");
    assertThat(RenderPaths.carrierClaudeResource(skill))
        .isEqualTo("claude/" + skill.canonicalName() + "/SKILL.md");
  }

  /** What a reader of the jar asks for: the entry name, carrier root included. */
  @Test
  void carrierJarEntriesLiveUnderMetaInf() {
    var skill = CatalogueIndex.ALL.get(0);
    assertThat(RenderPaths.CARRIER_JAR_ROOT).isEqualTo("META-INF/narrativetrace/skills/");
    assertThat(RenderPaths.CARRIER_JAR_ROOT + RenderPaths.carrierAgentsResource(skill))
        .isEqualTo("META-INF/narrativetrace/skills/agents/" + skill.canonicalName() + "/SKILL.md");
  }
}
