/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.feedback;

import java.util.regex.Pattern;

/**
 * The deciding half of {@link ValueFreeRule}: one static predicate per rule, each testable on its
 * own without building a report around it.
 *
 * <p><b>@llmNote</b> Every pattern is compiled once, at class initialisation. A rule runs over
 * every field of every report the verb drafts, so a per-call {@code Pattern.compile} would be paid
 * on the one path that must never be the slow one a user skips.
 */
final class ValueFreeMatchers {

  /**
   * {@code Name.method(param: value} — an identifier, a colon and something after it, inside a call
   * line's parentheses. The colon is what separates a rendered call from a structural one: the
   * {@code .nt} form writes {@code Name.method(param, param)} and never a value.
   *
   * <p><b>@edgeCase</b> Every runtime's Markdown renderer bolds the qualified name ({@code -
   * **Name.method**(param: value)}), so one optional emphasis marker ({@code **}, {@code __},
   * {@code *}, {@code _}) may sit between the method and the parenthesis — written as a bounded
   * class ({@code [*_]{0,2}}), never as the alternation {@code (?:\*\*|__|\*|_)?}: the overlapping
   * alternatives are what SpotBugs' ReDoS detector rejects, and a stray mixed pair it also admits
   * only makes this deny rule stricter.
   */
  private static final Pattern RENDERED_CALL =
      Pattern.compile("\\w+\\.\\w+[*_]{0,2}\\([^)]*\\b\\w+:\\s*\\S");

  /**
   * An outcome arrow followed by something other than the structural literal {@code value}. The
   * other two structural outcomes carry no arrow at all ({@code !! TypeName}, {@code ??
   * incomplete}), so they cannot reach this pattern; a rendered return always does.
   */
  private static final Pattern RENDERED_OUTCOME = Pattern.compile("\u2192\\s*(?!value\\b)\\S");

  /**
   * The rendered narrative's own duration suffix: an em dash, a number and a time unit. Anchored on
   * the em dash rather than on the number alone, so a version coordinate and a finding count — the
   * two numbers a legitimate report is full of — are not durations.
   */
  private static final Pattern DURATION =
      Pattern.compile("\u2014\\s*\\d+(\\.\\d+)?\\s?(ns|\u00b5s|ms|s)\\b");

  /**
   * The runtime's redaction marker, written out rather than imported: this library declares zero
   * dependencies and never links against the runtime it diagnoses. The cross-module security suite
   * asserts this literal equals {@code RedactionPolicy.MARKER}, so the two cannot drift.
   */
  static final String REDACTION_MARKER = "[REDACTED]";

  /**
   * One {@code key: value} or {@code key=value} binding, capturing the key. Keys are matched with
   * {@code UNICODE_CHARACTER_CLASS} so a Spanish or Chinese field name is a word here too, and
   * bounded in length so a pathological line cannot make this quadratic.
   *
   * <p><b>@llmNote</b> The value is a LOOKAHEAD, not a consumed character, and that is not a
   * micro-optimisation. Consuming it ate the first letter of the next key, so in an indented YAML
   * paste the scan matched {@code datasource:} and then resumed inside {@code password}, read the
   * key as {@code assword} and let the credential through. Corpus row {@code
   * named-secret-yaml-indented} is that bug.
   *
   * <p><b>@llmNote</b> The optional quotes are load-bearing for the SAME reason the lookahead is. A
   * JSON key is {@code "password": "hunter2"}, and the closing quote sits between the key and the
   * colon — so without them the one attachment every report carries, the doctor's own JSON, was the
   * one format the deny-list could not read at all. Corpus row {@code named-secret-json-quoted} is
   * that bug.
   */
  private static final Pattern KEYED_ASSIGNMENT =
      Pattern.compile(
          "[\"']?([\\w.\\-]{1,80})[\"']?\\s*[:=]\\s*(?=\\S)", Pattern.UNICODE_CHARACTER_CLASS);

  /**
   * A run of base64 (and base64url {@code _}) characters long enough to be an encoded secret.
   *
   * <p><b>@llmNote</b> The hyphen is deliberately NOT a run character, though base64url uses it: it
   * is the one character ordinary prose and this project's own doc anchors use as a word separator,
   * and admitting it makes every hyphenated phrase of 32 characters a secret ({@code
   * the-quick-brown-fox-jumps-over-the-lazy} measures 4.33 bits per character). A hyphenated
   * base64url token is still reached by its halves, and a JWT by {@link SecretVocabulary}'s own
   * shape.
   */
  private static final Pattern BASE64_RUN = Pattern.compile("[A-Za-z0-9+/=_]{32,}");

  /**
   * A run of nothing but hex digits, long enough to be a hash, an HMAC or an opaque id.
   *
   * <p><b>@llmNote</b> Entropy is not the discriminator for hex and cannot be: sixteen symbols cap
   * Shannon entropy at 4.0 bits per character, and a measured sample of random hex runs of 32 to
   * 128 characters never exceeded 3.97 — so the entropy ceiling below can never fire on hex, which
   * is half of what this rule is for. Length alone carries the hex half: no word is 32 hex digits.
   */
  private static final Pattern HEX_RUN = Pattern.compile("\\b[0-9a-fA-F]{32,}\\b");

  /** Bits per character above which an encoded run is treated as a secret rather than a word. */
  private static final double ENTROPY_CEILING = 4.0;

  /**
   * An email address. The local part must be non-empty, which is what keeps a Java annotation
   * ({@code @NotTraced}) and a Maven coordinate off this rule.
   */
  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\\.[A-Za-z]{2,}");

  /**
   * The three platforms' home directories. Matched with a trailing separator and a non-empty
   * account segment, so {@code /home} or a relative {@code build/} path is not one.
   */
  private static final Pattern HOME_PATH =
      Pattern.compile(
          "(/Users/|/home/)[^/\\s]+/|[A-Za-z]:\\\\Users\\\\[^\\\\\\s]+", Pattern.CASE_INSENSITIVE);

  /**
   * Every C0 and C1 control character except the line feed and the tab a report legitimately has,
   * plus Unicode's own bidirectional controls and the byte-order mark.
   *
   * <p><b>@llmNote</b> The bidi characters are in scope because they are the attack this rule is
   * for: a right-to-left override in an issue title reorders what the person triaging it SEES
   * without changing a byte of what was filed. They carry the {@code Bidi_Control} property, so
   * "control character" is their own name for themselves, not a widening.
   *
   * <p><b>@edgeCase</b> Deliberately NOT all of {@code \p{Cf}}. The zero-width joiner and
   * non-joiner are format characters too, and they are load-bearing letters in Devanagari, Bengali
   * and emoji sequences — refusing them would refuse a report written in Hindi, and reports may be
   * in any language.
   */
  private static final Pattern CONTROL =
      Pattern.compile("[\\x00-\\x08\\x0b-\\x1f\\x7f-\\x9f\\u202a-\\u202e\\u2066-\\u2069\\ufeff]");

  private ValueFreeMatchers() {}

  static boolean renderedCall(String text) {
    return RENDERED_CALL.matcher(text).find();
  }

  static boolean renderedOutcome(String text) {
    return RENDERED_OUTCOME.matcher(text).find();
  }

  static boolean duration(String text) {
    return DURATION.matcher(text).find();
  }

  static boolean marker(String text) {
    return text.contains(REDACTION_MARKER);
  }

  /**
   * A deny-listed name immediately followed by a value. Anchored on the key being ASSIGNED, not on
   * the word appearing anywhere: a report that says "the authorization header never arrived" is the
   * report we want, and the key there is {@code header}.
   */
  static boolean namedSecret(String text) {
    var matcher = KEYED_ASSIGNMENT.matcher(text);
    while (matcher.find()) {
      if (SecretVocabulary.namesASecret(matcher.group(1))) {
        return true;
      }
    }
    return false;
  }

  static boolean valueShape(String text) {
    return SecretVocabulary.containsASecretShape(text);
  }

  /** The hex half decided by length, the base64 half by the density of its own alphabet. */
  static boolean entropy(String text) {
    if (HEX_RUN.matcher(text).find()) {
      return true;
    }
    var runs = BASE64_RUN.matcher(text);
    while (runs.find()) {
      if (shannonBitsPerCharacter(runs.group()) > ENTROPY_CEILING) {
        return true;
      }
    }
    return false;
  }

  static boolean email(String text) {
    return EMAIL.matcher(text).find();
  }

  static boolean homePath(String text) {
    return HOME_PATH.matcher(text).find();
  }

  static boolean control(String text) {
    return CONTROL.matcher(text).find();
  }

  /**
   * Shannon entropy of the run's own character distribution, in bits per character. The run is
   * ASCII by construction of the patterns above, which is what lets the histogram be an array.
   */
  private static double shannonBitsPerCharacter(String run) {
    int[] counts = new int[128];
    for (int i = 0; i < run.length(); i++) {
      counts[run.charAt(i)]++;
    }
    double bits = 0;
    for (int count : counts) {
      if (count > 0) {
        double share = (double) count / run.length();
        bits -= share * (Math.log(share) / Math.log(2));
      }
    }
    return bits;
  }
}
