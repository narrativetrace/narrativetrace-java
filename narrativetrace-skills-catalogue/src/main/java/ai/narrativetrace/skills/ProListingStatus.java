/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

/**
 * A Pro listing's status, which must agree with the Pro feature guide (Tier A lint, per commit).
 */
public enum ProListingStatus {
  SHIPPED("shipped"),
  IN_DEVELOPMENT("in development"),
  PLANNED("planned");

  private final String label;

  ProListingStatus(String label) {
    this.label = label;
  }

  /** The exact phrase shown next to "Pro" on a rendered listing — never "paid", never a price. */
  public String label() {
    return label;
  }
}
