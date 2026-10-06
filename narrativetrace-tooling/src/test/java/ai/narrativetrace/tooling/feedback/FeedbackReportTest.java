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
package ai.narrativetrace.tooling.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import ai.narrativetrace.tooling.init.catdd.ContractVerifiable;
import ai.narrativetrace.tooling.init.catdd.InvariantCheckExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The gathered facts of one problem report, and the rules about which of them may be absent. */
@ExtendWith(InvariantCheckExtension.class)
class FeedbackReportTest implements ContractVerifiable<FeedbackReport> {

  private FeedbackReport report;

  @BeforeEach
  void setUp() {
    report = Reports.complete();
  }

  @Override
  public FeedbackReport subject() {
    return report;
  }

  @Override
  public boolean checkInvariant() {
    return report == null || report.invariant();
  }

  // --- what a report must carry ------------------------------------------------------------------

  @Test
  void everyFieldThatWillBeFiledIsOfferedToTheGate() {
    assertThat(report.fields().keySet())
        .containsExactly(
            "install", "step", "did", "happened", "expected", "agent", "doctor report", "trace");
  }

  @Test
  void refusesABlankRuntimeCategoryInstallStepOrLanguage() {
    assertThatIllegalArgumentException().isThrownBy(() -> Reports.with(b -> b.runtime("  ")));
    assertThatIllegalArgumentException().isThrownBy(() -> Reports.with(b -> b.category(null)));
    assertThatIllegalArgumentException().isThrownBy(() -> Reports.with(b -> b.install("")));
    assertThatIllegalArgumentException().isThrownBy(() -> Reports.with(b -> b.step("")));
    assertThatIllegalArgumentException().isThrownBy(() -> Reports.with(b -> b.language("")));
  }

  @Test
  void refusesABlankNarrativeBecauseAReportWithoutOneSaysNothing() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ProblemNarrative("", "it failed", "it to pass"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ProblemNarrative("I ran it", "  ", "it to pass"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ProblemNarrative("I ran it", "it failed", null));
  }

  // --- the doctor attachment rule (Q3) -----------------------------------------------------------

  @Test
  void theDoctorAndSkillCategoriesRequireADoctorReport() {
    assertThat(FeedbackCategory.DOCTOR.requiresDoctorReport()).isTrue();
    assertThat(FeedbackCategory.SKILL.requiresDoctorReport()).isTrue();
    assertThat(FeedbackCategory.PROMPT.requiresDoctorReport()).isFalse();
    assertThat(FeedbackCategory.LIBRARY.requiresDoctorReport()).isFalse();
  }

  @Test
  void aPromptReportMayCarryNoDoctorReportAsLongAsItSaysWhy() {
    FeedbackReport withoutDoctor =
        Reports.with(
            b ->
                b.category(FeedbackCategory.PROMPT)
                    .attachments(
                        Attachments.withoutDoctorReport(
                            "the build does not apply the plugin", "")));

    assertThat(withoutDoctor.attachments().doctorReport()).isEmpty();
    assertThat(withoutDoctor.attachments().doctorUnavailable())
        .isEqualTo("the build does not apply the plugin");
    assertThat(withoutDoctor.invariant()).isTrue();
  }

  @Test
  void aDoctorReportWithoutTheDoctorsOwnJsonIsRefused() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                Reports.with(
                    b ->
                        b.category(FeedbackCategory.DOCTOR)
                            .attachments(Attachments.withoutDoctorReport("I did not run it", ""))))
        .withMessageContaining("doctor");
  }

  @Test
  void anAttachmentSetNamesEitherTheReportOrWhyItIsMissing_neverBothAndNeverNeither() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Attachments("{\"findings\":[]}", "and also unavailable", ""));
    assertThatIllegalArgumentException().isThrownBy(() -> new Attachments("", "", ""));
  }

  @Test
  void anUnknownAgentIsRepresentedByEmptyStringsRatherThanByNull() {
    assertThat(AgentIdentity.unknown().product()).isEmpty();
    assertThat(AgentIdentity.unknown().model()).isEmpty();
    assertThatIllegalArgumentException().isThrownBy(() -> new AgentIdentity(null, ""));
  }
}
