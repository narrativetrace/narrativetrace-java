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

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RenderPathsTest {

  @Test
  void claudeSkillMdIsUnderDotClaudeSkillsBySegment() {
    var skill = CatalogueIndex.ALL.get(0);
    Path resolved = RenderPaths.claudeSkillMd(Path.of("/repo"), skill);
    assertThat(resolved)
        .isEqualTo(Path.of("/repo/.claude/skills/" + skill.claudeSegment() + "/SKILL.md"));
  }

  @Test
  void agentsMdIsAtTheRepoRoot() {
    assertThat(RenderPaths.agentsMd(Path.of("/repo"))).isEqualTo(Path.of("/repo/AGENTS.md"));
  }
}
