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
