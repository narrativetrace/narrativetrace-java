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

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import org.junit.jupiter.api.Test;

/** What the verb can learn about a project without being told: the install, and one trace. */
class FeedbackGathererTest {

  private static final String GOOD_TRACE =
      "scenario: Order is placed\n\n- OrderService.placeOrder(customerId, total)\n";

  private static final String RENDERED_TRACE =
      "scenario: Order is placed\n\n- OrderService.placeOrder(customerId: \"C-1\")\n";

  @Test
  void readsEveryNarrativeTraceCoordinateTheBuildDeclares() {
    DoctorSnapshot snapshot =
        DoctorSnapshot.healthy().toBuilder()
            .addDependencyCoordinate("ai.narrativetrace:narrativetrace-proxy:0.2.4")
            .build();

    assertThat(FeedbackGatherer.installCoordinate(snapshot))
        .isEqualTo(
            "ai.narrativetrace:narrativetrace-junit5:0.2.2,"
                + " ai.narrativetrace:narrativetrace-proxy:0.2.4");
  }

  @Test
  void saysSoRatherThanGuessingWhenTheBuildDeclaresNoneOfOurs() {
    DoctorSnapshot snapshot =
        DoctorSnapshot.healthy().toBuilder().clearDependencyCoordinates().build();

    assertThat(FeedbackGatherer.installCoordinate(snapshot))
        .isEqualTo(FeedbackGatherer.INSTALL_UNKNOWN);
  }

  @Test
  void choosesAStructuralTraceThatIsBothStructuralAndValueFree() {
    DoctorSnapshot snapshot =
        DoctorSnapshot.healthy().toBuilder()
            .putOutputFile("build/narrativetrace/structural/A/a.nt", GOOD_TRACE)
            .build();

    FeedbackGatherer.TraceChoice chosen = FeedbackGatherer.chooseTrace(snapshot, "");

    assertThat(chosen.content()).isEqualTo(GOOD_TRACE);
    assertThat(chosen.reason()).isEmpty();
  }

  @Test
  void skipsAFileThatIsNotAStructuralTraceAtAllAndSaysSo() {
    DoctorSnapshot snapshot =
        DoctorSnapshot.healthy().toBuilder()
            .putOutputFile("build/narrativetrace/structural/A/a.nt", RENDERED_TRACE)
            .build();

    FeedbackGatherer.TraceChoice chosen = FeedbackGatherer.chooseTrace(snapshot, "");

    assertThat(chosen.content()).isEmpty();
    assertThat(chosen.reason())
        .contains("build/narrativetrace/structural/A/a.nt")
        .contains("is not a structural trace");
  }

  /**
   * The second half of defence in depth: a file whose GRAMMAR is a structural trace and whose text
   * still breaks a rule. A scenario header is a humanised test name, so whatever a test method was
   * called ends up in it.
   */
  @Test
  void skipsAGrammaticalTraceWhoseTextStillBreaksARuleAndNamesTheRule() {
    DoctorSnapshot snapshot =
        DoctorSnapshot.healthy().toBuilder()
            .putOutputFile(
                "build/narrativetrace/structural/A/a.nt",
                "scenario: Mail reaches ada@example.com\n\n- Mailer.send(recipient)\n")
            .build();

    FeedbackGatherer.TraceChoice chosen = FeedbackGatherer.chooseTrace(snapshot, "");

    assertThat(chosen.content()).isEmpty();
    assertThat(chosen.reason()).contains("breaks vf.email");
  }

  @Test
  void prefersTheTraceTheUserNamedAndSaysSoWhenThatOneCannotBeUsed() {
    DoctorSnapshot snapshot =
        DoctorSnapshot.healthy().toBuilder()
            .putOutputFile("build/narrativetrace/structural/A/a.nt", GOOD_TRACE)
            .putOutputFile("build/narrativetrace/structural/B/b.nt", RENDERED_TRACE)
            .build();

    assertThat(FeedbackGatherer.chooseTrace(snapshot, "B/b.nt").content()).isEmpty();
    assertThat(FeedbackGatherer.chooseTrace(snapshot, "B/b.nt").reason()).contains("B/b.nt");
    assertThat(FeedbackGatherer.chooseTrace(snapshot, "A/a.nt").content()).isEqualTo(GOOD_TRACE);
  }

  @Test
  void saysSoWhenTheNamedTraceIsNotThere() {
    assertThat(FeedbackGatherer.chooseTrace(DoctorSnapshot.healthy(), "nowhere.nt").reason())
        .contains("nowhere.nt");
  }

  @Test
  void choosesDeterministicallyWhenSeveralTracesWouldDo() {
    DoctorSnapshot snapshot =
        DoctorSnapshot.healthy().toBuilder()
            .putOutputFile("build/narrativetrace/structural/Z/z.nt", GOOD_TRACE + "\n")
            .putOutputFile("build/narrativetrace/structural/A/a.nt", GOOD_TRACE)
            .build();

    assertThat(FeedbackGatherer.chooseTrace(snapshot, "").content()).isEqualTo(GOOD_TRACE);
  }

  @Test
  void attachesTheDoctorsJsonWhenThereIsOneAndSaysWhyWhenThereIsNot() {
    DoctorSnapshot snapshot = DoctorSnapshot.healthy();

    assertThat(FeedbackGatherer.attachments(snapshot, Reports.DOCTOR_JSON, "").hasDoctorReport())
        .isTrue();
    Attachments without = FeedbackGatherer.attachments(snapshot, "", "");
    assertThat(without.hasDoctorReport()).isFalse();
    assertThat(without.doctorUnavailable()).contains("doctor-report.json");
  }
}
