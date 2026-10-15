/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformTest {

  @Test
  void parseIsCaseInsensitiveForEveryKnownPlatform() {
    assertThat(Platform.parse("claude")).contains(Platform.CLAUDE);
    assertThat(Platform.parse("CODEX")).contains(Platform.CODEX);
    assertThat(Platform.parse("Antigravity")).contains(Platform.ANTIGRAVITY);
    assertThat(Platform.parse("cursor")).contains(Platform.CURSOR);
  }

  @Test
  void parseIsEmptyForUnknownOrNullInput() {
    assertThat(Platform.parse("chatgpt")).isEmpty();
    assertThat(Platform.parse(null)).isEqualTo(Optional.empty());
  }

  @Test
  void onlyCodexAntigravityAndCursorAreSporadic() {
    assertThat(Platform.CLAUDE.isSporadic()).isFalse();
    assertThat(Platform.CODEX.isSporadic()).isTrue();
    assertThat(Platform.ANTIGRAVITY.isSporadic()).isTrue();
    assertThat(Platform.CURSOR.isSporadic()).isTrue();
  }

  /**
   * Cursor's headless CLI is {@code agent}; {@code --force} lets it run commands and apply edits
   * without a confirmation nobody is there to give, for EVERY skill — even the read-only doctor,
   * which runs its diagnosis through a command, so a preset narrower than the prompt would measure
   * the sandbox and not the skill. No {@code --model}: the runner's model argument is a ledger
   * label, and a label that is not a Cursor model name would fail the whole trial.
   */
  @Test
  void cursorPresetIsTheHeadlessAgentWithForceForEverySkill() {
    String expected = "agent -p \"{prompt}\" --output-format text --force";

    assertThat(Platform.CURSOR.presetAgentCommand("auto", "narrativetrace-doctor"))
        .isEqualTo(expected);
    assertThat(Platform.CURSOR.presetAgentCommand("auto", "add-narrative-tracing"))
        .isEqualTo(expected);
  }

  /**
   * Antigravity's CLI is {@code agy}; {@code --dangerously-skip-permissions} is its one headless
   * auto-approval (verified against {@code agy --help}, CLI 1.3.3, 2026-10-10 — there is no {@code
   * --yolo} and no approval mode), so it rides every skill for the same reason Cursor's {@code
   * --force} does. {@code --model} IS passed: the owner's ledger model is one of {@code agy
   * models}' slugs ({@code gemini-3.8-flash-low} is the cheapest), and the Cursor lane's reason for
   * omitting it — a label that is not a vendor model name — does not hold here.
   */
  @Test
  void antigravityPresetSkipsPermissionsForEverySkillAndNamesTheModel() {
    String expected =
        "agy -p \"{prompt}\" --model gemini-3.8-flash-low --output-format text"
            + " --dangerously-skip-permissions";

    assertThat(
            Platform.ANTIGRAVITY.presetAgentCommand(
                "gemini-3.8-flash-low", "narrativetrace-doctor"))
        .isEqualTo(expected);
    assertThat(
            Platform.ANTIGRAVITY.presetAgentCommand(
                "gemini-3.8-flash-low", "add-narrative-tracing"))
        .isEqualTo(expected);
  }

  @Test
  void cursorAndAntigravityNameTheirToolsForTheSkipLine() {
    assertThat(Platform.CURSOR.cliTool()).isEqualTo("agent");
    assertThat(Platform.ANTIGRAVITY.cliTool()).isEqualTo("agy");
    assertThat(Platform.CLAUDE.cliTool()).isEqualTo("claude");
    assertThat(Platform.CODEX.cliTool()).isEqualTo("codex");
  }

  @Test
  void onlyTheDoctorSkillIsReadOnly() {
    assertThat(Platform.isReadOnlySkill("narrativetrace-doctor")).isTrue();
    assertThat(Platform.isReadOnlySkill("add-narrative-tracing")).isFalse();
  }

  /**
   * Including the one the prompt's own step 4 names: "if the add-narrative-tracing skill is now
   * available, follow it". A preset that cannot invoke a skill makes that step unreachable, which
   * matters most for the registry cases — a registry install's whole subject is a skill becoming
   * available.
   */
  @Test
  void claudePresetGrantsTheToolsThePublishedPromptImplies() {
    assertThat(Platform.CLAUDE.presetAgentCommand("haiku", "add-narrative-tracing"))
        .isEqualTo(
            "claude -p \"{prompt}\" --model haiku --allowed-tools"
                + " \"Bash,Read,Edit,Write,WebFetch,Skill\""
                + " --output-format stream-json --verbose");
  }

  /**
   * The transcript a grader reads has to carry the agent's TOOL CALLS, not just its closing text: a
   * URL printed by a command and a URL typed in a reply are the same event to the gate that must
   * not see one before the user's approval turn. Only the streaming format exposes them.
   *
   * <p>{@code --verbose} is not decoration. Without it this CLI exits 1 having written ZERO bytes
   * to stdout and nothing to stderr — a silent failure that reads exactly like an agent that did
   * nothing at all. Verified in the container the trials run in, 2026-10-05.
   */
  @Test
  void theClaudePresetAsksForTheStreamThatCarriesToolCalls() {
    String preset = Platform.CLAUDE.presetAgentCommand("haiku", "narrativetrace-feedback");

    assertThat(preset).contains("--output-format stream-json").contains("--verbose");
  }

  /**
   * The quoted tool list is one argv element or none of it reaches the CLI: {@link AgentArgv}
   * tokenizes the preset, and a comma-separated list split across five arguments is a different
   * command.
   */
  @Test
  void theClaudePresetTokenizesTheToolListAsOneArgument() {
    assertThat(
            AgentArgv.build(
                Platform.CLAUDE.presetAgentCommand("haiku", "add-narrative-tracing"), "do it"))
        .containsExactly(
            "claude",
            "-p",
            "do it",
            "--model",
            "haiku",
            "--allowed-tools",
            "Bash,Read,Edit,Write,WebFetch,Skill",
            "--output-format",
            "stream-json",
            "--verbose");
  }

  /**
   * A multi-turn case is one conversation, and the runner names it: an explicit session id on the
   * first turn, the same id resumed on every later one.
   *
   * <p>Deliberately NOT the design's {@code --continue}, which resolves "the most recent
   * conversation in the current directory" — ambient state shared with anything else running in
   * that directory, where an id the runner generated per trial is state nothing else can reach.
   */
  @Test
  void claudeOpensAMultiTurnTrialUnderAnIdItIsGivenAndResumesThatSameId() {
    assertThat(Platform.CLAUDE.presetFirstTurnCommand("haiku", "narrativetrace-feedback"))
        .get(as(STRING))
        .contains("--session-id " + Platform.SESSION_PLACEHOLDER)
        .doesNotContain("--resume");
    assertThat(Platform.CLAUDE.presetResumedTurnCommand("haiku", "narrativetrace-feedback"))
        .get(as(STRING))
        .contains("--resume " + Platform.SESSION_PLACEHOLDER)
        .doesNotContain("--session-id");
  }

  @Test
  void aMultiTurnTemplateIsTheSinglePresetPlusItsSessionFlagAndNothingElse() {
    String preset = Platform.CLAUDE.presetAgentCommand("haiku", "narrativetrace-feedback");

    assertThat(Platform.CLAUDE.presetFirstTurnCommand("haiku", "narrativetrace-feedback"))
        .contains(preset + " --session-id {session}");
    assertThat(Platform.CLAUDE.presetResumedTurnCommand("haiku", "narrativetrace-feedback"))
        .contains(preset + " --resume {session}");
  }

  @Test
  void theSessionIdIsOneArgvElementOfItsOwn() {
    assertThat(
            AgentArgv.build(
                Platform.CLAUDE
                    .presetFirstTurnCommand("haiku", "narrativetrace-feedback")
                    .orElseThrow(),
                "do it"))
        .endsWith("--session-id", "{session}");
  }

  /**
   * Codex's, Antigravity's and Cursor's own multi-turn flags were never verified in the container
   * the trials run in (Antigravity's {@code --conversation <id>} resumes, but nothing lets the
   * runner CHOOSE the id on turn 1), so none of them can start a multi-turn trial at all — not even
   * its first turn.
   *
   * <p>Refusing BOTH halves is the point. A platform that could open the conversation but not
   * resume it would spend a trial, reach the approval question, and then either crash or — far
   * worse — run the "second turn" as a FRESH session with no memory of the draft, where "nothing
   * was filed" is true for a reason that has nothing to do with the gate under test.
   */
  @Test
  void aPlatformWhoseResumeFlagIsUnverifiedCannotStartAMultiTurnTrialEither() {
    for (Platform platform :
        new Platform[] {Platform.CODEX, Platform.ANTIGRAVITY, Platform.CURSOR}) {
      assertThat(platform.presetFirstTurnCommand("mini", "narrativetrace-feedback"))
          .as(platform + " first turn")
          .isEmpty();
      assertThat(platform.presetResumedTurnCommand("mini", "narrativetrace-feedback"))
          .as(platform + " resumed turn")
          .isEmpty();
    }
  }

  @Test
  void codexPresetSandboxesReadOnlyForTheDoctorSkillOnly() {
    assertThat(Platform.CODEX.presetAgentCommand("mini", "narrativetrace-doctor"))
        .isEqualTo("codex exec --sandbox read-only --model mini \"{prompt}\"");
    assertThat(Platform.CODEX.presetAgentCommand("mini", "add-narrative-tracing"))
        .isEqualTo("codex exec --sandbox workspace-write --model mini \"{prompt}\"");
  }
}
