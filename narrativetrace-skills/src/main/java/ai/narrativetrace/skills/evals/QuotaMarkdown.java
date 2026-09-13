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

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The sporadic-lanes quota guard (owner-ruled 2026-09-13, rule 5): "A weekly allowance per
 * platform, in a ledger the runner reads ... The runner refuses a platform whose allowance is spent
 * and says so; there is no override flag — the owner edits the ledger." Reads/writes {@code
 * ledger/quota.md} (plan tier + weekly allowance table, plus a spend log the runner appends to).
 * Mirrors the TypeScript reference's {@code evals/quota.ts}.
 */
public final class QuotaMarkdown {

  private static final Pattern PIPE_ROW = Pattern.compile("^\\|.*\\|$");
  private static final Pattern SEPARATOR_ROW = Pattern.compile("^:?-+:?$");

  private QuotaMarkdown() {}

  /**
   * ISO 8601 week, e.g. {@code 2026-W37} — Monday-start, week 1 contains the year's first Thursday.
   */
  public static String isoWeek(LocalDate date) {
    long week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    int weekBasedYear = date.get(IsoFields.WEEK_BASED_YEAR);
    return weekBasedYear + "-W" + String.format(Locale.ROOT, "%02d", week);
  }

  public static QuotaLedger parse(String content) {
    List<QuotaAllowance> allowances = new ArrayList<>();
    for (List<String> cells : pipeRows(sectionBody(content, "## Allowance"))) {
      if (cells.size() < 3) {
        continue;
      }
      Platform platform = Platform.parse(cells.get(0)).orElse(null);
      if (platform == null) {
        continue;
      }
      allowances.add(new QuotaAllowance(platform, cells.get(1), parseIntOrZero(cells.get(2))));
    }
    List<QuotaSpendRow> spend = new ArrayList<>();
    for (List<String> cells : pipeRows(sectionBody(content, "## Spend log"))) {
      if (cells.size() < 5) {
        continue;
      }
      Platform platform = Platform.parse(cells.get(1)).orElse(null);
      if (platform == null) {
        continue;
      }
      spend.add(
          new QuotaSpendRow(cells.get(0), platform, cells.get(2), cells.get(3), cells.get(4)));
    }
    return new QuotaLedger(allowances, spend);
  }

  public static long spendCountThisWeek(QuotaLedger ledger, Platform platform, String week) {
    return ledger.spend().stream()
        .filter(row -> row.platform() == platform && row.week().equals(week))
        .count();
  }

  /**
   * No override flag by design (rule 5) — a spent allowance is fixed by editing {@code
   * ledger/quota.md}.
   */
  public static QuotaDecision checkQuota(QuotaLedger ledger, Platform platform, LocalDate now) {
    QuotaAllowance allowance =
        ledger.allowances().stream().filter(a -> a.platform() == platform).findFirst().orElse(null);
    if (allowance == null) {
      return QuotaDecision.refused(
          "ledger/quota.md carries no allowance row for \"" + platform + "\"");
    }
    String week = isoWeek(now);
    long spent = spendCountThisWeek(ledger, platform, week);
    if (spent >= allowance.weeklyAllowance()) {
      return QuotaDecision.refused(
          platform
              + "'s weekly allowance ("
              + allowance.weeklyAllowance()
              + ") is spent for "
              + week
              + " ("
              + spent
              + " run(s) already) — no override; the owner edits ledger/quota.md");
    }
    return QuotaDecision.allow();
  }

  /** The exact pipe-table row appended to the Spend log for one sporadic-lane trial. */
  public static String appendedSpendRowLine(QuotaSpendRow row) {
    return row.toMarkdownRow() + "\n";
  }

  private static int parseIntOrZero(String value) {
    try {
      return Integer.parseInt(value.strip());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String sectionBody(String content, String heading) {
    int start = content.indexOf(heading);
    if (start < 0) {
      return "";
    }
    String rest = content.substring(start + heading.length());
    int next = rest.indexOf("\n## ");
    return next < 0 ? rest : rest.substring(0, next);
  }

  /** Every pipe-table row in {@code section}, header and separator rows dropped. */
  private static List<List<String>> pipeRows(String section) {
    List<List<String>> rows = new ArrayList<>();
    boolean headerSkipped = false;
    for (String rawLine : section.split("\n", -1)) {
      String line = rawLine.strip();
      if (!PIPE_ROW.matcher(line).matches()) {
        continue;
      }
      List<String> cells =
          List.of(line.substring(1, line.length() - 1).split("\\|", -1)).stream()
              .map(String::strip)
              .toList();
      if (cells.stream().allMatch(cell -> SEPARATOR_ROW.matcher(cell).matches())) {
        continue;
      }
      if (!headerSkipped) {
        headerSkipped = true;
        continue;
      }
      rows.add(cells);
    }
    return rows;
  }
}
