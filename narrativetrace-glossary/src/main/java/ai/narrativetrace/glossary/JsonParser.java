/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal strict JSON parser for reading {@code glossary.json}.
 *
 * <p>INTENT: The glossary module must add no external runtime dependency (ADR-012), so the reader
 * hand-rolls parsing. Strictness is deliberate — the glossary is a hand-curated committed file, and
 * a typo should fail fast with a position, never be silently ignored.
 *
 * <p>Scope: RFC 8259 objects, arrays, strings (all escapes), integer numbers, {@code true}, {@code
 * false}, {@code null}. Fractional/exponent numbers are rejected — the glossary schema has none.
 * Duplicate object keys and trailing content are errors. JSON {@code null} parses to {@link #NULL}
 * (never Java {@code null}) so callers can distinguish "key absent" from "key explicitly null".
 *
 * <p>Container nesting is capped at {@link #MAX_NESTING_DEPTH} and refused before parsing begins,
 * because parsing is recursive and the document is user-supplied.
 */
final class JsonParser {

  /** Sentinel returned for a JSON {@code null} literal. */
  static final Object NULL = new Object();

  /**
   * Deepest {@code {}}/{@code []} nesting a glossary document may open.
   *
   * <p>The same number in every NarrativeTrace runtime, whatever each one's JSON reader is built
   * from — one limit, so a document accepted by one runtime is accepted by all. The curated shape
   * this schema actually uses tops out around four levels (contexts → terms → translations), so 16
   * is headroom rather than a realistic ceiling; its job is to turn a pathologically deep document
   * — hostile or merely corrupted — into one clean, named error.
   */
  static final int MAX_NESTING_DEPTH = 16;

  private final String text;
  private int pos;

  private JsonParser(String text) {
    this.text = text;
  }

  /**
   * Parses one complete JSON document.
   *
   * @param text JSON text; must not be {@code null}
   * @return {@link Map} (insertion-ordered), {@link List}, {@link String}, {@link Long}, {@link
   *     Boolean}, or {@link #NULL}
   * @throws IllegalArgumentException on any syntax error, with character position, or when
   *     container nesting exceeds {@link #MAX_NESTING_DEPTH}
   */
  static Object parse(String text) {
    if (text == null) {
      throw new IllegalArgumentException("JSON text must not be null");
    }
    requireBoundedNesting(text);
    var parser = new JsonParser(text);
    parser.skipWhitespace();
    var value = parser.parseValue();
    parser.skipWhitespace();
    if (parser.pos < text.length()) {
      throw parser.error("trailing content after JSON document");
    }
    return value;
  }

  /**
   * Refuses a document whose container nesting exceeds {@link #MAX_NESTING_DEPTH}, before {@link
   * #parseValue} can recurse into it.
   *
   * <p>{@code parseValue → parseObject/parseArray → parseValue} is real call-stack recursion, and
   * {@code glossary.json} is user-supplied, so the depth of the file decides the depth of the
   * stack. This scan is a single left-to-right pass with a counter — no recursion of its own — so
   * measuring a hostile document can never become the stack exhaustion it exists to prevent.
   *
   * <p><b>@edgeCase</b> Brackets inside a string literal are not structure: the scan skips each
   * literal whole, honouring backslash escapes, so {@code "[[[["} as a value counts nothing and an
   * escaped quote does not end the literal early. An unterminated literal runs to the end of the
   * text and the parser reports it by its own name.
   */
  private static void requireBoundedNesting(String text) {
    int depth = 0;
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '"') {
        i = endOfString(text, i);
      } else if (c == '{' || c == '[') {
        depth = opened(depth, i);
      } else if (c == '}' || c == ']') {
        depth--;
      }
      i++;
    }
  }

  /** Index of the literal's closing quote, or the text length when it is never closed. */
  private static int endOfString(String text, int openingQuote) {
    int i = openingQuote + 1;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '"') {
        return i;
      }
      i += c == '\\' ? 2 : 1;
    }
    return text.length();
  }

  private static int opened(int depth, int position) {
    int nested = depth + 1;
    if (nested > MAX_NESTING_DEPTH) {
      throw error(
          position, "nesting depth " + nested + " exceeds the maximum of " + MAX_NESTING_DEPTH);
    }
    return nested;
  }

  private Object parseValue() {
    char c = peek();
    return switch (c) {
      case '{' -> parseObject();
      case '[' -> parseArray();
      case '"' -> parseString();
      case 't' -> parseLiteral("true", Boolean.TRUE);
      case 'f' -> parseLiteral("false", Boolean.FALSE);
      case 'n' -> parseLiteral("null", NULL);
      default -> parseNumber();
    };
  }

  private Map<String, Object> parseObject() {
    expect('{');
    var object = new LinkedHashMap<String, Object>();
    skipWhitespace();
    if (peek() == '}') {
      pos++;
      return object;
    }
    while (true) {
      skipWhitespace();
      var key = parseString();
      skipWhitespace();
      expect(':');
      skipWhitespace();
      if (object.put(key, parseValue()) != null) {
        throw error("duplicate key '" + key + "'");
      }
      skipWhitespace();
      if (consumeSeparatorUntil('}')) {
        return object;
      }
    }
  }

  private List<Object> parseArray() {
    expect('[');
    var array = new ArrayList<Object>();
    skipWhitespace();
    if (peek() == ']') {
      pos++;
      return array;
    }
    while (true) {
      skipWhitespace();
      array.add(parseValue());
      skipWhitespace();
      if (consumeSeparatorUntil(']')) {
        return array;
      }
    }
  }

  /** Consumes either a {@code ,} (returning false) or the given closer (returning true). */
  private boolean consumeSeparatorUntil(char closer) {
    char c = peek();
    if (c == closer) {
      pos++;
      return true;
    }
    if (c != ',') {
      throw error("expected ',' or '" + closer + "'");
    }
    pos++;
    return false;
  }

  private String parseString() {
    expect('"');
    var sb = new StringBuilder();
    while (true) {
      if (pos >= text.length()) {
        throw error("unterminated string");
      }
      char c = text.charAt(pos++);
      if (c == '"') {
        return sb.toString();
      }
      if (c == '\\') {
        sb.append(parseEscape());
      } else if (c < 0x20) {
        throw error("raw control character in string");
      } else {
        sb.append(c);
      }
    }
  }

  private char parseEscape() {
    if (pos >= text.length()) {
      throw error("unterminated escape");
    }
    char c = text.charAt(pos++);
    return switch (c) {
      case '"' -> '"';
      case '\\' -> '\\';
      case '/' -> '/';
      case 'b' -> '\b';
      case 'f' -> '\f';
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      case 'u' -> parseUnicodeEscape();
      default -> throw error("invalid escape '\\" + c + "'");
    };
  }

  private char parseUnicodeEscape() {
    if (pos + 4 > text.length()) {
      throw error("unterminated unicode escape");
    }
    var hex = text.substring(pos, pos + 4);
    try {
      char value = (char) Integer.parseInt(hex, 16);
      pos += 4;
      return value;
    } catch (NumberFormatException e) {
      throw error("invalid unicode escape '\\u" + hex + "'", e);
    }
  }

  private Long parseNumber() {
    int start = pos;
    if (peek() == '-') {
      pos++;
    }
    while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
      pos++;
    }
    if (pos < text.length()
        && (text.charAt(pos) == '.' || text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
      throw error("fractional numbers are not supported by the glossary schema");
    }
    try {
      return Long.parseLong(text, start, pos, 10);
    } catch (NumberFormatException e) {
      throw error("invalid number", e);
    }
  }

  private Object parseLiteral(String literal, Object value) {
    if (!text.startsWith(literal, pos)) {
      throw error("invalid literal");
    }
    pos += literal.length();
    return value;
  }

  private char peek() {
    if (pos >= text.length()) {
      throw error("unexpected end of input");
    }
    return text.charAt(pos);
  }

  private void expect(char c) {
    if (peek() != c) {
      throw error("expected '" + c + "'");
    }
    pos++;
  }

  private void skipWhitespace() {
    while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
      pos++;
    }
  }

  private IllegalArgumentException error(String message) {
    return error(pos, message);
  }

  private static IllegalArgumentException error(int position, String message) {
    return new IllegalArgumentException("invalid JSON at position " + position + ": " + message);
  }

  private IllegalArgumentException error(String message, Throwable cause) {
    return new IllegalArgumentException("invalid JSON at position " + pos + ": " + message, cause);
  }
}
