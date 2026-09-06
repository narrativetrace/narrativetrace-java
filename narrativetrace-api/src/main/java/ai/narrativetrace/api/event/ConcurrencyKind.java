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

/**
 * Relationship between concurrent work and the launching call.
 *
 * <p>INTENT: Use this to distinguish awaited fan-out from background work that keeps running after
 * the parent returns.
 */
public enum ConcurrencyKind {
  /** Concurrent work that is later joined back into the parent flow. */
  FORK_JOIN,

  /** Background work launched from the parent without a join step. */
  FIRE_AND_FORGET,

  /**
   * Work a worker thread ran under a propagated context snapshot — a Spring {@code @Async} method,
   * a Micrometer-propagated task, any manual {@code snapshot.activate()}. Unlike the two above it
   * is not launched through a NarrativeTrace helper, so the caller never declared a group: the tag
   * is derived at the snapshot boundary. It marks the work as unordered with respect to its
   * siblings, which is what keeps the structural artifact stable across runs.
   */
  ASYNC
}
