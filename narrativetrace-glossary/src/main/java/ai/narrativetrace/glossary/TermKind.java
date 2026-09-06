/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

/**
 * Grammatical shape of a glossary term.
 *
 * <p>INTENT: Lets harvesting and translation treat words, phrases, and narration templates
 * differently — template entries key on raw template text and hold per-locale template variants,
 * while word/phrase entries key on normalized identifier language.
 */
public enum TermKind {
  WORD,
  NOUN_PHRASE,
  VERB_PHRASE,
  TEMPLATE;

  /** Returns the kebab-case label used in {@code glossary.json} (e.g. {@code noun-phrase}). */
  public String jsonName() {
    return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
  }
}
