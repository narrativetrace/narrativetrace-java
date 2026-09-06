/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

/**
 * Neutralizes control characters in text bound for line-oriented / terminal sinks.
 *
 * <p>INTENT: Rendered values, exception messages, and error context flow into SLF4J log messages
 * and a {@code System.out} console renderer. A raw {@code \r}/{@code \n} forges an extra log line
 * (CWE-117) that a line-based parser/SIEM cannot distinguish from a real one; a raw {@code ESC}
 * (0x1B) injects ANSI/OSC terminal sequences. This helper renders every control character as a
 * visible, inert escape so no sink emits a raw control byte.
 *
 * <p><b>@llmNote</b> Unlike {@code JsonEscape}, this does NOT escape {@code "} or {@code \\} —
 * those are legitimate value content and safe in logs/terminals. It touches control characters
 * only, so a value like {@code C:\temp} stays readable. Common controls map to their {@code
 * \n}/{@code \t} mnemonics; every other {@link Character#isISOControl(char)} code point maps to
 * {@code \\uXXXX}.
 *
 * <p><b>@edgeCase</b> Unpaired surrogates are escaped the same way, and for the same reason. A lone
 * {@code \\uD800} is not a control character, but it is not a code point either: no UTF-8 sink can
 * encode it, so {@code Files.writeString} raises {@code UnmappableCharacterException} and a value
 * the application merely <em>returned</em> fails the run that traced it. Escaping it here keeps the
 * text visible, lossless and encodable, exactly as for a control character. A well-formed pair is
 * left alone — an emoji is ordinary content.
 */
public final class ControlEscape {

  private ControlEscape() {}

  /**
   * Replaces control characters and unpaired surrogates with visible escape sequences, leaving all
   * other characters (including quotes and backslashes) unchanged.
   *
   * @param text the raw text (a rendered value, exception message, or error context)
   * @return the text with every unencodable character rendered as an inert escape sequence
   */
  public static String sanitize(String text) {
    var sb = new StringBuilder(text.length());
    var index = 0;
    while (index < text.length()) {
      index = appendOne(sb, text, index);
    }
    return sb.toString();
  }

  /**
   * Appends whatever starts at {@code index} — one character, one mnemonic escape, or one
   * well-formed surrogate pair.
   *
   * @return the index to read from next, which is two ahead for a pair
   */
  private static int appendOne(StringBuilder sb, String text, int index) {
    switch (text.charAt(index)) {
      case '\n' -> sb.append("\\n");
      case '\r' -> sb.append("\\r");
      case '\t' -> sb.append("\\t");
      case '\b' -> sb.append("\\b");
      case '\f' -> sb.append("\\f");
      default -> {
        return appendPlainOrEscaped(sb, text, index);
      }
    }
    return index + 1;
  }

  /**
   * The characters with no mnemonic: an ISO control or a lone surrogate becomes {@code \\uXXXX}, a
   * well-formed pair is copied through, and everything else is itself.
   *
   * @return the index to read from next
   */
  private static int appendPlainOrEscaped(StringBuilder sb, String text, int index) {
    char c = text.charAt(index);
    if (Character.isISOControl(c)) {
      appendUnicodeEscape(sb, c);
      return index + 1;
    }
    if (Character.isHighSurrogate(c)
        && index + 1 < text.length()
        && Character.isLowSurrogate(text.charAt(index + 1))) {
      sb.append(c).append(text.charAt(index + 1));
      return index + 2;
    }
    if (Character.isSurrogate(c)) {
      appendUnicodeEscape(sb, c);
      return index + 1;
    }
    sb.append(c);
    return index + 1;
  }

  private static void appendUnicodeEscape(StringBuilder sb, char c) {
    sb.append(String.format("\\u%04x", (int) c));
  }
}
