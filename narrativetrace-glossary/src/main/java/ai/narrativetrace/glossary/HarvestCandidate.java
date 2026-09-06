/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

/**
 * One aggregated observation of a normalized phrase at a code site during harvesting.
 *
 * <p>INTENT: The bridge between traces and the glossary. The merger turns unseen {@code (context,
 * phrase)} pairs into new terms; alias hits become vocabulary violations; everything else is
 * usage-report material only.
 *
 * @param context bounded context resolved from the declaring class's package
 * @param phrase normalized phrase produced by {@link TermNormalizer}
 * @param kind grammatical shape of the phrase
 * @param site observed code site, {@code Class.method} or {@code Class}
 * @param identifier raw identifier the phrase was normalized from
 * @param occurrences how many times this exact observation appeared; at least 1
 */
public record HarvestCandidate(
    String context, String phrase, TermKind kind, String site, String identifier, int occurrences) {

  public HarvestCandidate {
    if (context == null || context.isBlank()) {
      throw new IllegalArgumentException("context must not be blank");
    }
    if (phrase == null || phrase.isBlank()) {
      throw new IllegalArgumentException("phrase must not be blank");
    }
    if (kind == null) {
      throw new IllegalArgumentException("kind must not be null");
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
