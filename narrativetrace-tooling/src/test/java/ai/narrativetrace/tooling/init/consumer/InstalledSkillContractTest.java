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
package ai.narrativetrace.tooling.init.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.tooling.init.InstalledSkill;
import ai.narrativetrace.tooling.init.InstalledSkill.Presence;
import ai.narrativetrace.tooling.init.SkillFlavour;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The guards that keep an {@link InstalledSkill} from describing a project state that cannot exist.
 *
 * <p>INTENT: every planner decision is a function of this record alone, so a record that LIES — a
 * skill of ours with no coordinate, a linked presence naming no link, a link on a presence that is
 * not one — makes the planner's switch unanswerable rather than merely wrong. These are the
 * contract tested as the contract: each guard, with each input that should trip it.
 *
 * <p>From OUTSIDE the package on purpose. This record crosses the package boundary in real use —
 * the Gradle plugin reads project state from {@code ai.narrativetrace.gradle} — so the whole public
 * shape, the short constructor included, has to be reachable and has to refuse from out here too.
 *
 * <p><b>@llmNote</b> The link field and the two linked presences are ONE fact spelled two ways, and
 * the guard enforces the agreement in BOTH directions. Loosening either direction would let a
 * non-linked record carry a link target the planner then never looks at — which is precisely how a
 * write through a link gets planned.
 */
class InstalledSkillContractTest {

  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:1.0.0";

  private static final String TARGET = "../../.agents/skills/doctor";

  private static final String LINK_RULE = "a linked presence names what the link points at";

  // --- what must be present ---------------------------------------------------------------------

  @Test
  void refusesASkillWithNoFlavourOrNoPresence() {
    assertThatThrownBy(() -> skill(null, Presence.OURS, COORDINATE, "body\n", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("flavour");
    assertThatThrownBy(() -> skill(SkillFlavour.AGENTS, null, COORDINATE, "body\n", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("presence");
  }

  @Test
  void refusesANameThatIsNullOrBlank() {
    assertThatThrownBy(() -> named(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
    assertThatThrownBy(() -> named(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
    assertThatThrownBy(() -> named("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  /** {@code ""} is the documented "unknown", which is why null is the mistake worth catching. */
  @Test
  void refusesANullCoordinateBodyOrLinkRatherThanReadingItAsUnknown() {
    assertThatThrownBy(() -> skill(SkillFlavour.AGENTS, Presence.FOREIGN, null, "theirs\n", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("never null");
    assertThatThrownBy(() -> skill(SkillFlavour.AGENTS, Presence.FOREIGN, "", null, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("never null");
    assertThatThrownBy(() -> skill(SkillFlavour.AGENTS, Presence.FOREIGN, "", "theirs\n", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("never null");
  }

  // --- OURS carries its coordinate --------------------------------------------------------------

  /**
   * A page is ours only because a provenance line said so, and that line names a carrier. A
   * coordinate-less OURS would leave the refresh planner's staleness test comparing against nothing
   * — every build would then either refresh forever or never.
   */
  @Test
  void refusesASkillOfOursThatNamesNoCarrier() {
    assertThatThrownBy(() -> skill(SkillFlavour.AGENTS, Presence.OURS, "", "body\n", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("carries its coordinate");
    assertThatThrownBy(() -> skill(SkillFlavour.AGENTS, Presence.OURS, "   ", "body\n", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("carries its coordinate");
  }

  @Test
  void acceptsAnEmptyCoordinateForEveryPresenceButOurs() {
    assertThat(skill(SkillFlavour.AGENTS, Presence.FOREIGN, "", "theirs\n", "").coordinate())
        .isEmpty();
    assertThat(skill(SkillFlavour.AGENTS, Presence.NOT_A_DIRECTORY, "", "", "").coordinate())
        .isEmpty();
  }

  // --- a link and a linked presence are one fact ------------------------------------------------

  @Test
  void refusesALinkedPresenceThatNamesNoTarget() {
    assertThatThrownBy(() -> skill(SkillFlavour.CLAUDE, Presence.LINKED_DIRECTORY, "", "b\n", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(LINK_RULE);
    assertThatThrownBy(() -> skill(SkillFlavour.CLAUDE, Presence.LINKED_PAGE, "", "b\n", "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(LINK_RULE);
  }

  @Test
  void refusesATargetOnAPresenceThatIsNotALink() {
    assertThatThrownBy(() -> skill(SkillFlavour.AGENTS, Presence.OURS, COORDINATE, "b\n", TARGET))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(LINK_RULE);
    assertThatThrownBy(() -> skill(SkillFlavour.AGENTS, Presence.FOREIGN, "", "theirs\n", TARGET))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(LINK_RULE);
  }

  /** The short constructor is the unlinked shape, so it has to produce an unlinked record. */
  @Test
  void theShortConstructorNamesNoLink() {
    InstalledSkill skill =
        new InstalledSkill(SkillFlavour.AGENTS, "doctor", Presence.FOREIGN, "", "theirs\n");

    assertThat(skill.link()).isEmpty();
    assertThatThrownBy(skill::linkedAt).isInstanceOf(IllegalStateException.class);
  }

  // --- where the link sits ----------------------------------------------------------------------

  /**
   * The two linked presences differ in exactly one thing: whether the link is the directory or the
   * page inside it. That path is what the executor DELETES, so reading it off the wrong presence
   * deletes the wrong thing.
   */
  @Test
  void aLinkedDirectoryLinksAtTheDirectoryAndALinkedPageAtThePage() {
    assertThat(linked(Presence.LINKED_DIRECTORY).linkedAt())
        .isEqualTo(Path.of(".claude/skills/doctor"));
    assertThat(linked(Presence.LINKED_PAGE).linkedAt())
        .isEqualTo(Path.of(".claude/skills/doctor/SKILL.md"));
  }

  @Test
  void askingWhereNothingLinksIsACallerMistakeNotAStrangeProject() {
    InstalledSkill ours =
        new InstalledSkill(SkillFlavour.AGENTS, "doctor", Presence.OURS, COORDINATE, "body\n");

    assertThatThrownBy(ours::linkedAt)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(".agents/skills/doctor");
  }

  @Test
  void namesItsDirectoryAndItsPageUnderItsOwnFlavoursInstallRoot() {
    InstalledSkill vendor =
        new InstalledSkill(SkillFlavour.CLAUDE, "doctor", Presence.OURS, COORDINATE, "body\n");

    assertThat(vendor.directory()).isEqualTo(Path.of(".claude/skills/doctor"));
    assertThat(vendor.page()).isEqualTo(Path.of(".claude/skills/doctor/SKILL.md"));
  }

  // --- fixtures ---------------------------------------------------------------------------------

  private static InstalledSkill skill(
      SkillFlavour flavour, Presence presence, String coordinate, String body, String link) {
    return new InstalledSkill(flavour, "doctor", presence, coordinate, body, link);
  }

  private static InstalledSkill named(String name) {
    return new InstalledSkill(SkillFlavour.AGENTS, name, Presence.FOREIGN, "", "theirs\n", "");
  }

  private static InstalledSkill linked(Presence presence) {
    return new InstalledSkill(SkillFlavour.CLAUDE, "doctor", presence, "", "body\n", TARGET);
  }
}
