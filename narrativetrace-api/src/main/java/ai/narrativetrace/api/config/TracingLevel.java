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
 * Controls how much of the captured event stream survives into the final trace tree.
 *
 * <p>Levels are ordered by increasing verbosity. {@link #isEnabled(TracingLevel)} compares them by
 * ordinal, so a more verbose level implies the behaviors of all less verbose levels:
 *
 * <table>
 * <caption>Tracing levels</caption>
 * <tr><th>Level</th><th>Captures</th><th>Performance</th></tr>
 * <tr><td>{@link #OFF}</td><td>Nothing</td><td>Zero overhead</td></tr>
 * <tr><td>{@link #ERRORS}</td><td>Only paths ending in thrown or incomplete spans</td><td>Minimal</td></tr>
 * <tr><td>{@link #SUMMARY}</td><td>Roots plus leaf calls, parameter values suppressed</td><td>Low</td></tr>
 * <tr><td>{@link #NARRATIVE}</td><td>All calls, parameter values suppressed</td><td>Moderate</td></tr>
 * <tr><td>{@link #DETAIL}</td><td>All calls with eager rendered parameter values</td><td>Full</td></tr>
 * </table>
 *
 * <p>The level can be changed at runtime via the runtime module's {@code
 * NarrativeTraceConfig.setLevel(TracingLevel)}, which holds it in a volatile field for immediate
 * visibility across threads. The level is the vocabulary; where a level comes from, and what
 * happens when two sources disagree, is runtime behaviour and is not part of this contract.
 */
public enum TracingLevel {

  /** No trace capture. {@code NarrativeContext.isActive()} returns {@code false}. */
  OFF,

  /** Only thrown and incomplete paths are retained in the built tree. */
  ERRORS,

  /**
   * Only root and leaf calls are kept; intermediate frames are pruned and parameter values
   * suppressed.
   */
  SUMMARY,

  /** All calls are retained, but parameter values are replaced with empty captures. */
  NARRATIVE,

  /**
   * All calls are retained with full eager-rendered parameter values. This is the default level.
   */
  DETAIL;

  /**
   * Returns whether this level is at least as verbose as the required level.
   *
   * @param required the minimum level to check against
   * @return {@code true} if this level enables the required level's behavior
   */
  public boolean isEnabled(TracingLevel required) {
    return this.ordinal() >= required.ordinal();
  }

  /**
   * Parses a level name, tolerating case and surrounding whitespace, and falling back for null,
   * blank, or unrecognized input.
   *
   * <p>INTENT: Single parse point for ambient configuration (system properties, agent args) so an
   * invalid value degrades to a sensible default rather than crashing capture.
   *
   * @param name the level name to parse, may be {@code null}
   * @param fallback the level to return when {@code name} is null, blank, or unrecognized
   * @return the parsed level, or {@code fallback}
   */
  public static TracingLevel fromName(String name, TracingLevel fallback) {
    if (name == null || name.isBlank()) {
      return fallback;
    }
    try {
      return TracingLevel.valueOf(name.strip().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }
}
