/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The participant lane-up of a sequence diagram: every class the trace touched, in first-appearance
 * order.
 *
 * <p>INTENT: Both sequence renderers declare their lanes before emitting a single arrow, and both
 * need exactly the same list in exactly the same order — the order is what makes the two diagrams
 * comparable, and what makes either of them stable across runs. It was a private twin in each
 * renderer until 2026-09-12.
 *
 * <p><b>@llmNote</b> The walk goes through {@link TreeWalk}, so a cyclic or pathologically deep
 * tree (both reachable: {@code TraceNode.children} is an undefended list and a tree can arrive
 * deserialized) is bounded here exactly as it is during rendering. A node the walk stops at still
 * contributes its lane — the {@code onLimit} callback adds the same class name — because the
 * renderers do emit an arrow for it.
 */
final class SequenceParticipants {

  private SequenceParticipants() {}

  /**
   * Collects the participant class names reachable from {@code nodes}, in first-appearance order.
   *
   * @param nodes the tree roots to walk
   * @return an insertion-ordered set of raw class names, unsanitized — quoting is the caller's step
   */
  static LinkedHashSet<String> collect(List<TraceNode> nodes) {
    var result = new LinkedHashSet<String>();
    for (var root : nodes) {
      TreeWalk.walk(
          root,
          TraceNode::children,
          (n, depth) -> result.add(n.signature().className()),
          (n, depth, reason) -> result.add(n.signature().className()));
    }
    return result;
  }
}
