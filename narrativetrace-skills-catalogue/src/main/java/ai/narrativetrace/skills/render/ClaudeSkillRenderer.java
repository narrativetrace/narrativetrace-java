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

/**
 * Renders a typed {@link Skill} into the Claude plugin's {@code SKILL.md} shape: YAML frontmatter
 * ({@code name}, {@code description}, optional {@code when_to_use}) followed by the shared page
 * body ({@link SkillBody}). This is BUILD OUTPUT — the typed {@link Skill} is the only place any of
 * this text is hand-written.
 *
 * <p><b>No {@code allowed-tools} line, ever.</b> Anthropic's plugin directory accepts {@code
 * allowed-tools} beyond the read-only tools (Read, Grep, Glob, LS) only from Verified
 * organizations, and this publisher is not one (the portal's validation blocked a submission on
 * exactly this line, and held it again as "pre-approves broad shell access"). So the page never
 * pre-approves a command: a user gets Claude Code's normal permission prompt the first time a skill
 * runs {@code ./gradlew}, {@code git} or {@code find}.
 *
 * <p>A skill's declared {@link Skill#allowedTools()} stay in the model as the closed set of
 * commands it runs, which {@code Lints#allowedToolsViolations} and the lints that keep a publishing
 * or promoting skill from ever declaring its own command still read, and which the plugin README
 * discloses. They are data about the skill, not a rendering: nothing here turns them into
 * frontmatter, and {@code RenderDriftTest} fails if a committed page carries the key.
 *
 * <p>{@code name:} is always the skill's {@link Skill#canonicalName()} — never a shortened "claude
 * segment". RULED 2026-09-04 (reaffirmed 2026-09-13): the {@code narrativetrace} prefix is Claude's
 * PLUGIN namespace, not a skill's own name, and a repo-level {@code .claude/skills/} directory is a
 * flat namespace exactly like Codex's or Antigravity's, so a shortened name (e.g. {@code doctor})
 * would collide with every other vendor's skill of the same generic name. There is deliberately no
 * shortening code path left to keep this true; see {@link CodexSkillRenderer} for the sibling
 * platform this same catalogue also renders.
 */
public final class ClaudeSkillRenderer {

  private ClaudeSkillRenderer() {}

  /** Resolves the repo root from the {@code projectDir} system property Gradle's test task sets. */
  public static String render(Skill skill) {
    return render(skill, Path.of(System.getProperty("projectDir", ".")));
  }

  public static String render(Skill skill, Path repoRoot) {
    StringBuilder out = new StringBuilder();
    out.append("---\n");
    out.append("name: ").append(skill.canonicalName()).append('\n');
    out.append("description: ").append(SkillBody.yamlQuote(skill.description())).append('\n');
    skill
        .whenToUseOptional()
        .ifPresent(w -> out.append("when_to_use: ").append(SkillBody.yamlQuote(w)).append('\n'));
    out.append("---\n\n");

    SkillBody.append(out, skill, repoRoot);

    return out.toString();
  }
}
