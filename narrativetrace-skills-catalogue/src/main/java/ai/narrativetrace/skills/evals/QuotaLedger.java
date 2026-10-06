/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.util.List;

/** {@code ledger/quota.md}, parsed: the Allowance table plus every appended Spend log row. */
public record QuotaLedger(List<QuotaAllowance> allowances, List<QuotaSpendRow> spend) {

  public QuotaLedger {
    allowances = List.copyOf(allowances);
    spend = List.copyOf(spend);
  }
}
