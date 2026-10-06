/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.Skill;
import java.nio.file.Path;

/** Where a skill's rendered artifacts land, repo-root relative. */
public final class RenderPaths {

  private RenderPaths() {}

  /** {@code .claude/skills/<canonicalName>/SKILL.md} — Claude Code discovers it here. */
  public static Path claudeSkillMd(Path repoRoot, Skill skill) {
    return repoRoot.resolve(".claude/skills/" + skill.canonicalName() + "/SKILL.md");
  }

  /**
   * {@code .agents/skills/<canonicalName>/SKILL.md} — the repo-scope path Codex CLI documents for a
   * checked-in skill (see {@link CodexSkillRenderer}'s class doc for the verified citation); NOT
   * {@code .codex/skills}, which OpenAI's own docs name only as the per-user home-directory scope.
   */
  public static Path codexSkillMd(Path repoRoot, Skill skill) {
    return repoRoot.resolve(".agents/skills/" + skill.canonicalName() + "/SKILL.md");
  }

  public static Path agentsMd(Path repoRoot) {
    return repoRoot.resolve("AGENTS.md");
  }

  /**
   * {@code .claude-plugin/marketplace.json} — the file that makes this repository a plugin
   * marketplace. Outside {@code .claude/} on purpose: the vendor reads it from the repository ROOT,
   * and the plugin it lists is rooted at {@code ./.claude}, so a marketplace file inside the plugin
   * would be a directory listing itself.
   */
  public static Path marketplaceJson(Path repoRoot) {
    return repoRoot.resolve(".claude-plugin/marketplace.json");
  }

  /**
   * The CARRIER's resource root inside the published {@code narrativetrace-skills} jar (and inside
   * the {@code narrativetrace-cli} jar, which shares the same directory as a second resource root).
   * {@code META-INF/} rather than a top-level directory: a consumer's own {@code skills/} must
   * never collide with ours on a flat classpath.
   */
  public static final String CARRIER_JAR_ROOT = "META-INF/narrativetrace/skills/";

  /** Where the carrier's checked-in copy lives in this repository — the jar root's source. */
  private static final String CARRIER_SOURCE_ROOT =
      "narrativetrace-skills/src/main/resources/" + CARRIER_JAR_ROOT;

  /**
   * {@code agents/<canonicalName>/SKILL.md}, carrier-root-relative — the open-standard {@code
   * .agents} flavour ({@link CodexSkillRenderer}). This is the string {@code catalogue.json}
   * carries, so an installer resolves it against whichever carrier it opened (a jar, a directory)
   * without knowing this repository's layout.
   */
  public static String carrierAgentsResource(Skill skill) {
    return "agents/" + skill.canonicalName() + "/SKILL.md";
  }

  /**
   * {@code claude/<canonicalName>/SKILL.md}, carrier-root-relative ({@link ClaudeSkillRenderer}).
   */
  public static String carrierClaudeResource(Skill skill) {
    return "claude/" + skill.canonicalName() + "/SKILL.md";
  }

  /** The checked-in source of {@link #carrierAgentsResource}. */
  public static Path carrierAgentsSkillMd(Path repoRoot, Skill skill) {
    return repoRoot.resolve(CARRIER_SOURCE_ROOT + carrierAgentsResource(skill));
  }

  /** The checked-in source of {@link #carrierClaudeResource}. */
  public static Path carrierClaudeSkillMd(Path repoRoot, Skill skill) {
    return repoRoot.resolve(CARRIER_SOURCE_ROOT + carrierClaudeResource(skill));
  }

  /** The carrier's index: which skills it carries, and where each flavour's page sits. */
  public static Path carrierCatalogueJson(Path repoRoot) {
    return repoRoot.resolve(CARRIER_SOURCE_ROOT + CARRIER_CATALOGUE_NAME);
  }

  /** The catalogue's file name, carrier-root-relative. */
  public static final String CARRIER_CATALOGUE_NAME = "catalogue.json";
}
