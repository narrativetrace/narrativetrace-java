/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The vocabulary one project has declared to be its own, as the clarity scorers see it.
 *
 * <p>INTENT: Make the built-in dictionaries extensible per project without a second configuration
 * file. A repository's committed glossary (ADR-012) is the single vocabulary source; the glossary
 * module maps it onto this type, and every scorer consults it beside {@link VerbDictionary}, {@link
 * GenericTokenDetector} and {@link AbbreviationDictionary}. Clarity therefore teaches in the
 * project's own language instead of scoring its domain words as unknown.
 *
 * <p>Three rules are deliberate and load-bearing:
 *
 * <ul>
 *   <li><b>Only single tokens count.</b> The scorers tokenize identifiers, so a multi-word glossary
 *       phrase ({@code credit tranche}) can never match one token and is dropped at construction
 *       rather than silently never matching.
 *   <li><b>The built-in dictionaries keep their authority.</b> This type answers questions; it does
 *       not override answers. Each dictionary decides where the project vocabulary sits in its own
 *       precedence order, and none lets a project promote a generic word to domain vocabulary.
 *   <li><b>Accepted shorthand is its own set, not an inference over the other two.</b> Appearing as
 *       a token of some committed phrase is not a decision that the token is acceptable alone: a
 *       project that commits {@code calc total} has said nothing about {@code calc}. Only the
 *       glossary's {@code abbreviations} section says that.
 * </ul>
 *
 * <p><b>@edgeCase</b> An empty vocabulary is the normal state — a project without a committed
 * glossary scores exactly as it did before this type existed.
 *
 * @param verbs normalized lowercase single-token verbs the project declares
 * @param nouns normalized lowercase single-token nouns the project declares
 * @param abbreviations accepted shorthand, normalized lowercase token to its spelled-out expansion
 */
public record DomainVocabulary(
    Set<String> verbs, Set<String> nouns, Map<String, String> abbreviations) {

  private static final DomainVocabulary EMPTY = new DomainVocabulary(Set.of(), Set.of(), Map.of());

  /** Compatibility constructor for callers that declare no accepted shorthand. */
  public DomainVocabulary(Set<String> verbs, Set<String> nouns) {
    this(verbs, nouns, Map.of());
  }

  public DomainVocabulary {
    if (verbs == null) {
      throw new IllegalArgumentException("verbs must not be null");
    }
    if (nouns == null) {
      throw new IllegalArgumentException("nouns must not be null");
    }
    if (abbreviations == null) {
      throw new IllegalArgumentException("abbreviations must not be null");
    }
    verbs = singleTokens(verbs);
    nouns = singleTokens(nouns);
    abbreviations = singleTokenKeys(abbreviations);
  }

  /** The vocabulary of a project that has declared none — the default everywhere. */
  public static DomainVocabulary empty() {
    return EMPTY;
  }

  /**
   * Builds a vocabulary from raw declared terms, keeping only the single-token ones.
   *
   * @param verbs verb terms; must not be {@code null}
   * @param nouns noun terms; must not be {@code null}
   * @return the vocabulary, normalized to lowercase single tokens
   */
  public static DomainVocabulary of(Set<String> verbs, Set<String> nouns) {
    return new DomainVocabulary(verbs, nouns, Map.of());
  }

  /**
   * Builds a vocabulary from raw declared terms and the project's accepted shorthand.
   *
   * @param verbs verb terms; must not be {@code null}
   * @param nouns noun terms; must not be {@code null}
   * @param abbreviations accepted shorthand to its expansion; must not be {@code null}
   * @return the vocabulary, normalized to lowercase single tokens
   */
  public static DomainVocabulary of(
      Set<String> verbs, Set<String> nouns, Map<String, String> abbreviations) {
    return new DomainVocabulary(verbs, nouns, abbreviations);
  }

  /** Returns whether the project declared nothing this type can answer for. */
  public boolean isEmpty() {
    return verbs.isEmpty() && nouns.isEmpty() && abbreviations.isEmpty();
  }

  /**
   * Returns whether the project declared this token as one of its verbs.
   *
   * @param token single identifier token; must not be {@code null}
   */
  public boolean isDomainVerb(String token) {
    return verbs.contains(lowercase(token));
  }

  /**
   * Returns whether the project declared this token as one of its nouns.
   *
   * @param token single identifier token; must not be {@code null}
   */
  public boolean isDomainNoun(String token) {
    return nouns.contains(lowercase(token));
  }

  /**
   * Returns whether the project listed this token in its glossary's {@code abbreviations} section.
   *
   * <p>This is the <em>only</em> way a project accepts its own shorthand ({@code fx}, {@code
   * calc}), and it is a decision someone wrote down rather than a side effect of which tokens
   * happen to appear inside committed phrases. An accepted abbreviation is not asked to be spelled
   * out; it is spelled out <em>for</em> the reader, from {@link #expansionOf(String)}.
   *
   * @param token single identifier token; must not be {@code null}
   */
  public boolean isAcceptedAbbreviation(String token) {
    return abbreviations.containsKey(lowercase(token));
  }

  /**
   * Returns what the project says this accepted abbreviation stands for.
   *
   * @param token single identifier token; must not be {@code null}
   * @return the declared expansion, or {@code null} when the project did not declare this token
   */
  public String expansionOf(String token) {
    return abbreviations.get(lowercase(token));
  }

  private static String lowercase(String token) {
    if (token == null) {
      throw new IllegalArgumentException("token must not be null");
    }
    return token.toLowerCase(Locale.ROOT);
  }

  private static Set<String> singleTokens(Set<String> terms) {
    return terms.stream()
        .map(term -> term.trim().toLowerCase(Locale.ROOT))
        .filter(term -> !term.isEmpty() && !term.contains(" "))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Same normalization on the abbreviation side; a multi-word key could never match a token. */
  private static Map<String, String> singleTokenKeys(Map<String, String> raw) {
    var normalized = new LinkedHashMap<String, String>();
    for (var entry : raw.entrySet()) {
      var token = entry.getKey().trim().toLowerCase(Locale.ROOT);
      if (!token.isEmpty() && !token.contains(" ") && entry.getValue() != null) {
        normalized.put(token, entry.getValue());
      }
    }
    return Map.copyOf(normalized);
  }
}
