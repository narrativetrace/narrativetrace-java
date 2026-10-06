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

/**
 * The sporadic-lanes precondition gate composed as one decision (owner-ruled 2026-09-13, rules 3
 * and 5): "Claude is exempt from all seven rules"; Codex/Gemini refuse to start a trial unless
 * {@code narrativetrace-skills}' Tier A lints and Tier A2 replay are green at HEAD AND the weekly
 * allowance is not spent.
 *
 * @llmNote fully testable without a real {@code ./gradlew} invocation or a real ledger file: the
 *     tier check's {@link TierPrecondition.RunCommand} is injected, and the quota check reads an
 *     already-parsed {@link QuotaLedger} rather than the filesystem — a test drives every branch
 *     (Claude exemption, tier-red refusal, allowance-spent refusal, allowed).
 */
public final class SporadicPolicy {

  private SporadicPolicy() {}

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
