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

/**
 * Translates normalized glossary phrases into a target locale.
 *
 * <p>INTENT: The lookup engine of trace translation (plan Phase 6). The chain for a phrase within a
 * bounded context is: exact phrase entry → per-token word entries → untranslated. Untranslated
 * phrases render as-is and report {@code complete() == false}, so callers can collect them into the
 * "glossary gaps" footer that drives glossary completion.
 */
public final class GlossaryTranslator {

  /** context name → normalized term → glossary entry; built once, terms are immutable. */
  private final Map<String, Map<String, GlossaryTerm>> termsByContext;

  public GlossaryTranslator(Glossary glossary) {
    if (glossary == null) {
      throw new IllegalArgumentException("glossary must not be null");
    }
    var index = new HashMap<String, Map<String, GlossaryTerm>>();
    for (var term : glossary.terms()) {
      index.computeIfAbsent(term.context(), c -> new HashMap<>()).put(term.term(), term);
    }
    this.termsByContext = index;
  }

  /**
   * One phrase's translation outcome.
   *
   * @param text the translated phrase, or the original phrase when no translation applies
   * @param complete {@code true} iff every part of the phrase was covered by the glossary
   */
  public record PhraseTranslation(String text, boolean complete) {}

  /**
   * Translates a normalized phrase within {@code context} into {@code locale}.
   *
   * @param phrase normalized phrase (lowercase, space-separated), as produced by term harvesting
   * @param context bounded-context name the phrase was observed in
   * @param locale target locale tag (e.g. {@code "es"})
   */
  public PhraseTranslation translate(String phrase, String context, String locale) {
    requireNonBlank(phrase, "phrase");
    requireNonBlank(context, "context");
    requireNonBlank(locale, "locale");
    var exact = lookup(phrase, context, locale);
    if (exact != null) {
      return new PhraseTranslation(exact, true);
    }
    return translateTokenByToken(phrase, context, locale);
  }

  private PhraseTranslation translateTokenByToken(String phrase, String context, String locale) {
    var tokens = phrase.split(" ");
    var out = new StringBuilder();
    var complete = true;
    for (int i = 0; i < tokens.length; i++) {
      if (i > 0) {
        out.append(' ');
      }
      var word = lookup(tokens[i], context, locale);
      if (word == null) {
        complete = false;
        out.append(tokens[i]);
      } else {
        out.append(word);
      }
    }
    return new PhraseTranslation(out.toString(), complete);
  }

  /**
   * Looks up the locale variant of a template entry, keyed on the raw template text.
   *
   * <p>Exact lookup only — templates never fall back to per-token translation, since their text
   * carries placeholders and prose rather than a normalized phrase.
   */
  public java.util.Optional<String> templateVariant(
      String rawTemplate, String context, String locale) {
    requireNonBlank(rawTemplate, "rawTemplate");
    requireNonBlank(context, "context");
    requireNonBlank(locale, "locale");
    return java.util.Optional.ofNullable(lookup(rawTemplate, context, locale));
  }

  private static void requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  private String lookup(String phrase, String context, String locale) {
    var entry = termsByContext.getOrDefault(context, Map.of()).get(phrase);
    return entry != null ? entry.translations().get(locale) : null;
  }
}
