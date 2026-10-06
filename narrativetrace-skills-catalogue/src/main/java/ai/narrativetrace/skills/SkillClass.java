/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
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
