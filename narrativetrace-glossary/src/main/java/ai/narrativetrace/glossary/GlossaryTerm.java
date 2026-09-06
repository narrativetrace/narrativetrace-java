/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One entry of the domain glossary: a canonical term within a bounded context.
 *
 * <p>INTENT: The unit of ubiquitous language. Term identity is {@code (context, term)}; the same
 * term may exist independently in two contexts with different definitions and translations.
 *
 * <p>Human-owned fields ({@code definition}, {@code translations}, {@code synonyms}, curated
 * status) are never overwritten by harvesting.
 *
 * @param term canonical term in normalized form (lowercase, space-separated)
 * @param context bounded-context name this term belongs to
 * @param kind grammatical shape of the term
 * @param status curation lifecycle state
 * @param definition human-written meaning, or {@code null} if not yet curated
 * @param translations locale tag → translated term (human- or Pro-owned)
 * @param synonyms deprecated aliases of this term
 * @param sources first observed code sites, as {@code Class.member} strings
 * @param firstSeen date the term first entered the glossary; set once, never updated
 */
public record GlossaryTerm(
    String term,
    String context,
    TermKind kind,
    TermStatus status,
    String definition,
    Map<String, String> translations,
    List<SynonymAlias> synonyms,
    List<String> sources,
    LocalDate firstSeen) {

  public GlossaryTerm {
    if (term == null || term.isBlank()) {
      throw new IllegalArgumentException("term must not be blank");
    }
    if (context == null || context.isBlank()) {
      throw new IllegalArgumentException("context must not be blank");
    }
    if (kind == null) {
      throw new IllegalArgumentException("kind must not be null");
    }
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    if (firstSeen == null) {
      throw new IllegalArgumentException("firstSeen must not be null");
    }
    translations = Map.copyOf(translations);
    synonyms = List.copyOf(synonyms);
    sources = List.copyOf(sources);
  }
}
