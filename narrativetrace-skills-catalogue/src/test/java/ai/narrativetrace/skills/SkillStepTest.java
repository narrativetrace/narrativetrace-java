/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillStepTest {

  private static final StepBody EMPTY_COMMANDS = new StepBody.CommandStep(List.of());

  @Test
  void rejectsBlankTitle() {
    assertThatThrownBy(() -> new SkillStep(" ", EMPTY_COMMANDS, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullBody() {
    assertThatThrownBy(() -> new SkillStep("title", null, null, List.of(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void convenienceConstructorDefaultsFailureAndFlag() {
    var step = new SkillStep("title", EMPTY_COMMANDS, "verify text");
    assertThat(step.failure()).isEmpty();
    assertThat(step.flag()).isNull();
  }

  @Test
  void verifyAndFlagOptionalsReflectNullness() {
    var withBoth = new SkillStep("t", EMPTY_COMMANDS, "v", List.of(), "flag");
    assertThat(withBoth.verifyOptional()).contains("v");
    assertThat(withBoth.flagOptional()).contains("flag");

    var withNeither = new SkillStep("t", EMPTY_COMMANDS, null, List.of(), null);
    assertThat(withNeither.verifyOptional()).isEmpty();
    assertThat(withNeither.flagOptional()).isEmpty();
  }

  @Test
  void hasReplayableVerifyIsTrueOnlyForAVocabularyCommand() {
    var replayable = new SkillStep("t", EMPTY_COMMANDS, "git status");
    assertThat(replayable.hasReplayableVerify()).isTrue();

    var prose = new SkillStep("t", EMPTY_COMMANDS, "the report is well-formed");
    assertThat(prose.hasReplayableVerify()).isFalse();

    var none = new SkillStep("t", EMPTY_COMMANDS, null);
    assertThat(none.hasReplayableVerify()).isFalse();
  }
}
