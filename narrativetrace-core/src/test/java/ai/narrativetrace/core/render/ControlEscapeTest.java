/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ControlEscapeTest {

  @Test
  void mapsNewlineTabAndCarriageReturnToMnemonicEscapes() {
    assertThat(ControlEscape.sanitize("a\nb\tc\rd")).isEqualTo("a\\nb\\tc\\rd");
  }

  @Test
  void mapsBackspaceAndFormFeedToMnemonicEscapes() {
    assertThat(ControlEscape.sanitize("a\bb\fc")).isEqualTo("a\\bb\\fc");
  }

  @Test
  void mapsOtherC0ControlsToLowercaseUnicodeEscapes() {
    // ESC (0x1B) is the ANSI terminal-injection vector; it has no mnemonic and becomes \\u001b.
    assertThat(ControlEscape.sanitize("x" + (char) 0x1b + "y")).isEqualTo("x\\u001by");
  }

  @Test
  void leavesQuotesAndBackslashesUnchanged() {
    // Distinguishes control-escaping from JSON escaping: value content like C:\temp and " survive.
    assertThat(ControlEscape.sanitize("C:\\temp \"q\"")).isEqualTo("C:\\temp \"q\"");
  }

  @Test
  void leavesPlainTextUnchanged() {
    assertThat(ControlEscape.sanitize("order-42")).isEqualTo("order-42");
  }

  /**
   * Regression: a lone surrogate is not a control character and not a code point either. No UTF-8
   * sink can encode it, so it reached {@code Files.writeString} and raised {@code
   * UnmappableCharacterException} — a value the application merely returned failing the run that
   * traced it. Found by the security suite's hostile corpus.
   */
  @Test
  void escapesAnUnpairedHighSurrogate() {
    assertThat(ControlEscape.sanitize("a" + (char) 0xd800 + "b")).isEqualTo("a\\ud800b");
  }

  @Test
  void escapesAnUnpairedLowSurrogate() {
    assertThat(ControlEscape.sanitize("a" + (char) 0xdc00 + "b")).isEqualTo("a\\udc00b");
  }

  @Test
  void escapesAHighSurrogateAtTheEndOfTheText() {
    assertThat(ControlEscape.sanitize("a" + (char) 0xd83d)).isEqualTo("a\\ud83d");
  }

  @Test
  void escapesBothHalvesOfAReversedPair() {
    assertThat(ControlEscape.sanitize("" + (char) 0xdc00 + (char) 0xd800))
        .isEqualTo("\\udc00\\ud800");
  }

  /** A well-formed pair is ordinary content: an emoji must survive a narration unchanged. */
  @Test
  void leavesAWellFormedSurrogatePairAlone() {
    var monkey = "" + (char) 0xd83d + (char) 0xde48;

    assertThat(ControlEscape.sanitize("see " + monkey)).isEqualTo("see " + monkey);
  }

  @Test
  void leavesAPairFollowedByAnotherPairAlone() {
    var monkey = "" + (char) 0xd83d + (char) 0xde48;

    assertThat(ControlEscape.sanitize(monkey + monkey)).isEqualTo(monkey + monkey);
  }

  /** The boundary the look-ahead has to get right: a valid pair immediately after a lone half. */
  @Test
  void escapesALoneHalfAndKeepsThePairThatFollowsIt() {
    var monkey = "" + (char) 0xd83d + (char) 0xde48;

    assertThat(ControlEscape.sanitize((char) 0xd800 + monkey)).isEqualTo("\\ud800" + monkey);
  }

  @Test
  void everySanitizedStringIsEncodableAsUtf8() {
    var hostile = "a" + (char) 0xd800 + (char) 0x1b + (char) 0xdfff + "z";

    var sanitized = ControlEscape.sanitize(hostile);

    assertThat(java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(sanitized)).isTrue();
  }
}
