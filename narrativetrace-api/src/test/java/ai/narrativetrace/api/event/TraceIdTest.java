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

class TraceIdTest {

  private static final String VALID = "0123456789abcdef0123456789abcdef";

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> new TraceId(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("traceId");
  }

  @Test
  void rejectsWrongLength() {
    assertThatThrownBy(() -> new TraceId("0123456789abcdef"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32");
  }

  @Test
  void rejectsUppercaseHex() {
    assertThatThrownBy(() -> new TraceId("0123456789ABCDEF0123456789abcdef"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("traceId");
  }

  @Test
  void acceptsValid32CharHex() {
    var traceId = new TraceId(VALID);

    assertThat(traceId.value()).isEqualTo(VALID);
  }

  @Test
  void toStringReturnsHexValue() {
    var traceId = new TraceId(VALID);

    assertThat(traceId).hasToString(VALID);
  }

  @Test
  void ofFactoryCreatesTraceId() {
    var traceId = TraceId.of(VALID);

    assertThat(traceId.value()).isEqualTo(VALID);
  }

  @Test
  void equalsByValue() {
    var a = new TraceId(VALID);
    var b = new TraceId(VALID);
    var c = new TraceId("fedcba9876543210fedcba9876543210");

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
  }

  @Test
  void generateProducesValidTraceId() {
    var traceId = TraceId.generate();

    assertThat(traceId.value()).hasSize(32).matches("[0-9a-f]{32}");
  }
}
