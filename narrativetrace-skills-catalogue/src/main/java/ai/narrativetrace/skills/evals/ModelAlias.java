/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * The one table where a Claude model SHORTHAND ({@code haiku}) is turned into the full id ({@code
 * claude-haiku-5-5}) the runner both invokes and records (B-99) — read once, at argument-parsing
 * time ({@link EvalRunnerArgs#parse}), so the id the agent command names and the id {@code
 * ledger/runs.jsonl} records can never drift apart the way 44 rows already had (one alias, {@code
 * runs.jsonl} carrying whichever of "haiku" or its resolved id the operator happened to type).
 *
 * <p><b>@llmNote</b> An alias's target drifts without warning — this suite has already lived
 * through a morning where {@code haiku} stopped resolving to one model and started resolving to
 * another. Pinning the full id in the ledger is what makes a row a durable record of which model
 * actually ran; recording the alias instead would silently rewrite history every time the vendor
 * moved the alias's target. A platform/model pair this table has never seen run is refused rather
 * than guessed: adding the mapping once it is needed is the right failure mode, not inventing one
 * here.
 */
public final class ModelAlias {

  private ModelAlias() {}

  /** Every Claude shorthand this harness has actually run, and the one full id each resolves to. */
  private static final Map<String, String> CLAUDE_ALIASES = Map.of("haiku", "claude-haiku-5-5");

  /**
   * The id to both invoke and record for {@code model} on {@code platform}: unchanged for any
   * non-Claude platform or for a Claude model that already names a full id ({@code claude-}
   * prefixed), the table's mapping for a recognized Claude shorthand, and a refusal naming every
   * accepted alias and its id for anything else.
   *
   * @throws IllegalArgumentException when {@code platform} is Claude and {@code model} is neither a
   *     full id nor a recognized shorthand
   */
  public static String resolve(Platform platform, String model) {
    if (platform != Platform.CLAUDE || model.startsWith("claude-")) {
      return model;
    }
    String resolved = CLAUDE_ALIASES.get(model);
    if (resolved != null) {
      return resolved;
    }
    throw new IllegalArgumentException(
        "unknown Claude model alias \""
            + model
            + "\" — accepted: "
            + CLAUDE_ALIASES.entrySet().stream()
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining(", "))
            + ", or a full model id (starting \"claude-\")");
  }
}
