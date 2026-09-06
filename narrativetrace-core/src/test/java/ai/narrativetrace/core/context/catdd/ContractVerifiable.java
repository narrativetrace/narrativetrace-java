/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context.catdd;

/**
 * Implemented by test classes that verify a class invariant via Contract-Augmented TDD.
 *
 * <p>The test class implements this interface to expose its subject. {@link
 * InvariantCheckExtension} calls {@link #checkInvariant()} before and after each test.
 *
 * <p>The production class is unaware of this interface — it simply defines a package-private {@code
 * boolean invariant()} method. The test class bridges the two.
 *
 * @param <T> the type of the subject under test
 */
public interface ContractVerifiable<T> {

  /** Returns the subject under test. May return {@code null} before setUp has run. */
  T subject();

  /**
   * Checks the invariant on the subject. Returns {@code true} if the subject is in a valid state,
   * or if the subject is {@code null} (before setUp).
   */
  boolean checkInvariant();
}
