/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises the few value shapes that identify a secret whatever the field holding it is called.
 *
 * <p>INTENT: {@link RedactionPolicy}'s deny-list only sees <em>names</em>, and an adversarial
 * review's point was that not all secrets sit in clearly-named fields — a bearer token arrives as
 * {@code value}, {@code header}, {@code data}, or the third element of a list that has no name at
 * all. This is the complementary axis: what the bytes themselves say.
 *
 * <p><b>@llmNote</b> Deliberately, aggressively narrow. Every matcher here answers "this string is
 * a credential" and nothing weaker; there is no entropy heuristic, no "looks random" test, no
 * length rule. Redacting a value the user needed is a silent hole in their narrative that they
 * cannot switch off per-value, so the false-positive budget is near zero and each shape below has a
 * structural marker that ordinary business data does not accidentally produce.
 *
 * <p><b>@edgeCase</b> The one accepted false positive is a 13–19 digit identifier that happens to
 * satisfy the Luhn checksum — roughly one in ten of such numbers. That is inherent to detecting
 * card numbers at all, it is the trade every DLP makes, and the audit named it explicitly:
 * order-shaped numbers that fail Luhn must stay visible, which is what the tests pin. {@link
 * RedactionPolicy#DISABLED} turns the whole axis off.
 *
 * <p><b>@llmNote</b> National identity numbers are the fourth shape and live in {@link
 * NationalIdShapes}, kept apart because they are six checksums rather than one matcher. They are
 * language-neutral by construction — a CPF is a CPF whatever the field holding it is called, which
 * is the point: the name deny-list can be read in a language it was not written in, and a checksum
 * cannot.
 */
final class SecretValueShapes {

  /**
   * A JWT's first segment is base64url of a JSON object, so it always begins {@code eyJ} — {@code
   * {"} encoded. Requiring that literal, three dot-separated base64url segments, and a minimum
   * length reduces the false-positive rate to effectively zero: {@code a.b.c} and a dotted
   * hostname do not match.
   */
  private static final Pattern JWT =
      Pattern.compile("eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]*");

  /** Digits with the separators a human writes card numbers in, and nothing else. */
  private static final Pattern PAN_CANDIDATE = Pattern.compile("[0-9]{4,}[0-9 -]*");

  private static final Pattern SEPARATORS = Pattern.compile("[ -]");

  /**
   * A {@code Set-Cookie} value is {@code name=value} followed by at least one of the attributes RFC
   * 6265 defines. Requiring a semicolon <em>and</em> a known attribute keeps ordinary {@code
   * key=value} strings, and even {@code name=Ada; age=36}, visible.
   */
  private static final Pattern SET_COOKIE =
      Pattern.compile(
          "[^=;\\s]+=[^;]*;\\s*.*\\b(path|domain|expires|max-age|httponly|secure|samesite)\\b.*",
          Pattern.CASE_INSENSITIVE);

  /** Unicode marks: what NFD separates an accent into, and all this fold ever removes. */
  private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

  private static final char MAX_ASCII = 0x7f;

  private static final int PAN_MIN_DIGITS = 13;
  private static final int PAN_MAX_DIGITS = 19;

  private SecretValueShapes() {}

  /**
   * Whether the value's own shape identifies it as a credential.
   *
   * @param value any rendered string; {@code null} and blank answer {@code false}
   * @return {@code true} only for a JWT, a Luhn-valid PAN, a {@code Set-Cookie} string, or a
   *     national identity number that passes its own checksum
   */
  static boolean isSecretShaped(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    var trimmed = value.trim();
    return JWT.matcher(trimmed).matches()
        || isPan(trimmed)
        || SET_COOKIE.matcher(trimmed).matches()
        || NationalIdShapes.isNationalId(trimmed);
  }

  /** A card number: only digits and the separators cards are written with, and Luhn-valid. */
  private static boolean isPan(String value) {
    if (!PAN_CANDIDATE.matcher(value).matches()) {
      return false;
    }
    var digits = SEPARATORS.matcher(value).replaceAll("");
    if (digits.length() < PAN_MIN_DIGITS || digits.length() > PAN_MAX_DIGITS) {
      return false;
    }
    return passesLuhn(digits);
  }

  /**
   * The Luhn checksum, right to left: every second digit doubled, digits above nine reduced by
   * nine, the total divisible by ten.
   */
  private static boolean passesLuhn(String digits) {
    var sum = 0;
    var doubling = false;
    for (var i = digits.length() - 1; i >= 0; i--) {
      var digit = digits.charAt(i) - '0';
      if (doubling) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      doubling = !doubling;
    }
    return sum % 10 == 0;
  }

  /**
   * Folds a field name or a deny-list pattern to the one spelling both are compared in: lower case,
   * and without diacritics.
   *
   * <p>INTENT: The deny-list carries every language's vocabulary, and a Spanish team writes {@code
   * contrase\u00f1a} while the same team's DTO generator writes {@code contrasena}. Both are the
   * word "password" and both must be hidden, so the fold happens on <em>both</em> sides rather than
   * the pattern set listing every spelling. It also makes the two name-matching modes in {@link
   * RedactionPolicy} share one canonicalisation instead of each choosing a locale.
   *
   * <p><b>@llmNote</b> The ASCII scan in front is not premature: this runs once per introspected
   * member on the rendering path, and virtually every real field name is ASCII, where {@code
   * toLowerCase} alone already answers. Normalisation is only paid for by names that actually carry
   * a non-ASCII character.
   *
   * <p><b>@edgeCase</b> Total on any input. NFD leaves unpaired surrogates, noncharacters and bidi
   * controls alone rather than rejecting them, so a hostile field name folds to something harmless
   * instead of throwing out of a redaction decision — the one place an exception would fail open.
   */
  static String canonical(String text) {
    var lower = text.toLowerCase(Locale.ROOT);
    return isAscii(lower) ? lower : withoutDiacritics(lower);
  }

  private static boolean isAscii(String text) {
    for (var i = 0; i < text.length(); i++) {
      if (text.charAt(i) > MAX_ASCII) {
        return false;
      }
    }
    return true;
  }

  /** NFD splits a letter from its accent; dropping the marks leaves the letter. */
  private static String withoutDiacritics(String lower) {
    return COMBINING_MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
  }
}
