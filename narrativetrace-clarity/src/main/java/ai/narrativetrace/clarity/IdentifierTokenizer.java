/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Tokenizes camelCase and PascalCase identifiers into word lists for NLP analysis.
 *
 * <p><b>@edgeCase</b> Whitespace is a separator, exactly as {@code _} is. A real Java identifier
 * cannot contain a space, so this only ever fires on input the runtime did not author — but that
 * input reaches here, and splitting on {@code _} alone left the whitespace behind as its own
 * "word": {@code "_ "} tokenized to a single space. Downstream that is a term like any other, so
 * {@code TermNormalizer} produced a blank phrase from a non-blank identifier and tripped its own
 * postcondition, while {@code "a_ "} quietly normalized to {@code "a "}.
 */
public final class IdentifierTokenizer {

  private static final Pattern SPLIT_PATTERN =
      Pattern.compile(
          "(?<=[a-z])(?=[A-Z])"
              + "|(?<=[A-Z])(?=[A-Z][a-z])"
              + "|(?<=[a-zA-Z])(?=[0-9])"
              + "|(?<=[0-9])(?=[a-zA-Z])"
              + "|[_\\p{javaWhitespace}]+");

  public List<String> tokenize(String identifier) {
    return List.of(SPLIT_PATTERN.split(identifier)).stream()
        .filter(s -> !s.isEmpty())
        .map(String::toLowerCase)
        .toList();
  }
}
