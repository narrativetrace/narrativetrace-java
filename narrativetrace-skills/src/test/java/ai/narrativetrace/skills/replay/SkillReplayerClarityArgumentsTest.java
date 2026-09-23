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
