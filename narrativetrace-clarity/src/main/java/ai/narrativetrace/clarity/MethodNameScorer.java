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
 * Scores method names based on verb quality, domain vocabulary, and naming conventions.
 *
 * <p>The project's committed {@link DomainVocabulary} reaches every dictionary this scorer
 * consults, so a domain verb the built-in tiers never heard of stops scoring as unknown.
 */
public final class MethodNameScorer {

  private static final double VERB_QUALITY_WEIGHT = 0.45;
  private static final double TOKEN_SPECIFICITY_WEIGHT = 0.15;
  private static final double ABBREVIATION_WEIGHT = 0.10;
  private static final double TOKEN_COUNT_WEIGHT = 0.15;
  private static final double MORPHOLOGY_WEIGHT = 0.15;

  private static final List<String> VERB_SUFFIXES = List.of("ate", "ize", "ise", "ify", "en");

  private final IdentifierTokenizer tokenizer = new IdentifierTokenizer();
  private final CollocationDictionary collocationDictionary = new CollocationDictionary();
  private final VerbDictionary verbDictionary;
  private final GenericTokenDetector genericDetector;
  private final AbbreviationDictionary abbreviationDictionary;
  private final MorphologyAnalyzer morphologyAnalyzer;

  /** A scorer with no project vocabulary — the built-in dictionaries alone. */
  public MethodNameScorer() {
    this(DomainVocabulary.empty());
  }

  /**
   * @param vocabulary the project's declared vocabulary; must not be {@code null}
   */
  public MethodNameScorer(DomainVocabulary vocabulary) {
    this.verbDictionary = new VerbDictionary(vocabulary);
    this.genericDetector = new GenericTokenDetector(vocabulary);
    this.abbreviationDictionary = new AbbreviationDictionary(vocabulary);
    this.morphologyAnalyzer = new MorphologyAnalyzer(vocabulary);
  }

  public CollocationDictionary collocationDictionary() {
    return collocationDictionary;
  }

  public double score(String methodName) {
    var tokens = tokenizer.tokenize(methodName);
    if (tokens.isEmpty()) return 0.0;

    if (tokens.size() == 1) {
      return scoreSingleToken(tokens.get(0));
    }
    return scoreMultiToken(tokens);
  }

  private double scoreSingleToken(String token) {
    var verbResult = verbDictionary.categorize(token);
    double base =
        switch (verbResult.category()) {
          case GENERIC -> 0.10;
          case BOOLEAN_PREFIX -> 0.40;
          case STANDARD -> 0.45;
          case DOMAIN -> 0.60;
          case UNKNOWN -> {
            var morph = morphologyAnalyzer.analyze(token);
            yield morph.partOfSpeech() == MorphologyAnalyzer.PartOfSpeech.VERB ? 0.55 : 0.50;
          }
        };
    var abbr = abbreviationDictionary.lookup(token);
    if (abbr != null) base *= abbr.score();
    return clamp(base);
  }

  private double scoreMultiToken(List<String> tokens) {
    var firstToken = tokens.get(0);
    double verbQuality = scoreVerbQuality(firstToken);
    double tokenSpecificity = scoreTokenSpecificity(tokens);
    double abbreviation = scoreAbbreviations(tokens);
    double tokenCount = scoreTokenCount(tokens.size());
    double morphology = scoreMorphology(firstToken);

    return clamp(
        verbQuality * VERB_QUALITY_WEIGHT
            + tokenSpecificity * TOKEN_SPECIFICITY_WEIGHT
            + abbreviation * ABBREVIATION_WEIGHT
            + tokenCount * TOKEN_COUNT_WEIGHT
            + morphology * MORPHOLOGY_WEIGHT);
  }

  private double scoreVerbQuality(String firstToken) {
    var result = verbDictionary.categorize(firstToken);
    if (result.category() == VerbDictionary.Category.UNKNOWN) {
      var morphResult = morphologyAnalyzer.analyze(firstToken);
      if (morphResult.partOfSpeech() == MorphologyAnalyzer.PartOfSpeech.VERB) {
        return 0.6;
      }
      return 0.4;
    }
    return result.score();
  }

  private double scoreTokenSpecificity(List<String> tokens) {
    var nonVerbTokens = tokens.subList(1, tokens.size());
    if (nonVerbTokens.isEmpty()) return 0.0;
    return nonVerbTokens.stream()
        .mapToDouble(t -> genericDetector.detect(t).score())
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
    if (count >= 2 && count <= 4) return 1.0;
    if (count == 1) return 0.6;
    return Math.max(0.0, 0.7 - 0.15 * (count - 4));
  }

  private double scoreMorphology(String firstToken) {
    var verbResult = verbDictionary.categorize(firstToken);
    boolean inDictionary = verbResult.category() != VerbDictionary.Category.UNKNOWN;
    boolean isGeneric = verbResult.category() == VerbDictionary.Category.GENERIC;

    if (isGeneric) return 0.3;

    boolean hasSuffix = hasVerbSuffix(firstToken);
    if (inDictionary && hasSuffix) return 1.0;
    if (inDictionary || hasSuffix) return 0.8;
    return 0.3;
  }

  private boolean hasVerbSuffix(String token) {
    if (token.length() < 4) return false;
    for (var suffix : VERB_SUFFIXES) {
      if (token.endsWith(suffix) && token.length() > suffix.length()) {
        return true;
      }
    }
    return false;
  }

  private static double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
