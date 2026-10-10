/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import ai.narrativetrace.skills.FailureNote;
import ai.narrativetrace.skills.ReasonedRule;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.util.List;

/**
 * The pin — a structural trace promoted to a committed {@code .approved.nt} behind the user's yes —
 * as the four steps every skill that pins renders: approval mode on, run and show, ask and stop,
 * promote.
 *
 * <p>INTENT: the verify skill pins a flow it checked and the debug skill pins the regression it
 * fixed; it is the same act on the same artifact behind the same gate ({@link ApprovalGate}), so it
 * is written once.
 */
final class BaselinePin {

  /** The pin's "always" rule: the whole review copy is shown before the question. */
  static final ReasonedRule SHOW_THE_WHOLE_RECEIVED =
      ApprovalGate.showTheWholeBeforeAsking(
          ".received.nt",
          "the baseline becomes the contract every later change is held to, and a person can only"
              + " approve what they have actually read");

  /** The pin's "never" rules, in the order the pages list them. */
  static final List<ReasonedRule> NEVER =
      List.of(
          ApprovalGate.neverInTheTurnThatAsked("promote a baseline"),
          ApprovalGate.neverEditAfterShowing(".received.nt", "promoted", "run is rendered"),
          new ReasonedRule(
              "Never commit a .received.nt",
              "it is the review copy; the committed contract is the .approved.nt"));

  private BaselinePin() {}

  /**
   * The four pin steps, in order. The approval run is the whole suite, never the one test the skill
   * ran: approval mode compares every traced test, so a single-test run leaves every other traced
   * test without a baseline and the suite red.
   */
  static List<SkillStep> steps() {
    return List.of(
        approvalModeOn(),
        new SkillStep(
            "Run the suite in approval mode and show every .received.nt",
            new StepBody.CommandStep(
                List.of(VerifyCommands.RUN_THE_SUITE, VerifyCommands.FIND_RECEIVED)),
            "approval mode compares every traced test, not only the one this skill ran, so the"
                + " run that writes the review copies is the whole suite; the first run of a test"
                + " with no baseline fails on purpose — that failure is what writes its review copy"
                + " — and "
                + ApprovalGate.verifyShownWhole("each .received.nt that run wrote")
                + " — it holds names and shape and no value, which is why it is safe to commit"
                + " once approved; where a .approved.nt already existed, the reply also names what"
                + " the delta changed, by span id, in the program's own words, and whether it was"
                + " meant"),
        new SkillStep(
            "Ask once whether to pin it, then stop the turn",
            new StepBody.CommandStep(List.of()),
            ApprovalGate.verifyAskedThenStopped(
                "Do not run approveNarratives before the user says yes — promoting is the"
                    + " pinning. And everything else — the report, every caveat — goes before the"
                    + " question; the question is the reply's last line.")),
        new SkillStep(
            "Promote what was shown, and nothing else",
            new StepBody.CommandStep(List.of(VerifyCommands.APPROVE)),
            "each .approved.nt now holds exactly the text that was shown and no .received.nt is"
                + " left beside it — git status --short src/test/narratives lists the new or"
                + " changed .approved.nt files and nothing else; those are what get committed —"
                + " and ./gradlew test passes: the suite is green again after the promotion"));
  }

  private static SkillStep approvalModeOn() {
    return new SkillStep(
        "Turn approval mode on",
        new StepBody.CodeStep(
            "kotlin",
            """
            // build.gradle.kts, with the ai.narrativetrace plugin applied
            narrativeTrace {
                approval.set(true)
            }
            """),
        "build.gradle.kts carries the block above, and .gitignore carries the line"
            + " src/test/narratives/**/*.received.nt so a review copy is never committed",
        List.of(
            new FailureNote(
                "the build fails with an unresolved reference to narrativeTrace or approval",
                "the ai.narrativetrace Gradle plugin isn't applied to this project, so neither the"
                    + " switch nor the approveNarratives task exists",
                "add id(\"ai.narrativetrace\") to the plugins block and build again")),
        null,
        "if approval mode is off — the build has no approval.set(true), or the doctor's"
            + " config.approval-mode finding fails; when it is already on, go straight to the run");
  }
}
