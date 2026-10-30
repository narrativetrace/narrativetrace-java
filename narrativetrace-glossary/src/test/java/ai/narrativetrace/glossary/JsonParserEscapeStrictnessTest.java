/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADV-2026-10-30-3 — {@code JsonParser.parseUnicodeEscape} hands its four characters to {@code
 * Integer.parseInt(hex, 16)}, which accepts a leading sign. {@code \\u+041} and {@code \\u-001} are
 * not escapes at all (RFC 8259 is {@code \\u} followed by four hex digits), yet the reader accepts
 * both documents and decodes a character that is nowhere in the file: {@code A} for the first, and
 * {@code U+FFFF} — a noncharacter no conforming JSON document can hold as an escape — for the
 * second.
 *
 * <p>INTENT: the class's own contract is strictness, in two sentences: "Strictness is deliberate —
 * the glossary is a hand-curated committed file, and a typo should fail fast with a position, never
 * be silently ignored", and "@throws IllegalArgumentException on any syntax error". A typo of
 * exactly this shape — a {@code +} where a hex digit belongs — is the one it silently accepts.
 *
 * <p><b>@llmNote</b> This is cross-runtime, not cosmetic. The nesting cap right above it is
 * documented as "the same number in every NarrativeTrace runtime … so a document accepted by one
 * runtime is accepted by all"; a {@code glossary.json} carrying {@code \\u+041} is read by the Java
 * runtime and rejected by every other port's parser, because no library JSON parser allows a sign
 * there. The sign also walks straight past the {@code (char)} cast: {@code \\u-001} is {@code
 * (char) -1}.
 */
class JsonParserEscapeStrictnessTest {

  @Test
  @DisplayName("ADV-2026-10-30-3: a signed unicode escape is a syntax error, not an 'A'")
  void aPlusSignedUnicodeEscapeIsRefused() {
    assertThatThrownBy(() -> JsonParser.parse("\"\\u+041\""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("ADV-2026-10-30-3: a negative unicode escape is a syntax error, not U+FFFF")
  void aMinusSignedUnicodeEscapeIsRefused() {
    assertThatThrownBy(() -> JsonParser.parse("\"\\u-001\""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
