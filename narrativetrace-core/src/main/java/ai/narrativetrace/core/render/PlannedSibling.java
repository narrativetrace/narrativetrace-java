/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.core.tree.TreeWalk;

/**
 * The shared shape of one planned sibling while a sibling list is being laid out by a tree
 * renderer.
 *
 * <p>INTENT: Every renderer in this package plans a whole sibling list in one call — {@link
 * TreeWalk}'s {@code childrenOf} runs before any of those children are walked — and each of them
 * needs the same three things per entry: the node, text to print immediately before it, and text to
 * print after its whole subtree. Each renderer adds its own layout state on top (an indent depth, a
 * box-drawing prefix, a list marker, folded siblings), so this is the common base rather than one
 * shared entry type.
 *
 * <p><b>@llmNote</b> {@code leading} and {@code trailing} are deliberately mutable and
 * package-private: {@link SiblingCarry} attaches deferred segment text to them after the list is
 * planned, and the renderer then copies them into its own immutable per-node context record. Not
 * final, by design — subclassed by each renderer's private {@code Planned}; nothing outside this
 * package can see it.
 */
class PlannedSibling {

  final TraceNode node;
  String leading;
  String trailing;

  PlannedSibling(TraceNode node, String leading) {
    this.node = node;
    this.leading = leading;
  }
}
