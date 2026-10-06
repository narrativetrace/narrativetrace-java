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
package ai.narrativetrace.tooling.init;

/**
 * The two rendered forms of the same skill the carrier ships, and where each one is installed.
 *
 * <p>INTENT: a skill's text differs between the open-standard layout and the vendor layout by two
 * frontmatter fields, so the carrier ships both rather than making an installer synthesise one from
 * the other — rendering belongs in the catalogue, not in the installer.
 *
 * <p><b>@llmNote</b> {@link #AGENTS} is written into every project. {@link #CLAUDE} is written only
 * where a project is detected as that vendor's, or where the caller asked for it explicitly; the
 * planner owns that decision, not this enum.
 */
public enum SkillFlavour {

  /** The open-standard flavour, installed under {@code .agents/skills/}. */
  AGENTS(".agents/skills"),

  /** The vendor flavour, installed under {@code .claude/skills/}. */
  CLAUDE(".claude/skills");

  private final String installRoot;

  SkillFlavour(String installRoot) {
    this.installRoot = installRoot;
  }

  /** The project-relative directory this flavour's skill directories live in. */
  public String installRoot() {
    return installRoot;
  }
}
