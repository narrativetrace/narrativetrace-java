/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
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
   * What the Claude CLI is allowed to do in a trial, as a comma-separated {@code --allowed-tools}
   * list. It is derived from the published init prompt, not from convenience: step 1 is "read
   * https://narrativetrace.ai/java/llms.txt first" ({@code WebFetch}), steps 2-3 create and edit a
   * project ({@code Read}, {@code Edit}, {@code Write}), step 4 runs it ({@code Bash}), and step
   * 4's own first sentence — "if the `add-narrative-tracing` skill is now available, follow it" —
   * names a SKILL, which is a tool of its own. A preset that grants less than the prompt asks for
   * measures the sandbox rather than the skill: a trial where the agent is refused mid-step-1 and
   * stops is a harness result wearing a product result's clothes, and one that cannot invoke the
   * skill a registry just installed measures nothing the registry case was about.
   */
  private static final String CLAUDE_TOOLS = "Bash,Read,Edit,Write,WebFetch,Skill";

  /**
   * The machine-readable stream, because a grader has to read the agent's TOOL CALLS and not only
   * its closing text: a URL a command printed and a URL typed into a reply are the same event to
   * the gate that must not see either before the user's approval turn.
   *
   * <p><b>@llmNote</b> {@code --verbose} is not decoration and must never be dropped as such.
   * Without it this CLI exits 1 having written ZERO bytes to stdout and nothing to stderr — a
   * silent failure indistinguishable from an agent that did nothing at all. Verified in the
   * container the trials run in, 2026-10-05.
   */
  private static final String CLAUDE_TRANSCRIPT_FORMAT = "--output-format stream-json --verbose";

  /** What a multi-turn template carries where the trial's own session id goes. */
  public static final String SESSION_PLACEHOLDER = "{session}";

  /**
   * The default agent-command template for this platform, {@code {prompt}}-substituted by the
   * runner. Each CLI uses its own subscription login — the harness never passes an API key, so no
   * preset ever threads one through.
   */
  public String presetAgentCommand(String model, String canonicalSkillName) {
    return switch (this) {
      case CLAUDE ->
          "claude -p \"{prompt}\" --model "
              + model
              + " --allowed-tools \""
              + CLAUDE_TOOLS
              + "\" "
              + CLAUDE_TRANSCRIPT_FORMAT;
      case CODEX -> codexCommand(model, canonicalSkillName);
      case GEMINI -> geminiCommand(model, canonicalSkillName);
    };
  }

  /**
   * How this platform's trial opens a conversation the runner will drive further, or empty when its
   * own multi-turn flags have never been verified in the container the trials run in.
   */
  public Optional<String> presetFirstTurnCommand(String model, String canonicalSkillName) {
    return multiTurn(model, canonicalSkillName, "--session-id " + SESSION_PLACEHOLDER);
  }

  /**
   * How this platform's trial continues that same conversation, or empty for the same reason.
   *
   * <p>The id is RESUMED rather than forked, so turn 3 and every turn after it name the id turn 1
   * was given.
   */
  public Optional<String> presetResumedTurnCommand(String model, String canonicalSkillName) {
    return multiTurn(model, canonicalSkillName, "--resume " + SESSION_PLACEHOLDER);
  }

  /**
   * The single-turn preset plus one session flag, and nothing else — so a multi-turn trial is the
   * same sandbox, the same tools and the same transcript format as a single-turn one, differing
   * only in being one conversation.
   *
   * <p><b>@llmNote</b> Empty for every platform but Claude, and empty for BOTH halves. A platform
   * that could open a conversation but not resume it would spend a trial reaching the approval
   * question and then run its "second turn" as a FRESH session with no memory of the draft, where
   * "nothing was filed" is true for a reason that has nothing to do with the gate under test.
   */
  private Optional<String> multiTurn(String model, String canonicalSkillName, String sessionFlag) {
    return this == CLAUDE
        ? Optional.of(presetAgentCommand(model, canonicalSkillName) + " " + sessionFlag)
        : Optional.empty();
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
