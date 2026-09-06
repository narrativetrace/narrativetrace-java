/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Builds {@link VocabularyViolation}s from the alias uses a merge suppressed.
 *
 * <p>INTENT: One aggregation point between the merger's raw suppressed observations and every
 * violation surface. Observations of the same {@code (context, alias, site, identifier)} are summed
 * regardless of kind; the canonical term comes from the glossary's {@link AliasIndex}; the rename
 * suggestion is mechanical and may be absent.
 */
public final class VocabularyViolations {

  private VocabularyViolations() {}

  /**
   * Aggregates suppressed alias uses into deterministic violation records.
   *
   * @param glossary glossary whose synonym declarations caused the suppression; must not be {@code
   *     null}
   * @param suppressed suppressed observations from {@link MergeResult#suppressedAliasUses()}; must
   *     not be {@code null}
   * @return violations sorted by {@code (context, alias, site, identifier)}
   */
  public static List<VocabularyViolation> collect(
      Glossary glossary, List<HarvestCandidate> suppressed) {
    if (glossary == null) {
      throw new IllegalArgumentException("glossary must not be null");
    }
    if (suppressed == null) {
      throw new IllegalArgumentException("suppressed must not be null");
    }
    var aliasIndex = AliasIndex.of(glossary);
    var suggester = new RenameSuggester();
    var aggregated = new LinkedHashMap<String, VocabularyViolation>();
    for (var candidate : suppressed) {
      var violation = toViolation(candidate, aliasIndex, suggester);
      aggregated.merge(groupKey(violation), violation, VocabularyViolations::sum);
    }
    return aggregated.values().stream().sorted(ORDER).toList();
  }

  private static final Comparator<VocabularyViolation> ORDER =
      Comparator.comparing(VocabularyViolation::context)
          .thenComparing(VocabularyViolation::alias)
          .thenComparing(VocabularyViolation::site)
          .thenComparing(VocabularyViolation::identifier);

  private static VocabularyViolation toViolation(
      HarvestCandidate candidate, AliasIndex aliasIndex, RenameSuggester suggester) {
    var key = new TermKey(candidate.context(), candidate.phrase());
    var canonical =
        aliasIndex
            .canonicalFor(key)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "suppressed candidate is not an alias: " + candidate));
    var suggestion =
        suggester
            .suggest(candidate.identifier(), candidate.phrase(), canonical.term())
            .orElse(null);
    return new VocabularyViolation(
        candidate.context(),
        candidate.phrase(),
        canonical.term(),
        candidate.site(),
        candidate.identifier(),
        suggestion,
        candidate.occurrences());
  }

  private static String groupKey(VocabularyViolation violation) {
    return violation.context()
        + "|"
        + violation.alias()
        + "|"
        + violation.site()
        + "|"
        + violation.identifier();
  }

  private static VocabularyViolation sum(VocabularyViolation left, VocabularyViolation right) {
    return new VocabularyViolation(
        left.context(),
        left.alias(),
        left.canonicalTerm(),
        left.site(),
        left.identifier(),
        left.suggestedIdentifier(),
        left.occurrences() + right.occurrences());
  }
}
