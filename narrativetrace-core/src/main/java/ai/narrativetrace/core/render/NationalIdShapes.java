/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises national identity numbers by their own check digits, whatever the field is called.
 *
 * <p>INTENT: {@link SecretValueShapes} already answers "are these bytes a credential?" for a JWT, a
 * card number and a {@code Set-Cookie}. A national identity number is the same question in a
 * different jurisdiction, and it is the one piece of sensitive data whose *name* is most often in a
 * language the deny-list is read in but not written in — {@code numero}, {@code documento}, {@code
 * id}. The value's own checksum does not care what language the field name is in, which is why the
 * owner ruled these shapes language-neutral and on by default for everyone.
 *
 * <p><b>@llmNote</b> Every matcher here is a checksum, never a length-and-digits guess, and every
 * one is gated by a cheap {@link Pattern} before any arithmetic runs — the same discipline the Luhn
 * PAN matcher uses. A scheme without a check digit (the pre-1999 15-digit Chinese ID, a bare
 * Spanish DNI with the letter dropped) is deliberately absent: it would be indistinguishable from
 * an order number, and a security default that blanks ordinary business fields is one teams switch
 * off entirely.
 *
 * <p><b>@edgeCase</b> The Chilean RUT requires its verifier separator. Chile writes a RUT as {@code
 * 12.345.678-5} or {@code 12345678-5}, and the dash is what distinguishes it from any other
 * eight-digit number; accepting a bare nine-digit run would redact roughly one in eleven of every
 * order and invoice number in the world, which is the false-positive budget this class exists to
 * avoid. Dots are optional, the dash is not.
 *
 * <p><b>@edgeCase</b> CPF and CNPJ reject repeated-digit strings ({@code 00000000000}, {@code
 * 11111111111}) before the checksum, because every one of them satisfies both check digits and none
 * of them is a real document — they are the placeholder a form writes when it has none.
 */
final class NationalIdShapes {

  /** Chile: 7-8 digits, optional thousands dots, and a mod-11 verifier that may be {@code K}. */
  private static final Pattern RUT =
      Pattern.compile("(?:\\d{1,2}\\.\\d{3}\\.\\d{3}|\\d{7,8})-[0-9kK]");

  /** Brazil: 11 digits, written bare or as {@code NNN.NNN.NNN-NN}. */
  private static final Pattern CPF = Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{11}");

  /** Brazil: 14 digits, written bare or as {@code NN.NNN.NNN/NNNN-NN}. */
  private static final Pattern CNPJ =
      Pattern.compile("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}|\\d{14}");

  /** Spain: a DNI is 8 digits plus a letter; a NIE swaps the leading digit for {@code X/Y/Z}. */
  private static final Pattern SPANISH_ID =
      Pattern.compile("(?:[XYZ]\\d{7}|\\d{8})-?[A-Z]", Pattern.CASE_INSENSITIVE);

  /**
   * France: 13-character body plus a 2-digit key. Only the department (positions 6-7) may be
   * non-numeric, and only as Corsica's {@code 2A}/{@code 2B}.
   */
  private static final Pattern NIR =
      Pattern.compile("[1-478]\\d{4}(?:\\d{2}|2[AB])\\d{8}", Pattern.CASE_INSENSITIVE);

  /** China: the post-1999 resident identity card, 17 digits and a check character. */
  private static final Pattern CHINESE_ID = Pattern.compile("\\d{17}[0-9Xx]");

  private static final Pattern PUNCTUATION = Pattern.compile("[.\\-/]");
  private static final Pattern SPACE = Pattern.compile(" ");

  /** The Spanish check letter, indexed by the document number modulo 23. */
  private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

  /** A NIE's leading letter stands for a digit: {@code X}=0, {@code Y}=1, {@code Z}=2. */
  private static final String NIE_PREFIXES = "XYZ";

  private static final int[] CHINESE_WEIGHTS = {
    7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2
  };

  /** The Chinese check character, indexed by the weighted sum modulo 11. */
  private static final String CHINESE_CHECK_CHARACTERS = "10X98765432";

  private static final int EARLIEST_BIRTH_YEAR = 1900;
  private static final int LATEST_BIRTH_YEAR = 2100;

  private NationalIdShapes() {}

  /**
   * Whether the value is a national identity number that passes its own checksum.
   *
   * @param value a trimmed rendered value
   * @return {@code true} for a valid Chilean RUT, Brazilian CPF or CNPJ, Spanish DNI or NIE, French
   *     NIR, or Chinese resident identity card
   */
  static boolean isNationalId(String value) {
    return isRut(value)
        || isCpf(value)
        || isCnpj(value)
        || isSpanishId(value)
        || isFrenchNir(value)
        || isChineseResidentId(value);
  }

  private static boolean isRut(String value) {
    if (!RUT.matcher(value).matches()) {
      return false;
    }
    var compact = PUNCTUATION.matcher(value).replaceAll("");
    var body = compact.substring(0, compact.length() - 1);
    return Character.toLowerCase(compact.charAt(compact.length() - 1)) == rutVerifier(body);
  }

  /** Chile's mod 11: weights 2..7 cycling from the right, {@code 10} written {@code K}. */
  private static char rutVerifier(String body) {
    var sum = 0;
    var weight = 2;
    for (var i = body.length() - 1; i >= 0; i--) {
      sum += (body.charAt(i) - '0') * weight;
      weight = weight == 7 ? 2 : weight + 1;
    }
    var rest = 11 - (sum % 11);
    if (rest == 11) {
      return '0';
    }
    return rest == 10 ? 'k' : (char) ('0' + rest);
  }

  private static boolean isCpf(String value) {
    if (!CPF.matcher(value).matches()) {
      return false;
    }
    var digits = PUNCTUATION.matcher(value).replaceAll("");
    return !isRepeatedDigit(digits)
        && cpfCheckDigit(digits, 9) == digits.charAt(9) - '0'
        && cpfCheckDigit(digits, 10) == digits.charAt(10) - '0';
  }

  /** Brazil's mod 11 for the CPF: weights count down from {@code length + 1} to 2. */
  private static int cpfCheckDigit(String digits, int length) {
    var sum = 0;
    for (var i = 0; i < length; i++) {
      sum += (digits.charAt(i) - '0') * (length + 1 - i);
    }
    return checkDigitFromRemainder(sum);
  }

  private static boolean isCnpj(String value) {
    if (!CNPJ.matcher(value).matches()) {
      return false;
    }
    var digits = PUNCTUATION.matcher(value).replaceAll("");
    return !isRepeatedDigit(digits)
        && cnpjCheckDigit(digits, 12) == digits.charAt(12) - '0'
        && cnpjCheckDigit(digits, 13) == digits.charAt(13) - '0';
  }

  /** Brazil's mod 11 for the CNPJ: weights 2..9 cycling from the right. */
  private static int cnpjCheckDigit(String digits, int length) {
    var sum = 0;
    var weight = 2;
    for (var i = length - 1; i >= 0; i--) {
      sum += (digits.charAt(i) - '0') * weight;
      weight = weight == 9 ? 2 : weight + 1;
    }
    return checkDigitFromRemainder(sum);
  }

  /** Both Brazilian schemes share the final step: a remainder below two means a zero digit. */
  private static int checkDigitFromRemainder(int sum) {
    var remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  }

  private static boolean isRepeatedDigit(String digits) {
    for (var i = 1; i < digits.length(); i++) {
      if (digits.charAt(i) != digits.charAt(0)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isSpanishId(String value) {
    if (!SPANISH_ID.matcher(value).matches()) {
      return false;
    }
    var upper = PUNCTUATION.matcher(value.toUpperCase(Locale.ROOT)).replaceAll("");
    var body = upper.substring(0, upper.length() - 1);
    var prefix = NIE_PREFIXES.indexOf(body.charAt(0));
    var number = prefix < 0 ? body : prefix + body.substring(1);
    return DNI_LETTERS.charAt(Integer.parseInt(number) % 23) == upper.charAt(upper.length() - 1);
  }

  private static boolean isFrenchNir(String value) {
    var compact = SPACE.matcher(value).replaceAll("");
    if (!NIR.matcher(compact).matches()) {
      return false;
    }
    var body =
        compact.substring(0, 13).toUpperCase(Locale.ROOT).replace("2A", "19").replace("2B", "18");
    return Integer.parseInt(compact.substring(13)) == 97 - Long.parseLong(body) % 97;
  }

  private static boolean isChineseResidentId(String value) {
    if (!CHINESE_ID.matcher(value).matches() || !hasPlausibleBirthDate(value)) {
      return false;
    }
    var sum = 0;
    for (var i = 0; i < CHINESE_WEIGHTS.length; i++) {
      sum += (value.charAt(i) - '0') * CHINESE_WEIGHTS[i];
    }
    return CHINESE_CHECK_CHARACTERS.charAt(sum % 11) == Character.toUpperCase(value.charAt(17));
  }

  /**
   * Positions 7-14 of a Chinese resident id are the holder's birth date. Checking it costs three
   * integer parses and removes most of what the check character alone would let through — a
   * one-in-eleven hit rate on eighteen-digit numbers is otherwise the whole false-positive budget.
   */
  private static boolean hasPlausibleBirthDate(String value) {
    var year = Integer.parseInt(value.substring(6, 10));
    var month = Integer.parseInt(value.substring(10, 12));
    var day = Integer.parseInt(value.substring(12, 14));
    return year >= EARLIEST_BIRTH_YEAR
        && year <= LATEST_BIRTH_YEAR
        && month >= 1
        && month <= 12
        && day >= 1
        && day <= 31;
  }
}
