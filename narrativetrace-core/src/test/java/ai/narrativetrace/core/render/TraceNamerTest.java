/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.SpanIdGenerator;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class TraceNamerTest {

  @Test
  void allZerosHexProducesFirstWordFromEachArray() {
    var name = TraceNamer.name("00000000000000000000000000000000");
    var words = name.split(" ");

    assertThat(words).hasSize(3);
    // bits = 0, adj=0, noun=0, verb=0 → first word from each array
    assertThat(words[0]).isEqualTo("red");
    assertThat(words[1]).isEqualTo("fox");
    assertThat(words[2]).isEqualTo("runs");
  }

  @Test
  void allFsHexProducesLastWordFromEachArray() {
    var name = TraceNamer.name("ffffffffffffffffffffffffffffffff");
    var words = name.split(" ");

    assertThat(words).hasSize(3);
    // bits = 0xFFFFFFF, adj=0xFF=255, noun=0x1FF=511, verb=0xFF=255
    assertThat(words[0]).isEqualTo("nutty");
    assertThat(words[1]).isEqualTo("rig");
    assertThat(words[2]).isEqualTo("fans");
  }

  @Test
  void determinismSameInputSameOutput() {
    var hex = "a3f7c1b290de4f8801234567deadbeef";

    assertThat(TraceNamer.name(hex)).isEqualTo(TraceNamer.name(hex));
  }

  @Test
  void outputIsThreeSpaceSeparatedLowercaseWords() {
    var hex = "5a8b3c2d1e0f9876543210abcdef0123";
    var name = TraceNamer.name(hex);

    assertThat(name).matches("[a-z]+ [a-z]+ [a-z]+");
  }

  @Test
  void eachWordIsBetween3And6Characters() {
    var hex = "deadbeef01234567890abcdef1234567";
    var words = TraceNamer.name(hex).split(" ");

    for (var word : words) {
      assertThat(word.length()).as("word '%s'", word).isBetween(3, 6);
    }
  }

  @Test
  void differentTraceIdsProduceDifferentNames() {
    var names = new HashSet<String>();
    for (int i = 0; i < 1000; i++) {
      var traceId = SpanIdGenerator.traceId();
      names.add(TraceNamer.name(traceId.value()));
    }

    // With 33.5M combinations, 1000 random samples should produce ~1000 distinct names
    assertThat(names.size()).isGreaterThan(990);
  }

  @Test
  void onlyFirst7HexCharsMatter() {
    var hex1 = "abcdef0000000000000000000000aaaa";
    var hex2 = "abcdef0000000000000000000000bbbb";

    assertThat(TraceNamer.name(hex1)).isEqualTo(TraceNamer.name(hex2));
  }

  @Test
  void differentFirst7HexCharsProduceDifferentNames() {
    var hex1 = "1234567000000000000000000000aaaa";
    var hex2 = "7654321000000000000000000000aaaa";

    assertThat(TraceNamer.name(hex1)).isNotEqualTo(TraceNamer.name(hex2));
  }

  @Test
  void classLoadsWithoutErrorValidatingArrayLengths() {
    // Accessing any method forces class loading and static initializer execution
    var name = TraceNamer.name("00000000000000000000000000000000");
    assertThat(name).isNotNull();
  }

  @Test
  void validateRejectsWrongArrayLength() {
    assertThatThrownBy(() -> TraceNamer.validate("TEST", new String[] {"abc"}, 2))
        .isInstanceOf(ExceptionInInitializerError.class)
        .hasMessageContaining("has 1 words, expected 2");
  }

  @Test
  void validateRejectsWordShorterThan3Chars() {
    assertThatThrownBy(() -> TraceNamer.validate("TEST", new String[] {"ab", "def"}, 2))
        .isInstanceOf(ExceptionInInitializerError.class)
        .hasMessageContaining("length 2 outside 3-6 range");
  }

  @Test
  void validateRejectsWordLongerThan6Chars() {
    assertThatThrownBy(() -> TraceNamer.validate("TEST", new String[] {"toolong7"}, 1))
        .isInstanceOf(ExceptionInInitializerError.class)
        .hasMessageContaining("length 8 outside 3-6 range");
  }
}
