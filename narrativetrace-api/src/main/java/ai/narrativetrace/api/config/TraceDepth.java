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
package ai.narrativetrace.api.config;

/**
 * How deep a test's trace looks into the classes under test.
 *
 * <p>INTENT: {@link #PUBLIC} is what every integration records unless told otherwise — the
 * observable boundary of each class, its non-private methods. {@link #INTERNAL} additionally
 * records the class's own private methods, so a call chain that disappears into a private helper
 * (and the wrong value it returns) is visible. It is a verbose, test-time setting and never a
 * production one: it is reachable only through an explicit option, never from a default or a
 * profile.
 *
 * <p><b>@llmNote</b> The depth is part of what an approval baseline ({@code *.approved.nt})
 * declares, because a baseline cut at one depth and a run made at the other are not comparable —
 * see {@code NarrativeApproval}.
 */
public enum TraceDepth {

  /** Non-private methods only. The default everywhere. */
  PUBLIC,

  /** Non-private methods plus the private methods of the classes under test. Opt-in. */
  INTERNAL;

  /**
   * Parses {@code public} / {@code internal} (case and whitespace tolerant), else {@code fallback}.
   */
  public static TraceDepth fromName(String name, TraceDepth fallback) {
    if (name == null || name.isBlank()) {
      return fallback;
    }
    try {
      return TraceDepth.valueOf(name.strip().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }
}
