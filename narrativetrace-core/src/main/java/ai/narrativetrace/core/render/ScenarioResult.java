/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.util.Locale;

/**
 * Overall result of a traced scenario, in the two spellings the product needs.
 *
 * <p>INTENT: One fact, two audiences. The canonical {@code chapter-tree.schema.json} constrains
 * {@code scenario.result} to {@code success}/{@code error}, while the Markdown caption reads {@code
 * **Result:** PASSED}. Carrying both spellings on one enum keeps them from drifting and makes an
 * out-of-contract value unrepresentable rather than merely untested.
 *
 * <p><b>@llmNote</b> Never write {@link #name()} into an artifact. {@link #wireName()} is the only
 * spelling the cross-runtime schema accepts; {@link #displayName()} is for human-facing prose. The
 * other runtimes mirror this split; as of the 2026-08-27 audit none ships the equivalent type, so
 * this enum is where the contract is written down.
 */
public enum ScenarioResult {

  /** The scenario completed as intended. */
  SUCCESS("success", "PASSED"),

  /** The scenario failed. */
  ERROR("error", "FAILED");

  private final String wireName;
  private final String displayName;

  ScenarioResult(String wireName, String displayName) {
    this.wireName = wireName;
    this.displayName = displayName;
  }

  /** The schema-legal spelling, written into JSON artifacts. */
  public String wireName() {
    return wireName;
  }

  /** The human-facing spelling, rendered into Markdown and console output. */
  public String displayName() {
    return displayName;
  }

  /** Maps a test outcome flag to the matching result. */
  public static ScenarioResult of(boolean failed) {
    return failed ? ERROR : SUCCESS;
  }

  /**
   * Parses either spelling, case-insensitively.
   *
   * @throws IllegalArgumentException if the value is neither a wire nor a display spelling — the
   *     guard that stops an out-of-contract value reaching an artifact.
   */
  public static ScenarioResult from(String value) {
    if (value == null) {
      throw new IllegalArgumentException("scenario result must not be null");
    }
    var normalized = value.trim().toLowerCase(Locale.ROOT);
    for (var candidate : values()) {
      if (candidate.wireName.equals(normalized)
          || candidate.displayName.toLowerCase(Locale.ROOT).equals(normalized)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException(
        "unknown scenario result '" + value + "'; expected one of success, error, PASSED, FAILED");
  }
}
