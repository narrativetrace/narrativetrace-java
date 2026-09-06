/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

/**
 * Identity of a glossary term: bounded context plus normalized term text.
 *
 * <p>INTENT: All term and alias lookups key on this pair — the same normalized text is a distinct
 * concept in each context.
 *
 * @param context bounded-context name
 * @param normalized term text in normalized form (lowercase, space-separated)
 */
public record TermKey(String context, String normalized) {

  public static TermKey of(GlossaryTerm term) {
    return new TermKey(term.context(), term.term());
  }
}
