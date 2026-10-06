/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

/** Whether a sporadic-lane trial may spend quota right now, and why not when it may not. */
public record QuotaDecision(boolean allowed, String reason) {

  public static QuotaDecision allow() {
    return new QuotaDecision(true, null);
  }

  public static QuotaDecision refused(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("a refused QuotaDecision's reason must not be blank");
    }
    return new QuotaDecision(false, reason);
  }
}
