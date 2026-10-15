/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
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

    // Even a spent ledger (which would refuse Codex/Antigravity) must not matter for Claude.
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
  void antigravityFollowsTheSameGateAsCodex() {
    QuotaLedger noAllowanceRow = new QuotaLedger(List.of(), List.of());

    assertThatThrownBy(
            () ->
                SporadicPolicy.beforeTrial(
                    Platform.ANTIGRAVITY, Path.of("."), (cmd, cwd) -> {}, noAllowanceRow, TODAY))
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

  // --- the skip line
  // ------------------------------------------------------------------------------

  private static final QuotaLedger NOT_INSTALLED =
      new QuotaLedger(List.of(new QuotaAllowance(Platform.CURSOR, "not installed", 0)), List.of());

  @Test
  void aLaneWithAWeeklyAllowanceOfZeroSkipsWithOneLineNamingItsTool() {
    var reason = SporadicPolicy.skipReason(Platform.CURSOR, NOT_INSTALLED);

    assertThat(reason).isPresent();
    assertThat(reason.get())
        .startsWith("SKIP")
        .contains("cursor")
        .contains("`agent`")
        .contains("ledger/quota.md")
        .doesNotContain("\n");
  }

  @Test
  void aLaneWithAnAllowanceDoesNotSkipAndNeitherDoesTheExemptLane() {
    assertThat(SporadicPolicy.skipReason(Platform.CODEX, PLENTY)).isEmpty();
    assertThat(SporadicPolicy.skipReason(Platform.CLAUDE, NOT_INSTALLED)).isEmpty();
  }

  /** A missing row is a ledger the owner has not finished, which is a refusal, never a skip. */
  @Test
  void aLaneWithNoAllowanceRowAtAllDoesNotSkipSoTheRefusalStillSpeaks() {
    assertThat(SporadicPolicy.skipReason(Platform.CURSOR, PLENTY)).isEmpty();
  }

  @Test
  void aSpentAllowanceIsARefusalNotASkip() {
    assertThat(SporadicPolicy.skipReason(Platform.CODEX, SPENT)).isEmpty();
  }
}
