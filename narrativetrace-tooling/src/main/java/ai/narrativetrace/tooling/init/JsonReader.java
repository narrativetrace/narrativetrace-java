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
package ai.narrativetrace.tooling.init;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The smallest JSON reader that can read a carrier catalogue: objects, arrays and strings, nothing
 * else.
 *
 * <p>INTENT: {@code narrativetrace-tooling} takes zero dependencies, so the one JSON document the
 * installer reads is parsed here rather than by a library. The subset is deliberate — a catalogue
 * holds only strings, so a number, a boolean or a {@code null} in one is a malformed catalogue and
 * says so, instead of being quietly accepted by a fuller parser.
 *
 * <p><b>@llmNote</b> Refuses rather than repairs: every malformed input throws {@link
 * IllegalArgumentException} naming the zero-based cursor offset where reading stopped. Reading a
 * carrier is configuration, not a hot path, so the failure regime is fail-fast and loud.
 *
 * <p><b>@pattern</b> Recursive descent over an immutable input string with one cursor.
 */
final class JsonReader {

  private final String text;
  private int position;

  private JsonReader(String text) {
    this.text = text;
  }

  /**
   * Reads the one complete value in {@code text}.
   *
   * @return a {@code Map<String, Object>}, a {@code List<Object>} or a {@code String}
   * @throws IllegalArgumentException on any malformed or trailing input
   */
  static Object parse(String text) {
    if (text == null) {
      throw new IllegalArgumentException("no JSON text to read");
    }
    JsonReader reader = new JsonReader(text);
    Object value = reader.readValue();
    reader.skipWhitespace();
    if (reader.position != text.length()) {
      throw reader.error("trailing content after the top-level value");
    }
    return value;
  }

  private Object readValue() {
    skipWhitespace();
    char c = peek();
    return switch (c) {
      case '{' -> readObject();
      case '[' -> readArray();
      case '"' -> readString();
      default -> throw error("expected an object, an array or a string, found '" + c + "'");
    };
  }

  private Map<String, Object> readObject() {
    expect('{');
    Map<String, Object> members = new LinkedHashMap<>();
    skipWhitespace();
    if (peek() == '}') {
      position++;
      return members;
    }
    boolean more = true;
    while (more) {
      skipWhitespace();
      String key = readString();
      skipWhitespace();
      expect(':');
      members.put(key, readValue());
      more = nextIsComma('}');
    }
    return members;
  }

  private List<Object> readArray() {
    expect('[');
    List<Object> elements = new ArrayList<>();
    skipWhitespace();
    if (peek() == ']') {
      position++;
      return elements;
    }
    boolean more = true;
    while (more) {
      elements.add(readValue());
      more = nextIsComma(']');
    }
    return elements;
  }

  /** True when a comma follows (another member), false when {@code closer} ends the collection. */
  private boolean nextIsComma(char closer) {
    skipWhitespace();
    char c = next();
    if (c == closer) {
      return false;
    }
    if (c != ',') {
      throw error("expected ',' or '" + closer + "', found '" + c + "'");
    }
    return true;
  }

  private String readString() {
    expect('"');
    StringBuilder out = new StringBuilder();
    while (true) {
      char c = next();
      if (c == '"') {
        return out.toString();
      }
      if (c == '\\') {
        out.append(readEscape());
      } else if (c < 0x20) {
        throw error("a control character inside a string must be escaped");
      } else {
        out.append(c);
      }
    }
  }

  private char readEscape() {
    char c = next();
    return switch (c) {
      case '"', '\\', '/' -> c;
      case 'b' -> '\b';
      case 'f' -> '\f';
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      case 'u' -> readUnicodeEscape();
      default -> throw error("unknown escape '\\" + c + "'");
    };
  }

  private char readUnicodeEscape() {
    if (position + 4 > text.length()) {
      throw error("a \\u escape needs four hexadecimal digits");
    }
    String digits = text.substring(position, position + 4);
    position += 4;
    int value = 0;
    for (int i = 0; i < digits.length(); i++) {
      int digit = Character.digit(digits.charAt(i), 16);
      if (digit < 0) {
        throw error("\\u" + digits + " is not four hexadecimal digits");
      }
      value = value * 16 + digit;
    }
    return (char) value;
  }

  private void skipWhitespace() {
    while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
      position++;
    }
  }

  private char peek() {
    if (position >= text.length()) {
      throw error("the document ends here");
    }
    return text.charAt(position);
  }

  private char next() {
    char c = peek();
    position++;
    return c;
  }

  private void expect(char expected) {
    char c = next();
    if (c != expected) {
      throw error("expected '" + expected + "', found '" + c + "'");
    }
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException("malformed JSON at offset " + position + ": " + message);
  }
}
