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
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

class TraceparentPropertyTest {

  @Property
  void formatThenParseIsIdentity(
      @ForAll @CharRange(from = 'a', to = 'f') @StringLength(31) String traceTail,
      @ForAll @CharRange(from = 'a', to = 'f') @StringLength(15) String spanTail,
      @ForAll @IntRange(min = 0, max = 255) int flags) {
    var original = new Traceparent(TraceId.of("1" + traceTail), SpanId.of("1" + spanTail), flags);

    assertThat(Traceparent.parse(original.format())).isEqualTo(original);
  }

  /** Totality: a header from a stranger either yields nothing or yields a re-emittable value. */
  @Property
  void parseIsTotalOverArbitraryInput(@ForAll String anything) {
    var parsed = Traceparent.parse(anything);

    if (parsed != null) {
      assertThat(Traceparent.parse(parsed.format())).isEqualTo(parsed);
    }
  }

  /** One corrupted character anywhere must never be silently accepted as a different trace. */
  @Property
  void aCorruptedHeaderNeverParsesToTheOriginal(
      @ForAll @IntRange(min = 0, max = 54) int position,
      @ForAll @CharRange(from = 'g', to = 'z') char replacement) {
    var original = new Traceparent(TraceId.generate(), SpanId.generate(), 1);
    var header = original.format();
    var corrupted = header.substring(0, position) + replacement + header.substring(position + 1);

    assertThat(Traceparent.parse(corrupted)).isNotEqualTo(original);
  }

  @Property
  void everyGeneratedIdPairFormatsToTheCanonicalLength(
      @ForAll @IntRange(min = 0, max = 255) int flags) {
    var traceparent = new Traceparent(TraceId.generate(), SpanId.generate(), flags);

    assertThat(traceparent.format())
        .hasSize(55)
        .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
  }
}
