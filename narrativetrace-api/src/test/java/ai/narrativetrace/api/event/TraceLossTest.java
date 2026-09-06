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

class TraceLossTest {

  @Test
  void noneReportsNothingLost() {
    assertThat(TraceLoss.none().any()).isFalse();
    assertThat(TraceLoss.none().droppedEvents()).isZero();
  }

  @Test
  void droppedEventsCountAsLoss() {
    assertThat(new TraceLoss(1, 0, 0).any()).isTrue();
  }

  @Test
  void refusedScopesCountAsLoss() {
    assertThat(new TraceLoss(0, 1, 3).any()).isTrue();
  }

  @Test
  void sumsComponentwise() {
    var total = new TraceLoss(10, 1, 4).plus(new TraceLoss(5, 2, 6));

    assertThat(total).isEqualTo(new TraceLoss(15, 3, 10));
  }

  @Test
  void differenceAgainstAnEarlierReadingIsTheLossBetweenThem() {
    var before = new TraceLoss(100, 2, 8);
    var after = new TraceLoss(140, 3, 11);

    assertThat(after.since(before)).isEqualTo(new TraceLoss(40, 1, 3));
  }

  @Test
  void differenceFloorsAtZeroWhenCountersWereResetUnderneath() {
    var before = new TraceLoss(100, 5, 50);
    var after = new TraceLoss(3, 0, 0);

    assertThat(after.since(before)).isEqualTo(TraceLoss.none());
  }

  @Test
  void rejectsNegativeCounts() {
    assertThatThrownBy(() -> new TraceLoss(-1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("negative");
  }

  @Test
  void rejectsANegativeDiscardCount() {
    assertThatThrownBy(() -> new TraceLoss(0, 0, 0, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("negative");
  }

  /**
   * The three-argument form is what every reading before discards existed used, and it must keep
   * meaning the same thing: nothing was discarded, not "unknown".
   */
  @Test
  void theThreeCountFormDiscardedNothing() {
    assertThat(new TraceLoss(1, 2, 3).discardedSpans()).isZero();
    assertThat(new TraceLoss(1, 2, 3)).isEqualTo(new TraceLoss(1, 2, 3, 0));
  }

  /**
   * Discarding is not incompleteness: the request that owned the work had already ended, so no
   * rendered narrative is missing anything, and no footer may claim otherwise.
   */
  @Test
  void discardedSpansAreCountedButDoNotMakeATraceIncomplete() {
    var loss = new TraceLoss(0, 0, 0, 7);

    assertThat(loss.discardedSpans()).isEqualTo(7);
    assertThat(loss.any()).isFalse();
  }

  @Test
  void sumsDiscardsComponentwiseToo() {
    var total = new TraceLoss(10, 1, 4, 6).plus(new TraceLoss(5, 2, 6, 9));

    assertThat(total).isEqualTo(new TraceLoss(15, 3, 10, 15));
  }

  @Test
  void differenceCoversDiscardsToo() {
    var before = new TraceLoss(100, 2, 8, 4);
    var after = new TraceLoss(140, 3, 11, 30);

    assertThat(after.since(before)).isEqualTo(new TraceLoss(40, 1, 3, 26));
  }
}
