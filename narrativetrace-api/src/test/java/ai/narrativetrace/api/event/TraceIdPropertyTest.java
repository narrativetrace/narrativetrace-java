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

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.StringLength;

class TraceIdPropertyTest {

  @Property
  void generatedTraceIdIsAlwaysValid() {
    var traceId = TraceId.generate();

    assertThat(traceId.value()).hasSize(32).matches("[0-9a-f]{32}");
    assertThat(traceId.toString()).isEqualTo(traceId.value());
  }

  @Property
  void roundTripsViaOf(
      @ForAll @CharRange(from = '0', to = '9') @StringLength(16) String digits,
      @ForAll @CharRange(from = 'a', to = 'f') @StringLength(16) String letters) {
    var hex = digits + letters;
    var traceId = TraceId.of(hex);

    assertThat(traceId.value()).isEqualTo(hex);
    assertThat(TraceId.of(traceId.toString())).isEqualTo(traceId);
  }
}
