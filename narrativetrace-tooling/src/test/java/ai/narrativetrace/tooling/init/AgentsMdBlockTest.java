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

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The always-on pointer an agent reads on its next session. Its text is the catalogue's own, so the
 * cases here pin the frame around it: the markers, the detected facts, and which commands a project
 * is told to run.
 */
class AgentsMdBlockTest {

  private static ProjectState gradleProject() {
    return ProjectState.builder().gradleProject(true).build();
  }

  @Test
  void isOneWellFormedRegionStampedWithTheCarrier() {
    String block = AgentsMdBlock.render(Carriers.real(), gradleProject());

    MarkedBlock.Scan scan = MarkedBlock.scan(block);
    assertThat(scan.problems()).isEmpty();
    assertThat(scan.regions()).hasSize(1);
    assertThat(scan.regions().get(0).coordinate()).isEqualTo(Carriers.realCoordinate());
    assertThat(block)
        .startsWith("<!-- narrativetrace:start ")
        .endsWith("<!-- narrativetrace:end -->\n");
  }

  @Test
  void listsEverySkillWithItsCatalogueDescriptionVerbatimAndInOrder() {
    Carrier carrier = Carriers.real();

    String block = AgentsMdBlock.render(carrier, gradleProject());

    int previous = -1;
    for (SkillEntry skill : carrier.skills()) {
      String line = "- `" + skill.name() + "` — " + skill.description();
      assertThat(block).contains(line);
      int at = block.indexOf(line);
      assertThat(at).as("catalogue order is kept").isGreaterThan(previous);
      previous = at;
    }
  }

  @Test
  void namesWhereTracesLand(@TempDir Path dir) {
    ProjectState detected = ProjectState.builder().outputDirectory("out/traces").build();

    assertThat(AgentsMdBlock.render(Carriers.fake(dir, "a"), detected)).contains("`out/traces`");
    assertThat(AgentsMdBlock.render(Carriers.fake(dir, "a"), ProjectState.builder().build()))
        .contains("`build/narrativetrace`");
  }

  /**
   * The Gradle preview is {@code --diff}, not {@code --dry-run}: Gradle owns {@code --dry-run} as a
   * built-in that skips every task, so the section would otherwise tell a reader to run a command
   * that prints nothing at all. The CLI form below keeps {@code --dry-run}, where nothing shadows
   * it.
   */
  @Test
  void namesTheGradleTasksInAGradleProject(@TempDir Path dir) {
    String block = AgentsMdBlock.render(Carriers.fake(dir, "a"), gradleProject());

    assertThat(block)
        .contains("./gradlew narrativetraceDoctor")
        .contains("./gradlew narrativetraceInit --diff")
        .contains("./gradlew narrativetraceUninstall")
        .doesNotContain("narrativetrace doctor")
        .doesNotContain("--dry-run");
  }

  @Test
  void namesTheCommandLineVerbsWhereThereIsNoGradleBuild(@TempDir Path dir) {
    String block = AgentsMdBlock.render(Carriers.fake(dir, "a"), ProjectState.builder().build());

    assertThat(block)
        .contains("narrativetrace doctor")
        .contains("narrativetrace init --dry-run")
        .contains("narrativetrace uninstall")
        .doesNotContain("./gradlew");
  }

  @Test
  void pointsAtTheRuntimesDocumentationIndex(@TempDir Path dir) {
    assertThat(AgentsMdBlock.render(Carriers.fake(dir, "a"), gradleProject()))
        .contains("https://narrativetrace.ai/java/llms.txt");
  }

  @Test
  void carriesTheThreeRulesTheEvaluationsKeepTrippingOver(@TempDir Path dir) {
    String block = AgentsMdBlock.render(Carriers.fake(dir, "a"), gradleProject());

    assertThat(block).contains("-parameters").contains("redaction").contains(".received.nt");
  }

  @Test
  void saysNothingAboutAVersionBeyondTheMachineWrittenStamp(@TempDir Path dir) {
    String block = AgentsMdBlock.render(Carriers.fake(dir, "a"), gradleProject());
    String withoutTheMarker = block.substring(block.indexOf('\n'));

    assertThat(withoutTheMarker).doesNotContainPattern("\\d+\\.\\d+\\.\\d+");
  }

  @Test
  void refusesToRenderWithoutACarrierOrAProject(@TempDir Path dir) {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> AgentsMdBlock.render(null, gradleProject()))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> AgentsMdBlock.render(Carriers.fake(dir, "a"), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isTheSameTextForTheSameInputs(@TempDir Path dir) {
    Carrier carrier = Carriers.fake(dir, "a", "b");

    assertThat(AgentsMdBlock.render(carrier, gradleProject()))
        .isEqualTo(AgentsMdBlock.render(carrier, gradleProject()));
  }
}
