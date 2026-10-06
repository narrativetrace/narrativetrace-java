/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

/**
 * One appended row of {@code ledger/quota.md}'s Spend log: one sporadic-lane (Codex/Gemini) trial.
 */
public record QuotaSpendRow(
    String date, Platform platform, String skill, String caseName, String week) {

  public QuotaSpendRow {
    requireText(date, "date");
    if (platform == null) {
      throw new IllegalArgumentException("a QuotaSpendRow's platform must not be null");
    }
    requireText(skill, "skill");
    requireText(caseName, "caseName");
    requireText(week, "week");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("a QuotaSpendRow's " + field + " must not be blank");
    }
  }

  /** The exact pipe-table row {@code QuotaMarkdown} appends to the Spend log. */
  public String toMarkdownRow() {
    return "| "
        + date
        + " | "
        + platform.name().toLowerCase(java.util.Locale.ROOT)
        + " | "
        + skill
        + " | "
        + caseName
        + " | "
        + week
        + " |";
  }
}
