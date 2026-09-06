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

class SpanIdTest {

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> new SpanId(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("spanId");
  }

  @Test
  void rejectsWrongLength() {
    assertThatThrownBy(() -> new SpanId("abcdef01"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("16");
  }

  @Test
  void rejectsUppercaseHex() {
    assertThatThrownBy(() -> new SpanId("0123456789ABCDEF"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("spanId");
  }

  @Test
  void acceptsValid16CharHex() {
    var spanId = new SpanId("0123456789abcdef");

    assertThat(spanId.value()).isEqualTo("0123456789abcdef");
  }

  @Test
  void toStringReturnsHexValue() {
    var spanId = new SpanId("0123456789abcdef");

    assertThat(spanId).hasToString("0123456789abcdef");
  }

  @Test
  void ofFactoryCreatesSpanId() {
    var spanId = SpanId.of("fedcba9876543210");

    assertThat(spanId.value()).isEqualTo("fedcba9876543210");
  }

  @Test
  void equalsByValue() {
    var a = new SpanId("0123456789abcdef");
    var b = new SpanId("0123456789abcdef");
    var c = new SpanId("fedcba9876543210");

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
  }

  @Test
  void generateProducesValidSpanId() {
    var spanId = SpanId.generate();

    assertThat(spanId.value()).hasSize(16).matches("[0-9a-f]{16}");
  }
}
