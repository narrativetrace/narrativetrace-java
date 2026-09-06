/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

/**
 * One wire-header value from {@code headers.json}.
 *
 * @param id stable kebab-case identifier
 * @param description what the case is testing
 * @param value the materialized header value
 * @param accepted whether a conforming parser must accept it; every other case must be refused
 *     without throwing
 */
public record HeaderCase(String id, String description, String value, boolean accepted) {

  @Override
  public String toString() {
    return id;
  }
}
