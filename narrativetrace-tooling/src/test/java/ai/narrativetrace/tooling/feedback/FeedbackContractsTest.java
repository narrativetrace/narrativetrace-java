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

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The guard clauses, the small accessors and the printed words — the parts of the feedback surface
 * a happy-path test walks past.
 *
 * <p>A guard clause is production behaviour, not a comment: it is what turns a caller's mistake
 * into a message instead of a report filed with a field missing. Each one is asserted here with the
 * input that trips it.
 */
class FeedbackContractsTest {

  // --- guards that refuse null where an absent value is "" ---------------------------------------

  @Test
  void everyTextReaderRefusesNullRatherThanTreatingItAsEmpty() {
    assertThatIllegalArgumentException().isThrownBy(() -> HomePaths.toTilde(null));
    assertThatIllegalArgumentException().isThrownBy(() -> StructuralTrace.looksStructural(null));
    assertThatIllegalArgumentException().isThrownBy(() -> ValueFreeCheck.rulesRefusing(null));
    assertThatIllegalArgumentException().isThrownBy(() -> ValueFreeRule.EMAIL.rejects(null));
  }

  @Test
  void aViolationNamesBothAFieldAndARule() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ValueFreeViolation("  ", ValueFreeRule.EMAIL));
    assertThatIllegalArgumentException().isThrownBy(() -> new ValueFreeViolation("did", null));
  }

  @Test
  void anAttachmentSetRefusesNullWhereAnAbsentAttachmentIsEmpty() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Attachments(null, "why", ""));
  }

  @Test
  void aTraceChoiceIsEitherContentOrAReason() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FeedbackGatherer.TraceChoice("content", "and a reason"));
    assertThatIllegalArgumentException().isThrownBy(() -> new FeedbackGatherer.TraceChoice("", ""));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FeedbackGatherer.TraceChoice(null, ""));
  }

  @Test
  void aDraftedResultRefusesToExistWithoutItsReportOrWithADraftThatHidesTheBody() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FeedbackDraft.Drafted(Reports.complete(), null, "body"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FeedbackDraft.Drafted(Reports.complete(), "a summary", "the body"))
        .withMessageContaining("byte for byte");
  }

  @Test
  void aReportRefusesAMissingCategoryNarrativeAgentOrAttachmentSet() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Reports.with(b -> b.narrative(null)))
        .withMessageContaining("narrative");
    assertThatIllegalArgumentException().isThrownBy(() -> Reports.with(b -> b.agent(null)));
    assertThatIllegalArgumentException().isThrownBy(() -> Reports.with(b -> b.attachments(null)));
  }

  // --- what the printed words actually say -------------------------------------------------------

  @Test
  void aRefusalPrintsOneLinePerViolationUnderACountedHeader() {
    FeedbackDraft.Refused refused =
        new FeedbackDraft.Refused(
            List.of(
                new ValueFreeViolation("happened", ValueFreeRule.RENDERED_CALL),
                new ValueFreeViolation("trace", ValueFreeRule.EMAIL)));

    assertThat(refused.describe())
        .contains("2 rule(s) refused it")
        .contains("  - happened: vf.rendered-call")
        .contains("  - trace: vf.email")
        .endsWith("\n");
  }

  @Test
  void anUnknownAgentReadsAsNotReportedRatherThanAsAnEmptyLine() {
    FeedbackDraft.Drafted drafted =
        (FeedbackDraft.Drafted)
            FeedbackDrafter.draft(Reports.with(b -> b.agent(AgentIdentity.unknown())));

    assertThat(drafted.body()).contains("- agent: not reported");
  }

  @Test
  void anAgentThatNamedOnlyItsProductIsPrintedWithoutATrailingSeparator() {
    assertThat(new AgentIdentity("example-cli", "").describe()).isEqualTo("example-cli");
    assertThat(AgentIdentity.unknown().describe()).isEmpty();
  }

  // --- the closed sets --------------------------------------------------------------------------

  @Test
  void aCategoryIsLookedUpByIdAndAnUnknownIdIsRefusedRatherThanDefaulted() {
    assertThat(FeedbackCategory.ofId("library")).isEqualTo(FeedbackCategory.LIBRARY);
    assertThat(FeedbackCategory.ofId("prompt")).isEqualTo(FeedbackCategory.PROMPT);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FeedbackCategory.ofId("everything"))
        .withMessageContaining("prompt, skill, doctor, library");
  }

  @Test
  void aReportRoundTripsThroughItsOwnBuilder() {
    FeedbackReport original = Reports.complete();

    assertThat(original.toBuilder().build()).isEqualTo(original);
    assertThat(original.invariant()).isTrue();
  }

  @Test
  void aNonCanonicalRuntimeBreaksTheInvariantEvenThoughTheConstructorAcceptsIt() {
    assertThat(Reports.with(b -> b.runtime("Java SE")).invariant())
        .as("a runtime becomes a label, so upper case or a space there is a broken label")
        .isFalse();
  }

  // --- the budget and the escaping --------------------------------------------------------------

  @Test
  void theUrlBudgetIsCheckableOnItsOwn() {
    assertThat(IssueFormUrl.staysUnderBudget("x".repeat(IssueFormUrl.MAX_LENGTH))).isTrue();
    assertThat(IssueFormUrl.staysUnderBudget("x".repeat(IssueFormUrl.MAX_LENGTH + 1))).isFalse();
  }

  @Test
  void anEnvelopeEscapesAControlCharacterRatherThanEmittingItRaw() {
    String smuggled = Character.toString(1);

    String json = FeedbackJson.gh("gh issue create" + smuggled);

    assertThat(json).doesNotContain(smuggled).contains("u0001");
  }

  @Test
  void aGatheredAttachmentSetWithNoTraceSaysSoInTheUnavailableReason() {
    Attachments attachments =
        FeedbackGatherer.attachments(
            ai.narrativetrace.tooling.doctor.DoctorSnapshot.healthy(), "", "");

    assertThat(attachments.hasStructuralTrace()).isFalse();
    assertThat(attachments.doctorUnavailable()).contains("run the doctor first");
  }
}
