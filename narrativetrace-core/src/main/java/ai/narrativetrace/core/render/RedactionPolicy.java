/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Name-based deny-list that decides whether a reflectively-introspected field value must be hidden.
 *
 * <p>INTENT: Reflective introspection is sensitive-data-by-default — a POJO/record/{@code Map}
 * without a curated {@code toString()} otherwise leaks every field value into traces, logs, and
 * exports. This policy redacts values whose <em>field name</em> matches a known-sensitive pattern,
 * so secrets nested inside DTOs are hidden without requiring an annotation on every one.
 *
 * <p><b>@llmNote</b> Matching is a case- and accent-insensitive substring test against {@link
 * #DEFAULT} patterns ({@code password}, {@code cvv}, {@code ssn}, {@code token}, {@code secret},
 * {@code authorization}, {@code cardnumber}, {@code sessionid}, {@code jwt}, &hellip;). Substring
 * (not exact) is deliberate — it catches {@code userPassword}, {@code cardCvv}, {@code apiToken} —
 * erring toward over-redaction, which is the safe default for a security control. Teams that trace
 * fields colliding with a pattern override the set via {@link #ofPatterns}, widen it from outside
 * the build via {@link AdditionalRedactionPatterns}, or opt out with {@link #DISABLED}.
 *
 * <p><b>@llmNote</b> The default vocabulary is <em>multilingual and always on</em> — Spanish,
 * Portuguese, French and Chinese words sit in {@link #DEFAULT_PATTERNS} beside the English ones,
 * with no locale to select and nothing to opt into. A deny-list that only reads English hides a
 * {@code password} field and shows the {@code contrase\u00f1a} beside it, which is not a weaker
 * guarantee but a differently-distributed one: it protects whoever happens to name fields in the
 * language the list was written in. Names are folded through {@link SecretValueShapes#canonical},
 * so the accented and unaccented spellings of a word are one pattern rather than two.
 *
 * <p><b>@edgeCase</b> Ten of the patterns are matched on identifier-token boundaries instead of as
 * substrings: {@code pan} and {@code iban} are short enough that the substring rule would have
 * redacted {@code companyName}, {@code expansionRatio}, {@code panelId} and {@code japaneseAddress}
 * — a security default that silently blanks ordinary business fields is one teams switch off
 * entirely. Eight non-English words earned the same treatment for the same reason. See {@link
 * #WORD_PATTERNS}.
 *
 * <p><b>@edgeCase</b> Narrow whenever in doubt: two words that started out short and token-matched
 * — Spanish {@code clave} and French {@code carte} — turned out to name their own ordinary business
 * fields ({@code clavePrimaria}/{@code claveForanea}, {@code carteGraphique}/{@code carteRoutiere})
 * rather than colliding with someone else's. A token match cannot rescue a word whose own compounds
 * are the false positive, so both were narrowed to the specific compounds that are credentials —
 * {@code claveAcceso}/{@code claveSecreta} and {@code carteBancaire}/{@code numeroCarte} — and
 * moved into {@link #DEFAULT_PATTERNS} as substrings, since a whole word that long needs no token
 * boundary to stay safe.
 *
 * <p><b>@llmNote</b> There is a second, independent axis: {@link #shouldRedactValue} asks whether
 * the <em>value</em> is shaped like a credential, because a bearer token can arrive under any name
 * at all. That axis lives in {@link SecretValueShapes} and is off only for {@link #DISABLED}.
 */
public final class RedactionPolicy {

  /**
   * Marker emitted in place of a redacted value, matching the {@code @NotTraced} parameter marker.
   */
  public static final String MARKER = "[REDACTED]";

  /**
   * Names matched anywhere inside an identifier, flattened from {@link SensitiveVocabulary}.
   *
   * <p><b>@llmNote</b> The vocabulary is a list of concepts so that a half-covered one — a name in
   * one language and no value shape — is visible to a reader and to a test. Matching never walks
   * it: it is flattened here once, during class initialisation, and every traced call sees only
   * these two flat sets. The concept structure therefore costs nothing per call, and the vocabulary
   * can grow to any number of languages without touching steady-state performance.
   */
  private static final Set<String> DEFAULT_PATTERNS = SensitiveVocabulary.substringTerms();

  /**
   * Patterns matched against whole identifier tokens rather than as substrings.
   *
   * <p><b>@llmNote</b> Only names too short to be safe as substrings belong here. {@code pan} is
   * three letters and appears inside {@code company}, {@code expansion}, {@code panel}, {@code
   * span} and {@code japanese}; {@code iban} appears inside {@code caribbean}. Tokenising the field
   * name first means {@code cardPan}, {@code card_pan}, {@code PAN} and {@code panNumber} all match
   * while {@code companyName} does not.
   *
   * <p><b>@edgeCase</b> Every non-English word here earned its place by colliding with a real
   * business field, which is why the list is not simply "the short ones": {@code rut} is inside
   * {@code truth}, {@code brute} and {@code scrutiny}; {@code cuit} inside {@code circuit} and
   * {@code biscuit}; {@code dni} inside {@code midnight}; {@code nir} inside {@code nirvana};
   * {@code mima} inside {@code semiMajorAxis} once the case boundary is lower-cased away. {@code
   * senha} is the subtle one — no English word contains it, but {@code chosenHash} and {@code
   * frozenHash} do, across the camel-case seam. {@code cpf} and {@code cnpj} are here for length
   * alone.
   *
   * <p><b>@edgeCase</b> {@code clave} and {@code carte} do <em>not</em> belong here, even though
   * both are short — see the "narrow whenever in doubt" note on the class. A token match only
   * protects a short word from *someone else's* compound; it cannot protect a codebase's own {@code
   * clavePrimaria} or {@code carteGraphique} from a pattern that <em>is</em> their prefix. Both
   * live in {@link #DEFAULT_PATTERNS} instead, spelled out as the specific compounds that are
   * credentials.
   */
  private static final Set<String> WORD_PATTERNS = SensitiveVocabulary.identifierTokenTerms();

  /**
   * Splits an identifier into words: on any non-alphanumeric run, on a lower-to-upper transition
   * ({@code cardPan}), and at the end of an acronym ({@code PANNumber}).
   */
  private static final Pattern TOKEN_BOUNDARY =
      Pattern.compile("[^A-Za-z0-9]+|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

  /** Secure default: redacts values for common sensitive field-name patterns. */
  public static final RedactionPolicy DEFAULT = defaults();

  /**
   * Builds the default policy, reading {@link AdditionalRedactionPatterns} as it goes.
   *
   * <p><b>@llmNote</b> Package-private and separate from {@link #DEFAULT} only so a test can build
   * the default policy again after setting the property. {@link #DEFAULT} itself is constructed
   * once, during class initialisation, which is the moment the knob is read for it.
   *
   * @return a policy carrying the built-in vocabulary plus whatever the operator appended
   */
  static RedactionPolicy defaults() {
    return new RedactionPolicy(
        union(DEFAULT_PATTERNS, AdditionalRedactionPatterns.configured()), WORD_PATTERNS, true);
  }

  /** Opt-out policy that redacts nothing by name (annotations are still honored elsewhere). */
  public static final RedactionPolicy DISABLED = new RedactionPolicy(Set.of(), Set.of(), false);

  private final Set<String> lowerPatterns;
  private final Set<String> wordPatterns;
  private final boolean maskSecretValueShapes;

  private RedactionPolicy(Set<String> patterns, Set<String> words, boolean maskSecretValueShapes) {
    this.lowerPatterns = canonicalise(patterns);
    this.wordPatterns = canonicalise(words);
    this.maskSecretValueShapes = maskSecretValueShapes;
  }

  private static Set<String> canonicalise(Set<String> patterns) {
    return patterns.stream()
        .map(SecretValueShapes::canonical)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Creates a policy with a custom set of case-insensitive substring patterns, replacing the
   * defaults entirely.
   *
   * <p><b>@edgeCase</b> Replaces the <em>name</em> deny-list only. Value-shape masking stays on,
   * because it answers a different question — "are these bytes a credential?" — that a custom name
   * list is not an opinion about. {@link #DISABLED} is the way to turn everything off.
   *
   * <p><b>@edgeCase</b> "Exactly the supplied patterns" is exactly true of the application's
   * opinion, and the operator running it has a second one: {@link AdditionalRedactionPatterns} is
   * still appended here. Replacing the vocabulary is a decision about which names an application
   * traces; it is not permission to undo a deployment's own widening.
   *
   * @param patterns field-name substrings that trigger redaction
   * @return a policy matching the supplied patterns plus any configured additions
   */
  public static RedactionPolicy ofPatterns(Set<String> patterns) {
    return new RedactionPolicy(
        union(patterns, AdditionalRedactionPatterns.configured()), Set.of(), true);
  }

  /** The supplied patterns plus the operator's additions, without copying when there are none. */
  private static Set<String> union(Set<String> patterns, Set<String> additional) {
    if (additional.isEmpty()) {
      return patterns;
    }
    var merged = new HashSet<>(patterns);
    merged.addAll(additional);
    return Set.copyOf(merged);
  }

  /**
   * The single redaction rule for a named member: an explicit {@code @NotTraced} annotation always
   * redacts, and otherwise the name-based deny-list decides.
   *
   * <p>INTENT: Every surface that can name a member — reflective introspection in {@code
   * ValueRenderer}, and {@code @Narrated}/{@code @OnError} template paths — asks this one method.
   * Two implementations of "is this redacted?" would drift, and a drifted redaction rule is a leak
   * on whichever surface fell behind.
   *
   * @param memberName the declared field or record-component name
   * @param annotated whether that member carries {@code @NotTraced}
   * @return {@code true} if the value behind the member must be hidden
   */
  public boolean isRedacted(String memberName, boolean annotated) {
    return annotated || shouldRedact(memberName);
  }

  /**
   * Decides whether a value should be redacted based on its field name.
   *
   * @param fieldName the declared field or record-component name
   * @return {@code true} if the name matches any deny-list pattern (case-insensitive substring, or
   *     whole identifier token for the short patterns)
   */
  public boolean shouldRedact(String fieldName) {
    if (fieldName == null) {
      return false;
    }
    var canonical = SecretValueShapes.canonical(fieldName);
    for (var pattern : lowerPatterns) {
      if (canonical.contains(pattern)) {
        return true;
      }
    }
    return matchesWholeToken(fieldName, canonical);
  }

  /**
   * Whether the whole name, or any identifier token of it, is one of the short deny-list patterns.
   *
   * <p><b>@edgeCase</b> The whole-name test is not redundant with the token test. Tokenising
   * assumes conventional casing, and {@code IbAn} splits into {@code Ib} + {@code An} — neither of
   * which is a pattern. A field whose entire name <em>is</em> the pattern must match however its
   * author capitalised it, so the flat comparison runs first.
   */
  private boolean matchesWholeToken(String fieldName, String canonicalName) {
    if (wordPatterns.isEmpty()) {
      return false;
    }
    if (wordPatterns.contains(canonicalName)) {
      return true;
    }
    return Arrays.stream(TOKEN_BOUNDARY.split(fieldName))
        .anyMatch(token -> wordPatterns.contains(SecretValueShapes.canonical(token)));
  }

  /**
   * Decides whether a value must be hidden because of what it <em>is</em>, whatever it is called.
   *
   * <p>INTENT: The name deny-list cannot see a bearer token passed as {@code value}, returned as a
   * bare {@code String}, or sitting unnamed inside a list. This is the complementary axis the
   * 2026-09-02 audit asked for, and it recognises exactly three shapes — a JWT, a Luhn-valid card
   * number, and a {@code Set-Cookie} string. See {@link SecretValueShapes} for why the matcher is
   * deliberately narrow rather than an entropy heuristic.
   *
   * @param value the rendered string form of a captured value; {@code null} is never redacted
   * @return {@code true} if the value's own shape identifies it as a credential
   */
  public boolean shouldRedactValue(String value) {
    return maskSecretValueShapes && SecretValueShapes.isSecretShaped(value);
  }
}
