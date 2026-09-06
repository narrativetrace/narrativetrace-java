/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

/**
 * An insurance policy on file. Monetary amounts are integer cents.
 *
 * @param policyNumber unique policy identifier, e.g. {@code POL-1001}
 * @param holderName name of the policy holder
 * @param coverageLimitCents maximum total payout the policy covers
 * @param deductibleCents amount the holder pays before coverage applies
 * @param active whether the policy is currently in force
 */
public record Policy(
    String policyNumber,
    String holderName,
    long coverageLimitCents,
    long deductibleCents,
    boolean active) {

  public Policy {
    if (policyNumber == null || policyNumber.isBlank()) {
      throw new IllegalArgumentException("policyNumber must not be blank");
    }
    if (holderName == null || holderName.isBlank()) {
      throw new IllegalArgumentException("holderName must not be blank");
    }
    if (coverageLimitCents <= 0) {
      throw new IllegalArgumentException("coverageLimitCents must be positive");
    }
    if (deductibleCents < 0 || deductibleCents > coverageLimitCents) {
      throw new IllegalArgumentException(
          "deductibleCents must be between 0 and the coverage limit");
    }
  }
}
