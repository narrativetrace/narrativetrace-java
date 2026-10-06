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

/** The {@code gh} line the verb PRINTS and never runs. */
class GhCommandLineTest {

  private static final String BODY_FILE = "build/narrativetrace/feedback/feedback-body.md";

  @Test
  void printsTheWholeInvocationIncludingTheBodyFileAndEveryLabel() {
    String line = GhCommandLine.of(Reports.complete(), BODY_FILE);

    assertThat(line)
        .startsWith("gh issue create")
        .contains("--repo narrativetrace/narrativetrace-java")
        .contains("--template narrativetrace-report.yml")
        .contains("--title 'doctor: trap.redaction-proof'")
        .contains("--body-file " + BODY_FILE)
        .contains("--label from-agent")
        .contains("--label runtime:java")
        .contains("--label category:doctor")
        .contains("--label lang:en");
  }

  @Test
  void quotesTheTitleSoAHostileStepCannotBecomeASecondCommand() {
    String line = GhCommandLine.of(Reports.with(b -> b.step("x'; rm -rf /tmp; echo '")), BODY_FILE);

    assertThat(line).contains("--title 'doctor: x'\\''; rm -rf /tmp; echo '\\'''");
    assertThat(line.indexOf("--body-file")).isGreaterThan(line.indexOf("--title"));
  }

  @Test
  void isOneLineBecauseAPrintedCommandWithANewlineInItIsTwoCommands() {
    String line = GhCommandLine.of(Reports.with(b -> b.step("first\nsecond")), BODY_FILE);

    assertThat(line).doesNotContain("\n");
  }

  @Test
  void refusesABlankBodyFileAndAReportFromAnotherRuntime() {
    assertThatIllegalArgumentException().isThrownBy(() -> GhCommandLine.of(Reports.complete(), ""));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GhCommandLine.of(Reports.with(b -> b.runtime("dotnet")), BODY_FILE));
  }
}
