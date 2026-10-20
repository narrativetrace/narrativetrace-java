/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * B-99: {@code ledger/runs.jsonl} carried 44 rows of {@code "model":"haiku"} beside 69 of {@code
 * "model":"claude-haiku-5-5"} — the same model, recorded two ways, because the runner passed {@code
 * --model} straight through unresolved to both the agent command and the ledger row. {@link
 * ModelAlias#resolve} is the one table that now sits between the raw flag and both consumers.
 */
class ModelAliasTest {

  @Test
  void resolvesTheKnownClaudeShorthandToItsFullId() {
    assertThat(ModelAlias.resolve(Platform.CLAUDE, "haiku")).isEqualTo("claude-haiku-5-5");
  }

  @Test
  void leavesAnAlreadyFullClaudeIdUnchanged() {
    assertThat(ModelAlias.resolve(Platform.CLAUDE, "claude-haiku-5-5"))
        .isEqualTo("claude-haiku-5-5");
  }

  @Test
  void refusesAnUnknownClaudeShorthandNamingTheAcceptedIds() {
    assertThatThrownBy(() -> ModelAlias.resolve(Platform.CLAUDE, "sonnet"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sonnet")
        .hasMessageContaining("haiku (claude-haiku-5-5)");
  }

  @Test
  void leavesEveryNonClaudePlatformsModelUnchangedEvenIfItWouldLookLikeAClaudeShorthand() {
    assertThat(ModelAlias.resolve(Platform.CODEX, "mini")).isEqualTo("mini");
    assertThat(ModelAlias.resolve(Platform.ANTIGRAVITY, "gemini-3.8-flash-low"))
        .isEqualTo("gemini-3.8-flash-low");
    assertThat(ModelAlias.resolve(Platform.CODEX, "haiku")).isEqualTo("haiku");
  }
}
