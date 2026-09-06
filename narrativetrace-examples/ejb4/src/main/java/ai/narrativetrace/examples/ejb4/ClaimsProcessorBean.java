/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

/**
 * Orchestrates claim processing: policy lookup, fraud screening, payout calculation. Deployed as a
 * {@code @Stateless} session bean the servlet calls through a container proxy — the java agent
 * narrates the whole chain with zero code changes.
 */
@Stateless
public class ClaimsProcessorBean {

  @EJB private PolicyLookupEJB policyLookup;
  private final FraudChkMgr fraudChk;
  private final CoverageCalcEJB coverageCalc;

  /**
   * Container constructor: an empty registry until the container injects the {@code @Startup}
   * {@link PolicyLookupEJB} singleton after construction.
   */
  public ClaimsProcessorBean() {
    this(new PolicyLookupEJB(), new FraudChkMgr(), new CoverageCalcEJB());
  }

  /** Creates the processor over the given collaborators. */
  public ClaimsProcessorBean(
      PolicyLookupEJB policyLookup, FraudChkMgr fraudChk, CoverageCalcEJB coverageCalc) {
    this.policyLookup = policyLookup;
    this.fraudChk = fraudChk;
    this.coverageCalc = coverageCalc;
  }

  /** Decides {@code claim}: approved with a payout, or rejected with a reason. */
  public ClaimDecision processClaim(Claim claim) {
    if (claim == null) {
      throw new IllegalArgumentException("claim must not be null");
    }
    var policyOnFile = policyLookup.findPolicyByNumber(claim.policyNumber());
    if (policyOnFile.isEmpty()) {
      return ClaimDecision.rejected("no policy on file");
    }
    var policy = policyOnFile.get();
    if (!policy.active()) {
      return ClaimDecision.rejected("policy lapsed");
    }
    if (fraudChk.chkClaim(claim)) {
      return ClaimDecision.rejected("referred to fraud unit");
    }
    var payoutCents = coverageCalc.calcPayout(policy, claim);
    if (payoutCents == 0) {
      return ClaimDecision.rejected("below deductible");
    }
    return ClaimDecision.approved(payoutCents);
  }
}
