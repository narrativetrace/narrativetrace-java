/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.util.List;

/**
 * Full clarity-analysis result for one scenario or class.
 *
 * <p>INTENT: Renderers, Gradle checks, and JSON export all consume this record rather than
 * recomputing scores or issue rankings.
 *
 * <p>All scores range from 0.0 (poor) to 1.0 (excellent). Typical thresholds:
 *
 * <ul>
 *   <li>0.80+ — good naming, no action needed
 *   <li>0.60-0.79 — acceptable, minor improvements suggested
 *   <li>below 0.60 — poor naming, significant refactoring recommended
 * </ul>
 *
 * @param overallScore Weighted combination of all dimension scores (weights: method 0.30, class
 *     0.20, param 0.25, structural 0.15, cohesion 0.10).
 * @param methodNameScore Average quality of method names (verb+noun structure, domain vocabulary,
 *     absence of generic verbs).
 * @param classNameScore Average quality of class names (role suffixes, abbreviation avoidance).
 * @param parameterNameScore Average quality of parameter names (descriptiveness, absence of
 *     single-letter or generic tokens).
 * @param structuralScore Structural penalty converted into a 0..1 score (deep nesting, excessive
 *     parameters, long methods).
 * @param cohesionScore Vocabulary consistency within each class (collocation of related domain
 *     terms).
 * @param issues Ranked list of actionable naming issues, highest severity first.
 * @param elementNotes One teaching note per element at every score (not threshold-gated like {@link
 *     #issues}); the appeal-process channel that makes a bare score explainable.
 */
public record ClarityResult(
    double overallScore,
    double methodNameScore,
    double classNameScore,
    double parameterNameScore,
    double structuralScore,
    double cohesionScore,
    List<ClarityIssue> issues,
    List<ElementNote> elementNotes) {

  /**
   * Delegating overload for the ~28 construction sites that predate per-element notes. Defaults
   * {@link #elementNotes} to empty so existing callers stand unchanged.
   */
  public ClarityResult(
      double overallScore,
      double methodNameScore,
      double classNameScore,
      double parameterNameScore,
      double structuralScore,
      double cohesionScore,
      List<ClarityIssue> issues) {
    this(
        overallScore,
        methodNameScore,
        classNameScore,
        parameterNameScore,
        structuralScore,
        cohesionScore,
        issues,
        List.of());
  }
}
