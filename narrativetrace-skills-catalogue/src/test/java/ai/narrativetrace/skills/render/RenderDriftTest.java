/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code SKILL.md}, the {@code AGENTS.md} managed section and the carrier's resources are committed
 * BUILD OUTPUT — this test is their drift check: what {@link CatalogueIndex#ALL}'s typed source
 * renders TODAY must byte-match what is committed. A failure here means someone hand-edited a
 * rendered file, or changed the catalogue without re-rendering — both are the same bug
 * (documentation/what-to-commit.md's rule: regenerated, reviewed in diffs, checked against drift,
 * never hand-edited).
 *
 * <p>The carrier's pages are pinned against the SAME renderer output as this repository's own
 * pages, not against each other: a carrier that drifted from the source would ship a jar whose
 * contents nobody in this repository reads, and an installer would be the first to notice.
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("skills")
  void renderedCodexSkillMdMatchesTheTypedCatalogue(Skill skill) {
    Path onDisk = RenderPaths.codexSkillMd(REPO_ROOT, skill);
    assertThat(onDisk)
        .as(onDisk + " must exist — run the skills renderer and commit its output")
        .exists();
    assertThat(readText(onDisk)).isEqualTo(CodexSkillRenderer.render(skill));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("skills")
  void carriedAgentsFlavourMatchesTheTypedCatalogue(Skill skill) {
    Path onDisk = RenderPaths.carrierAgentsSkillMd(REPO_ROOT, skill);
    assertThat(onDisk)
        .as(onDisk + " must exist — run the skills renderer and commit its output")
        .exists();
    assertThat(readText(onDisk)).isEqualTo(CodexSkillRenderer.render(skill));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("skills")
  void carriedClaudeFlavourMatchesTheTypedCatalogue(Skill skill) {
    Path onDisk = RenderPaths.carrierClaudeSkillMd(REPO_ROOT, skill);
    assertThat(onDisk)
        .as(onDisk + " must exist — run the skills renderer and commit its output")
        .exists();
    assertThat(readText(onDisk)).isEqualTo(ClaudeSkillRenderer.render(skill));
  }

  @Test
  void carriedCatalogueJsonMatchesTheTypedCatalogue() {
    Path onDisk = RenderPaths.carrierCatalogueJson(REPO_ROOT);
    assertThat(onDisk)
        .as(onDisk + " must exist — run the skills renderer and commit its output")
        .exists();
    assertThat(readText(onDisk)).isEqualTo(CatalogueJsonRenderer.render(CatalogueIndex.ALL));
  }

  @Test
  void renderedMarketplaceJsonMatchesTheTypedCatalogue() {
    Path onDisk = RenderPaths.marketplaceJson(REPO_ROOT);
    assertThat(onDisk)
        .as(onDisk + " must exist — run the skills renderer and commit its output")
        .exists();
    assertThat(readText(onDisk))
        .isEqualTo(MarketplaceJsonRenderer.render(CatalogueIndex.MARKETPLACE));
  }

  /** Nothing else may sit in the carrier: a stray file would ship in the published jar. */
  @Test
  void theCarrierCarriesNothingBesidesTheRenderedPagesAndTheCatalogue() throws IOException {
    Path carrierRoot = RenderPaths.carrierCatalogueJson(REPO_ROOT).getParent();
    List<Path> expected = new ArrayList<>();
    expected.add(RenderPaths.carrierCatalogueJson(REPO_ROOT));
    for (Skill skill : CatalogueIndex.ALL) {
      expected.add(RenderPaths.carrierAgentsSkillMd(REPO_ROOT, skill));
      expected.add(RenderPaths.carrierClaudeSkillMd(REPO_ROOT, skill));
    }

    try (var walk = Files.walk(carrierRoot)) {
      assertThat(walk.filter(Files::isRegularFile)).containsExactlyInAnyOrderElementsOf(expected);
    }
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
