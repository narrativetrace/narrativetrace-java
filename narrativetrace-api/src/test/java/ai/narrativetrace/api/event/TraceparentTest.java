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
package ai.narrativetrace.api.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TraceparentTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";
  private static final String HEADER = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";

  /**
   * Cross-port pin: the W3C specification's own worked example, the header every NarrativeTrace
   * port's traceparent suite is built on. If this ever needs changing, the wire format diverged and
   * the ports no longer interoperate.
   */
  @Test
  void parsesTheW3cSpecificationsWorkedExample() {
    assertThat(Traceparent.parse(HEADER))
        .isEqualTo(
            new Traceparent(
                TraceId.of("4bf92f3577b34da6a3ce929d0e0e4736"), SpanId.of("00f067aa0ba902b7"), 1));
    assertThat(Traceparent.parse(HEADER).format()).isEqualTo(HEADER);
  }

  @Test
  void parsesAWellFormedHeader() {
    var parsed = Traceparent.parse(HEADER);

    assertThat(parsed).isNotNull();
    assertThat(parsed.traceId().value()).isEqualTo(TRACE_ID);
    assertThat(parsed.parentSpanId().value()).isEqualTo(SPAN_ID);
    assertThat(parsed.traceFlags()).isEqualTo(1);
  }

  @Test
  void treatsAnAbsentHeaderAsNoTraceContext() {
    assertThat(Traceparent.parse(null)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "not-a-traceparent",
        "00-" + TRACE_ID + "-" + SPAN_ID,
        "0-" + TRACE_ID + "-" + SPAN_ID + "-01",
        "000-" + TRACE_ID + "-" + SPAN_ID + "-01",
        "0g-" + TRACE_ID + "-" + SPAN_ID + "-01",
        "00-bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
        "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-0f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00F067AA0BA902B7-01",
        "00-" + TRACE_ID + "-" + SPAN_ID + "-0",
        "00-" + TRACE_ID + "-" + SPAN_ID + "-0g",
        "00-" + TRACE_ID + "-" + SPAN_ID + "-011",
        " 00-" + TRACE_ID + "-" + SPAN_ID + "-01",
        "00-" + TRACE_ID + "-" + SPAN_ID + "-01 ",
      })
  void rejectsAMalformedHeaderWithoutThrowing(String malformed) {
    assertThat(Traceparent.parse(malformed)).isNull();
  }

  @Test
  void rejectsTheForbiddenFfVersion() {
    assertThat(Traceparent.parse("ff-" + TRACE_ID + "-" + SPAN_ID + "-01")).isNull();
  }

  @Test
  void rejectsAnAllZeroTraceId() {
    assertThat(Traceparent.parse("00-" + "0".repeat(32) + "-" + SPAN_ID + "-01")).isNull();
  }

  @Test
  void rejectsAnAllZeroParentSpanId() {
    assertThat(Traceparent.parse("00-" + TRACE_ID + "-" + "0".repeat(16) + "-01")).isNull();
  }

  @Test
  void rejectsTrailingFieldsOnVersionZero() {
    assertThat(Traceparent.parse(HEADER + "-extra")).isNull();
  }

  @Test
  void acceptsTrailingFieldsOnAFutureVersionAndReadsTheFirstFour() {
    var parsed = Traceparent.parse("01-" + TRACE_ID + "-" + SPAN_ID + "-01-what-comes-next");

    assertThat(parsed).isNotNull();
    assertThat(parsed.traceId().value()).isEqualTo(TRACE_ID);
    assertThat(parsed.parentSpanId().value()).isEqualTo(SPAN_ID);
  }

  @Test
  void rejectsAFutureVersionWithAnEmptyTrailingField() {
    assertThat(Traceparent.parse("01-" + TRACE_ID + "-" + SPAN_ID + "-01-")).isNull();
  }

  @Test
  void formatsAsAVersionZeroHeader() {
    var traceparent = new Traceparent(TraceId.of(TRACE_ID), SpanId.of(SPAN_ID), 1);

    assertThat(traceparent.format()).isEqualTo(HEADER).hasSize(55);
    assertThat(traceparent).hasToString(HEADER);
  }

  @Test
  void formatsAFutureVersionBackAsVersionZero() {
    var parsed = Traceparent.parse("cc-" + TRACE_ID + "-" + SPAN_ID + "-01-vendor");

    assertThat(parsed.format()).startsWith("00-");
  }

  @Test
  void padsASingleDigitFlagsByte() {
    var traceparent = new Traceparent(TraceId.of(TRACE_ID), SpanId.of(SPAN_ID), 0);

    assertThat(traceparent.format()).endsWith("-00");
  }

  @Test
  void readsTheSampledBitOutOfTheFlagsByte() {
    assertThat(Traceparent.parse(HEADER).sampled()).isTrue();
    assertThat(Traceparent.parse("00-" + TRACE_ID + "-" + SPAN_ID + "-00").sampled()).isFalse();
    assertThat(Traceparent.parse("00-" + TRACE_ID + "-" + SPAN_ID + "-fe").sampled()).isFalse();
    assertThat(Traceparent.parse("00-" + TRACE_ID + "-" + SPAN_ID + "-ff").sampled()).isTrue();
  }

  @Test
  void rejectsNullIdsAtConstruction() {
    assertThatThrownBy(() -> new Traceparent(null, SpanId.of(SPAN_ID), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("traceId");
    assertThatThrownBy(() -> new Traceparent(TraceId.of(TRACE_ID), null, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("parentSpanId");
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 256})
  void rejectsFlagsOutsideOneByteAtConstruction(int flags) {
    assertThatThrownBy(() -> new Traceparent(TraceId.of(TRACE_ID), SpanId.of(SPAN_ID), flags))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("traceFlags");
  }
}
