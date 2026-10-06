/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Boundary coverage for {@link SkillsCarrier#resolve}'s fallback reason: a throwable whose message
 * is present but BLANK is treated the same as one with no message at all — {@code isBlank()} is the
 * guard {@code SkillsCarrierTest}'s null-message case does not exercise.
 */
class SkillsCarrierAdversarialM3Test {

  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:7.7.7";

  @Test
  void reportsAFailureWhoseMessageIsBlankRatherThanNullByItsType() {
    var resolution =
        SkillsCarrier.resolve(
            () -> {
              throw new IllegalStateException("   ");
            },
            COORDINATE);

    assertThat(resolution.carrier()).isNull();
    assertThat(resolution.failure()).contains("IllegalStateException");
  }
}
