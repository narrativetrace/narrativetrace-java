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

class PolicyLookupEJBTest {

  private PolicyLookupEJB policyLookup;

  @BeforeEach
  void setUp() {
    policyLookup = new PolicyLookupEJB();
  }

  @Test
  void registeredPolicyIsFoundByNumber() {
    var policy = new Policy("POL-1001", "Ana Suarez", 10_000_00L, 500_00L, true);
    policyLookup.register(policy);

    assertThat(policyLookup.findPolicyByNumber("POL-1001")).contains(policy);
  }

  @Test
  void unknownPolicyNumberReturnsEmpty() {
    assertThat(policyLookup.findPolicyByNumber("POL-9999")).isEmpty();
  }

  @Test
  void nullPolicyNumberLookupReturnsEmptyInsteadOfThrowing() {
    assertThat(policyLookup.findPolicyByNumber(null)).isEmpty();
  }

  @Test
  void reRegisteringAPolicyNumberReplacesTheEarlierVersion() {
    policyLookup.register(new Policy("POL-1001", "Ana Suarez", 10_000_00L, 500_00L, true));
    var renewal = new Policy("POL-1001", "Ana Suarez", 20_000_00L, 250_00L, true);

    policyLookup.register(renewal);

    assertThat(policyLookup.findPolicyByNumber("POL-1001")).contains(renewal);
  }

  @Test
  void demoPoliciesAreSeededForTheContainerDeployment() {
    policyLookup.seedDemoPolicies();

    assertThat(policyLookup.findPolicyByNumber("POL-1001"))
        .hasValueSatisfying(policy -> assertThat(policy.active()).isTrue());
    assertThat(policyLookup.findPolicyByNumber("POL-2002"))
        .hasValueSatisfying(policy -> assertThat(policy.active()).isFalse());
    assertThat(policyLookup.findPolicyByNumber("POL-3003"))
        .hasValueSatisfying(
            policy -> assertThat(policy.coverageLimitCents()).isGreaterThan(250_000_00L));
  }

  @Test
  void registeringNullIsRejected() {
    assertThatThrownBy(() -> policyLookup.register(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policy");
  }
}
