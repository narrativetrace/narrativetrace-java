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

class FraudChkMgrTest {

  @Test
  void claimAboveTheLargeLossThresholdIsSuspicious() {
    var claim = new Claim("CLM-1", "POL-1001", 250_000_01L, "warehouse fire");

    assertThat(new FraudChkMgr().chkClaim(claim)).isTrue();
  }

  @Test
  void claimExactlyAtTheLargeLossThresholdIsNotSuspicious() {
    var claim = new Claim("CLM-2", "POL-1001", 250_000_00L, "warehouse fire");

    assertThat(new FraudChkMgr().chkClaim(claim)).isFalse();
  }

  @Test
  void ordinaryClaimIsNotSuspicious() {
    var claim = new Claim("CLM-3", "POL-1001", 1_200_00L, "bicycle stolen from garage");

    assertThat(new FraudChkMgr().chkClaim(claim)).isFalse();
  }

  @Test
  void stagedLossKeywordIsSuspiciousRegardlessOfAmount() {
    var claim = new Claim("CLM-4", "POL-1001", 100_00L, "car staged at the lake shore");

    assertThat(new FraudChkMgr().chkClaim(claim)).isTrue();
  }

  @Test
  void stagedLossKeywordMatchingIsCaseInsensitive() {
    var claim = new Claim("CLM-5", "POL-1001", 100_00L, "STAGED collision on Route 9");

    assertThat(new FraudChkMgr().chkClaim(claim)).isTrue();
  }
}
