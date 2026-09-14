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

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import ai.narrativetrace.skills.catalogue.ProListings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regenerates every rendered artifact — the Claude and Codex {@code SKILL.md} pages and the {@code
 * AGENTS.md} managed section — from the typed catalogue. Run via {@code ./gradlew
 * :narrativetrace-skills:renderSkills}; {@code RenderDriftTest} is the drift gate this output is
 * checked against on every {@code ./gradlew check}.
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
      writePage(
          RenderPaths.claudeSkillMd(repoRoot, skill), ClaudeSkillRenderer.render(skill, repoRoot));
      writePage(
          RenderPaths.codexSkillMd(repoRoot, skill), CodexSkillRenderer.render(skill, repoRoot));
    }
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
