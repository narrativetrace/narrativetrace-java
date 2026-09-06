/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

/**
 * One declarative {@code TraceNode} call-tree shape from {@code trace-shapes.json}.
 *
 * @param id stable kebab-case identifier
 * @param description what breaks
 * @param kind {@code "chain"} (a linear chain {@code n} nodes deep) or {@code "cycle"} (a ring of
 *     {@code n} nodes; {@code n: 1} is a self-holding node)
 * @param n the chain's depth or the ring's length
 */
public record TraceShapeCase(String id, String description, String kind, int n) {

  @Override
  public String toString() {
    return id;
  }
}
