/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import org.gradle.api.provider.Property;

/**
 * Gradle DSL block for clarity-check thresholds.
 *
 * <p>INTENT: Use this to decide how strict the generated {@code clarityCheck} task should be.
 */
public abstract class ClarityExtension {

  public abstract Property<Double> getMinScore();

  public abstract Property<Integer> getMaxHighIssues();

  /**
   * Maximum number of suite-level issues (e.g. {@code non-canonical-term} vocabulary violations)
   * tolerated before {@code clarityCheck} fails. Defaults to unlimited, making suite issues
   * advisory; set to {@code 0} to hard-fail on any violation.
   */
  public abstract Property<Integer> getMaxSuiteIssues();

  public abstract Property<Boolean> getWarnOnly();
}
