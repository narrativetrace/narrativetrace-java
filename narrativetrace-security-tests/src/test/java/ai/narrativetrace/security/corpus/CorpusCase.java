/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

/**
 * One hostile scalar from {@code strings.json} or {@code injection.json}.
 *
 * @param id stable kebab-case identifier, quoted by a failing assertion so the case is findable
 * @param description what breaks, not what the bytes are
 * @param value the materialized value, {@code repeat} already expanded
 */
public record CorpusCase(String id, String description, String value) {

  @Override
  public String toString() {
    return id;
  }
}
