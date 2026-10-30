/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary.fixtures;

/**
 * An enum that overloads both names the compiler writes into every enum.
 *
 * <p>The skip rule is keyed on shape, not on the name alone: {@code values()} takes nothing and
 * {@code valueOf} takes exactly one {@code String}. These two overloads take neither shape, so both
 * are vocabulary, and the two the compiler wrote are not.
 */
public enum EnumStaticNearMissFixture {
  GOLD,
  SILVER;

  /** The near miss: static, named {@code valueOf}, and not the compiler's signature. */
  public static EnumStaticNearMissFixture valueOf(int ordinal, boolean strict) {
    return strict && ordinal == 0 ? GOLD : SILVER;
  }

  /** The near miss: named {@code values}, and an instance method that takes an argument. */
  public boolean values(String filter) {
    return name().startsWith(filter);
  }
}
