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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuotaMarkdownTest {

  @Test
  void isoWeekMatchesTheKnownCalendarMapping() {
    assertThat(QuotaMarkdown.isoWeek(LocalDate.of(2026, 9, 13))).isEqualTo("2026-W37");
    assertThat(QuotaMarkdown.isoWeek(LocalDate.of(2026, 1, 1))).isEqualTo("2026-W01");
    // A January date whose week belongs to the PREVIOUS week-based year (rolls back correctly).
    assertThat(QuotaMarkdown.isoWeek(LocalDate.of(2027, 1, 1))).isEqualTo("2026-W53");
  }

  private static final String LEDGER =
      """
      # Sporadic eval quota

      ## Allowance

      | platform | plan tier | weekly allowance |
      |---|---|---|
      | codex | basic | 4 |
      | gemini | not installed | 0 |

      ## Spend log

      | date | platform | skill | case | week |
      |---|---|---|---|---|
      | 2026-09-13 | codex | narrativetrace-doctor | happy-path | 2026-W37 |
      """;

  @Test
  void parseReadsBothTables() {
    QuotaLedger ledger = QuotaMarkdown.parse(LEDGER);
    assertThat(ledger.allowances())
        .containsExactly(
            new QuotaAllowance(Platform.CODEX, "basic", 4),
            new QuotaAllowance(Platform.GEMINI, "not installed", 0));
    assertThat(ledger.spend())
        .containsExactly(
            new QuotaSpendRow(
                "2026-09-13", Platform.CODEX, "narrativetrace-doctor", "happy-path", "2026-W37"));
  }

  @Test
  void parseIgnoresAMissingSectionOrAnUnknownPlatformRow() {
    QuotaLedger ledger = QuotaMarkdown.parse("# empty\n\nno sections here\n");
    assertThat(ledger.allowances()).isEmpty();
    assertThat(ledger.spend()).isEmpty();

    QuotaLedger withJunk =
        QuotaMarkdown.parse(
            """
            ## Allowance

            | platform | plan tier | weekly allowance |
            |---|---|---|
            | chatgpt | pro | 10 |

            ## Spend log

            | date | platform | skill | case | week |
            |---|---|---|---|---|
            | 2026-09-13 | chatgpt | s | c | 2026-W37 |
            """);
    assertThat(withJunk.allowances()).isEmpty();
    assertThat(withJunk.spend()).isEmpty();
  }

  @Test
  void parseTreatsANonNumericWeeklyAllowanceAsZero() {
    QuotaLedger ledger =
        QuotaMarkdown.parse(
            """
            ## Allowance

            | platform | plan tier | weekly allowance |
            |---|---|---|
            | gemini | not installed | n/a |
            """);
    assertThat(ledger.allowances())
        .containsExactly(new QuotaAllowance(Platform.GEMINI, "not installed", 0));
  }

  @Test
  void spendCountThisWeekCountsOnlyMatchingPlatformAndWeek() {
    QuotaLedger ledger = QuotaMarkdown.parse(LEDGER);
    assertThat(QuotaMarkdown.spendCountThisWeek(ledger, Platform.CODEX, "2026-W37")).isEqualTo(1);
    assertThat(QuotaMarkdown.spendCountThisWeek(ledger, Platform.CODEX, "2026-W38")).isZero();
    assertThat(QuotaMarkdown.spendCountThisWeek(ledger, Platform.GEMINI, "2026-W37")).isZero();
  }

  @Test
  void checkQuotaRefusesAPlatformWithNoAllowanceRow() {
    QuotaLedger ledger = new QuotaLedger(List.of(), List.of());
    QuotaDecision decision =
        QuotaMarkdown.checkQuota(ledger, Platform.CODEX, LocalDate.of(2026, 9, 13));
    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).contains("no allowance row");
  }

  @Test
  void checkQuotaAllowsUnderAllowanceAndRefusesAtAllowance() {
    QuotaLedger ledger = QuotaMarkdown.parse(LEDGER);
    LocalDate now = LocalDate.of(2026, 9, 13);

    assertThat(QuotaMarkdown.checkQuota(ledger, Platform.CODEX, now).allowed()).isTrue();
    assertThat(QuotaMarkdown.checkQuota(ledger, Platform.GEMINI, now).allowed()).isFalse();
    assertThat(QuotaMarkdown.checkQuota(ledger, Platform.GEMINI, now).reason())
        .contains("no override");
  }

  @Test
  void checkQuotaRefusesOnceSpendReachesTheWeeklyAllowance() {
    QuotaLedger fourSpent =
        new QuotaLedger(
            List.of(new QuotaAllowance(Platform.CODEX, "basic", 1)),
            List.of(
                new QuotaSpendRow("2026-09-13", Platform.CODEX, "s", "happy-path", "2026-W37")));
    QuotaDecision decision =
        QuotaMarkdown.checkQuota(fourSpent, Platform.CODEX, LocalDate.of(2026, 9, 13));
    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).contains("weekly allowance (1) is spent for 2026-W37");
  }

  @Test
  void appendedSpendRowLineIsOneNewlineTerminatedMarkdownRow() {
    QuotaSpendRow row =
        new QuotaSpendRow(
            "2026-09-13", Platform.CODEX, "narrativetrace-doctor", "happy-path", "2026-W37");
    assertThat(QuotaMarkdown.appendedSpendRowLine(row))
        .isEqualTo("| 2026-09-13 | codex | narrativetrace-doctor | happy-path | 2026-W37 |\n");
  }
}
