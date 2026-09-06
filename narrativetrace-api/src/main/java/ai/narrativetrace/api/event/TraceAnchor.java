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
 * Wall-clock anchor for one trace: a {@link System#currentTimeMillis()} and {@link
 * System#nanoTime()} pair captured together when the trace starts.
 *
 * <p>INTENT: Events carry monotonic nanos (correct for durations, meaningless as an epoch).
 * Translating them to wall-clock needs a paired reading; anchoring per trace instead of once per
 * JVM bounds NTP drift by the trace's duration rather than the process uptime.
 *
 * @param epochMillis Wall-clock reading at trace start.
 * @param nanoTime Monotonic reading captured together with {@link #epochMillis()}.
 */
public record TraceAnchor(long epochMillis, long nanoTime) {

  /** Anchor pinned to now: a freshly captured wall-clock/monotonic pair. */
  public static TraceAnchor now() {
    return new TraceAnchor(System.currentTimeMillis(), System.nanoTime());
  }

  /** Wall-clock epoch millis corresponding to the given monotonic reading. */
  public long toEpochMillis(long eventNanos) {
    return epochMillis + (eventNanos - nanoTime) / 1_000_000;
  }
}
