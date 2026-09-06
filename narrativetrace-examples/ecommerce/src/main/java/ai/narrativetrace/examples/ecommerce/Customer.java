/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import ai.narrativetrace.api.annotation.NarrativeSummary;

public record Customer(String id, String name, CustomerTier tier) {

  @NarrativeSummary
  public String narrativeSummary() {
    return name + " (" + tier + ")";
  }
}
