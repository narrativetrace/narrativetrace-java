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
import java.util.ArrayList;
import java.util.List;

/**
 * {@code narrativetrace-verify} — reads what a change actually did before the agent says it is
 * done, and pins the result as an approval baseline behind the user's yes.
 *
 * <p>The loop runs after the tests are green and before the report: decide whether the change is
 * worth tracing and say so (a pure function is not); write the intent down BEFORE the run, because
 * a trace read against nothing confirms whatever happened; run the smallest real path; read the
 * value-free structural trace against the intent; open values only on the span that looks wrong;
 * fix and re-read; pin; report what the trace showed, citing span ids.
 *
 * <p>The pin is the feedback skill's approval gate, in its words ({@link ApprovalGate}): show the
 * whole {@code .received.nt}, ask once and stop the turn, promote exactly what was shown. It is
 * four steps rather than one so each part of the gate has its own verify line.
 *
 * <p><b>@llmNote</b> This skill declares NO allowed tools, and that is a safety property: the
 * promotion runs through {@code ./gradlew}, so pre-approving the wrapper would pre-approve the
 * promotion in the turn the gate says must stop. {@code Lints#promotionNotPreApproved} fails the
 * build if a tool beyond read-only discovery is ever added.
 */
public final class NarrativeTraceVerifySkill {

  /** Values, the fix and the re-read happen only when the structural read found a mismatch. */
  static final String ONLY_ON_A_MISMATCH =
      "only when the structural read named a span that does not match the intent; otherwise go"
          + " on to the pin";

  private NarrativeTraceVerifySkill() {}

  static Skill build() {
    return new Skill(
        "narrativetrace-verify",
        SkillClass.GUIDED,
        "Verifies a change in a Java project by reading what the code actually did before saying"
            + " it is done. Use after the tests are green and before reporting a change that"
            + " crosses collaborators, branches, retries, runs async, carries state between calls,"
            + " or touches code not written in this session — and skip it, saying why, for a pure"
            + " function or a one-class edit. Writes the intent down first, runs the smallest real"
            + " path with NarrativeTrace on, reads the value-free structural trace against the"
            + " intent, opens values only on the span that looks wrong, fixes and re-reads, then"
            + " pins the flow as an approval baseline behind your yes and reports what the trace"
            + " showed, citing span ids. Say 'verify this change with the trace', 'check what the"
            + " code actually did', 'did the flow do what I meant', or 'pin this flow as a"
            + " baseline' to invoke it.",
        "Non-obvious triggers: the suite is green but the change touched more call sites than it"
            + " added; a notification, payment or retry path changed; a .received.nt appeared after"
            + " a test run; you are about to write 'tests pass' as the whole report.",
        "sixty-seconds",
        steps(),
        List.of(
            TraceReading.CITE_SPAN_IDS,
            new ReasonedRule(
                "Use the cheapest flavour that answers the question",
                "the structural trace first and a value on one span only is what keeps the common"
                    + " case near zero tokens"),
            BaselinePin.SHOW_THE_WHOLE_RECEIVED),
        never(),
        List.of(),
        List.of(TraceReading.FLAVOURS, TraceReading.SHAPES));
  }

  private static List<ReasonedRule> never() {
    List<ReasonedRule> never =
        new ArrayList<>(
            List.of(
                new ReasonedRule(
                    "Never report a change as done on green tests alone once this skill decided to"
                        + " trace",
                    "the suite checks what someone thought to assert; the trace shows what the code"
                        + " did"),
                new ReasonedRule(
                    "Never read a trace against nothing",
                    "an intent written after the run bends to whatever happened — that is why it"
                        + " comes first"),
                TraceReading.NEVER_REDACTION_OFF));
    never.addAll(BaselinePin.NEVER);
    return List.copyOf(never);
  }

  private static List<SkillStep> steps() {
    List<SkillStep> steps = new ArrayList<>(loop());
    steps.addAll(BaselinePin.steps());
    steps.add(report());
    return List.copyOf(steps);
  }

  private static List<SkillStep> loop() {
    return List.of(
        decide(),
        new SkillStep(
            "Write the intent down before running anything",
            new StepBody.CommandStep(List.of()),
            "three to six lines in the reply, under the word Intent, written before the first"
                + " traced run: which collaborators the change touches, in which order, under which"
                + " branch, how many times — the oracle the trace is read against, never edited"
                + " after the run"),
        run(),
        new SkillStep(
            "Read the structural trace first, against the intent",
            new StepBody.CommandStep(List.of(VerifyCommands.FIND_STRUCTURAL)),
            "the .nt of the test just run was opened and read whole before any value was looked"
                + " at, and the reply walks it against the intent — calls, order, branch,"
                + " multiplicity — naming every match and every mismatch by its span id (#2.1);"
                + " the shapes below are the checklist"),
        new SkillStep(
            "Open values on the span that looks wrong, and only there",
            new StepBody.CommandStep(List.of(VerifyCommands.FIND_NARRATIVES)),
            "only the flagged span was read in the .md narrative, found by the id the .nt gave it —"
                + " not the whole file; a [REDACTED] value stays redacted",
            List.of(),
            null,
            ONLY_ON_A_MISMATCH),
        new SkillStep(
            "Fix, re-run, read again",
            new StepBody.CommandStep(List.of(VerifyCommands.RUN_THE_PATH)),
            "the same test ran again after the fix and its new .nt was read whole: the span that"
                + " was wrong now matches the intent, and a fix that changed the shape was read"
                + " again from the structural read",
            List.of(),
            null,
            ONLY_ON_A_MISMATCH));
  }

  private static SkillStep report() {
    return new SkillStep(
        "Report what the trace showed",
        new StepBody.CommandStep(List.of()),
        "two sentences on what the trace showed, every claim citing the span id it rests on —"
            + " a claim without an id is not a claim, and only ids in the .nt that was read"
            + " count — with the .nt attached or quoted; 'tests pass' alone is not the"
            + " report");
  }

  private static SkillStep decide() {
    return new SkillStep(
        "Decide whether to trace, and say so",
        new StepBody.CommandStep(List.of()),
        "before anything runs, the reply says which it is: 'tracing: <the reason>' when the change"
            + " crosses two or more collaborators over a boundary, branches, retries, runs async"
            + " or concurrently, carries state between calls, touched more call sites than it"
            + " added, or includes code not written in this session; or 'skipping"
            + " narrativetrace-verify: <a pure function | a one-class edit with no collaborator | a"
            + " flow one test already walks end to end>' — a skip ends the skill here, and that"
            + " sentence is the report. A whole flow's .nt is dozens of lines: cheap where the"
            + " path is not obvious, waste where it is");
  }

  private static SkillStep run() {
    return new SkillStep(
        "Run the smallest real path with tracing on",
        new StepBody.SnippetStep(
            "java", "sixty-seconds/src/test/java/com/example/orders/PlaceOrderFlowTest.java"),
        VerifyCommands.RUN_THE_PATH,
        List.of(
            new FailureNote(
                "no .nt for the test appears under build/narrativetrace/structural",
                "the JUnit 5 extension is not registered, or the collaborators on the path are not"
                    + " wrapped with the test's NarrativeContext",
                "run narrativetrace-doctor and apply its fix, then run the test again")),
        null,
        "the project already has a test that drives the changed path through its real"
            + " collaborators, run that one — again, if it already ran: a trace from a run made"
            + " before the Intent was written does not count; otherwise write the smallest one, as"
            + " below");
  }
}
