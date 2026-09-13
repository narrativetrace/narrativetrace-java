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

import ai.narrativetrace.skills.Skill;
import java.util.List;

/**
 * Regenerates {@code ledger/promotion.md} — the skill × platform matrix — from the typed skill
 * catalogue and {@code ledger/runs.jsonl}. Pure functions only: this is BUILD OUTPUT, checked
 * against drift like {@code SKILL.md} and the {@code AGENTS.md} managed section. Mirrors the
 * TypeScript reference's {@code tools/promotion-render.ts}.
 */
public final class PromotionRenderer {

  private PromotionRenderer() {}

  private static final List<Platform> COLUMNS =
      List.of(Platform.CLAUDE, Platform.CODEX, Platform.GEMINI);

  private static List<RunLedgerRow> rowsFor(
      List<RunLedgerRow> runs, String skill, Platform platform) {
    return runs.stream()
        .filter(row -> row.skill().equals(skill) && row.platform() == platform)
        .toList();
  }

  /** Owner-ruled 2026-09-13's promotion state: "one green trigger sample + green happy-path". */
  private static boolean isApproved(List<RunLedgerRow> rows) {
    return rows.stream().anyMatch(r -> r.caseName().contains("happy-path") && r.pass())
        && rows.stream().anyMatch(r -> r.caseName().contains("trigger") && r.pass());
  }

  private static String cellFor(List<RunLedgerRow> runs, String skill, Platform platform) {
    List<RunLedgerRow> rows = rowsFor(runs, skill, platform);
    if (rows.isEmpty()) {
      return "not yet run";
    }
    if (isApproved(rows)) {
      return "approved";
    }
    RunLedgerRow latest = rows.get(rows.size() - 1);
    String date = latest.date().length() >= 10 ? latest.date().substring(0, 10) : latest.date();
    return (latest.pass() ? "green (" : "red (") + latest.model() + ", " + date + ")";
  }

  private static final String HEADER =
      """
# Promotion matrix

Skill × platform. A cell turns **approved** after one green trigger sample and one green
happy-path case on that platform (skill-harness-design.md §5); **green (model, date)** marks the
most recent passing trial; **red (model, date)** marks the most recent failing one; **not yet
run** means no trial has landed a row in `ledger/runs.jsonl` for that pair yet. An empty or
non-approved cell never blocks the nightly — only the Claude lane's own green gates shipping
(`evals/README.md`'s sporadic policy) — but it blocks that skill's promotion to fully-approved
status. A wording or fixture change clears that skill's row; re-approval accrues again from
`ledger/runs.jsonl`.

Regenerated from `ledger/runs.jsonl` and the typed skill catalogue — never hand-edited; drift
fails `./gradlew check` (`PromotionDriftTest`).
""";

  /**
   * Regenerates {@code ledger/promotion.md}'s full content from the typed catalogue and the ledger.
   */
  public static String render(List<Skill> skills, List<RunLedgerRow> runs) {
    StringBuilder out = new StringBuilder(HEADER);
    out.append('\n');
    out.append("| Skill | Claude | Codex | Gemini |\n");
    out.append("|---|---|---|---|\n");
    for (Skill skill : skills) {
      out.append("| `").append(skill.canonicalName()).append("` | ");
      for (int i = 0; i < COLUMNS.size(); i++) {
        out.append(cellFor(runs, skill.canonicalName(), COLUMNS.get(i)));
        out.append(i == COLUMNS.size() - 1 ? " |\n" : " | ");
      }
    }
    return out.toString();
  }
}
