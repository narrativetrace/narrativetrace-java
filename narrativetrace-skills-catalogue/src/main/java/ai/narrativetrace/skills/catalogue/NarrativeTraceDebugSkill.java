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
 * {@code narrativetrace-debug} — finds the cause of a symptom by reading what the code did with the
 * values, fixes it where it diverged, and pins the reproduction behind the user's yes.
 *
 * <p>The loop starts from a symptom, not a change: reproduce it with the smallest input and tracing
 * on; across threads read the sequence diagram first; name, by span id, the first span whose inputs
 * are right and whose result is wrong — before touching code; narrow to that span's sub-tree by
 * wrapping one more collaborator, never by redacting; hand a defect in NarrativeTrace itself to
 * {@code narrativetrace-feedback} instead of patching around it; fix it in that span, re-run the
 * same input, and check the structural trace shows nothing else moved; keep the reproduction as the
 * regression test and pin its structural trace ({@link BaselinePin}); report the root cause by span
 * id.
 *
 * <p>Unlike {@code narrativetrace-verify} there is no cost rule that skips the trace: once there is
 * a bug, the trace is the cheap way to find it. The cost rule here is only "narrow the span before
 * you read values". Both skills render the same {@link TraceReading} sections.
 *
 * <p><b>@llmNote</b> This skill declares NO allowed tools for the same reason the verify skill
 * does: the pin promotes through {@code ./gradlew}, so pre-approving the wrapper would pre-approve
 * the promotion in the turn the gate says must stop ({@code Lints#promotionNotPreApproved}). In
 * Java {@code @NotTraced} REDACTS a value — it is never a way to scope a trace, which is why the
 * bisect step narrows by wrapping one more collaborator instead.
 */
public final class NarrativeTraceDebugSkill {

  private NarrativeTraceDebugSkill() {}

  static Skill build() {
    return new Skill(
        "narrativetrace-debug",
        SkillClass.GUIDED,
        "Finds the cause of a wrong result in a Java project by reading what the code did with"
            + " the values, not by stepping through it. Use when a symptom is reported — a wrong"
            + " amount, a wrong id, a call in the wrong order, a test that fails with a value"
            + " nobody expected. Reproduces it with the smallest input and NarrativeTrace on, finds"
            + " the first span where a value diverges and names it by its span id (the sequence"
            + " diagram first when threads are involved), narrows to that span's sub-tree, fixes it"
            + " there and checks the structural trace shows nothing else moved, pins the"
            + " reproduction as a regression test and an approval baseline behind your yes, and"
            + " reports the root cause by span id. Hands a defect in NarrativeTrace itself to"
            + " narrativetrace-feedback. Say 'debug this with the trace', 'find where this value"
            + " goes wrong', or 'why is this result wrong' to invoke it.",
        "Non-obvious triggers: a support ticket quoting a wrong amount or total; a rounding,"
            + " currency or timezone difference between what was expected and what happened; a"
            + " value that is right going into a service and wrong coming out; a flaky ordering"
            + " between threads.",
        "sixty-seconds",
        steps(),
        always(),
        never(),
        List.of(),
        List.of(TraceReading.FLAVOURS, TraceReading.SHAPES));
  }

  private static List<ReasonedRule> always() {
    return List.of(
        TraceReading.CITE_SPAN_IDS,
        new ReasonedRule(
            "Narrow to one span before reading its values",
            "debugging is where values pay for themselves, but only on the span that diverged —"
                + " a whole trace of values buries the one that matters"),
        BaselinePin.SHOW_THE_WHOLE_RECEIVED);
  }

  private static List<ReasonedRule> never() {
    List<ReasonedRule> never =
        new ArrayList<>(
            List.of(
                new ReasonedRule(
                    "Never change code before the diverging span is named",
                    "a fix made before the trace says where the value went wrong is a guess, and a"
                        + " guess that turns the test green hides the defect it missed"),
                new ReasonedRule(
                    "Never make the symptom go away somewhere other than the diverging span",
                    "a correction downstream, a caught exception or a changed expectation silences"
                        + " the symptom and leaves the defect for the next caller of that span"),
                TraceReading.NEVER_REDACTION_OFF));
    never.addAll(BaselinePin.NEVER);
    return List.copyOf(never);
  }

  private static List<SkillStep> steps() {
    List<SkillStep> steps =
        new ArrayList<>(
            List.of(
                reproduce(),
                symptom(),
                diagram(),
                localize(),
                bisect(),
                handOff(),
                fix(),
                delta(),
                keep()));
    steps.addAll(BaselinePin.steps());
    steps.add(report());
    return List.copyOf(steps);
  }

  private static SkillStep reproduce() {
    return new SkillStep(
        "Reproduce the symptom with tracing on",
        new StepBody.SnippetStep(
            "java", "sixty-seconds/src/test/java/com/example/orders/PlaceOrderFlowTest.java"),
        DebugCommands.REPRODUCE,
        List.of(
            new FailureNote(
                "no .md for the test appears under build/narrativetrace/traces",
                "the JUnit 5 extension is not registered, or the collaborators on the path are not"
                    + " wrapped with the test's NarrativeContext",
                "run narrativetrace-doctor and apply its fix, then run the test again")),
        null,
        "a test already drives the path with the input from the symptom, run that one; otherwise"
            + " write the smallest one, as below — the reported input, through the real"
            + " collaborators each wrapped with the test's NarrativeContext, asserting the value"
            + " the symptom says should have come out");
  }

  private static SkillStep symptom() {
    return new SkillStep(
        "Find the symptom in the values",
        new StepBody.CommandStep(List.of(VerifyCommands.FIND_NARRATIVES)),
        "the reported value is in this run's .md narrative, or the reproducing test fails on it —"
            + " a symptom that does not reproduce is said so, and the loop stops here");
  }

  private static SkillStep diagram() {
    return new SkillStep(
        "Across threads, read the sequence diagram first",
        new StepBody.CommandStep(List.of(DebugCommands.FIND_DIAGRAMS)),
        "the reproduction's .mmd was read before any span's values, and the reply says which call"
            + " ran before which across the threads, by the span id in each call's note — the"
            + " span it points to is the one localized next",
        List.of(),
        null,
        "only when the path crosses threads — the .nt shows a fork, async or fire-and-forget"
            + " marker — or the symptom is about order (a call that ran before or after another);"
            + " otherwise go straight to localizing");
  }

  private static SkillStep localize() {
    return new SkillStep(
        "Localize by reading: name the first span where a value diverges",
        new StepBody.CommandStep(List.of()),
        "the reproduction's .md spans were read from the root down until the first one whose"
            + " inputs are what the symptom implies but whose result, the value it passes on, or"
            + " the branch it takes is not; the reply names that span by its id (#1.3) and the"
            + " boundary — the collaborator, the parameter or return, the value that arrived and"
            + " the value that left — before any code is changed: by reading, not by stepping"
            + " through a debugger or adding prints");
  }

  private static SkillStep bisect() {
    return new SkillStep(
        "Bisect by span, not by file",
        new StepBody.CommandStep(List.of(DebugCommands.REPRODUCE)),
        "only the sub-tree under the diverging id was read on each re-run — the spans whose id"
            + " begins with it (#1.3, #1.3.1, #1.3.2) — and where the work inside that span is not"
            + " traced, the collaborator it calls was wrapped with NarrativeTraceProxy.trace in the"
            + " reproducing test, as the listing above wraps its service, and the run repeated,"
            + " until the divergence sits in the smallest span that has it; never @NotTraced to"
            + " narrow — in Java it redacts a value, it does not scope a trace",
        List.of(),
        null,
        "only when the value went into the diverging span right and came out wrong, and what"
            + " happens in between is more than that span's own few lines; otherwise the diverging"
            + " span is the defect — go on to the fix");
  }

  private static SkillStep fix() {
    return new SkillStep(
        "Fix it in the diverging span, re-run the same input, read the same span",
        new StepBody.CommandStep(List.of(DebugCommands.REPRODUCE)),
        "the change is in the code of that span — the method the diverging id names, or what it"
            + " calls — and the reproducing test passes; the same span, by the same id, now"
            + " carries the value the symptom implied; a change anywhere else that makes the test"
            + " pass silences the symptom and leaves the defect, so it is undone");
  }

  private static SkillStep delta() {
    return new SkillStep(
        "Check that nothing else moved",
        new StepBody.CommandStep(List.of(VerifyCommands.FIND_STRUCTURAL)),
        "the fixed run's .nt was read whole and compared, line by line, with the call lines of"
            + " the reproduction's .md — a red run writes no .nt (the .nt on disk is the last green"
            + " one), so the shape before the fix is the .md's calls and ids without their values:"
            + " the same calls in the same order under the same ids; a value fix moves no line of"
            + " a value-free trace, and every line that did move is named by its id in the reply"
            + " and either explained by the fix or undone");
  }

  private static SkillStep keep() {
    return new SkillStep(
        "Keep the reproduction as the regression test",
        new StepBody.CommandStep(List.of()),
        "the reproducing test stays in the suite with the input from the symptom and asserts the"
            + " value the fixed span now carries — not only that nothing throws — so it fails when"
            + " the fix is undone; its structural trace is what the pin below makes the baseline");
  }

  private static SkillStep handOff() {
    return new SkillStep(
        "Hand a defect in NarrativeTrace itself to narrativetrace-feedback",
        new StepBody.CommandStep(List.of()),
        "narrativetrace-feedback was started with the span id and the value-free .nt — never a"
            + " value from the trace — and the project's code was not changed to work around it;"
            + " the loop ends with that hand-off",
        List.of(),
        null,
        "only when the trace and the code disagree — a call the code makes has no span, a span"
            + " shows a value the code did not pass, one span has two ids in two flavours — or the"
            + " diverging span is inside NarrativeTrace; otherwise go on to the fix");
  }

  private static SkillStep report() {
    return new SkillStep(
        "Report the root cause as the trace showed it",
        new StepBody.CommandStep(List.of()),
        "the root cause in the user's own terms — the span id where it diverged, the value that"
            + " arrived and the value that left, the branch it took — and what the fix changed in"
            + " that span; every claim cites the span id it rests on, from the .nt or .md read in"
            + " this session: a claim without an id is not a claim. The report is written in full"
            + " before the pin question, where the gate puts it, and the closing reply after the"
            + " promotion names the span id again in its one-line summary of the cause");
  }
}
