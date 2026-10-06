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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The reader is a deliberate SUBSET of JSON — objects, arrays and strings. These cases pin both
 * halves of that: what it must read exactly, and what it must refuse rather than silently accept.
 */
class JsonReaderTest {

  @Test
  void readsNestedObjectsArraysAndStrings() {
    Object value = JsonReader.parse("{\"a\": [\"x\", {\"b\": \"y\"}], \"c\": \"z\"}");

    assertThat(value).isEqualTo(Map.of("a", List.of("x", Map.of("b", "y")), "c", "z"));
  }

  @Test
  void readsAnEmptyObjectAndAnEmptyArray() {
    assertThat(JsonReader.parse("{}")).isEqualTo(Map.of());
    assertThat(JsonReader.parse("[]")).isEqualTo(List.of());
    assertThat(JsonReader.parse("{   }")).isEqualTo(Map.of());
    assertThat(JsonReader.parse("[\n\t ]")).isEqualTo(List.of());
    assertThat(JsonReader.parse("{\"a\": {}, \"b\": []}"))
        .isEqualTo(Map.of("a", Map.of(), "b", List.of()));
  }

  @Test
  void keepsMemberOrder() {
    Map<?, ?> value = (Map<?, ?>) JsonReader.parse("{\"z\": \"1\", \"a\": \"2\", \"m\": \"3\"}");

    assertThat(value.keySet().stream().map(String::valueOf).toList())
        .containsExactly("z", "a", "m");
  }

  @Test
  void readsEveryStringEscape() {
    Object value =
        JsonReader.parse("[\"\\\"\", \"\\\\\", \"\\/\", \"\\b\\f\\n\\r\\t\", \"\\u00e9\"]");

    assertThat(value).isEqualTo(List.of("\"", "\\", "/", "\b\f\n\r\t", "\u00e9"));
  }

  @Test
  void toleratesWhitespaceEverywhereBetweenTokens() {
    assertThat(JsonReader.parse("  {\n\t\"a\"  :  [ \"x\" ,  \"y\" ]  }  "))
        .isEqualTo(Map.of("a", List.of("x", "y")));
  }

  // --- refusals ---------------------------------------------------------------------------

  @Test
  void refusesANumberBecauseACatalogueHasNoNumbers() {
    assertThatThrownBy(() -> JsonReader.parse("{\"a\": 1}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expected an object, an array or a string, found '1'");
  }

  @Test
  void refusesABooleanAndANull() {
    assertThatThrownBy(() -> JsonReader.parse("{\"a\": true}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("found 't'");
    assertThatThrownBy(() -> JsonReader.parse("{\"a\": null}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("found 'n'");
  }

  @Test
  void refusesTrailingContentAfterTheTopLevelValue() {
    assertThatThrownBy(() -> JsonReader.parse("{} {}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("trailing content");
  }

  @Test
  void refusesAnUnterminatedString() {
    assertThatThrownBy(() -> JsonReader.parse("[\"x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the document ends here");
  }

  @Test
  void refusesARawControlCharacterInsideAString() {
    assertThatThrownBy(() -> JsonReader.parse("[\"a\u0001b\"]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("control character inside a string must be escaped");
  }

  @Test
  void refusesAnUnknownEscape() {
    assertThatThrownBy(() -> JsonReader.parse("[\"\\q\"]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown escape");
  }

  @Test
  void refusesATruncatedUnicodeEscape() {
    assertThatThrownBy(() -> JsonReader.parse("[\"\\u00\"]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("four hexadecimal digits");
  }

  @Test
  void refusesAUnicodeEscapeCutOffByTheEndOfTheDocument() {
    assertThatThrownBy(() -> JsonReader.parse("[\"\\u00"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needs four hexadecimal digits");
  }

  /** Four digits that end exactly at the last character: one off, and a valid escape is refused. */
  @Test
  void readsAUnicodeEscapeThatEndsExactlyAtTheLastCharacter() {
    assertThatThrownBy(() -> JsonReader.parse("[\"\\u0041"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the document ends here");
  }

  @Test
  void refusesAUnicodeEscapeThatIsNotHexadecimal() {
    assertThatThrownBy(() -> JsonReader.parse("[\"\\uzzzz\"]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not four hexadecimal digits");
  }

  @Test
  void refusesAMissingColonAndAMissingComma() {
    assertThatThrownBy(() -> JsonReader.parse("{\"a\" \"b\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expected ':'");
    assertThatThrownBy(() -> JsonReader.parse("[\"a\" \"b\"]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expected ',' or ']'");
  }

  @Test
  void refusesAnUnquotedObjectKey() {
    assertThatThrownBy(() -> JsonReader.parse("{a: \"b\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expected '\"'");
  }

  @Test
  void refusesAnEmptyDocumentAndANullOne() {
    assertThatThrownBy(() -> JsonReader.parse(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the document ends here");
    assertThatThrownBy(() -> JsonReader.parse(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no JSON text to read");
  }

  @Test
  void reportsTheZeroBasedCursorOffsetWhereReadingStopped() {
    assertThatThrownBy(() -> JsonReader.parse("{\"a\": 1}")).hasMessageContaining("offset 6");
  }
}
