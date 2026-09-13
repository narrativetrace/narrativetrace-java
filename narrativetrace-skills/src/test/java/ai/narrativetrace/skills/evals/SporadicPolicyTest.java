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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SporadicPolicyTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

  private static final QuotaLedger PLENTY =
      new QuotaLedger(List.of(new QuotaAllowance(Platform.CODEX, "basic", 4)), List.of());

  private static final QuotaLedger SPENT =
      new QuotaLedger(
          List.of(new QuotaAllowance(Platform.CODEX, "basic", 1)),
          List.of(new QuotaSpendRow("2026-09-13", Platform.CODEX, "s", "c", "2026-W37")));

  @Test
  void claudeIsExemptFromBothTheTierCheckAndTheQuotaCheck() {
    AtomicInteger tierCalls = new AtomicInteger();

    // Even a spent ledger (which would refuse Codex/Gemini) must not matter for Claude.
    assertThatCode(
            () ->
                SporadicPolicy.beforeTrial(
                    Platform.CLAUDE,
                    Path.of("."),
                    (cmd, cwd) -> tierCalls.incrementAndGet(),
                    SPENT,
                    TODAY))
        .doesNotThrowAnyException();
    assertThat(tierCalls).hasValue(0);
  }

  @Test
  void codexRefusesWhenTierAAndTierA2AreRed() {
    assertThatThrownBy(
            () ->
                SporadicPolicy.beforeTrial(
                    Platform.CODEX,
                    Path.of("."),
                    (cmd, cwd) -> {
                      throw new IOException("red");
                    },
                    PLENTY,
                    TODAY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Tier A lints / Tier A2 replay are not green");
  }

  @Test
  void codexRefusesOnceTheWeeklyAllowanceIsSpentEvenWhenTiersAreGreen() {
    assertThatThrownBy(
            () ->
                SporadicPolicy.beforeTrial(
                    Platform.CODEX, Path.of("."), (cmd, cwd) -> {}, SPENT, TODAY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no override");
  }

  @Test
  void codexIsAllowedWhenTiersAreGreenAndAllowanceRemains() {
    assertThatCode(
            () ->
                SporadicPolicy.beforeTrial(
                    Platform.CODEX, Path.of("."), (cmd, cwd) -> {}, PLENTY, TODAY))
        .doesNotThrowAnyException();
  }

  @Test
  void geminiFollowsTheSameGateAsCodex() {
    QuotaLedger noAllowanceRow = new QuotaLedger(List.of(), List.of());

    assertThatThrownBy(
            () ->
                SporadicPolicy.beforeTrial(
                    Platform.GEMINI, Path.of("."), (cmd, cwd) -> {}, noAllowanceRow, TODAY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no allowance row");
  }

  @Test
  void theTierCheckRunsBeforeTheQuotaCheckSoARedTierNeverSpendsQuota() {
    // A tier-red refusal must fire even against a ledger that would otherwise allow the run —
    // pins the order, not just the two outcomes in isolation.
    assertThatThrownBy(
            () ->
                SporadicPolicy.beforeTrial(
                    Platform.CODEX,
                    Path.of("."),
                    (cmd, cwd) -> {
                      throw new IOException("red");
                    },
                    PLENTY,
                    TODAY))
        .hasMessageContaining("Tier A lints / Tier A2 replay are not green");
  }
}
