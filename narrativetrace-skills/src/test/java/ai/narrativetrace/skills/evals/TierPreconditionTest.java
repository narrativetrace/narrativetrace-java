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
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TierPreconditionTest {

  @Test
  void commandRunsBothTierAAndTierA2ViaTheClosedVocabulary() {
    List<String> command = TierPrecondition.command();
    assertThat(command.get(0)).isEqualTo("./gradlew");
    assertThat(command).contains(":narrativetrace-skills:test");
    assertThat(command).contains("ai.narrativetrace.skills.lint.*");
    assertThat(command).contains("ai.narrativetrace.skills.replay.TierA2ReplayTest");
  }

  @Test
  void assertDeterministicTiersGreenPassesThroughASucceedingFakeCommand() {
    assertThatCode(
            () ->
                TierPrecondition.assertDeterministicTiersGreen(
                    Path.of("."),
                    (cmd, cwd) -> {
                      // succeeds — no exception
                    }))
        .doesNotThrowAnyException();
  }

  @Test
  void assertDeterministicTiersGreenWrapsAFailingFakeCommandWithAQuotaPreservingMessage() {
    assertThatThrownBy(
            () ->
                TierPrecondition.assertDeterministicTiersGreen(
                    Path.of("."),
                    (cmd, cwd) -> {
                      throw new IOException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Tier A lints / Tier A2 replay are not green at HEAD")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void assertDeterministicTiersGreenWrapsAnInterruptedFakeCommandAndRestoresTheInterruptFlag() {
    assertThatThrownBy(
            () ->
                TierPrecondition.assertDeterministicTiersGreen(
                    Path.of("."),
                    (cmd, cwd) -> {
                      throw new InterruptedException("interrupted");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(InterruptedException.class);
    assertThat(Thread.interrupted()).isTrue(); // also clears the flag for the next test
  }

  @Test
  void runViaProcessBuilderSucceedsOnZeroExitAndThrowsOtherwise() {
    // Real, trivial subprocesses (never ./gradlew — this never runs a real Tier B trial).
    assertThatCode(() -> runReal(List.of("true"))).doesNotThrowAnyException();
    assertThatThrownBy(() -> runReal(List.of("false"))).isInstanceOf(IOException.class);
  }

  private static void runReal(List<String> command) throws IOException, InterruptedException {
    TierPrecondition.runViaProcessBuilder(command, Path.of("."));
  }
}
