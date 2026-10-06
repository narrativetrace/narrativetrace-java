/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What drives a trial's agent: one template per turn, one prompt per turn, and the session id that
 * makes several turns one conversation. Every way of being wrong here is refused rather than
 * degraded, because a multi-turn case that quietly ran as several independent single turns would
 * grade an approval that was never asked for.
 */
class AgentTurnsTest {

  private static final String SESSION = "11111111-2222-4333-8444-555555555555";

  private static AgentTurns claude(List<String> replies) {
    return AgentTurns.of(
        Platform.CLAUDE, "haiku", "narrativetrace-feedback", null, "turn one", replies, SESSION);
  }

  @Test
  void aCaseWithNoScriptedRepliesIsOneTurnDrivenByTheSingleTurnPreset() {
    AgentTurns turns = claude(List.of());

    assertThat(turns.turnCount()).isEqualTo(1);
    assertThat(turns.promptForTurn(1)).isEqualTo("turn one");
    assertThat(turns.commandForTurn(1))
        .isEqualTo(Platform.CLAUDE.presetAgentCommand("haiku", "narrativetrace-feedback"));
  }

  @Test
  void anExplicitAgentCommandOverridesTheSingleTurnPreset() {
    AgentTurns turns =
        AgentTurns.of(
            Platform.CLAUDE,
            "haiku",
            "narrativetrace-feedback",
            "echo {prompt}",
            "turn one",
            List.of(),
            SESSION);

    assertThat(turns.commandForTurn(1)).isEqualTo("echo {prompt}");
  }

  @Test
  void aScriptedReplyBecomesALaterTurnWithTheResumedTemplate() {
    AgentTurns turns = claude(List.of("yes, file it"));

    assertThat(turns.turnCount()).isEqualTo(2);
    assertThat(turns.promptForTurn(2)).isEqualTo("yes, file it");
    assertThat(turns.commandForTurn(1)).contains("--session-id " + SESSION);
    assertThat(turns.commandForTurn(2)).contains("--resume " + SESSION);
  }

  /** The placeholder is gone by the time a command is handed over: the id is in the argv. */
  @Test
  void noTurnsCommandStillCarriesTheSessionPlaceholder() {
    AgentTurns turns = claude(List.of("yes, file it", "thanks"));

    for (int turn = 1; turn <= turns.turnCount(); turn++) {
      assertThat(turns.commandForTurn(turn)).doesNotContain(Platform.SESSION_PLACEHOLDER);
    }
  }

  @Test
  void everyTurnAfterTheFirstResumesTheSameConversation() {
    AgentTurns turns = claude(List.of("yes, file it", "thanks"));

    assertThat(turns.turnCount()).isEqualTo(3);
    assertThat(turns.commandForTurn(3)).isEqualTo(turns.commandForTurn(2));
    assertThat(turns.promptForTurn(3)).isEqualTo("thanks");
  }

  /**
   * One override string cannot be two templates, and the one it would be is the FIRST turn's — so
   * an accepted override would run every later turn as a fresh conversation.
   */
  @Test
  void refusesAnOverrideForAMultiTurnCaseRatherThanUsingItForTheFirstTurnOnly() {
    assertThatThrownBy(
            () ->
                AgentTurns.of(
                    Platform.CLAUDE,
                    "haiku",
                    "narrativetrace-feedback",
                    "echo {prompt}",
                    "turn one",
                    List.of("yes, file it"),
                    SESSION))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--agent-command");
  }

  @Test
  void refusesAMultiTurnCaseOnAPlatformThatCannotResumeASession() {
    assertThatThrownBy(
            () ->
                AgentTurns.of(
                    Platform.CODEX,
                    "mini",
                    "narrativetrace-feedback",
                    null,
                    "turn one",
                    List.of("yes, file it"),
                    SESSION))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CODEX");
  }

  /**
   * A single-turn case on a sporadic lane is untouched by any of this: those lanes run every case
   * that needs only one turn, and only a multi-turn one is out of reach.
   */
  @Test
  void aSingleTurnCaseStillRunsOnAPlatformThatCannotResumeASession() {
    AgentTurns turns =
        AgentTurns.of(
            Platform.CODEX, "mini", "narrativetrace-doctor", null, "turn one", List.of(), SESSION);

    assertThat(turns.turnCount()).isEqualTo(1);
    assertThat(turns.commandForTurn(1))
        .isEqualTo(Platform.CODEX.presetAgentCommand("mini", "narrativetrace-doctor"));
  }

  /**
   * A session id reaches the command by substitution, so one carrying whitespace would split into
   * two argv elements and silently change the command it was spliced into.
   */
  @Test
  void refusesASessionIdThatIsNotOneOpaqueToken() {
    assertThatThrownBy(() -> claudeWithSession("not a uuid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("session id");
    assertThatThrownBy(() -> claudeWithSession(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("session id");
  }

  private static AgentTurns claudeWithSession(String sessionId) {
    return AgentTurns.of(
        Platform.CLAUDE,
        "haiku",
        "narrativetrace-feedback",
        null,
        "turn one",
        List.of("yes, file it"),
        sessionId);
  }

  @Test
  void refusesABlankFirstPromptBecauseATurnWithNoPromptCannotBeDriven() {
    assertThatThrownBy(
            () ->
                AgentTurns.of(
                    Platform.CLAUDE,
                    "haiku",
                    "narrativetrace-feedback",
                    null,
                    "  ",
                    List.of(),
                    SESSION))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("prompt");
  }

  @Test
  void refusesATurnNumberOutsideTheConversation() {
    AgentTurns turns = claude(List.of("yes, file it"));

    assertThatThrownBy(() -> turns.promptForTurn(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> turns.promptForTurn(3)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> turns.commandForTurn(3)).isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * The existing "grade the fixture as it stands" seam: no agent runs at all, so there is no
   * command for turn 1 and the trial goes straight to its grader.
   */
  @Test
  void aTrialThatDrivesNoAgentHasNoCommandForItsOneTurn() {
    AgentTurns turns = AgentTurns.graded("turn one");

    assertThat(turns.drivesAnAgent()).isFalse();
    assertThat(turns.turnCount()).isEqualTo(1);
    assertThat(turns.promptForTurn(1)).isEqualTo("turn one");
  }

  @Test
  void aTrialDrivenByAPresetDoesDriveAnAgent() {
    assertThat(claude(List.of()).drivesAnAgent()).isTrue();
  }

  // The record is public, so its own invariants hold whether it was built through of() or directly.

  @Test
  void refusesATrialWithNoTurnAtAll() {
    assertThatThrownBy(() -> new AgentTurns("echo {prompt}", null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one");
    assertThatThrownBy(() -> new AgentTurns("echo {prompt}", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one");
  }

  /** A later turn's prompt is as load-bearing as the first: a blank one cannot be driven either. */
  @Test
  void refusesABlankPromptAtAnyTurn() {
    assertThatThrownBy(() -> new AgentTurns("a {prompt}", "b {prompt}", List.of("one", "  ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank prompt");
    assertThatThrownBy(() -> new AgentTurns("a {prompt}", "b {prompt}", Arrays.asList("one", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank prompt");
  }

  /**
   * Directly built, a multi-turn value still cannot exist without a template that resumes its
   * session — otherwise every turn after the first would open a conversation of its own.
   */
  @Test
  void refusesSeveralTurnsWithNoTemplateThatResumesTheSession() {
    assertThatThrownBy(() -> new AgentTurns("echo {prompt}", null, List.of("one", "two")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resumes its session");
    assertThatThrownBy(() -> new AgentTurns("echo {prompt}", "  ", List.of("one", "two")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resumes its session");
  }

  @Test
  void aCaseWithNoRepliesDeclaredAtAllIsStillOneTurn() {
    AgentTurns turns =
        AgentTurns.of(
            Platform.CLAUDE, "haiku", "narrativetrace-feedback", null, "turn one", null, SESSION);

    assertThat(turns.turnCount()).isEqualTo(1);
  }

  /** The list a caller handed over cannot change what this value says afterwards. */
  @Test
  void keepsItsOwnCopyOfTheTurnsItWasGiven() {
    List<String> prompts = new ArrayList<>(List.of("one", "two"));
    AgentTurns turns = new AgentTurns("a {prompt}", "b {prompt}", prompts);

    prompts.add("three");

    assertThat(turns.turnCount()).isEqualTo(2);
  }
}
