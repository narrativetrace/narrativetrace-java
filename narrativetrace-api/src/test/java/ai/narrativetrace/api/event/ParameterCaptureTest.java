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

import org.junit.jupiter.api.Test;

class ParameterCaptureTest {

  @Test
  void capturesNameAndRenderedValueWithRedactedFalse() {
    var capture = new ParameterCapture("customerId", "\"C-123\"", false);

    assertThat(capture.name()).isEqualTo("customerId");
    assertThat(capture.renderedValue()).isEqualTo("\"C-123\"");
    assertThat(capture.redacted()).isFalse();
  }

  @Test
  void supportsRedactedTrue() {
    var capture = new ParameterCapture("password", "[REDACTED]", true);

    assertThat(capture.redacted()).isTrue();
    assertThat(capture.renderedValue()).isEqualTo("[REDACTED]");
  }

  @Test
  void renderedValueReturnsStoredStringForNonRedacted() {
    var capture = new ParameterCapture("customerId", "\"C-123\"", false);

    assertThat(capture.renderedValue()).isEqualTo("\"C-123\"");
  }

  @Test
  void renderedValueReturnsStoredStringForRedacted() {
    var capture = new ParameterCapture("password", "[REDACTED]", true);

    assertThat(capture.renderedValue()).isEqualTo("[REDACTED]");
  }

  @Test
  void threeArgConstructorProducesNullStructuredValue() {
    var capture = new ParameterCapture("id", "\"X\"", false);

    assertThat(capture.structuredValue()).isNull();
  }

  @Test
  void fourArgConstructorPreservesStructuredValue() {
    var structured = new RenderedValue.LongVal(42L);
    var capture = new ParameterCapture("count", "42", false, structured);

    assertThat(capture.structuredValue()).isEqualTo(structured);
  }
}
