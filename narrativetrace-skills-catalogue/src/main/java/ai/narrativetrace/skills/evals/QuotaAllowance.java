/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

/** One row of {@code ledger/quota.md}'s Allowance table: a platform's plan tier and weekly cap. */
public record QuotaAllowance(Platform platform, String planTier, int weeklyAllowance) {

  public QuotaAllowance {
    if (platform == null) {
      throw new IllegalArgumentException("a QuotaAllowance's platform must not be null");
    }
    if (planTier == null || planTier.isBlank()) {
      throw new IllegalArgumentException("a QuotaAllowance's planTier must not be blank");
    }
  }
}
