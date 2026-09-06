/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Mechanically derives the canonical-term rename for an identifier using a deprecated alias.
 *
 * <p>INTENT: The plan's rename rule — find the alias's token window inside the identifier's
 * normalized token list, splice in the canonical term's tokens, and re-join in the identifier's
 * original casing convention ({@code openAccountWithOverdraft} → {@code openOverdraftAccount}).
 * Matching happens on normalized tokens so plural spellings still match; the untouched prefix and
 * suffix keep their original raw spelling.
 */
public final class RenameSuggester {

  /**
   * Case-preserving split, same boundaries as clarity's {@code IdentifierTokenizer} (which
   * lowercases and therefore cannot be reused for casing reconstruction).
   */
  private static final Pattern RAW_SPLIT =
      Pattern.compile(
          "(?<=[a-z])(?=[A-Z])"
              + "|(?<=[A-Z])(?=[A-Z][a-z])"
              + "|(?<=[a-zA-Z])(?=[0-9])"
              + "|(?<=[0-9])(?=[a-zA-Z])"
              + "|_");

  private final TermNormalizer normalizer = new TermNormalizer();

  /**
   * Suggests the canonical rename of an identifier that uses a deprecated alias.
   *
   * @param identifier offending identifier; must not be blank
   * @param aliasPhrase normalized alias phrase observed in the identifier; must not be blank
   * @param canonicalPhrase normalized canonical term to splice in; must not be blank
   * @return the renamed identifier in the original casing convention, or empty when the alias
   *     tokens do not appear contiguously in the identifier
   */
  public Optional<String> suggest(String identifier, String aliasPhrase, String canonicalPhrase) {
    if (identifier == null || identifier.isBlank()) {
      throw new IllegalArgumentException("identifier must not be blank");
    }
    if (aliasPhrase == null || aliasPhrase.isBlank()) {
      throw new IllegalArgumentException("alias phrase must not be blank");
    }
    if (canonicalPhrase == null || canonicalPhrase.isBlank()) {
      throw new IllegalArgumentException("canonical phrase must not be blank");
    }
    var rawTokens = rawTokens(identifier);
    var normalizedTokens = List.of(normalizer.phrase(identifier).split(" "));
    assert rawTokens.size() == normalizedTokens.size()
        : "raw and normalized token counts must align";
    int start = windowStart(normalizedTokens, List.of(aliasPhrase.split(" ")));
    if (start < 0) {
      return Optional.empty();
    }
    int end = start + aliasPhrase.split(" ").length;
    return Optional.of(
        rebuild(identifier, rawTokens, start, end, List.of(canonicalPhrase.split(" "))));
  }

  private static List<String> rawTokens(String identifier) {
    return List.of(RAW_SPLIT.split(identifier)).stream().filter(s -> !s.isEmpty()).toList();
  }

  /** Returns the first index where {@code window} occurs contiguously in {@code tokens}, or -1. */
  private static int windowStart(List<String> tokens, List<String> window) {
    for (int i = 0; i + window.size() <= tokens.size(); i++) {
      if (tokens.subList(i, i + window.size()).equals(window)) {
        return i;
      }
    }
    return -1;
  }

  private static String rebuild(
      String identifier, List<String> rawTokens, int start, int end, List<String> replacement) {
    var tokens = new ArrayList<String>();
    tokens.addAll(rawTokens.subList(0, start));
    tokens.addAll(replacement);
    tokens.addAll(rawTokens.subList(end, rawTokens.size()));
    if (identifier.indexOf('_') >= 0) {
      return String.join("_", tokens.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList());
    }
    return joinCamel(
        tokens, Character.isUpperCase(identifier.charAt(0)), start, replacement.size());
  }

  private static String joinCamel(
      List<String> tokens, boolean pascal, int spliceStart, int spliceLength) {
    var out = new StringBuilder();
    for (int i = 0; i < tokens.size(); i++) {
      var token = tokens.get(i);
      boolean spliced = i >= spliceStart && i < spliceStart + spliceLength;
      boolean capitalize = i > 0 || pascal;
      out.append(spliced || i == 0 ? cased(token, capitalize) : token);
    }
    return out.toString();
  }

  private static String cased(String token, boolean capitalize) {
    var lower = token.toLowerCase(Locale.ROOT);
    return capitalize ? Character.toUpperCase(lower.charAt(0)) + lower.substring(1) : lower;
  }
}
