/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.Skill;
import java.nio.file.Path;

/**
 * Renders a typed {@link Skill} into the Claude plugin's {@code SKILL.md} shape: YAML frontmatter
 * ({@code name}, {@code description}, optional {@code when_to_use}, {@code allowed-tools} emitted
 * from the command vocabulary — harness principle 7, runtime-enforced, not just linted) followed by
 * the shared page body ({@link SkillBody}). This is BUILD OUTPUT — the typed {@link Skill} is the
 * only place any of this text is hand-written.
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
    out.append("allowed-tools: ").append(String.join(", ", skill.allowedTools())).append('\n');
    out.append("---\n\n");

    SkillBody.append(out, skill, repoRoot);

    return out.toString();
  }
}
