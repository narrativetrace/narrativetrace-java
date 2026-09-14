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
}
