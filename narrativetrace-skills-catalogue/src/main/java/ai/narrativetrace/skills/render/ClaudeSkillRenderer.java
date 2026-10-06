/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.CommandVocabulary;
import ai.narrativetrace.skills.Skill;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Renders a typed {@link Skill} into the Claude plugin's {@code SKILL.md} shape: YAML frontmatter
 * ({@code name}, {@code description}, optional {@code when_to_use}, {@code allowed-tools} emitted
 * from the command vocabulary — harness principle 7, runtime-enforced, not just linted) followed by
 * the shared page body ({@link SkillBody}). This is BUILD OUTPUT — the typed {@link Skill} is the
 * only place any of this text is hand-written.
 *
 * <p>{@code allowed-tools} carries TOOL PATTERNS, never command names ({@link
 * CommandVocabulary#claudeToolPattern}): the field lists tools, and a permission rule is spelled
 * {@code Tool} or {@code Tool(specifier)}, so a bare {@code git} there names a tool that does not
 * exist and pre-approves nothing at all. The rendering is pinned by {@code
 * Lints#allowedToolsViolations} — the catalogue declares commands, this renderer spells them, and
 * neither side may quietly become the other.
 *
 * <p>A skill that declares NO allowed tool gets no {@code allowed-tools} line at all, not an empty
 * one. The field grants its listed tools for the turn that loads the skill WITHOUT prompting, so
 * for a skill whose commands can make something public, the absence of the field is the safety
 * property: every command then asks. An empty value would be a field whose meaning depends on how
 * the reader parses it, which is not a property anything can rely on. {@code
 * Lints#publishingNotPreApproved} is the other half of that guarantee.
 *
 * <p>{@code name:} is always the skill's {@link Skill#canonicalName()} — never a shortened "claude
 * segment". RULED 2026-09-04 (reaffirmed 2026-09-13): the {@code narrativetrace} prefix is Claude's
 * PLUGIN namespace, not a skill's own name, and this repository ships no plugin — a repo-level
 * {@code .claude/skills/} directory is a flat namespace exactly like Codex's or Gemini's, so a
 * shortened name (e.g. {@code doctor}) would collide with every other vendor's skill of the same
 * generic name. There is deliberately no shortening code path left to keep this true; see {@link
 * CodexSkillRenderer} for the sibling platform this same catalogue also renders.
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
    if (!skill.allowedTools().isEmpty()) {
      out.append("allowed-tools: ").append(allowedToolsValue(skill)).append('\n');
    }
    out.append("---\n\n");

    SkillBody.append(out, skill, repoRoot);

    return out.toString();
  }

  /**
   * The {@code allowed-tools} value: every declared command as a {@link
   * CommandVocabulary#claudeToolPattern} tool pattern, comma-separated.
   *
   * <p>Comma-separated rather than the documentation's other accepted form (space-separated): a
   * pattern carries spaces of its own ({@code Bash(git *)}), so only the comma reads back
   * unambiguously.
   */
  private static String allowedToolsValue(Skill skill) {
    return skill.allowedTools().stream()
        .map(CommandVocabulary::claudeToolPattern)
        .collect(Collectors.joining(", "));
  }
}
