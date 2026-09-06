/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.render.ValueRenderer;
import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.oracle.Oracles;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

/**
 * Target 1 of the parity document's fuzzing list: the wire-format reader.
 *
 * <p>INTENT: {@code traceparent} is the one input a stranger controls completely and the one the
 * library reads on a request path. Its contract is unusual and worth restating: {@link
 * Traceparent#parse} returns {@code null} for anything malformed and must never throw, because a
 * bad header from a stranger has to degrade to "start a fresh trace", never to a failed request.
 *
 * <p><b>@llmNote</b> The corpus runs first and the generated sweep second, in that order, so a
 * failure names a case id when one exists. The generated half draws from the header's own alphabet
 * rather than arbitrary text, which is what makes it reach the parser's interesting branches
 * instead of bouncing off the length check.
 */
class TraceparentParsingPropertyTest {

  private static final int NUL = 0x0000;
  private static final int ARABIC_INDIC_FOUR = 0x0664;

  @Test
  void everyCorpusHeaderParsesToTheOutcomeItDeclares() {
    for (var header : HostileCorpus.traceparents()) {
      var parsed =
          Oracles.withinBudget(
              "traceparent " + header.id(), () -> Traceparent.parse(header.value()));
      if (header.accepted()) {
        assertThat(parsed).as("%s: %s", header.id(), header.description()).isNotNull();
      } else {
        assertThat(parsed).as("%s: %s", header.id(), header.description()).isNull();
      }
    }
  }

  @Test
  void everyCorpusHeaderTheParserAcceptsRoundTrips() {
    for (var header : HostileCorpus.traceparents()) {
      var parsed = Traceparent.parse(header.value());
      if (parsed == null) {
        continue;
      }
      assertThat(Traceparent.parse(parsed.format()))
          .as("%s must survive format-then-parse unchanged", header.id())
          .isEqualTo(parsed);
    }
  }

  /**
   * The {@code tracestate} companion is not parsed by this library, so the oracle is narrower and
   * worth pinning as such: carrying one through the value renderer must not throw, and if a {@code
   * tracestate} reader is ever added, its cases already live in the corpus.
   */
  @Test
  void everyCorpusTracestateSurvivesBeingCarriedAsAValue() {
    var renderer = new ValueRenderer();
    for (var state : HostileCorpus.tracestates()) {
      assertThatCode(() -> renderer.render(state.value()))
          .as("%s: %s", state.id(), state.description())
          .doesNotThrowAnyException();
    }
  }

  @Property(tries = 500)
  void parsingNeverThrowsWhateverTheHeaderContains(@ForAll("headerShaped") String header) {
    assertThatCode(() -> Traceparent.parse(header)).doesNotThrowAnyException();
  }

  @Property(tries = 200)
  void parsingNeverThrowsOnArbitraryText(@ForAll @StringLength(max = 200) String header) {
    assertThatCode(() -> Traceparent.parse(header)).doesNotThrowAnyException();
  }

  @Property(tries = 500)
  void whatTheParserAcceptsAlwaysRoundTrips(@ForAll("headerShaped") String header) {
    var parsed = Traceparent.parse(header);
    if (parsed != null) {
      assertThat(Traceparent.parse(parsed.format())).isEqualTo(parsed);
    }
  }

  /** Nothing the parser accepts may carry an id the W3C ABNF forbids. */
  @Property(tries = 500)
  void whatTheParserAcceptsIsAlwaysWellFormed(@ForAll("headerShaped") String header) {
    var parsed = Traceparent.parse(header);
    if (parsed == null) {
      return;
    }
    assertThat(parsed.traceId().value()).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
    assertThat(parsed.parentSpanId().value()).matches("[0-9a-f]{16}").isNotEqualTo("0".repeat(16));
    assertThat(parsed.traceFlags()).isBetween(0, 0xff);
  }

  /** Every header this library formats is one it accepts back — the interoperability floor. */
  @Property(tries = 200)
  void everyHeaderTheLibraryFormatsIsAcceptedBack(@ForAll("wellFormed") String header) {
    var parsed = Traceparent.parse(header);

    assertThat(parsed).as("a conforming header must parse: %s", header).isNotNull();
    assertThat(parsed.format()).isEqualTo(header);
  }

  /**
   * Text built from the header's own alphabet: hex digits, the delimiter, and the characters a
   * hostile sender reaches for. Arbitrary unicode almost never survives the first length check.
   */
  @Provide
  Arbitrary<String> headerShaped() {
    var alphabet =
        Arbitraries.of(
            "0",
            "1",
            "9",
            "a",
            "f",
            "F",
            "-",
            "z",
            " ",
            "\n",
            String.valueOf((char) NUL),
            String.valueOf((char) ARABIC_INDIC_FOUR));
    return alphabet.list().ofMinSize(0).ofMaxSize(60).map(parts -> String.join("", parts));
  }

  /** A header the library itself would emit, from arbitrary ids and flags. */
  @Provide
  Arbitrary<String> wellFormed() {
    var hex = Arbitraries.chars().range('0', '9').range('a', 'f');
    var traceId =
        hex.list()
            .ofSize(32)
            .map(TraceparentParsingPropertyTest::join)
            .filter(id -> !isAllZero(id));
    var spanId =
        hex.list()
            .ofSize(16)
            .map(TraceparentParsingPropertyTest::join)
            .filter(id -> !isAllZero(id));
    var flags = Arbitraries.integers().between(0, 0xff).map(flag -> String.format("%02x", flag));
    return Combinators.combine(traceId, spanId, flags)
        .as((trace, span, flag) -> "00-" + trace + "-" + span + "-" + flag);
  }

  private static String join(List<Character> characters) {
    var sb = new StringBuilder(characters.size());
    characters.forEach(sb::append);
    return sb.toString();
  }

  private static boolean isAllZero(String value) {
    return value.chars().allMatch(c -> c == '0');
  }
}
