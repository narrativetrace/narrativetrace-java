/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * INTENT: everything that drives a trial's agent — one command template per turn, one prompt per
 * turn, and the session id that makes several turns ONE conversation rather than several unrelated
 * ones.
 *
 * <p>A single-turn trial is the ordinary shape and the only one that existed before the feedback
 * cases: one prompt, the platform's own preset, no session. A multi-turn trial is the shape a case
 * needs when what it measures must happen in a LATER turn than the one that asked for it — an
 * approval is only an approval if it arrives in a turn of the user's own.
 *
 * <p><b>@llmNote</b> Every way of being wrong is REFUSED here rather than degraded, and that is the
 * load-bearing decision in this type. A multi-turn case that quietly ran as several independent
 * single turns would reach its approval question with an agent that had forgotten the draft, and
 * then pass its grader — "nothing was filed" — for a reason that has nothing to do with the gate.
 *
 * @param firstTurnCommand the template for turn 1, or {@code null} to drive no agent at all and
 *     grade the fixture as it stands
 * @param resumedTurnCommand the template every turn after the first uses, or {@code null} for a
 *     single-turn trial
 * @param prompts one per turn, in turn order: {@code prompt.md} first, then the case's scripted
 *     user replies
 */
public record AgentTurns(String firstTurnCommand, String resumedTurnCommand, List<String> prompts) {

  /**
   * The shape a session id has to have to be substituted into a command safely: one opaque token.
   * An id carrying whitespace or a quote would split into two argv elements and silently change the
   * command it was spliced into.
   */
  private static final Pattern SESSION_ID =
      Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

  public AgentTurns {
    if (prompts == null || prompts.isEmpty()) {
      throw new IllegalArgumentException("a trial needs at least one turn's prompt");
    }
    // Checked BEFORE the copy, deliberately: List.copyOf rejects a null element with a
    // NullPointerException out of the JDK, which would mask the named reason a caller needs.
    for (String prompt : prompts) {
      if (prompt == null || prompt.isBlank()) {
        throw new IllegalArgumentException("a turn with a blank prompt cannot be driven");
      }
    }
    prompts = List.copyOf(prompts);
    if (prompts.size() > 1 && (resumedTurnCommand == null || resumedTurnCommand.isBlank())) {
      throw new IllegalArgumentException(
          "a trial of " + prompts.size() + " turns needs a template that resumes its session");
    }
  }

  /**
   * What drives this case on this platform.
   *
   * @param overrideCommand an operator's explicit {@code --agent-command}, or {@code null} for the
   *     platform's preset
   * @param firstPrompt the case's own {@code prompt.md}
   * @param scriptedReplies the case's declared user replies, in turn order; empty for one turn
   * @param sessionId the id this trial's conversation is opened and resumed under
   * @throws IllegalArgumentException when this platform cannot resume a session a multi-turn case
   *     needs, when an override is given for a multi-turn case, or when the session id is not one
   *     opaque token
   */
  public static AgentTurns of(
      Platform platform,
      String model,
      String canonicalSkillName,
      String overrideCommand,
      String firstPrompt,
      List<String> scriptedReplies,
      String sessionId) {
    List<String> prompts = promptsOf(firstPrompt, scriptedReplies);
    if (prompts.size() == 1) {
      return new AgentTurns(
          overrideCommand == null
              ? platform.presetAgentCommand(model, canonicalSkillName)
              : overrideCommand,
          null,
          prompts);
    }
    requireNoOverride(overrideCommand);
    requireAWellShapedSessionId(sessionId);
    return new AgentTurns(
        named(platform.presetFirstTurnCommand(model, canonicalSkillName), platform, sessionId),
        named(platform.presetResumedTurnCommand(model, canonicalSkillName), platform, sessionId),
        prompts);
  }

  /** A trial that drives no agent: the fixture is graded exactly as the case scaffolded it. */
  public static AgentTurns graded(String prompt) {
    return new AgentTurns(null, null, List.of(prompt));
  }

  /** Whether an agent runs at all, or the trial goes straight to its grader. */
  public boolean drivesAnAgent() {
    return firstTurnCommand != null;
  }

  /** How many turns this trial drives. */
  public int turnCount() {
    return prompts.size();
  }

  /** What the agent is handed on {@code turn}, 1-based. */
  public String promptForTurn(int turn) {
    return prompts.get(indexOf(turn));
  }

  /**
   * The template {@code turn} runs, with the session id already substituted — a caller hands this
   * straight to {@link AgentArgv}, which only ever substitutes the prompt.
   */
  public String commandForTurn(int turn) {
    return indexOf(turn) == 0 ? firstTurnCommand : resumedTurnCommand;
  }

  private int indexOf(int turn) {
    if (turn < 1 || turn > prompts.size()) {
      throw new IllegalArgumentException(
          "turn " + turn + " is outside a conversation of " + prompts.size());
    }
    return turn - 1;
  }

  private static List<String> promptsOf(String firstPrompt, List<String> scriptedReplies) {
    List<String> prompts = new ArrayList<>();
    prompts.add(firstPrompt);
    prompts.addAll(scriptedReplies == null ? List.of() : scriptedReplies);
    return prompts;
  }

  /**
   * A multi-turn case uses its platform's preset pair, never an override: one override string
   * cannot be two templates, and the one it would be is the FIRST turn's — so an accepted override
   * would run every later turn as a fresh conversation.
   */
  private static void requireNoOverride(String overrideCommand) {
    if (overrideCommand != null) {
      throw new IllegalArgumentException(
          "a multi-turn case runs on its platform's preset: one --agent-command cannot be both the"
              + " template that opens a session and the one that resumes it");
    }
  }

  private static void requireAWellShapedSessionId(String sessionId) {
    if (sessionId == null || !SESSION_ID.matcher(sessionId).matches()) {
      throw new IllegalArgumentException(
          "a multi-turn trial's session id must be one opaque token (a uuid), not \""
              + sessionId
              + "\"");
    }
  }

  /** This trial's own id in place of the template's placeholder. */
  private static String named(Optional<String> template, Platform platform, String sessionId) {
    return template
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    platform
                        + " cannot drive a multi-turn case: its own multi-turn flags have never"
                        + " been verified in the container the trials run in"))
        .replace(Platform.SESSION_PLACEHOLDER, sessionId);
  }
}
