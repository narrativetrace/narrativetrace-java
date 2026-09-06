/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClaimsProcessorBeanTest {

  private PolicyLookupEJB policyLookup;
  private ClaimsProcessorBean claimsProcessor;

  @BeforeEach
  void setUp() {
    policyLookup = new PolicyLookupEJB();
    claimsProcessor =
        new ClaimsProcessorBean(policyLookup, new FraudChkMgr(), new CoverageCalcEJB());
  }

  @Test
  void coveredClaimIsApprovedWithTheCalculatedPayout() {
    policyLookup.register(new Policy("POL-1001", "Ana Suarez", 10_000_00L, 500_00L, true));
    var claim = new Claim("CLM-1", "POL-1001", 8_000_00L, "kitchen water damage");

    var decision = claimsProcessor.processClaim(claim);

    assertThat(decision.status()).isEqualTo(ClaimDecision.Status.APPROVED);
    assertThat(decision.payoutCents()).isEqualTo(7_500_00L);
  }

  @Test
  void claimAgainstAnUnknownPolicyIsRejected() {
    var claim = new Claim("CLM-2", "POL-9999", 8_000_00L, "kitchen water damage");

    var decision = claimsProcessor.processClaim(claim);

    assertThat(decision.status()).isEqualTo(ClaimDecision.Status.REJECTED);
    assertThat(decision.payoutCents()).isZero();
    assertThat(decision.reason()).isEqualTo("no policy on file");
  }

  @Test
  void claimAgainstALapsedPolicyIsRejected() {
    policyLookup.register(new Policy("POL-1001", "Ana Suarez", 10_000_00L, 500_00L, false));
    var claim = new Claim("CLM-3", "POL-1001", 8_000_00L, "kitchen water damage");

    var decision = claimsProcessor.processClaim(claim);

    assertThat(decision.status()).isEqualTo(ClaimDecision.Status.REJECTED);
    assertThat(decision.payoutCents()).isZero();
    assertThat(decision.reason()).isEqualTo("policy lapsed");
  }

  @Test
  void suspiciousClaimIsReferredToTheFraudUnitBeforeAnyPayout() {
    policyLookup.register(new Policy("POL-1001", "Ana Suarez", 900_000_00L, 500_00L, true));
    var claim = new Claim("CLM-4", "POL-1001", 300_000_00L, "warehouse fire");

    var decision = claimsProcessor.processClaim(claim);

    assertThat(decision.status()).isEqualTo(ClaimDecision.Status.REJECTED);
    assertThat(decision.payoutCents()).isZero();
    assertThat(decision.reason()).isEqualTo("referred to fraud unit");
  }

  @Test
  void claimBelowTheDeductibleIsRejectedInsteadOfApprovedForZero() {
    policyLookup.register(new Policy("POL-1001", "Ana Suarez", 10_000_00L, 500_00L, true));
    var claim = new Claim("CLM-5", "POL-1001", 300_00L, "cracked window pane");

    var decision = claimsProcessor.processClaim(claim);

    assertThat(decision.status()).isEqualTo(ClaimDecision.Status.REJECTED);
    assertThat(decision.payoutCents()).isZero();
    assertThat(decision.reason()).isEqualTo("below deductible");
  }

  @Test
  void lapsedPolicyWinsOverFraudScreeningWhenBothApply() {
    policyLookup.register(new Policy("POL-1001", "Ana Suarez", 900_000_00L, 500_00L, false));
    var claim = new Claim("CLM-6", "POL-1001", 300_000_00L, "staged warehouse fire");

    var decision = claimsProcessor.processClaim(claim);

    assertThat(decision.reason()).isEqualTo("policy lapsed");
  }

  @Test
  void containerConstructedProcessorStartsWithAnEmptyRegistryUntilInjectionHappens() {
    var containerConstructed = new ClaimsProcessorBean();
    var claim = new Claim("CLM-7", "POL-1001", 8_000_00L, "kitchen water damage");

    var decision = containerConstructed.processClaim(claim);

    assertThat(decision.status()).isEqualTo(ClaimDecision.Status.REJECTED);
    assertThat(decision.reason()).isEqualTo("no policy on file");
  }

  @Test
  void processingANullClaimIsRejectedUpFront() {
    assertThatThrownBy(() -> claimsProcessor.processClaim(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claim");
  }
}
