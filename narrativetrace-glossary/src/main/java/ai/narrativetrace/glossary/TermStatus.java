/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

/**
 * Curation lifecycle of a glossary term.
 *
 * <p>INTENT: Distinguishes machine-harvested entries awaiting review ({@link #HARVESTED}) from
 * human-reviewed vocabulary ({@link #CURATED}) and entries no longer observed in code ({@link
 * #STALE}). Harvesting may create HARVESTED entries but never changes CURATED ones; STALE is set
 * only by an explicit human-invoked operation, never automatically.
 */
public enum TermStatus {
  HARVESTED,
  CURATED,
  STALE;

  /** Returns the lowercase label used in {@code glossary.json} (e.g. {@code harvested}). */
  public String jsonName() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
