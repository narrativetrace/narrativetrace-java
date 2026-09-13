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
package ai.narrativetrace.skills;

/**
 * How mechanically a skill's outcome can be checked (frozen taxonomy, {@code
 * skill-harness-design.md} §7): {@code MECHANICAL} skills are fully replayable — every step is
 * {@code commands} or {@code file}+{@code code}, and {@code verify} is a deterministic, exit-code
 * oracle; {@code GUIDED} skills mix replayable steps with a few that need a human or an agent's
 * judgment to confirm; {@code JUDGMENTAL} skills are mostly judgment calls a mechanical check
 * cannot stand in for.
 */
public enum SkillClass {
  MECHANICAL,
  GUIDED,
  JUDGMENTAL
}
