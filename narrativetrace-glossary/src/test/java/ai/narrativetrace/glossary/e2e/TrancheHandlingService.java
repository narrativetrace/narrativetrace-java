/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary.e2e;

/**
 * A weak verb ({@code handle}) over a noun the built-in {@code CollocationDictionary} does not know
 * at all ({@code tranche}) — the subject of {@link
 * GlossaryAwareClarityScanTest#aGlossaryVerbPhraseIsProposedAsTheCollocationSuggestionForItsNoun}.
 * Unlike {@link TrancheService}, the verb here is generic rather than the glossary's own, so the
 * collocation issue is never suppressed — only its suggestion is in question.
 */
public final class TrancheHandlingService {

  public String handleTranche(String tranche) {
    return tranche;
  }
}
