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
 * Renders a typed {@link Skill} into the Codex CLI's documented {@code SKILL.md} shape: a minimal
 * YAML frontmatter of {@code name} and {@code description} only, followed by the same page body
 * every platform shares ({@link SkillBody}). {@code name:} is always {@link Skill#canonicalName()}
 * — the same "no shortening" rule {@link ClaudeSkillRenderer} follows, for the same reason: a
 * repo-checked-in skills directory is a flat namespace with no plugin prefix to hide behind.
 *
 * <p><b>Layout, verified 2026-09-13 against OpenAI's own docs</b> (<a
 * href="https://developers.openai.com/codex/skills">developers.openai.com/codex/skills</a>,
 * redirects to <a
 * href="https://learn.chatgpt.com/docs/build-skills">learn.chatgpt.com/docs/build-skills</a>): a
 * repository checks skills into {@code .agents/skills/<name>/SKILL.md} (repo scope — either {@code
 * $CWD/.agents/skills} or {@code $REPO_ROOT/.agents/skills}); {@code ~/.codex/skills} is documented
 * only as the per-user, cross-repository scope (analogous to {@code $HOME/.agents/ skills}), never
 * a path a repository ships. This repository therefore renders {@code .agents/skills/}, not {@code
 * .codex/skills/} — see {@link RenderPaths#codexSkillMd}.
 *
 * <p>Frontmatter, same source: the page's own YAML example names exactly two keys, {@code name} and
 * {@code description}; a separate optional {@code agents/openai.yaml} carries presentation metadata
 * ({@code display_name}, {@code icon_small}, …) that is not part of {@code SKILL.md}'s frontmatter
 * and nothing in this catalogue currently needs. Neither {@code when_to_use} nor {@code
 * allowed-tools} — both Claude-specific — is a documented Codex key, so this renderer omits them;
 * the information they would have carried is already in the shared body's prose.
 */
public final class CodexSkillRenderer {

  private CodexSkillRenderer() {}

  /** Resolves the repo root from the {@code projectDir} system property Gradle's test task sets. */
  public static String render(Skill skill) {
    return render(skill, Path.of(System.getProperty("projectDir", ".")));
  }

  public static String render(Skill skill, Path repoRoot) {
    StringBuilder out = new StringBuilder();
    out.append("---\n");
    out.append("name: ").append(skill.canonicalName()).append('\n');
    out.append("description: ").append(SkillBody.yamlQuote(skill.description())).append('\n');
    out.append("---\n\n");

    SkillBody.append(out, skill, repoRoot);

    return out.toString();
  }
}
