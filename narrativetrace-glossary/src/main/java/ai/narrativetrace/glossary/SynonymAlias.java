/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

/**
 * A deprecated alias of a canonical glossary term.
 *
 * <p>INTENT: Records "this phrasing exists in the wild; always use the canonical term instead".
 * Harvesting suppresses aliases (never re-adds them as terms) and flags their use in code as a
 * vocabulary violation.
 *
 * @param alias the deprecated phrasing, in normalized form (lowercase, space-separated)
 * @param note optional human context for why the alias exists (may be {@code null})
 */
public record SynonymAlias(String alias, String note) {

  public SynonymAlias {
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("alias must not be blank");
    }
  }
}
