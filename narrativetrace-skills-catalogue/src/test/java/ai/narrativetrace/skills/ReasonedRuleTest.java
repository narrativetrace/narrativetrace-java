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

import org.junit.jupiter.api.Test;

class ReasonedRuleTest {

  @Test
  void holdsItsFields() {
    var rule = new ReasonedRule("never do X", "because Y");
    assertThat(rule.rule()).isEqualTo("never do X");
    assertThat(rule.reason()).isEqualTo("because Y");
  }

  @Test
  void rejectsBlankRuleOrReason() {
    assertThatThrownBy(() -> new ReasonedRule(" ", "reason"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReasonedRule("rule", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
