/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

/**
 * One use of a deprecated synonym in code, reported by a harvest run.
 *
 * <p>INTENT: The run-artifact form of "always use the canonical term" — feeds the console summary,
 * {@code glossary-usage.json}, and the {@code non-canonical-term} clarity issue. Never written into
 * the committed glossary.
 *
 * @param context bounded context in which the alias is deprecated
 * @param alias normalized deprecated phrase observed
 * @param canonicalTerm normalized canonical term to use instead
 * @param site code site of the offending identifier, {@code Class.method} or {@code Class}
 * @param identifier raw offending identifier
 * @param suggestedIdentifier mechanical rename to the canonical term, or {@code null} when no
 *     contiguous alias window exists in the identifier
 * @param occurrences observed uses at this site; at least 1
 */
public record VocabularyViolation(
    String context,
    String alias,
    String canonicalTerm,
    String site,
    String identifier,
    String suggestedIdentifier,
    int occurrences) {

  public VocabularyViolation {
    if (context == null || context.isBlank()) {
      throw new IllegalArgumentException("context must not be blank");
    }
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("alias must not be blank");
    }
    if (canonicalTerm == null || canonicalTerm.isBlank()) {
      throw new IllegalArgumentException("canonicalTerm must not be blank");
    }
    if (site == null || site.isBlank()) {
      throw new IllegalArgumentException("site must not be blank");
    }
    if (identifier == null || identifier.isBlank()) {
      throw new IllegalArgumentException("identifier must not be blank");
    }
    if (occurrences < 1) {
      throw new IllegalArgumentException("occurrences must be at least 1: " + occurrences);
    }
  }
}
