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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.tooling.init.InstalledSkill.Presence;
import org.junit.jupiter.api.Test;

/**
 * The snapshot a test builds by hand — the reason every planner case below is a unit test with no
 * disk at all. These cases cover the defaults, the lookup and the contract guards.
 */
class ProjectStateTest {

  @Test
  void anUntouchedProjectIsTheDefault() {
    ProjectState state = ProjectState.builder().build();

    assertThat(state.agentsMd()).isEmpty();
    assertThat(state.claudeMd()).isEmpty();
    assertThat(state.hasClaudeDirectory()).isFalse();
    assertThat(state.gradleProject()).isFalse();
    assertThat(state.installedSkills()).isEmpty();
    assertThat(state.markedRuleFiles()).isEmpty();
    assertThat(state.outputDirectory()).isEqualTo("build/narrativetrace");
    assertThat(state.invariant()).isTrue();
  }

  @Test
  void keepsEverythingItWasGiven() {
    ProjectState state =
        ProjectState.builder()
            .agentsMd("# A\n")
            .claudeMd("# C\n")
            .claudeDirectory(true)
            .gradleProject(true)
            .outputDirectory("out")
            .markedRuleFile(".cursorrules", "rules")
            .addInstalledSkill(ours("doctor"))
            .build();

    assertThat(state.agentsMd()).hasValue("# A\n");
    assertThat(state.claudeMd()).hasValue("# C\n");
    assertThat(state.hasClaudeDirectory()).isTrue();
    assertThat(state.gradleProject()).isTrue();
    assertThat(state.outputDirectory()).isEqualTo("out");
    assertThat(state.markedRuleFiles()).containsEntry(".cursorrules", "rules");
    assertThat(state.installedSkill(SkillFlavour.AGENTS, "doctor")).isPresent();
    assertThat(state.installedSkill(SkillFlavour.CLAUDE, "doctor")).isEmpty();
  }

  @Test
  void keepsItsCollectionsImmutable() {
    ProjectState state = ProjectState.builder().addInstalledSkill(ours("doctor")).build();

    assertThatThrownBy(() -> state.installedSkills().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.markedRuleFiles().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void refusesToDescribeTheSamePathTwice() {
    ProjectState.Builder builder =
        ProjectState.builder().addInstalledSkill(ours("doctor")).addInstalledSkill(ours("doctor"));

    assertThatThrownBy(builder::build).isInstanceOf(AssertionError.class);
  }

  @Test
  void refusesAnInstalledSkillThatContradictsItself() {
    assertThatThrownBy(
            () -> new InstalledSkill(SkillFlavour.AGENTS, "d", Presence.OURS, "", "body"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("carries its coordinate");
    assertThatThrownBy(() -> new InstalledSkill(null, "d", Presence.FOREIGN, "", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("flavour and a presence");
    assertThatThrownBy(() -> new InstalledSkill(SkillFlavour.AGENTS, " ", Presence.FOREIGN, "", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be blank");
    assertThatThrownBy(
            () -> new InstalledSkill(SkillFlavour.AGENTS, "d", Presence.FOREIGN, null, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("never null");
  }

  /**
   * A snapshot that lists a skill under a flavour whose install root is a link contradicts itself:
   * whatever was found there was found THROUGH the link, so it describes somebody else's tree.
   */
  @Test
  void refusesToListASkillUnderALinkedInstallRoot() {
    ProjectState.Builder builder =
        ProjectState.builder()
            .linkedInstallRoot(SkillFlavour.AGENTS, "/elsewhere")
            .addInstalledSkill(ours("doctor"));

    assertThatThrownBy(builder::build).isInstanceOf(AssertionError.class);
  }

  @Test
  void refusesAnInstalledSkillWhoseLinkContradictsItsPresence() {
    assertThatThrownBy(
            () ->
                new InstalledSkill(
                    SkillFlavour.AGENTS, "d", Presence.FOREIGN, "", "body", "../elsewhere"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("linked presence");
    assertThatThrownBy(
            () ->
                new InstalledSkill(
                    SkillFlavour.AGENTS, "d", Presence.LINKED_DIRECTORY, "", "body", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("linked presence");
    assertThatThrownBy(
            () -> new InstalledSkill(SkillFlavour.AGENTS, "d", Presence.FOREIGN, "", "b", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("never null");
  }

  /** Asking where a link sits when nothing is a link is a caller's mistake, not a project's. */
  @Test
  void refusesToNameALinkThatIsNotThere() {
    assertThatThrownBy(() -> ours("doctor").linkedAt())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(".agents/skills/doctor");
  }

  private static InstalledSkill ours(String name) {
    return new InstalledSkill(
        SkillFlavour.AGENTS,
        name,
        Presence.OURS,
        "ai.narrativetrace:narrativetrace-skills:1.0.0",
        "body");
  }
}
