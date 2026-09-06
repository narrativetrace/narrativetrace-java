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
 * Scores class names based on role suffixes, domain vocabulary, and naming patterns.
 *
 * <p>The project's committed {@link DomainVocabulary} reaches every dictionary this scorer
 * consults, so its own nouns read as domain prefixes rather than broad ones.
 */
public final class ClassNameScorer {

  private static final double ROLE_SUFFIX_WEIGHT = 0.50;
  private static final double PREFIX_QUALITY_WEIGHT = 0.20;
  private static final double ABBREVIATION_WEIGHT = 0.10;
  private static final double TOKEN_COUNT_WEIGHT = 0.10;
  private static final double MORPHOLOGY_WEIGHT = 0.10;

  private final IdentifierTokenizer tokenizer = new IdentifierTokenizer();
  private final RoleSuffixDictionary roleSuffixDictionary = new RoleSuffixDictionary();
  private final GenericTokenDetector genericDetector;
  private final AbbreviationDictionary abbreviationDictionary;
  private final MorphologyAnalyzer morphologyAnalyzer;

  /** A scorer with no project vocabulary — the built-in dictionaries alone. */
  public ClassNameScorer() {
    this(DomainVocabulary.empty());
  }

  /**
   * @param vocabulary the project's declared vocabulary; must not be {@code null}
   */
  public ClassNameScorer(DomainVocabulary vocabulary) {
    this.genericDetector = new GenericTokenDetector(vocabulary);
    this.abbreviationDictionary = new AbbreviationDictionary(vocabulary);
    this.morphologyAnalyzer = new MorphologyAnalyzer(vocabulary);
  }

  public double score(String className) {
    var tokens = tokenizer.tokenize(className);
    if (tokens.isEmpty()) return 0.0;

    if (tokens.size() == 1) {
      return scoreSingleToken(tokens.get(0));
    }
    return scoreMultiToken(tokens);
  }

  private double scoreSingleToken(String token) {
    var roleResult = roleSuffixDictionary.classify(token);
    if (roleResult.category() == RoleSuffixDictionary.Category.GENERIC) {
      return 0.05;
    }
    if (roleResult.category() == RoleSuffixDictionary.Category.DESIGN_PATTERN
        || roleResult.category() == RoleSuffixDictionary.Category.FUNCTIONAL) {
      return 0.10;
    }
    var morph = morphologyAnalyzer.analyze(token);
    if (morph.partOfSpeech() == MorphologyAnalyzer.PartOfSpeech.ADJECTIVE) {
      return 0.85;
    }
    return 0.75;
  }

  private double scoreMultiToken(List<String> tokens) {
    var lastToken = tokens.get(tokens.size() - 1);
    var prefixTokens = tokens.subList(0, tokens.size() - 1);

    double roleSuffix = scoreRoleSuffix(lastToken);
    double prefixQuality = scorePrefixQuality(prefixTokens);
    double abbreviation = scoreAbbreviations(tokens);
    double tokenCount = scoreTokenCount(tokens.size());
    double morphology = scoreMorphology(lastToken);

    return clamp(
        roleSuffix * ROLE_SUFFIX_WEIGHT
            + prefixQuality * PREFIX_QUALITY_WEIGHT
            + abbreviation * ABBREVIATION_WEIGHT
            + tokenCount * TOKEN_COUNT_WEIGHT
            + morphology * MORPHOLOGY_WEIGHT);
  }

  private double scoreRoleSuffix(String lastToken) {
    var result = roleSuffixDictionary.classify(lastToken);
    return switch (result.category()) {
      case DESIGN_PATTERN, FUNCTIONAL -> 1.0;
      case GENERIC -> 0.3;
      case UNKNOWN -> 0.8;
    };
  }

  private double scorePrefixQuality(List<String> prefixTokens) {
    if (prefixTokens.isEmpty()) return 0.0;
    return prefixTokens.stream()
        .mapToDouble(
            t -> {
              var result = genericDetector.detect(t);
              return switch (result.tier()) {
                case MEANINGLESS -> 0.0;
                case VAGUE -> 0.2;
                case TYPED_GENERIC -> 0.7;
                case NOT_GENERIC -> 1.0;
              };
            })
        .average()
        .orElse(0.0);
  }

  private double scoreAbbreviations(List<String> tokens) {
    return tokens.stream()
        .mapToDouble(
            t -> {
              var entry = abbreviationDictionary.lookup(t);
              return entry != null ? entry.score() : 1.0;
            })
        .average()
        .orElse(1.0);
  }

  private double scoreTokenCount(int count) {
    if (count >= 2 && count <= 3) return 1.0;
    if (count == 1) return 0.5;
    return Math.max(0.0, 0.9 - 0.1 * (count - 3));
  }

  private double scoreMorphology(String lastToken) {
    var morph = morphologyAnalyzer.analyze(lastToken);
    return switch (morph.partOfSpeech()) {
      case NOUN -> 1.0;
      case ADJECTIVE -> 0.9;
      case VERB -> 0.3;
      case UNKNOWN -> 0.7;
    };
  }

  private static double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
