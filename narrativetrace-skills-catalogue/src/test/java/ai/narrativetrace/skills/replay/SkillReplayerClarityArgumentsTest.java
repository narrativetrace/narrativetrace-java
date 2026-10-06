/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.replay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link SkillReplayer#clarityGradleArguments} in isolation: no Gradle daemon, no fixture — just
 * the argument list {@link SkillReplayer#clarityScan}/{@link SkillReplayer#clarityCheck} hand to
 * {@link org.gradle.testkit.runner.GradleRunner}. {@code TierA2ReplayTest} is the integration proof
 * that the real nested build accepts these arguments; this pins the pure decision so a regression
 * shows up as a fast unit failure instead of only a slow replay one.
 */
class SkillReplayerClarityArgumentsTest {

  @Test
  void omitsTheTestRepoPropertyWhenNoPathIsGiven() {
    assertThat(SkillReplayer.clarityGradleArguments("clarityScan", null))
        .containsExactly("clean", "clarityScan");
  }

  @Test
  void omitsTheTestRepoPropertyWhenThePathIsBlank() {
    assertThat(SkillReplayer.clarityGradleArguments("clarityCheck", "   "))
        .containsExactly("clean", "clarityCheck");
  }

  @Test
  void appendsTheTestRepoPropertyWhenAPathIsGiven() {
    assertThat(SkillReplayer.clarityGradleArguments("clarityScan", "/repo/build/test-repo"))
        .containsExactly(
            "clean", "clarityScan", "-PnarrativetraceTestMavenRepo=/repo/build/test-repo");
  }
}
