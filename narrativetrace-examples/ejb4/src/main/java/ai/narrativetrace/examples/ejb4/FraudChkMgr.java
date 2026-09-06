/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

import java.util.Locale;

/**
 * Flags claims for the fraud unit. The class and method names are deliberately EJB-era
 * abbreviations ("Chk", "Mgr") — this example's clarity report later flags them as rename
 * candidates.
 */
public class FraudChkMgr {

  static final long LARGE_LOSS_THRESHOLD_CENTS = 250_000_00L;
  static final String STAGED_LOSS_KEYWORD = "staged";

  /** Returns {@code true} when {@code claim} should be referred to the fraud unit. */
  public boolean chkClaim(Claim claim) {
    return claim.claimedAmountCents() > LARGE_LOSS_THRESHOLD_CENTS
        || claim.description().toLowerCase(Locale.ROOT).contains(STAGED_LOSS_KEYWORD);
  }
}
