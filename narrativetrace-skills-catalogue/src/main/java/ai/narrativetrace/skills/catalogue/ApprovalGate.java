/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import ai.narrativetrace.skills.ReasonedRule;

/**
 * The approval gate every skill uses before it turns something it SHOWED into something durable or
 * public: show the whole artifact, ask once and stop the turn, and act only on what was shown.
 *
 * <p>INTENT: the feedback skill files a public issue and the verify skill promotes a committed
 * approval baseline; both are a person's decision, made on an artifact they read, in a turn of
 * their own. Written once so the two skills state the gate in the same words — a rule restated by
 * hand is a rule that drifts — with only the artifact and the act filled in.
 */
final class ApprovalGate {

  private ApprovalGate() {}

  /** The verify line of the "show it" step: the artifact itself, whole, is in the reply. */
  static String verifyShownWhole(String artifact) {
    return "the whole text of " + artifact + " is in the reply, not a summary of it";
  }

  /**
   * The verify line of the "ask once" step, followed by what must not happen before the yes.
   *
   * @param beforeTheYes the act this skill must not start before the user's answer, as a sentence
   */
  static String verifyAskedThenStopped(String beforeTheYes) {
    return "the reply ends with the question and nothing after it — the answer is the user's next"
        + " message, never something assumed in this one. "
        + beforeTheYes;
  }

  /** "Show the whole draft before asking anything", with this skill's reason. */
  static ReasonedRule showTheWholeBeforeAsking(String artifact, String why) {
    return new ReasonedRule("Show the whole " + artifact + " before asking anything", why);
  }

  /** "Never file in the turn that asked" — the yes is the user's next message. */
  static ReasonedRule neverInTheTurnThatAsked(String act) {
    return new ReasonedRule(
        "Never " + act + " in the turn that asked",
        "approval is the user's next message — a yes assumed in the same turn is not one");
  }

  /**
   * "Never edit the draft after showing it" — what was approved is what is acted on.
   *
   * @param artifact what was shown, as the rule names it ("draft")
   * @param actedOn the act in the past tense ("filed")
   * @param changed what a change produces, and how it is made again ("report is drafted")
   */
  static ReasonedRule neverEditAfterShowing(String artifact, String actedOn, String changed) {
    return new ReasonedRule(
        "Never edit the " + artifact + " after showing it",
        "what was approved has to be what is "
            + actedOn
            + ", so a changed "
            + changed
            + " again and shown again");
  }
}
