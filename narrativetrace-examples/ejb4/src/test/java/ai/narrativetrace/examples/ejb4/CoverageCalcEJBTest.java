/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoverageCalcEJBTest {

  @Test
  void payoutIsClaimedAmountMinusDeductible() {
    var policy = new Policy("POL-1001", "Ana Suarez", 100_000_00L, 500_00L, true);
    var claim = new Claim("CLM-1", "POL-1001", 8_000_00L, "kitchen water damage");

    var payoutCents = new CoverageCalcEJB().calcPayout(policy, claim);

    assertThat(payoutCents).isEqualTo(7_500_00L);
  }

  @Test
  void payoutIsCappedAtTheCoverageLimit() {
    var policy = new Policy("POL-1001", "Ana Suarez", 10_000_00L, 500_00L, true);
    var claim = new Claim("CLM-2", "POL-1001", 25_000_00L, "roof collapse");

    var payoutCents = new CoverageCalcEJB().calcPayout(policy, claim);

    assertThat(payoutCents).isEqualTo(9_500_00L);
  }

  @Test
  void claimBelowTheDeductibleFloorsAtZeroInsteadOfGoingNegative() {
    var policy = new Policy("POL-1001", "Ana Suarez", 10_000_00L, 500_00L, true);
    var claim = new Claim("CLM-3", "POL-1001", 300_00L, "cracked window pane");

    var payoutCents = new CoverageCalcEJB().calcPayout(policy, claim);

    assertThat(payoutCents).isZero();
  }

  @Test
  void claimExactlyAtTheDeductiblePaysZero() {
    var policy = new Policy("POL-1001", "Ana Suarez", 10_000_00L, 500_00L, true);
    var claim = new Claim("CLM-4", "POL-1001", 500_00L, "fence post replacement");

    var payoutCents = new CoverageCalcEJB().calcPayout(policy, claim);

    assertThat(payoutCents).isZero();
  }
}
