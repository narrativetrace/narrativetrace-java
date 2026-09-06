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

class ClaimTest {

  @Test
  void blankClaimIdIsRejected() {
    assertThatThrownBy(() -> new Claim("  ", "POL-1001", 100_00L, "hail damage"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimId");
  }

  @Test
  void blankPolicyNumberIsRejected() {
    assertThatThrownBy(() -> new Claim("CLM-1", "", 100_00L, "hail damage"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNumber");
  }

  @Test
  void nonPositiveClaimedAmountIsRejected() {
    assertThatThrownBy(() -> new Claim("CLM-1", "POL-1001", 0L, "hail damage"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimedAmountCents");
  }

  @Test
  void nullDescriptionIsRejected() {
    assertThatThrownBy(() -> new Claim("CLM-1", "POL-1001", 100_00L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("description");
  }
}
