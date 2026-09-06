/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.List;

/**
 * Outcome of one additive merge of a harvest into an existing glossary.
 *
 * <p>INTENT: The single value from which all run output derives — the merged glossary is written
 * back to disk, {@code newTerms} feeds the console summary, and {@code suppressedAliasUses} feeds
 * vocabulary-violation reporting.
 *
 * @param glossary merged glossary; a superset of the input, never mutated entries
 * @param newTerms terms added by this merge, in canonical order
 * @param suppressedAliasUses observations whose phrase matched a deprecated alias in its context —
 *     kept out of the glossary, reported as violations
 */
public record MergeResult(
    Glossary glossary, List<GlossaryTerm> newTerms, List<HarvestCandidate> suppressedAliasUses) {

  public MergeResult {
    if (glossary == null) {
      throw new IllegalArgumentException("glossary must not be null");
    }
    if (newTerms == null) {
      throw new IllegalArgumentException("newTerms must not be null");
    }
    if (suppressedAliasUses == null) {
      throw new IllegalArgumentException("suppressedAliasUses must not be null");
    }
    newTerms = List.copyOf(newTerms);
    suppressedAliasUses = List.copyOf(suppressedAliasUses);
  }
}
