/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.core.tree.TreeWalk;
import java.util.List;

/**
 * The carry buffer discipline shared by every tree renderer's sibling planner.
 *
 * <p>INTENT: A segment's own text (a fork header, a fire-and-forget header, a fold line) cannot
 * always print the moment the segment is planned: {@link TreeWalk}'s {@code childrenOf} plans
 * <em>every</em> segment of a sibling list in one call, before any of them is walked, while the
 * rendering order is visitation order — text belonging to a segment that follows a plain sibling
 * must appear only after that sibling's whole subtree is rendered. A {@code carry} StringBuilder
 * holds such text until a walked node exists to hang it on; these two operations are that handover,
 * and they were four identical copies before.
 *
 * <p><b>@llmNote</b> Ordering is the whole point — change nothing here without re-reading the four
 * renderers' {@code planChildren}/{@code planSiblings} methods. {@link #flush} hands the carry to
 * the next node as its leading text and empties it; {@link #flushToLast} disposes of whatever is
 * left once the list is fully planned, appending it to the last planned node's trailing text or, if
 * the list produced no walked node at all, straight to the output buffer.
 */
final class SiblingCarry {

  private SiblingCarry() {}

  /** Consumes and returns {@code carry}'s text, or {@code null} when there is none to attach. */
  static String flush(StringBuilder carry) {
    if (carry.isEmpty()) {
      return null;
    }
    var text = carry.toString();
    carry.setLength(0);
    return text;
  }

  /**
   * Attaches whatever text is still carried to the last planned sibling, or to {@code sb} when
   * nothing was planned.
   *
   * <p><b>@sideEffects</b> Mutates the last entry's {@code trailing} text, or appends to {@code sb}
   * when the list is empty.
   *
   * @param planned the sibling list just planned, in rendering order
   * @param carry text with no node of its own left to lead
   * @param sb the output buffer, used only when the sibling list planned no walked node at all
   */
  static void flushToLast(
      List<? extends PlannedSibling> planned, StringBuilder carry, StringBuilder sb) {
    if (carry.isEmpty()) {
      return;
    }
    if (planned.isEmpty()) {
      // Nothing walked in this sibling list at all (every segment was an empty fire-and-forget or a
      // fork) — nothing to attach to, and nothing else pending to interleave before it either.
      sb.append(carry);
      return;
    }
    var last = planned.get(planned.size() - 1);
    last.trailing = (last.trailing == null ? "" : last.trailing) + carry;
  }
}
