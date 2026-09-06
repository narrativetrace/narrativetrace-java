/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.List;

/**
 * Everything one harvest pass observed, in deterministic order.
 *
 * <p>INTENT: A pure value handed to {@link GlossaryMerger}; contains observations only — no merge
 * decisions. Candidates are sorted by {@code (context, phrase, kind, site)} so downstream output is
 * reproducible run-over-run.
 *
 * @param candidates aggregated observations; never {@code null}
 */
public record HarvestResult(List<HarvestCandidate> candidates) {

  public HarvestResult {
    if (candidates == null) {
      throw new IllegalArgumentException("candidates must not be null");
    }
    candidates = List.copyOf(candidates);
  }
}
