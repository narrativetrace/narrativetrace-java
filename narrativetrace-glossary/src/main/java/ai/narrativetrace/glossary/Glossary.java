/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The whole domain glossary of one repository: bounded contexts plus canonical terms.
 *
 * <p>INTENT: The in-memory form of {@code glossary.json} — simultaneously the translation
 * dictionary, the reviewable domain documentation, and the vocabulary norm clarity diagnostics
 * enforce (ADR-012).
 *
 * <p>Structural invariants are enforced at construction, so an inconsistent glossary can never
 * exist: term identity {@code (context, term)} is unique, every term's context is declared, and no
 * deprecated alias equals a canonical term within the same context. Terms are canonicalized to
 * {@code (context, term)} order at construction, so two glossaries with the same vocabulary are
 * equal regardless of insertion order.
 *
 * <p>Accepted project shorthand is a <em>declared</em> section, not a side effect of which tokens
 * happen to appear inside committed phrases: committing {@code calc total} says nothing about
 * whether {@code calc} is acceptable on its own, and only {@code abbreviations} does.
 *
 * @param schemaVersion glossary file schema version — <b>canonicalized</b>: whatever is supplied
 *     (it must be at least 1) the stored value is 2 when {@code abbreviations} is non-empty and 1
 *     otherwise, because the version describes which shape the content needs
 * @param contexts bounded contexts keyed by context name
 * @param abbreviations accepted project shorthand, abbreviation to its spelled-out expansion;
 *     schema 2. Human-owned — harvesting never writes it
 * @param terms canonical terms; identity is {@code (context, term)}
 */
public record Glossary(
    int schemaVersion,
    Map<String, BoundedContext> contexts,
    Map<String, String> abbreviations,
    List<GlossaryTerm> terms) {

  /** The shape before accepted shorthand was declarable. */
  private static final int SCHEMA_WITHOUT_ABBREVIATIONS = 1;

  /** The shape that carries the root-level {@code abbreviations} section. */
  private static final int SCHEMA_ABBREVIATIONS = 2;

  /** Compatibility constructor for the schema-1 shape, which declares no abbreviations. */
  public Glossary(
      int schemaVersion, Map<String, BoundedContext> contexts, List<GlossaryTerm> terms) {
    this(schemaVersion, contexts, Map.of(), terms);
  }

  public Glossary {
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be at least 1: " + schemaVersion);
    }
    contexts = Map.copyOf(contexts);
    abbreviations = canonicalAbbreviations(abbreviations);
    schemaVersion = schemaVersionFor(abbreviations);
    terms = terms.stream().sorted(TERM_ORDER).toList();
    var violation = findViolation(contexts, terms);
    if (violation.isPresent()) {
      throw new IllegalArgumentException(violation.get());
    }
  }

  /**
   * The version is <em>derived</em>, never stored independently: schema 2 exists only for the
   * {@code abbreviations} section, so holding a version that the content does not need would be
   * redundant state that can disagree with itself. Deriving it is also what keeps a repository that
   * never used the feature byte-identical when a schema-2-aware writer touches its file.
   */
  private static int schemaVersionFor(Map<String, String> abbreviations) {
    return abbreviations.isEmpty() ? SCHEMA_WITHOUT_ABBREVIATIONS : SCHEMA_ABBREVIATIONS;
  }

  /**
   * Lowercases and trims every abbreviation so a declared {@code FX} answers a token spelled {@code
   * fx}, and refuses the ambiguities that canonicalization would otherwise hide.
   */
  private static Map<String, String> canonicalAbbreviations(Map<String, String> raw) {
    var canonical = new LinkedHashMap<String, String>();
    for (var entry : raw.entrySet()) {
      var abbreviation = requireToken(entry.getKey());
      var expansion = requireExpansion(entry.getValue(), abbreviation);
      if (canonical.put(abbreviation, expansion) != null) {
        throw new IllegalArgumentException(
            "duplicate abbreviation after normalization: " + abbreviation);
      }
    }
    return Map.copyOf(canonical);
  }

  private static String requireToken(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("abbreviation must not be blank");
    }
    var token = key.trim().toLowerCase(Locale.ROOT);
    if (token.chars().anyMatch(Character::isWhitespace)) {
      throw new IllegalArgumentException("abbreviation must be a single token, was '" + key + "'");
    }
    return token;
  }

  private static String requireExpansion(String value, String abbreviation) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("expansion of '" + abbreviation + "' must not be blank");
    }
    return value.trim();
  }

  /** Canonical term order of the glossary file: context name first, then term text. */
  public static final Comparator<GlossaryTerm> TERM_ORDER =
      Comparator.comparing(GlossaryTerm::context).thenComparing(GlossaryTerm::term);

  /** Returns a description of the first structural violation, or empty when consistent. */
  private static Optional<String> findViolation(
      Map<String, BoundedContext> contexts, List<GlossaryTerm> terms) {
    return findDuplicateTermKey(terms)
        .or(() -> findUndeclaredContext(contexts, terms))
        .or(() -> findAliasCollision(terms));
  }

  private static Optional<String> findDuplicateTermKey(List<GlossaryTerm> terms) {
    var seen = new HashSet<TermKey>();
    for (var term : terms) {
      if (!seen.add(TermKey.of(term))) {
        return Optional.of("duplicate term key: " + TermKey.of(term));
      }
    }
    return Optional.empty();
  }

  private static Optional<String> findUndeclaredContext(
      Map<String, BoundedContext> contexts, List<GlossaryTerm> terms) {
    for (var term : terms) {
      if (!contexts.containsKey(term.context())) {
        return Optional.of(
            "term '" + term.term() + "' references undeclared context '" + term.context() + "'");
      }
    }
    return Optional.empty();
  }

  private static Optional<String> findAliasCollision(List<GlossaryTerm> terms) {
    var termKeys = new HashSet<TermKey>();
    terms.forEach(term -> termKeys.add(TermKey.of(term)));
    for (var term : terms) {
      for (var synonym : term.synonyms()) {
        if (termKeys.contains(new TermKey(term.context(), synonym.alias()))) {
          return Optional.of(
              "alias '"
                  + synonym.alias()
                  + "' equals a canonical term in context '"
                  + term.context()
                  + "'");
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Returns whether this glossary is structurally consistent.
   *
   * <p>Constructor guards make this always true for live instances; the method re-checks the same
   * rules for test-time invariant verification.
   */
  boolean invariant() {
    return schemaVersion == schemaVersionFor(abbreviations)
        && findViolation(contexts, terms).isEmpty()
        && abbreviations.entrySet().stream().allMatch(Glossary::isCanonicalAbbreviation);
  }

  private static boolean isCanonicalAbbreviation(Map.Entry<String, String> entry) {
    var abbreviation = entry.getKey();
    return abbreviation != null
        && !abbreviation.isBlank()
        && abbreviation.equals(abbreviation.trim().toLowerCase(Locale.ROOT))
        && abbreviation.chars().noneMatch(Character::isWhitespace)
        && entry.getValue() != null
        && !entry.getValue().isBlank();
  }
}
