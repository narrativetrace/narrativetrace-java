/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

/**
 * Computes claim payouts. The class and method names are deliberately EJB-era abbreviations
 * ("Calc", "EJB") — this example's clarity report later flags them as rename candidates.
 */
public class CoverageCalcEJB {

  /** Returns the payout in cents for {@code claim} under {@code policy}. */
  public long calcPayout(Policy policy, Claim claim) {
    var coveredCents = Math.min(claim.claimedAmountCents(), policy.coverageLimitCents());
    return Math.max(0L, coveredCents - policy.deductibleCents());
  }
}
