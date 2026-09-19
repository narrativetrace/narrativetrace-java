/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary.fixtures;

/** An enum: the compiler writes {@code values()} and {@code valueOf(String)} into every one. */
public enum CustomerTierFixture {
  GOLD,
  SILVER;

  /** A method someone actually named, so the enum still contributes its own vocabulary. */
  public boolean qualifiesForFreeShipping() {
    return this == GOLD;
  }
}
