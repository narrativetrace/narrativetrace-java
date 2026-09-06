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

class TraceAnchorTest {

  @Test
  void mapsMonotonicReadingsRelativeToTheAnchorPair() {
    var anchor = new TraceAnchor(1_000_000L, 500_000_000L);

    assertThat(anchor.toEpochMillis(500_000_000L)).isEqualTo(1_000_000L);
    assertThat(anchor.toEpochMillis(500_000_000L + 5_000_000_000L)).isEqualTo(1_005_000L);
  }

  @Test
  void eventsBeforeTheAnchorMapBackwards() {
    var anchor = new TraceAnchor(1_000_000L, 500_000_000L);

    assertThat(anchor.toEpochMillis(500_000_000L - 2_000_000_000L)).isEqualTo(998_000L);
  }

  @Test
  void subMillisecondPrecisionTruncatesTowardZero() {
    var anchor = new TraceAnchor(1_000_000L, 0L);

    assertThat(anchor.toEpochMillis(1_234_567L)).isEqualTo(1_000_001L);
    assertThat(anchor.toEpochMillis(-1_500_000L)).isEqualTo(999_999L);
  }

  @Test
  void nowCapturesAConsistentPair() {
    var before = System.currentTimeMillis();
    var anchor = TraceAnchor.now();
    var mapped = anchor.toEpochMillis(System.nanoTime());

    assertThat(mapped).isBetween(before - 1, System.currentTimeMillis() + 1);
  }
}
