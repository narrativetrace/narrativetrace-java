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
 * One skill as the carrier's {@code catalogue.json} lists it: the name that becomes a directory in
 * the consumer project, the description an always-on agent pointer repeats verbatim, and the
 * carrier-relative path of each rendered flavour.
 *
 * <p>INTENT: the installer's unit of work. Everything the planner needs about a skill is here, so a
 * plan can be computed without opening the carrier a second time.
 *
 * <p><b>@llmNote</b> The two paths are relative to the carrier root ({@code
 * META-INF/narrativetrace/skills/}) and are entry names inside a jar, so they always use {@code /},
 * never the platform separator.
 *
 * @param name the skill's directory name, unique within a catalogue
 * @param description the one-paragraph trigger description, copied into the managed block as-is
 * @param agentsPath carrier-relative path of the open-standard flavour
 * @param claudePath carrier-relative path of the vendor flavour
 */
public record SkillEntry(String name, String description, String agentsPath, String claudePath) {

  public SkillEntry {
    requireText(name, "name");
    requireText(description, "description");
    requireText(agentsPath, "agents path");
    requireText(claudePath, "claude path");
  }

  /** The carrier-relative path of one flavour's rendered page. */
  public String pathFor(SkillFlavour flavour) {
    if (flavour == null) {
      throw new IllegalArgumentException("a flavour must be given to pick a skill's path");
    }
    return flavour == SkillFlavour.AGENTS ? agentsPath : claudePath;
  }

  private static void requireText(String value, String what) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("a catalogue skill's " + what + " must not be blank");
    }
  }
}
