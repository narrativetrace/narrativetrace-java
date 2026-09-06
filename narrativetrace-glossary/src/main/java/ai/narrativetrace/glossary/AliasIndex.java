/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lookup from {@code (context, normalized alias)} to the canonical term that deprecates it.
 *
 * <p>INTENT: Harvesting and violation reporting both ask the same question — "is this normalized
 * phrase a deprecated alias here?" — so the answer is precomputed once per glossary. Matching is
 * exact on the whole normalized phrase and scoped per context: an alias is only recognized in
 * contexts where its canonical term is defined.
 */
public final class AliasIndex {

  private final Map<TermKey, GlossaryTerm> canonicalByAlias;

  private AliasIndex(Map<TermKey, GlossaryTerm> canonicalByAlias) {
    this.canonicalByAlias = Map.copyOf(canonicalByAlias);
  }

  /**
   * Builds the index from a glossary's synonym declarations.
   *
   * @param glossary glossary to index; must not be {@code null}
   * @return index over every {@code (term context, synonym alias)} pair
   */
  public static AliasIndex of(Glossary glossary) {
    if (glossary == null) {
      throw new IllegalArgumentException("glossary must not be null");
    }
    var byAlias = new HashMap<TermKey, GlossaryTerm>();
    for (var term : glossary.terms()) {
      for (var synonym : term.synonyms()) {
        byAlias.put(new TermKey(term.context(), synonym.alias()), term);
      }
    }
    return new AliasIndex(byAlias);
  }

  /**
   * Returns whether the key names a deprecated alias in its context.
   *
   * @param key context plus normalized phrase; must not be {@code null}
   */
  public boolean isAlias(TermKey key) {
    return canonicalFor(key).isPresent();
  }

  /**
   * Returns the canonical term that deprecates the given alias.
   *
   * @param key context plus normalized phrase; must not be {@code null}
   * @return the canonical term, or empty when the phrase is not an alias in that context
   */
  public Optional<GlossaryTerm> canonicalFor(TermKey key) {
    if (key == null) {
      throw new IllegalArgumentException("key must not be null");
    }
    return Optional.ofNullable(canonicalByAlias.get(key));
  }
}
