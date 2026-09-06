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
 * Analyzes word morphology for verb tense detection and base form extraction.
 *
 * <p>Verb detection defers to {@link VerbDictionary}, so a project's declared verbs are recognized
 * here too — {@code fold} reads as a verb in a project whose glossary says so.
 */
public final class MorphologyAnalyzer {

  public enum PartOfSpeech {
    VERB,
    NOUN,
    ADJECTIVE,
    UNKNOWN
  }

  public record Result(PartOfSpeech partOfSpeech) {}

  private static final int MIN_SUFFIX_LENGTH = 4;

  private static final List<String> VERB_SUFFIXES = List.of("ate", "ize", "ise", "ify", "en");

  private static final List<String> NOUN_SUFFIXES =
      List.of(
          "tion", "sion", "ment", "ness", "ity", "ance", "ence", "er", "or", "ary", "ery", "ory");

  private static final List<String> ADJECTIVE_SUFFIXES =
      List.of("able", "ible", "ive", "ous", "al", "ic", "ed");

  private final VerbDictionary verbDictionary;

  /** An analyzer with no project vocabulary — the built-in dictionaries alone. */
  public MorphologyAnalyzer() {
    this(DomainVocabulary.empty());
  }

  /**
   * @param vocabulary the project's declared vocabulary; must not be {@code null}
   */
  public MorphologyAnalyzer(DomainVocabulary vocabulary) {
    this.verbDictionary = new VerbDictionary(vocabulary);
  }

  public Result analyze(String token) {
    var lower = token.toLowerCase(java.util.Locale.ROOT);

    // VerbDictionary takes precedence
    var verbResult = verbDictionary.categorize(lower);
    if (verbResult.category() != VerbDictionary.Category.UNKNOWN) {
      return new Result(PartOfSpeech.VERB);
    }

    if (lower.length() < MIN_SUFFIX_LENGTH) {
      return new Result(PartOfSpeech.UNKNOWN);
    }

    // Check suffixes in priority order: verb > noun > adjective
    if (matchesSuffix(lower, VERB_SUFFIXES)) {
      return new Result(PartOfSpeech.VERB);
    }
    if (matchesSuffix(lower, NOUN_SUFFIXES)) {
      return new Result(PartOfSpeech.NOUN);
    }
    if (matchesSuffix(lower, ADJECTIVE_SUFFIXES)) {
      return new Result(PartOfSpeech.ADJECTIVE);
    }

    return new Result(PartOfSpeech.UNKNOWN);
  }

  private boolean matchesSuffix(String word, List<String> suffixes) {
    for (var suffix : suffixes) {
      if (word.endsWith(suffix) && word.length() > suffix.length()) {
        return true;
      }
    }
    return false;
  }
}
