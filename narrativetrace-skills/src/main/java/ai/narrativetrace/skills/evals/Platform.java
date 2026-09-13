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
package ai.narrativetrace.skills.evals;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Tier B platform presets (skill-harness-design.md §5.1; the sporadic-lanes ruling, owner-ruled
 * 2026-09-13): fills the runner's agent-command seam for each of the three supported CLIs so a
 * trial can be started with just {@code --platform} — never invented for a platform the seam
 * doesn't name, and always overridable by passing an explicit agent command (the preset is a
 * default, not a lock-in). Mirrors the TypeScript reference's {@code evals/platform-presets.ts}.
 */
public enum Platform {
  CLAUDE,
  CODEX,
  GEMINI;

  /**
   * Codex and Gemini sit on cheaper plans and run under the sporadic policy (never scheduled,
   * promotion points only, quota-guarded). Claude runs on the harness's own regular cadence and is
   * exempt from all of it.
   */
  private static final List<Platform> SPORADIC_PLATFORMS = List.of(CODEX, GEMINI);

  public static Optional<Platform> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(Platform.valueOf(value.toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public boolean isSporadic() {
    return SPORADIC_PLATFORMS.contains(this);
  }

  /**
   * {@code narrativetrace-doctor} is scoped read-only by design (agent-skills.md: "diagnosis only,
   * and read-only: it never edits, generates, or deletes a file"); every other cataloged skill
   * installs or edits files. Used to pick the least-privileged sandbox/approval mode a trial needs.
   */
  public static boolean isReadOnlySkill(String canonicalSkillName) {
    return "narrativetrace-doctor".equals(canonicalSkillName);
  }

  /**
   * The default agent-command template for this platform, {@code {prompt}}-substituted by the
   * runner. Each CLI uses its own subscription login — the harness never passes an API key, so no
   * preset ever threads one through.
   */
  public String presetAgentCommand(String model, String canonicalSkillName) {
    return switch (this) {
      case CLAUDE -> "claude -p \"{prompt}\" --model " + model + " --allowed-tools Bash";
      case CODEX -> codexCommand(model, canonicalSkillName);
      case GEMINI -> geminiCommand(model, canonicalSkillName);
    };
  }

  /**
   * Codex {@code -s/--sandbox}: {@code read-only} for a read-only skill, {@code workspace-write}
   * otherwise.
   */
  private static String codexCommand(String model, String canonicalSkillName) {
    String sandbox = isReadOnlySkill(canonicalSkillName) ? "read-only" : "workspace-write";
    return "codex exec --sandbox " + sandbox + " --model " + model + " \"{prompt}\"";
  }

  /**
   * Gemini {@code --approval-mode}: {@code plan} (its read-only mode) for a read-only skill, {@code
   * auto_edit} otherwise.
   */
  private static String geminiCommand(String model, String canonicalSkillName) {
    String approvalMode = isReadOnlySkill(canonicalSkillName) ? "plan" : "auto_edit";
    return "gemini -p \"{prompt}\" --model " + model + " --approval-mode " + approvalMode;
  }
}
