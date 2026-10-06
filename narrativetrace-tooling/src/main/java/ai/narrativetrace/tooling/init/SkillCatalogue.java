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

import java.util.List;
import java.util.Optional;

/**
 * The carrier's index: which runtime shipped it and which skills it carries.
 *
 * <p>INTENT: the whole of {@code catalogue.json}, validated once at read time so every later stage
 * — planner, renderer, executor — can treat it as a fact rather than as input.
 *
 * <p><b>@llmNote</b> Deliberately carries NO version: the jar's own coordinate is the stamp, so a
 * checked-in catalogue never drifts from the artifact that ships it.
 *
 * @param runtime the runtime slug the carrier was rendered for, {@code java} here
 * @param skills every skill in catalogue order, each name appearing exactly once
 */
public record SkillCatalogue(String runtime, List<SkillEntry> skills) {

  public SkillCatalogue {
    if (runtime == null || runtime.isBlank()) {
      throw new IllegalArgumentException("a catalogue's runtime must not be blank");
    }
    if (skills == null || skills.isEmpty()) {
      throw new IllegalArgumentException("a catalogue must list at least one skill");
    }
    skills = List.copyOf(skills);
    long distinct = skills.stream().map(SkillEntry::name).distinct().count();
    if (distinct != skills.size()) {
      throw new IllegalArgumentException("a catalogue names a skill twice: " + names(skills));
    }
  }

  /** The entry with this name, or empty — the lookup an installer does per target directory. */
  public Optional<SkillEntry> skill(String name) {
    return skills.stream().filter(entry -> entry.name().equals(name)).findFirst();
  }

  private static String names(List<SkillEntry> skills) {
    return skills.stream().map(SkillEntry::name).sorted().toList().toString();
  }
}
