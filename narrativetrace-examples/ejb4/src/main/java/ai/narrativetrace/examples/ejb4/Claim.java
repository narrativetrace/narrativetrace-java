/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

/**
 * A claim filed against a policy. Monetary amounts are integer cents.
 *
 * @param claimId unique claim identifier, e.g. {@code CLM-1}
 * @param policyNumber the policy this claim is filed against
 * @param claimedAmountCents amount the claimant is asking for
 * @param description free-text description of the loss
 */
public record Claim(
    String claimId, String policyNumber, long claimedAmountCents, String description) {

  public Claim {
    if (claimId == null || claimId.isBlank()) {
      throw new IllegalArgumentException("claimId must not be blank");
    }
    if (policyNumber == null || policyNumber.isBlank()) {
      throw new IllegalArgumentException("policyNumber must not be blank");
    }
    if (claimedAmountCents <= 0) {
      throw new IllegalArgumentException("claimedAmountCents must be positive");
    }
    if (description == null) {
      throw new IllegalArgumentException("description must not be null");
    }
  }
}
