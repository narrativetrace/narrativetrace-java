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

import org.junit.jupiter.api.Test;

/** Drafting: rewrite what can be normalised, refuse what cannot, render only what passed. */
class FeedbackDrafterTest {

  @Test
  void aValueFreeReportIsDraftedAndItsBodyIsContainedInItsDraft() {
    FeedbackDraft drafted = FeedbackDrafter.draft(Reports.complete());

    assertThat(drafted).isInstanceOf(FeedbackDraft.Drafted.class);
    FeedbackDraft.Drafted ready = (FeedbackDraft.Drafted) drafted;
    assertThat(ready.draft()).contains(ready.body());
  }

  @Test
  void aHomeDirectoryIsRewrittenToATildeRatherThanRefused() {
    FeedbackDraft drafted =
        FeedbackDrafter.draft(
            Reports.with(
                b ->
                    b.narrative(
                        new ProblemNarrative(
                            "ran it in /Users/ada/work/orders",
                            "nothing appeared under /home/ada/work/orders/build",
                            "a trace file"))));

    assertThat(drafted).isInstanceOf(FeedbackDraft.Drafted.class);
    FeedbackDraft.Drafted ready = (FeedbackDraft.Drafted) drafted;
    assertThat(ready.report().narrative().did()).isEqualTo("ran it in ~/work/orders");
    assertThat(ready.body()).contains("~/work/orders/build").doesNotContain("/Users/ada");
  }

  @Test
  void aReportCarryingAValueIsRefusedAndTheRefusalNamesTheFieldAndTheRule() {
    FeedbackDraft drafted =
        FeedbackDrafter.draft(
            Reports.with(
                b ->
                    b.narrative(
                        new ProblemNarrative(
                            "ran the doctor",
                            "it rendered OrderService.placeOrder(customerId: \"C-1234\")",
                            "no value in the trace"))));

    assertThat(drafted).isInstanceOf(FeedbackDraft.Refused.class);
    FeedbackDraft.Refused refused = (FeedbackDraft.Refused) drafted;
    assertThat(refused.violations())
        .extracting(ValueFreeViolation::field, ValueFreeViolation::rule)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("happened", ValueFreeRule.RENDERED_CALL));
  }

  /**
   * The doctor's report is exempt from ONE rule ({@code vf.marker}, because its own check
   * vocabulary names the marker) and read by every other — so a credential that reached a doctor
   * message still refuses the whole draft.
   */
  @Test
  void aSecretInTheAttachedDoctorReportRefusesTheWholeDraft() {
    String leaking =
        Reports.DOCTOR_JSON.replace("\"exitCode\"", "\"password\": \"hunter2\", \"exitCode\"");
    assertThat(leaking)
        .as("the injection has to actually land, or this test asserts nothing")
        .contains("hunter2")
        .isNotEqualTo(Reports.DOCTOR_JSON);

    FeedbackDraft drafted =
        FeedbackDrafter.draft(Reports.with(b -> b.attachments(Attachments.of(leaking, ""))));

    assertThat(drafted).isInstanceOf(FeedbackDraft.Refused.class);
    assertThat(((FeedbackDraft.Refused) drafted).violations())
        .extracting(ValueFreeViolation::field)
        .containsOnly(ValueFreeCheck.DOCTOR_REPORT_FIELD);
  }

  /**
   * The regression this pair of changes exists for: a report drafted from the doctor's OWN output,
   * byte for byte, on a project that has NarrativeTrace installed. Before the field-scoped
   * exemption this refused — `trap.redaction-proof`'s message and fix both quote the marker — which
   * meant the verb could draft nothing at all, for anyone.
   */
  @Test
  void aReportCarryingTheDoctorsRealRedactionFindingDraftsCleanly() {
    assertThat(Reports.DOCTOR_JSON)
        .as("the helper has to carry the doctor's own text for this to be the regression")
        .contains("[REDACTED]");

    assertThat(FeedbackDrafter.draft(Reports.complete())).isInstanceOf(FeedbackDraft.Drafted.class);
  }

  @Test
  void aRefusalCarriesAtLeastOneViolationAndADraftRefusesANullReport() {
    assertThatIllegalArgumentException().isThrownBy(() -> FeedbackDrafter.draft(null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FeedbackDraft.Refused(java.util.List.of()));
  }

  @Test
  void theDraftShowsEveryGatheredFactAndTheBodyShowsNothingAboutTheProcess() {
    FeedbackDraft.Drafted ready = (FeedbackDraft.Drafted) FeedbackDrafter.draft(Reports.complete());

    assertThat(ready.body())
        .contains("doctor")
        .contains("ai.narrativetrace:narrativetrace-core:0.2.4")
        .contains("trap.redaction-proof")
        .contains("ran the doctor, applied the fix it printed, ran it again")
        .contains("OrderService.placeOrder(customerId, total)")
        .doesNotContain("Filing on GitHub is public");
    assertThat(ready.draft()).contains("Filing on GitHub is public");
  }

  @Test
  void aReportWithoutADoctorReportSaysSoInTheBodyInsteadOfOmittingTheSection() {
    FeedbackDraft.Drafted ready =
        (FeedbackDraft.Drafted)
            FeedbackDrafter.draft(
                Reports.with(
                    b ->
                        b.category(FeedbackCategory.PROMPT)
                            .attachments(
                                Attachments.withoutDoctorReport(
                                    "the build does not apply the plugin", ""))));

    assertThat(ready.body())
        .contains("No doctor report")
        .contains("the build does not apply the plugin")
        .contains("No structural trace");
  }
}
