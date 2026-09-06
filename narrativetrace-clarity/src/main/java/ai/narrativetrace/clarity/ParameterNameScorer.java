/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.util.List;

/**
 * Scores parameter names based on specificity, length, and domain vocabulary.
 *
 * <p>The project's committed {@link DomainVocabulary} reaches both dictionaries this scorer
 * consults, so a declared noun scores as domain vocabulary and declared shorthand is not penalized.
 */
public final class ParameterNameScorer {

  private final IdentifierTokenizer tokenizer = new IdentifierTokenizer();
  private final GenericTokenDetector genericDetector;
  private final AbbreviationDictionary abbreviationDictionary;

  /** A scorer with no project vocabulary — the built-in dictionaries alone. */
  public ParameterNameScorer() {
    this(DomainVocabulary.empty());
  }

  /**
   * @param vocabulary the project's declared vocabulary; must not be {@code null}
   */
  public ParameterNameScorer(DomainVocabulary vocabulary) {
    this.genericDetector = new GenericTokenDetector(vocabulary);
    this.abbreviationDictionary = new AbbreviationDictionary(vocabulary);
  }

  public double score(String paramName) {
    var tokens = tokenizer.tokenize(paramName);
    if (tokens.isEmpty()) return 0.0;

    if (tokens.size() == 1) {
      return scoreSingleToken(tokens.get(0));
    }
    return scoreMultiToken(tokens);
  }

  private double scoreSingleToken(String token) {
    var generic = genericDetector.detect(token);
    return switch (generic.tier()) {
      case MEANINGLESS -> 0.0;
      case VAGUE -> 0.10;
      case TYPED_GENERIC -> 0.50;
      case NOT_GENERIC -> {
        var abbr = abbreviationDictionary.lookup(token);
        yield abbr != null ? 0.40 : 0.80;
      }
    };
  }

  private double scoreMultiToken(List<String> tokens) {
    boolean hasMeaningless =
        tokens.stream()
            .anyMatch(
                t -> genericDetector.detect(t).tier() == GenericTokenDetector.Tier.MEANINGLESS);
    if (hasMeaningless) return 0.15;

    double avgGeneric =
        tokens.stream()
            .mapToDouble(
                t -> {
                  var tier = genericDetector.detect(t).tier();
                  if (tier == GenericTokenDetector.Tier.TYPED_GENERIC) return 0.9;
                  return genericDetector.detect(t).score();
                })
            .average()
            .orElse(0.0);

    double abbreviation = scoreAbbreviations(tokens);

    boolean hasDomainToken =
        tokens.stream()
            .anyMatch(
                t -> genericDetector.detect(t).tier() == GenericTokenDetector.Tier.NOT_GENERIC);
    double domainBonus = hasDomainToken ? 1.0 : 0.7;

    return clamp(avgGeneric * 0.45 + abbreviation * 0.25 + domainBonus * 0.30);
  }

  private double scoreAbbreviations(List<String> tokens) {
    return tokens.stream()
        .mapToDouble(
            t -> {
              var entry = abbreviationDictionary.lookup(t);
              if (entry == null) return 1.0;
              return switch (entry.tier()) {
                case UNIVERSAL -> 1.0;
                case WELL_KNOWN -> 0.8;
                case AMBIGUOUS -> 0.5;
              };
            })
        .average()
        .orElse(1.0);
  }

  private static double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
