/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PolicyTest {

  @Test
  void blankPolicyNumberIsRejected() {
    assertThatThrownBy(() -> new Policy(" ", "Ana Suarez", 10_000_00L, 500_00L, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNumber");
  }

  @Test
  void blankHolderNameIsRejected() {
    assertThatThrownBy(() -> new Policy("POL-1001", "", 10_000_00L, 500_00L, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("holderName");
  }

  @Test
  void nonPositiveCoverageLimitIsRejected() {
    assertThatThrownBy(() -> new Policy("POL-1001", "Ana Suarez", 0L, 0L, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("coverageLimitCents");
  }

  @Test
  void negativeDeductibleIsRejected() {
    assertThatThrownBy(() -> new Policy("POL-1001", "Ana Suarez", 10_000_00L, -1L, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deductibleCents");
  }

  @Test
  void deductibleAboveTheCoverageLimitIsRejected() {
    assertThatThrownBy(() -> new Policy("POL-1001", "Ana Suarez", 500_00L, 10_000_00L, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deductibleCents");
  }
}
