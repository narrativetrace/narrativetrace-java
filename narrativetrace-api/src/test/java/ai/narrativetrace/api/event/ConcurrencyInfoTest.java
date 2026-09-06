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

class ConcurrencyInfoTest {

  /**
   * The set is a cross-port contract: {@code concurrency.kind} is enumerated in {@code
   * chapter-tree.schema.json} and every port mirrors it, so growing it is a deliberate, additive
   * act — never a side effect. ASYNC was added 2026-08-28 for work adopted from a propagated
   * snapshot.
   */
  @Test
  void concurrencyKindHasExactlyThreeValues() {
    assertThat(ConcurrencyKind.values())
        .containsExactly(
            ConcurrencyKind.FORK_JOIN, ConcurrencyKind.FIRE_AND_FORGET, ConcurrencyKind.ASYNC);
  }

  @Test
  void forkJoinInfoPreservesAllFields() {
    var info =
        new ConcurrencyInfo("group-1", "pool-1-thread-1", 42L, false, ConcurrencyKind.FORK_JOIN);

    assertThat(info.groupId()).isEqualTo("group-1");
    assertThat(info.threadName()).isEqualTo("pool-1-thread-1");
    assertThat(info.threadId()).isEqualTo(42L);
    assertThat(info.virtual()).isFalse();
    assertThat(info.kind()).isEqualTo(ConcurrencyKind.FORK_JOIN);
  }

  @Test
  void fireAndForgetInfoPreservesKind() {
    var info =
        new ConcurrencyInfo("group-2", "async-1", 99L, true, ConcurrencyKind.FIRE_AND_FORGET);

    assertThat(info.kind()).isEqualTo(ConcurrencyKind.FIRE_AND_FORGET);
    assertThat(info.virtual()).isTrue();
  }
}
