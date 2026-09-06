/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import java.util.List;

/**
 * One declarative object-graph shape from {@code graphs.json}.
 *
 * <p>Exactly one of {@code layers} and {@code kind} drives construction: {@code layers} stacks
 * wrappers outward around the payload, {@code kind} names a shape a stack cannot express. See the
 * corpus README for the table.
 *
 * @param id stable kebab-case identifier
 * @param description what breaks
 * @param kind shape name, or {@code null} when {@code layers} applies
 * @param layers wrapper kinds, innermost first
 * @param layer the wrapper repeated by {@code repeatLayer}
 * @param container the container used by {@code width} and {@code selfInCollection}
 * @param member the misbehaving member used by {@code hostileMember}
 * @param state the {@code future}/{@code throwable} variant
 * @param payload {@code "secret-record"} when the builder must plant a sentinel-bearing record
 * @param n the shape's size — depth, width, ring length or field count
 */
public record GraphCase(
    String id,
    String description,
    String kind,
    List<String> layers,
    String layer,
    String container,
    String member,
    String state,
    String payload,
    int n) {

  /** Whether this shape carries a {@code @NotTraced} sentinel the redaction oracle can look for. */
  public boolean carriesSecret() {
    return "secret-record".equals(payload);
  }

  @Override
  public String toString() {
    return id;
  }
}
