/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonParserTest {

  @Test
  void parsesNestedObjectsArraysStringsNumbersBooleansAndNull() {
    var parsed =
        JsonParser.parse(
            """
            {
              "version": 1,
              "negative": -42,
              "empty": {},
              "flag": true,
              "off": false,
              "nothing": null,
              "list": ["a", "b"],
              "nested": { "quote": "say \\"hi\\"", "tab": "a\\tb", "unicode": "\\u00e9" }
            }
            """);

    assertThat(parsed)
        .isEqualTo(
            Map.of(
                "version",
                1L,
                "negative",
                -42L,
                "empty",
                Map.of(),
                "flag",
                true,
                "off",
                false,
                "nothing",
                JsonParser.NULL,
                "list",
                List.of("a", "b"),
                "nested",
                Map.of("quote", "say \"hi\"", "tab", "a\tb", "unicode", "é")));
  }

  @Test
  void preservesObjectKeyInsertionOrder() {
    @SuppressWarnings("unchecked")
    var parsed = (Map<String, Object>) JsonParser.parse("{\"z\": 1, \"a\": 2, \"m\": 3}");

    assertThat(parsed.keySet()).containsExactly("z", "a", "m");
  }

  @Test
  void rejectsMalformedDocuments() {
    assertRejected("", "end of input");
    assertRejected("{\"a\": 1} extra", "trailing content");
    assertRejected("{\"a\": 1, \"a\": 2}", "duplicate key");
    assertRejected("{'a': 1}", "expected '\"'");
    assertRejected("{\"a\" 1}", "expected ':'");
    assertRejected("{\"a\": 1 \"b\": 2}", "expected ','");
    assertRejected("[1, 2", "end of input");
    assertRejected("[1 2]", "expected ','");
    assertRejected("\"unterminated", "unterminated string");
    assertRejected("\"bad \\x escape\"", "invalid escape");
    assertRejected("\"bad \\u12 escape\"", "invalid unicode escape");
    assertRejected("\"truncated \\u12", "unterminated unicode escape");
    assertRejected("truthy", "invalid literal");
    assertRejected("nul", "invalid literal");
    assertRejected("1.5", "fractional");
    assertRejected("1e3", "fractional");
    assertRejected("-", "invalid number");
    assertRejected("+1", "invalid number");
    assertRejected(null, "must not be null");
  }

  @Test
  void decodesEverySimpleEscape() {
    assertThat(JsonParser.parse("\"\\\\ \\/ \\b \\f \\n \\r \\t \\\"\""))
        .isEqualTo("\\ / \b \f \n \r \t \"");
  }

  @Test
  void rejectsEscapeAtEndOfInput() {
    assertRejected("\"abc\\", "unterminated escape");
  }

  @Test
  void rejectsRawControlCharacterInsideString() {
    assertRejected("\"a\nb\"", "raw control character");
  }

  @Test
  void toleratesWhitespaceAtEveryStructuralPosition() {
    assertThat(JsonParser.parse("  1  ")).isEqualTo(1L);
    assertThat(JsonParser.parse("{ }")).isEqualTo(Map.of());
    assertThat(JsonParser.parse("[ ]")).isEqualTo(List.of());
    assertThat(JsonParser.parse("[1 , 2]")).isEqualTo(List.of(1L, 2L));
    assertThat(JsonParser.parse("{ \"a\" : 1 , \"b\" : 2 }")).isEqualTo(Map.of("a", 1L, "b", 2L));
  }

  @Test
  void rejectsUppercaseExponentAndTruncatedTrailingUnicodeEscape() {
    assertRejected("1E3", "fractional");
    assertRejected("\"\\u0041", "unterminated string");
  }

  private static void assertRejected(String json, String expectedMessagePart) {
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> JsonParser.parse(json))
        .withMessageContaining(expectedMessagePart);
  }
}
