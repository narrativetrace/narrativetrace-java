/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

/**
 * Command and verify literals for the problem-report skill — the Gradle spelling of the {@code
 * feedback} verb, because an agent skill's closed command vocabulary is this repository's own
 * wrapper and a skill may never instruct installing a global tool.
 *
 * <p>Every command here is what an ADOPTER runs, in their own project. The angle-bracket
 * placeholders are the one thing the agent substitutes: the three sentences and the step are the
 * report, and no literal can carry somebody else's report. {@code SkillReplayer} maps each of these
 * onto the fixture with text of its own, which is the same translation layer {@link DoctorCommands}
 * already relies on.
 *
 * <p><b>@llmNote</b> There is deliberately no {@code gh} command here. The {@code gh issue create}
 * line is the CLI verb's third channel, printed and never run, and it is outside the vocabulary by
 * ruling — so the skill NAMES it for somebody who already has that tool and never invokes it.
 */
public final class FeedbackCommands {

  /** Drafts the report, checks it carries no values, and prints the whole thing. */
  public static final String DRAFT_REPORT =
      "./gradlew narrativetraceFeedback --channel draft --category <category>"
          + " --step <where it happened> --did <what you did> --happened <what happened>"
          + " --expected <what you expected>";

  /** Prints the pre-filled issue-form URL for the same report, once the user has said yes. */
  public static final String PRINT_URL =
      "./gradlew narrativetraceFeedback --channel url --category <category>"
          + " --step <where it happened> --did <what you did> --happened <what happened>"
          + " --expected <what you expected>";

  /** Where the verb leaves the two files, relative to the project it ran in. */
  public static final String DRAFT_PATH = "build/narrativetrace/feedback/feedback-draft.md";

  /** The body file the user pastes into the issue form's last box. */
  public static final String BODY_PATH = "build/narrativetrace/feedback/feedback-body.md";

  public static final String VERIFY_DRAFT_WRITTEN =
      "the command exited 0 and "
          + DRAFT_PATH
          + " and "
          + BODY_PATH
          + " both exist — a run that"
          + " named a vf.* rule instead wrote neither, and the field it named is what to fix";

  public static final String VERIFY_DRAFT_SHOWN = ApprovalGate.verifyShownWhole(DRAFT_PATH);

  public static final String VERIFY_QUESTION_ASKED =
      ApprovalGate.verifyAskedThenStopped(
          "Do not print the issue URL or run gh before the user says yes — showing the URL is the"
              + " filing.");

  public static final String VERIFY_URL_PRINTED =
      "the printed URL is in the reply, together with the name of " + BODY_PATH + " to paste";

  /** Lists what the verb wrote, so a reader can open the body file before pasting it. */
  public static final String FIND_FEEDBACK_FILES =
      "find build/narrativetrace/feedback -name \"*.md\"";

  private FeedbackCommands() {}
}
