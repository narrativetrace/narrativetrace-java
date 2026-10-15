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
package ai.narrativetrace.tooling.init;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.init.PointerReading.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

class PointerReadingTest {

  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:1.2.3";

  @Test
  void readsNothingForAProjectOnNoPlatformThatNeedsAPointer() {
    ProjectState state =
        ProjectState.builder()
            .detectedPlatform("antigravity")
            .detectedPlatform("claude-code")
            .build();

    assertThat(PointerReading.readAll(state)).isEmpty();
  }

  @Test
  void aDetectedPlatformWhosePointerIsMissingReadsAsAbsent() {
    List<PointerReading> readings =
        PointerReading.readAll(ProjectState.builder().detectedPlatform("cursor").build());

    assertThat(readings).hasSize(1);
    assertThat(readings.get(0).platformId()).isEqualTo("cursor");
    assertThat(readings.get(0).platformName()).isEqualTo("Cursor");
    assertThat(readings.get(0).path()).isEqualTo(".cursor/rules/narrativetrace.mdc");
    assertThat(readings.get(0).status()).isEqualTo(Status.ABSENT);
  }

  @Test
  void aStampedSectionReadsWithItsCoordinate() {
    ProjectState state =
        ProjectState.builder()
            .detectedPlatform("cursor")
            .pointerFile(".cursor/rules/narrativetrace.mdc", CursorRule.render("t", COORDINATE))
            .build();

    PointerReading reading = PointerReading.readAll(state).get(0);

    assertThat(reading.status()).isEqualTo(Status.SECTION);
    assertThat(reading.coordinate()).isEqualTo(COORDINATE);
  }

  @Test
  void anUnstampedSectionReadsAsASectionWithNoCoordinate() {
    ProjectState state =
        ProjectState.builder()
            .detectedPlatform("cursor")
            .pointerFile(".cursor/rules/narrativetrace.mdc", CursorRule.render("t", ""))
            .build();

    assertThat(PointerReading.readAll(state).get(0).coordinate()).isEmpty();
  }

  @Test
  void aFileWithoutExactlyOneSectionReadsAsForeign() {
    ProjectState none =
        ProjectState.builder()
            .detectedPlatform("replit")
            .pointerFile("replit.md", "# mine\n")
            .build();
    ProjectState two =
        ProjectState.builder()
            .detectedPlatform("replit")
            .pointerFile(
                "replit.md",
                "<!-- narrativetrace:start -->\n<!-- narrativetrace:end -->\n"
                    + "<!-- narrativetrace:start -->\n<!-- narrativetrace:end -->\n")
            .build();

    assertThat(PointerReading.readAll(none).get(0).status()).isEqualTo(Status.FOREIGN);
    assertThat(PointerReading.readAll(two).get(0).status()).isEqualTo(Status.FOREIGN);
  }

  @Test
  void aPointerBehindALinkReadsAsLinkedWhateverElseIsKnown() {
    ProjectState state =
        ProjectState.builder().detectedPlatform("replit").pointerBehindLink("replit.md").build();

    assertThat(PointerReading.readAll(state).get(0).status()).isEqualTo(Status.LINKED);
  }

  @Test
  void readsOneReadingPerDetectedPlatformInTableOrder() {
    ProjectState state =
        ProjectState.builder().detectedPlatform("replit").detectedPlatform("cursor").build();

    assertThat(PointerReading.readAll(state))
        .extracting(PointerReading::platformId)
        .containsExactly("cursor", "replit");
  }
}
