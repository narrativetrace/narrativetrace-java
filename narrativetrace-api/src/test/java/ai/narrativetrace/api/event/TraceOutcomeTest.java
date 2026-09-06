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

class TraceOutcomeTest {

  @Test
  void returnedHoldsReturnValue() {
    var outcome = new TraceOutcome.Returned("\"order-42\"");

    assertThat(outcome.renderedValue()).isEqualTo("\"order-42\"");
    assertThat(outcome).isInstanceOf(TraceOutcome.class);
  }

  @Test
  void returnedOneArgConstructorProducesNullStructuredValue() {
    var outcome = new TraceOutcome.Returned("42");

    assertThat(outcome.structuredValue()).isNull();
  }

  @Test
  void returnedTwoArgConstructorPreservesStructuredValue() {
    var structured = new RenderedValue.DoubleVal(99.9);
    var outcome = new TraceOutcome.Returned("99.9", structured);

    assertThat(outcome.structuredValue()).isEqualTo(structured);
  }

  @Test
  void threwHoldsException() {
    var exception = new RuntimeException("insufficient funds");
    var outcome = new TraceOutcome.Threw(exception);

    assertThat(outcome.exception()).isSameAs(exception);
    assertThat(outcome).isInstanceOf(TraceOutcome.class);
  }

  @Test
  void incompleteRepresentsInFlightMethod() {
    var outcome = new TraceOutcome.Incomplete();

    assertThat(outcome).isInstanceOf(TraceOutcome.class);
  }
}
