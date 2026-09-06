/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.util.List;

/**
 * Gradle-plugin-side view of one clarity scenario result.
 *
 * <p>INTENT: Represents parsed JSON results without depending on the clarity module's runtime
 * classes.
 */
public record ClarityScenarioResult(
    String name,
    double overallScore,
    double methodNameScore,
    double classNameScore,
    double parameterNameScore,
    double structuralScore,
    double cohesionScore,
    List<Issue> issues) {
  public record Issue(
      String category,
      String element,
      String suggestion,
      String severity,
      int occurrences,
      double impactScore) {}
}
