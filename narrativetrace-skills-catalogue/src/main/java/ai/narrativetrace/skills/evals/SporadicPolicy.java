/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * The sporadic-lanes precondition gate composed as one decision (owner-ruled 2026-09-13, rules 3
 * and 5): "Claude is exempt from all seven rules"; Codex, Antigravity and Cursor refuse to start a
 * trial unless {@code narrativetrace-skills}' Tier A lints and Tier A2 replay are green at HEAD AND
 * the weekly allowance is not spent.
 *
 * @llmNote fully testable without a real {@code ./gradlew} invocation or a real ledger file: the
 *     tier check's {@link TierPrecondition.RunCommand} is injected, and the quota check reads an
 *     already-parsed {@link QuotaLedger} rather than the filesystem — a test drives every branch
 *     (Claude exemption, tier-red refusal, allowance-spent refusal, allowed).
 */
public final class SporadicPolicy {

  private SporadicPolicy() {}

  /**
   * The one line a lane prints INSTEAD of running when its weekly allowance is zero, or empty when
   * the lane should go on to {@link #beforeTrial}.
   *
   * <p><b>@llmNote</b> A zero allowance row is the owner's own "the CLI is not installed or signed
   * in yet" (the ledger convention for Antigravity and Cursor), so the lane skips and the run exits
   * green — it never fails a build over a tool nobody has installed. A MISSING row, a spent
   * allowance and a red tier are not that state: they stay refusals.
   */
  public static Optional<String> skipReason(Platform platform, QuotaLedger ledger) {
    if (!platform.isSporadic()) {
      return Optional.empty();
    }
    boolean notInstalled =
        ledger.allowances().stream()
            .anyMatch(a -> a.platform() == platform && a.weeklyAllowance() == 0);
    return notInstalled
        ? Optional.of(
            "SKIP: the "
                + platform.name().toLowerCase(Locale.ROOT)
                + " lane did not run — its `"
                + platform.cliTool()
                + "` CLI is not installed or signed in (weekly allowance 0 in ledger/quota.md);"
                + " the owner raises the allowance once it is")
        : Optional.empty();
  }

  /**
   * Throws when {@code platform} is sporadic and either precondition fails; does nothing for a
   * non-sporadic platform (Claude today), which is exempt from both checks.
   *
   * @throws IllegalStateException carrying the tier-red or quota-refused reason
   */
  public static void beforeTrial(
      Platform platform,
      Path repoRoot,
      TierPrecondition.RunCommand runCommand,
      QuotaLedger ledger,
      LocalDate today) {
    if (!platform.isSporadic()) {
      return;
    }
    TierPrecondition.assertDeterministicTiersGreen(repoRoot, runCommand);
    QuotaDecision decision = QuotaMarkdown.checkQuota(ledger, platform, today);
    if (!decision.allowed()) {
      throw new IllegalStateException(decision.reason());
    }
  }
}
