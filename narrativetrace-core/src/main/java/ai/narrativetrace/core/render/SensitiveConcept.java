/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One sensitive concept: what it is called, in which languages, how each of those names matches,
 * and what its values look like.
 *
 * <p>INTENT: The deny-list used to be a flat bag of strings, and a flat bag cannot express a
 * <em>half-covered</em> concept. "US Social Security Number" shipped as the single English
 * abbreviation {@code ssn}: {@code socialSecurityNumber} did not match it, no other language was
 * present, and no value shape recognised {@code 123-45-6789} arriving under an innocuous name.
 * Nothing recorded that those three facts were missing, because there was nowhere to record them.
 * Making the concept the unit gives each of them a field, and {@link #valueShape} a required one.
 *
 * <p><b>@llmNote</b> This type is the <em>authoring</em> surface, not the matching one. {@link
 * RedactionPolicy} flattens the vocabulary into its two lookup sets once, during class
 * initialisation, and the matching hot path is untouched. Adding a concept — or a whole language —
 * therefore costs nothing per traced call.
 *
 * <p><b>@edgeCase</b> Nothing here is ever read from disk. The vocabulary is compiled in, so there
 * is no file an attacker or a careless deploy can edit to weaken redaction. That is why this is a
 * Java type and not the data file an earlier draft proposed.
 *
 * @param id stable identifier, kebab-case, used by tests and the cross-port comparison
 * @param category what kind of secret this is, for coverage reporting rather than matching
 * @param names the terms this concept goes by, per language
 * @param valueShape how a value is recognised without its name, or an explicit {@link
 *     ValueShape.None} recording why it cannot be
 */
record SensitiveConcept(
    String id, Category category, Map<Language, Set<Term>> names, ValueShape valueShape) {

  /**
   * One word a concept goes by, and how that particular word is compared.
   *
   * <p><b>@edgeCase</b> The mode belongs to the <em>term</em>, not the concept, and the password
   * concept is why. {@code password} must match as a substring so {@code userPassword} is caught,
   * while the Portuguese {@code senha} must match only whole identifier tokens because it sits
   * inside {@code chosenHash} and {@code frozenHash} across the camel-case seam. One concept, two
   * modes. Hoisting the mode to the concept would silently re-broaden {@code senha} and blank
   * ordinary fields.
   *
   * @param text the word, already {@link SecretValueShapes#canonical canonical} — lower-cased and
   *     accent-folded
   * @param match how this word is compared against an identifier
   */
  record Term(String text, MatchMode match) {

    /** A word matched anywhere inside an identifier: {@code token} catches {@code paymentToken}. */
    static Term of(String text) {
      return new Term(text, MatchMode.SUBSTRING);
    }

    /**
     * A word matched only as a whole identifier token.
     *
     * <p>For the short ones that live inside ordinary business vocabulary — {@code pan} inside
     * {@code company} and {@code span}, {@code nir} inside {@code nirvana}, {@code senha} inside
     * {@code chosenHash}. Substring-matching any of these blanks half a trace.
     */
    static Term token(String text) {
      return new Term(text, MatchMode.IDENTIFIER_TOKEN);
    }
  }

  /** What kind of secret a concept is. Drives coverage reporting, not matching. */
  enum Category {
    /** Something that authenticates or authorises: passwords, tokens, keys. */
    CREDENTIAL,
    /** A government-issued identifier for a person. */
    NATIONAL_ID,
    /** Payment instruments and the numbers that move money. */
    PAYMENT,
    /** Session and correlation material that grants access if replayed. */
    SESSION
  }

  /** How one term is compared against an identifier. */
  enum MatchMode {
    /** Contained anywhere in the name. */
    SUBSTRING,
    /** Equal to the whole name, or to one of its identifier tokens. */
    IDENTIFIER_TOKEN
  }

  /**
   * Languages the vocabulary is maintained in.
   *
   * <p><b>@llmNote</b> Adding one is a column to fill across every concept — a finite, reviewable
   * list that {@code SensitiveVocabularyCoverageTest} reports on — rather than an open-ended effort
   * to recall every word in that language for a secret.
   */
  enum Language {
    EN,
    ES,
    PT,
    FR,
    DE,
    ZH
  }

  /**
   * How a concept is recognised from its value alone, independent of the field name.
   *
   * <p>INTENT: This is the field that makes an absence visible. A concept cannot be declared
   * without answering "and what does one look like?", so "nobody wrote a shape for US SSN" stops
   * being an invisible hole and becomes a {@link None} that had to be justified in review.
   */
  sealed interface ValueShape {

    /**
     * The value axis recognises this concept.
     *
     * @param shapeId names the check in {@link SecretValueShapes} or {@link NationalIdShapes}
     */
    record Detected(String shapeId) implements ValueShape {}

    /**
     * The value axis deliberately does not recognise this concept.
     *
     * @param reason why not — either the concept has no recognisable shape (a password is any
     *     string at all), or recognising it would collide with ordinary data (bare nine digits is
     *     every order number ever written)
     */
    record None(String reason) implements ValueShape {}
  }

  /** Every term of this concept, in every language. */
  Set<Term> allTerms() {
    return names.values().stream().flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
  }
}
