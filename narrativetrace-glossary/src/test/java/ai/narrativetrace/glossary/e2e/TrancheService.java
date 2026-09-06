/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary.e2e;

/**
 * A class whose verb and noun live only in a project glossary, never in the built-in dictionaries —
 * the subject of {@link GlossaryAwareClarityScanTest}.
 */
public final class TrancheService {

  public String foldTranche(String tranche) {
    return tranche;
  }
}
