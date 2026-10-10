/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

/**
 * Hands out the {@link SpanId}s of one sibling list, in the order a renderer plans it.
 *
 * <p>INTENT: Every tree renderer here plans a sibling list segment by segment — a plain call, a
 * fork's members in {@link SpanId#CONCURRENT_ORDER}, a fire-and-forget launcher, a folded run of
 * iterations — and that planning order IS the id order. Taking the next id at the moment a span is
 * planned keeps the numbering in one place instead of an offset threaded through every planner.
 */
final class SpanCursor {

  private final String parent;
  private int position;

  /**
   * @param parent the id of the node whose children are being planned; {@code null} for roots
   */
  SpanCursor(String parent) {
    this.parent = parent;
  }

  /** The id of the next sibling in planning order. */
  String next() {
    position++;
    return SpanId.child(parent, position);
  }
}
