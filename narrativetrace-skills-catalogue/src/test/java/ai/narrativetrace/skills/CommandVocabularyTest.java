/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandVocabularyTest {

  @Test
  void javaVocabularyIsGradleWrapperGitAndFindOnly() {
    assertThat(CommandVocabulary.JAVA).containsExactly("./gradlew", "git", "find");
  }

  @Test
  void aVocabularyCommandRendersAsABashToolPatternMatchingItAndItsArguments() {
    assertThat(CommandVocabulary.claudeToolPattern("git")).isEqualTo("Bash(git *)");
  }

  @Test
  void firstTokenReadsUpToTheFirstSpace() {
    assertThat(CommandVocabulary.firstToken("./gradlew :sixty-seconds:build"))
        .isEqualTo("./gradlew");
    assertThat(CommandVocabulary.firstToken("git")).isEqualTo("git");
  }

  @Test
  void firstTokenHandlesBlankAndNull() {
    assertThat(CommandVocabulary.firstToken("")).isEmpty();
    assertThat(CommandVocabulary.firstToken(null)).isEmpty();
    assertThat(CommandVocabulary.firstToken("  ")).isEmpty();
  }
}
