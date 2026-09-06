/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pins RFC 8259 string escaping for the one shared escaper all JSON emitters must use. */
class JsonEscapeTest {

  @Test
  void escapesQuotesAndBackslashes() {
    assertThat(JsonEscape.escape("a\"b\\c")).isEqualTo("a\\\"b\\\\c");
  }

  @Test
  void escapesNamedControlCharacters() {
    assertThat(JsonEscape.escape("a\nb\rc\td\be\ff")).isEqualTo("a\\nb\\rc\\td\\be\\ff");
  }

  @Test
  void escapesRemainingC0ControlCharactersAsUnicode() {
    var input = "a" + (char) 0 + "b" + (char) 1 + "c" + (char) 31 + "d";

    assertThat(JsonEscape.escape(input)).isEqualTo("a\\u0000b\\u0001c\\u001fd");
  }

  @Test
  void leavesPrintableAndNonAsciiTextUntouched() {
    assertThat(JsonEscape.escape("héllo wörld — 日本語!")).isEqualTo("héllo wörld — 日本語!");
  }

  @Test
  void nullBecomesLiteralNullText() {
    assertThat(JsonEscape.escape(null)).isEqualTo("null");
  }

  @Test
  void emptyStringStaysEmpty() {
    assertThat(JsonEscape.escape("")).isEmpty();
  }
}
