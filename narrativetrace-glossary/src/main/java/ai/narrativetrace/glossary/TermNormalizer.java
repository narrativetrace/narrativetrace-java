/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.clarity.IdentifierTokenizer;
import ai.narrativetrace.clarity.MorphologyAnalyzer;
import ai.narrativetrace.clarity.RoleSuffixDictionary;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Normalizes code identifiers into glossary phrase form.
 *
 * <p>INTENT: All identifier spellings of one concept must converge to a single normalized phrase —
 * {@code accountWithOverdraft}, {@code AccountWithOverdraft}, and {@code account_with_overdraft}
 * all become {@code "account with overdraft"} — because term and alias matching always happens on
 * the normalized form. Reuses the clarity module's {@link IdentifierTokenizer} (camelCase /
 * snake_case splitting) and {@link MorphologyAnalyzer} (verb detection); singularization is a
 * deliberately small English heuristic, applied to non-verb, non-stopword tokens.
 *
 * <p>Because normalized phrases are term identity in persisted glossaries, singularization must
 * never coin non-words from real ones ({@code alias} must not become {@code alia}) and must be
 * idempotent — every emitted token is a fixpoint of {@link #singularize}, so re-normalizing a
 * phrase is the identity (property-tested). Changing these rules re-keys existing glossaries and
 * must stay in lockstep across all ports.
 */
public final class TermNormalizer {

  /** Function words that must survive normalization untouched (never singularized). */
  private static final Set<String> STOPWORDS =
      Set.of(
          "with", "and", "or", "of", "to", "for", "by", "from", "in", "on", "at", "as", "was", "is",
          "has");

  /** Trailing patterns whose {@code es} suffix marks a plural ({@code boxes}, {@code classes}). */
  private static final List<String> ES_PLURAL_ENDINGS =
      List.of("ses", "xes", "zes", "ches", "shes");

  /**
   * Words ending in {@code s} that are singular ({@code alias}, {@code gas}, Latin {@code -us}
   * nouns), invariant plurals ({@code series}, {@code species}), or not nouns at all ({@code
   * always}) — never stripped. Also the only way an {@code s}-final {@code -es} stem is accepted
   * ({@code gases} → {@code gas}, {@code statuses} → {@code status}); an unlisted {@code s}-final
   * stem means the plural was built as {@code -se + s} ({@code clauses} → {@code clause}).
   */
  private static final Set<String> S_FINAL_SINGULARS =
      Set.of(
          "alias", "always", "atlas", "bias", "bonus", "bus", "campus", "canvas", "census", "chaos",
          "corpus", "focus", "gas", "lens", "locus", "news", "radius", "series", "species",
          "status", "surplus", "virus");

  /** Exception-type suffixes stripped when harvesting failure vocabulary. */
  private static final Set<String> EXCEPTION_SUFFIXES = Set.of("exception", "error");

  private final IdentifierTokenizer tokenizer = new IdentifierTokenizer();
  private final MorphologyAnalyzer morphology = new MorphologyAnalyzer();
  private final RoleSuffixDictionary roleSuffixes = new RoleSuffixDictionary();

  /**
   * Normalizes one identifier to phrase form: lowercase, space-separated, plural nouns
   * singularized.
   *
   * @param identifier camelCase, PascalCase, or snake_case identifier carrying at least one
   *     readable word
   * @return the normalized phrase; never blank
   * @throws IllegalArgumentException when the identifier is blank or carries no readable word
   */
  public String phrase(String identifier) {
    var normalized = String.join(" ", requireReadableTokens(identifier));
    assert !normalized.isBlank() : "normalization must never erase the identifier";
    assert normalized.equals(normalized.toLowerCase(java.util.Locale.ROOT))
        : "normalized phrase must be lowercase";
    return normalized;
  }

  /**
   * One harvestable phrase produced by normalization.
   *
   * @param phrase normalized phrase text
   * @param kind grammatical shape derived from structure (leading verb, token count)
   */
  public record Candidate(String phrase, TermKind kind) {

    public Candidate {
      if (phrase == null || phrase.isBlank()) {
        throw new IllegalArgumentException("phrase must not be blank");
      }
      if (kind == null) {
        throw new IllegalArgumentException("kind must not be null");
      }
    }
  }

  /**
   * Normalizes a method name into harvest candidates.
   *
   * <p>A method with a leading verb yields its verb phrase plus the object noun phrase (leading
   * stopwords dropped); any other method yields a single noun candidate.
   *
   * @param methodName method identifier carrying at least one readable word
   * @return one or two candidates, never empty
   * @throws IllegalArgumentException when the name is blank or carries no readable word
   */
  public List<Candidate> methodCandidates(String methodName) {
    var tokens = requireReadableTokens(methodName);
    if (!isVerb(tokens.get(0))) {
      return List.of(nounCandidate(tokens));
    }
    var candidates = new ArrayList<Candidate>();
    candidates.add(new Candidate(String.join(" ", tokens), TermKind.VERB_PHRASE));
    var object = withoutLeadingStopwords(tokens.subList(1, tokens.size()));
    if (!object.isEmpty()) {
      candidates.add(nounCandidate(object));
    }
    assert !candidates.isEmpty() : "a method always yields at least one candidate";
    return List.copyOf(candidates);
  }

  /**
   * Normalizes a parameter name into a noun candidate, stripping the trailing {@code id} role token
   * ({@code overdraftAccountId} → {@code "overdraft account"}).
   *
   * @param parameterName parameter identifier; must not be blank
   * @return the noun candidate, or empty when only the role token remains ({@code id})
   */
  public Optional<Candidate> parameterCandidate(String parameterName) {
    return strippedCandidate(parameterName, "id"::equals);
  }

  /**
   * Normalizes a class name into a noun candidate, stripping a recognized role suffix ({@code
   * OverdraftService} → {@code "overdraft"}).
   *
   * @param className simple class name; must not be blank
   * @return the noun candidate, or empty when only the role suffix remains ({@code Service})
   */
  public Optional<Candidate> classCandidate(String className) {
    return strippedCandidate(className, this::isRoleSuffix);
  }

  /**
   * Normalizes an exception type name into a noun candidate, stripping the {@code Exception} /
   * {@code Error} suffix ({@code InsufficientFundsException} → {@code "insufficient fund"}).
   *
   * @param exceptionTypeName simple exception type name; must not be blank
   * @return the noun candidate, or empty when only the suffix remains
   */
  public Optional<Candidate> exceptionCandidate(String exceptionTypeName) {
    return strippedCandidate(exceptionTypeName, EXCEPTION_SUFFIXES::contains);
  }

  /**
   * The shared body of the three {@code Optional}-returning normalizations.
   *
   * <p><b>@edgeCase</b> An identifier with no readable word — {@code __} is one, and the tokenizer
   * drops separators — yields no candidate rather than raising. These three already model "nothing
   * to harvest here" with an empty {@code Optional} (the case where only a role suffix remains), so
   * this is the same answer to the same question, and the caller was never promised a candidate.
   */
  private Optional<Candidate> strippedCandidate(
      String identifier, java.util.function.Predicate<String> trailingRole) {
    if (identifier == null || identifier.isBlank()) {
      throw new IllegalArgumentException("identifier must not be blank");
    }
    var tokens = normalizedTokens(identifier);
    if (tokens.isEmpty()) {
      return Optional.empty();
    }
    if (trailingRole.test(tokens.get(tokens.size() - 1))) {
      tokens = tokens.subList(0, tokens.size() - 1);
    }
    return tokens.isEmpty() ? Optional.empty() : Optional.of(nounCandidate(tokens));
  }

  private boolean isRoleSuffix(String token) {
    return roleSuffixes.classify(token).category() != RoleSuffixDictionary.Category.UNKNOWN;
  }

  private static List<String> withoutLeadingStopwords(List<String> tokens) {
    int start = 0;
    while (start < tokens.size() && STOPWORDS.contains(tokens.get(start))) {
      start++;
    }
    return tokens.subList(start, tokens.size());
  }

  private static Candidate nounCandidate(List<String> tokens) {
    var kind = tokens.size() == 1 ? TermKind.WORD : TermKind.NOUN_PHRASE;
    return new Candidate(String.join(" ", tokens), kind);
  }

  /**
   * The normalized tokens of an identifier that must have some, for the two methods whose contract
   * promises a non-blank answer.
   *
   * <p><b>@llmNote</b> "Not blank" is the wrong precondition and used to be the only one: {@code
   * __} is not blank, and the tokenizer reads no word in it, so the postcondition asserting that
   * normalization never erases an identifier failed instead — an {@code AssertionError} out of the
   * glossary harvest, which runs inside the user's own test run.
   *
   * @throws IllegalArgumentException when the identifier is blank or carries no readable word
   */
  private List<String> requireReadableTokens(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      throw new IllegalArgumentException("identifier must not be blank");
    }
    var tokens = normalizedTokens(identifier);
    if (tokens.isEmpty()) {
      throw new IllegalArgumentException(
          "identifier must carry at least one readable word: " + identifier);
    }
    return tokens;
  }

  private List<String> normalizedTokens(String identifier) {
    return tokenizer.tokenize(identifier).stream().map(this::normalizeToken).toList();
  }

  private String normalizeToken(String token) {
    if (STOPWORDS.contains(token) || isVerb(token)) {
      return token;
    }
    return singularize(token);
  }

  private boolean isVerb(String token) {
    return morphology.analyze(token).partOfSpeech() == MorphologyAnalyzer.PartOfSpeech.VERB;
  }

  private static String singularize(String token) {
    if (S_FINAL_SINGULARS.contains(token)) {
      return token;
    }
    if (token.endsWith("ies") && token.length() > 3) {
      return token.substring(0, token.length() - 3) + "y";
    }
    if (ES_PLURAL_ENDINGS.stream().anyMatch(token::endsWith) && token.length() > 3) {
      var stem = token.substring(0, token.length() - 2);
      if (isStableSingular(stem)) {
        return stem;
      }
      // An unstable "ses" stem means the plural was built as -se + s ("cases", "responses"):
      // fall through to the single-s rule so the e survives.
    }
    if (token.endsWith("s") && token.length() > 1 && !keepsTrailingS(token)) {
      return token.substring(0, token.length() - 1);
    }
    return token;
  }

  /**
   * A stem is an acceptable singular iff {@link #singularize} would return it unchanged. Unlike
   * {@link #keepsTrailingS}, an {@code us}/{@code is} ending does not bless a stem: those endings
   * usually mean the plural was {@code -se + s} ({@code clauses} → {@code claus}, {@code promises}
   * → {@code promis}), so only {@code ss} stems and listed words qualify.
   */
  private static boolean isStableSingular(String stem) {
    return !stem.endsWith("s") || stem.endsWith("ss") || S_FINAL_SINGULARS.contains(stem);
  }

  /** Words ending in ss/us/is are not English plurals ({@code status}, {@code analysis}). */
  private static boolean keepsTrailingS(String token) {
    return token.endsWith("ss") || token.endsWith("us") || token.endsWith("is");
  }
}
