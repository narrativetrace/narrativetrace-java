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
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillClass;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.util.List;

/**
 * {@code narrativetrace-feedback} — reports a defect in NarrativeTrace itself, with the user's
 * approval and nothing else.
 *
 * <p>The shape is the whole point: draft, SHOW the draft in full, ask one question, stop the turn.
 * Filing is public and permanent, so the decision has to be the user's in a turn of their own — not
 * inferred from the turn that asked, and not from a summary of a report they never saw. The gate's
 * wording is {@link ApprovalGate}'s, shared with the verify skill's pin step.
 *
 * <p><b>@llmNote</b> This skill declares NO allowed tools, and that is a safety property rather
 * than an omission. A Claude-flavour {@code allowed-tools} grants its listed tools for the turn
 * that loads the skill, without prompting — so a skill that declared {@code ./gradlew} would
 * pre-approve its own reporting command. {@code Lints#publishingNotPreApproved} fails the build if
 * that is ever added back.
 */
public final class NarrativeTraceFeedbackSkill {

  private NarrativeTraceFeedbackSkill() {}

  static Skill build() {
    return new Skill(
        "narrativetrace-feedback",
        SkillClass.GUIDED,
        "Reports a defect in NarrativeTrace itself — the library, the doctor, an agent skill, or"
            + " the published install prompt. Use when a doctor finding is wrong or its fix does"
            + " not work, when a skill step cannot be followed or its verify cannot be met, when"
            + " the install prompt is wrong, or when the library misbehaves and the project is"
            + " configured correctly. Drafts the report from this project (the install"
            + " coordinates, the doctor's own JSON report, and at most one structural trace),"
            + " refuses to write one that carries a value from your traces and names the rule"
            + " that refused it, shows you the whole draft, and then asks once whether to file it"
            + " publicly. Files nothing without your answer and sends nothing anywhere. Say"
            + " 'report this to NarrativeTrace', 'the doctor's fix did not work', or 'file a bug"
            + " about this skill' to invoke it.",
        "Non-obvious triggers: a doctor fix that leaves the same finding failing; a skill step"
            + " whose verify cannot be met on a correctly configured project; wording in the"
            + " install prompt that led somewhere wrong.",
        "sixty-seconds",
        List.of(
            new SkillStep(
                "Gather what the report needs",
                new StepBody.CommandStep(List.of(DoctorCommands.RUN_DOCTOR_GRADLE)),
                "the JSON report at "
                    + DoctorCommands.DOCTOR_REPORT_PATH
                    + " exists, so the report can carry it; a project whose build cannot run this"
                    + " task is reported under the prompt or library category instead",
                List.of(AddNarrativeTracingSkill.DOCTOR_TASK_NOT_FOUND),
                null),
            new SkillStep(
                "Draft the report and let the gate check it",
                new StepBody.CommandStep(List.of(FeedbackCommands.DRAFT_REPORT)),
                FeedbackCommands.VERIFY_DRAFT_WRITTEN,
                List.of(
                    new FailureNote(
                        "the command exits 2 naming a vf.* rule",
                        "a field carries a value from this project's own run — a rendered call"
                            + " line, an elapsed time, a credential-shaped string, an address",
                        "rewrite that one field to describe what happened instead of pasting it,"
                            + " and draft again; never work around the rule by moving the text to"
                            + " another field")),
                null),
            new SkillStep(
                "Show the whole draft, not a summary of it",
                new StepBody.CommandStep(List.of(FeedbackCommands.FIND_FEEDBACK_FILES)),
                FeedbackCommands.VERIFY_DRAFT_SHOWN,
                List.of(),
                null),
            new SkillStep(
                "Ask once whether to file it, then stop the turn",
                new StepBody.CommandStep(List.of()),
                FeedbackCommands.VERIFY_QUESTION_ASKED,
                List.of(),
                null),
            new SkillStep(
                "Print the way to file it, and nothing else",
                new StepBody.CommandStep(List.of(FeedbackCommands.PRINT_URL)),
                FeedbackCommands.VERIFY_URL_PRINTED,
                List.of(),
                null)),
        List.of(
            ApprovalGate.showTheWholeBeforeAsking(
                "draft",
                "filing is public and permanent, and a person can only approve what they have"
                    + " actually read"),
            new ReasonedRule(
                "Ask in the user's own language",
                "the report may be written in any language, and a question nobody understands is"
                    + " not a question"),
            new ReasonedRule(
                "Tell the user that filing is public, under their own account, before they answer",
                "a public issue shows that their project uses NarrativeTrace, and that is their"
                    + " decision to make knowingly")),
        List.of(
            new ReasonedRule(
                "Never attach a rendered trace, a log file or a source file",
                "those carry the values from the user's own run; the structural trace carries the"
                    + " same shape of the same call without any of them, and the verb attaches it"
                    + " on its own"),
            ApprovalGate.neverInTheTurnThatAsked("file"),
            ApprovalGate.neverEditAfterShowing("draft", "filed", "report is drafted"),
            new ReasonedRule(
                "Never open the URL or run the printed command",
                "submitting is the user's act, in their own browser or their own shell, under"
                    + " their own account"),
            new ReasonedRule(
                "Never route a rule's refusal around the gate",
                "a field that cannot be filed is a field to rewrite, not to move somewhere the"
                    + " rule does not look")),
        List.of());
  }
}
