/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

/**
 * The outcome of processing a claim.
 *
 * @param status whether the claim was approved or rejected
 * @param payoutCents payout in cents; always {@code 0} for rejections
 * @param reason why the claim was rejected; empty for approvals
 */
public record ClaimDecision(Status status, long payoutCents, String reason) {

  /** Terminal state of a processed claim. */
  public enum Status {
    APPROVED,
    REJECTED
  }

  /** Returns an approval paying out {@code payoutCents}. */
  public static ClaimDecision approved(long payoutCents) {
    return new ClaimDecision(Status.APPROVED, payoutCents, "");
  }

  /** Returns a rejection explained by {@code reason}. */
  public static ClaimDecision rejected(String reason) {
    return new ClaimDecision(Status.REJECTED, 0L, reason);
  }
}
