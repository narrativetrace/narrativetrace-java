/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

/**
 * RFC 8259 JSON string escaping shared by every hand-rolled JSON emitter.
 *
 * <p>INTENT: One escaper, used everywhere a captured value lands inside a JSON string literal.
 * Captured values are untrusted input — quotes must not forge fields and control characters
 * (U+0000–U+001F) must never reach the output raw, or the document becomes invalid or injectable.
 *
 * <p><b>@llmNote</b> Do not add a local {@code escapeJson} helper to an exporter; delegate here so
 * escaping can never drift between emitters again.
 */
public final class JsonEscape {

  private JsonEscape() {}

  /**
   * Escapes a string for embedding inside a JSON string literal.
   *
   * @param s Raw text, may be {@code null}.
   * @return Escaped text, or the literal text {@code "null"} when {@code s} is null.
   */
  public static String escape(String s) {
    if (s == null) return "null";
    var sb = new StringBuilder(s.length() + 8);
    for (int i = 0; i < s.length(); i++) {
      appendEscaped(sb, s.charAt(i));
    }
    return sb.toString();
  }

  private static void appendEscaped(StringBuilder sb, char c) {
    switch (c) {
      case '\\' -> sb.append("\\\\");
      case '"' -> sb.append("\\\"");
      case '\n' -> sb.append("\\n");
      case '\r' -> sb.append("\\r");
      case '\t' -> sb.append("\\t");
      case '\b' -> sb.append("\\b");
      case '\f' -> sb.append("\\f");
      default -> {
        if (c < 0x20) {
          sb.append(String.format("\\u%04x", (int) c));
        } else {
          sb.append(c);
        }
      }
    }
  }
}
