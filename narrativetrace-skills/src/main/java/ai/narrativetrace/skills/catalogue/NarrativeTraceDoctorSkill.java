/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import ai.narrativetrace.skills.CommandVocabulary;
import ai.narrativetrace.skills.FailureNote;
import ai.narrativetrace.skills.ReasonedRule;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillClass;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.util.List;

/**
 * {@code narrativetrace-doctor} — read-only diagnosis. "Run the doctor, interpret, point at the
 * fix," plus the three studies-evidenced steps that stay with diagnosis rather than setup (frozen
 * ruling, {@code agent-skills-2026-09-12.md} §7 ruling 9: steps 3–5 of the original six-step pool).
 *
 * <p>Every step is written for what an ADOPTER runs in their own project, never a fixture path from
 * this repository; {@code SkillReplayer} (Tier A2) is what maps each of these onto the {@code
 * sixty-seconds} fixture, never the other way around.
 */
public final class NarrativeTraceDoctorSkill {

  private NarrativeTraceDoctorSkill() {}

  static Skill build() {
    return new Skill(
        "narrativetrace-doctor",
        SkillClass.MECHANICAL,
        "Diagnoses a NarrativeTrace Java install and configuration. Use when nothing is being"
            + " traced, no build/narrativetrace output appears, the JUnit 5 extension never seems"
            + " to run, parameter names render as arg0/arg1, or you are not sure NarrativeTrace is"
            + " wired up correctly. Checks the JDK and JUnit Jupiter versions, the launcher on"
            + " testRuntimeOnly, narrativetrace.output, whether NarrativeTraceExtension is"
            + " actually registered, whether any sink receives what is traced, whether -parameters"
            + " degraded parameter names, whether redaction is proven in a test, and stale"
            + " .received.nt approval files. Read-only — makes no changes. Say 'check my"
            + " narrativetrace setup', 'is narrativetrace broken', or 'why isn't anything being"
            + " traced' to invoke it.",
        "Non-obvious triggers: a build that traces nothing without any visible error; parameter"
            + " names printed as arg0/arg1; a stale .received.nt left after an approval mismatch.",
        "sixty-seconds",
        List.of(
            new SkillStep(
                "Run the doctor and read the report",
                new StepBody.CommandStep(List.of(DoctorCommands.RUN_DOCTOR_GRADLE)),
                DoctorCommands.VERIFY_ELEVEN_FINDINGS,
                List.of(AddNarrativeTracingSkill.DOCTOR_TASK_NOT_FOUND),
                null),
            new SkillStep(
                "Prove redaction in a test",
                new StepBody.CodeStep(
                    "java",
                    """
                    @Test
                    void redactsTheSensitiveParameter() {
                      var rendered = renderCallWith(deniedParameterName, secretValue);
                      assertThat(rendered).doesNotContain(secretValue);
                      assertThat(rendered).contains("[REDACTED]");
                      assertThat(rendered).contains(neighboringNonSensitiveValue);
                    }
                    """),
                DoctorCommands.VERIFY_REDACTION_FINDING_PRESENT,
                List.of(
                    new FailureNote(
                        "@NotTraced is imported but the test still fails this check",
                        "the annotation was imported as a reminder and never actually applied to"
                            + " the field or parameter",
                        "annotate the sensitive field or parameter itself — an import alone"
                            + " redacts nothing (trap.unused-not-traced-import)")),
                null),
            new SkillStep(
                "Read the rendered trace before asserting",
                new StepBody.CommandStep(List.of(DoctorCommands.FIND_RENDERED_TRACES)),
                "the newest file this lists under build/narrativetrace was actually opened and"
                    + " read before writing any assertion against it",
                List.of(),
                null),
            new SkillStep(
                "Diff the structural trace on approval, not just values",
                new StepBody.CommandStep(List.of(DoctorCommands.NO_STALE_RECEIVED_FILE)),
                "any .received.nt this lists sitting beside a .approved.nt has been diffed against"
                    + " that baseline before the run is treated as clean",
                List.of(),
                "unstudied — eval cell pending")),
        List.of(
            new ReasonedRule(
                "Run the doctor this session before reporting a finding",
                "a stale report from an earlier session can no longer be true")),
        List.of(
            new ReasonedRule(
                "Never edit, generate, or delete a file",
                "the doctor is read-only by design — generation is a separate, later skill"),
            new ReasonedRule(
                "Never claim a finding passed without having run the doctor this session",
                "the report is the only source of truth, not memory of a past run")),
        CommandVocabulary.JAVA);
  }
}
