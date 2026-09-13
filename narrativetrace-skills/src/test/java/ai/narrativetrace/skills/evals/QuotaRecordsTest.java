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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuotaRecordsTest {

  @Test
  void quotaAllowanceRejectsANullPlatformOrBlankPlanTier() {
    assertThatThrownBy(() -> new QuotaAllowance(null, "basic", 4))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QuotaAllowance(Platform.CODEX, " ", 4))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void quotaSpendRowRejectsBlankFieldsAndRendersItsMarkdownRow() {
    assertThatThrownBy(() -> new QuotaSpendRow(" ", Platform.CODEX, "s", "c", "2026-W37"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QuotaSpendRow("2026-01-01", null, "s", "c", "2026-W37"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QuotaSpendRow("2026-01-01", Platform.CODEX, " ", "c", "2026-W37"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QuotaSpendRow("2026-01-01", Platform.CODEX, "s", " ", "2026-W37"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QuotaSpendRow("2026-01-01", Platform.CODEX, "s", "c", " "))
        .isInstanceOf(IllegalArgumentException.class);

    QuotaSpendRow row =
        new QuotaSpendRow(
            "2026-09-13", Platform.CODEX, "narrativetrace-doctor", "happy-path", "2026-W37");
    assertThat(row.toMarkdownRow())
        .isEqualTo("| 2026-09-13 | codex | narrativetrace-doctor | happy-path | 2026-W37 |");
  }

  @Test
  void quotaLedgerCopiesItsLists() {
    QuotaLedger ledger = new QuotaLedger(java.util.List.of(), java.util.List.of());
    assertThat(ledger.allowances()).isEmpty();
    assertThat(ledger.spend()).isEmpty();
  }

  @Test
  void quotaDecisionFactoriesEnforceTheirInvariant() {
    assertThat(QuotaDecision.allow().allowed()).isTrue();
    assertThat(QuotaDecision.allow().reason()).isNull();
    assertThat(QuotaDecision.refused("no allowance").allowed()).isFalse();
    assertThat(QuotaDecision.refused("no allowance").reason()).isEqualTo("no allowance");
    assertThatThrownBy(() -> QuotaDecision.refused(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
