/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.api.tree;

import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.event.TraceNode;
import java.util.List;

/**
 * Immutable trace tree returned by capture APIs.
 *
 * <p>INTENT: Renderers, exporters, and tests should depend on this interface rather than on the
 * capture internals or raw event lists. It is the hand-off point between the runtime that captures
 * and everything that reads.
 *
 * <p>A tree contains zero or more root {@link TraceNode}s, each representing a top-level invocation
 * and its nested call structure.
 *
 * @see TraceNode
 */
public interface TraceTree {

  /**
   * Returns the root-level trace nodes.
   *
   * @return Immutable root list. Empty means nothing in the current scope was traced.
   */
  List<TraceNode> roots();

  /**
   * Returns whether this tree contains any traced nodes.
   *
   * @return {@code true} when {@link #roots()} is empty.
   */
  boolean isEmpty();

  /**
   * The trace this tree belongs to, as the capturing context assigned it.
   *
   * <p>INTENT: One tree is one trace, and the trace id is a property of the <em>trace</em>, not of
   * any node in it. A capture whose nodes all kept their {@link
   * ai.narrativetrace.api.event.SpanContext} carries the same id there too; this accessor is what
   * lets a tree whose nodes never had one — assembled by hand, replayed from an artifact, or
   * produced by a static scan — still be exported as one trace instead of several.
   *
   * <p><b>@llmNote</b> Exporters must not read this in preference to a node's own span context: the
   * resolution order is inherit-from-context first, then this, then generate (ADR-014). The default
   * is {@code null} so hand-built trees stay valid; an exporter facing {@code null} is the only
   * case allowed to generate.
   *
   * @return the assigned trace id, or {@code null} when this tree was built without a context
   */
  default TraceId traceId() {
    return null;
  }

  /**
   * What this capture is missing, and why — {@link TraceLoss#none()} unless something was lost.
   *
   * <p>INTENT: A short narrative must never be indistinguishable from a quiet one. The loss travels
   * with the tree so that every renderer can say so in its own output, instead of the fact living
   * only in a console summary the reader of a trace file never sees.
   *
   * <p><b>@llmNote</b> This is the capturing context's reading <em>at capture time</em>: {@code
   * droppedEvents} is process-wide since start, the refusal counts are the capturing thread's. On a
   * long-lived shared context that is a high-water mark, not a per-capture figure — a drop an hour
   * ago still reports here. That is deliberate: the advice the footer gives (raise the buffer)
   * stays correct either way, and under-reporting an incomplete trace is the worse error. Bracket
   * with {@link TraceLoss#since(TraceLoss)} when you need it attributed exactly.
   *
   * <p>The default is {@link TraceLoss#none()} so hand-built trees — tests, replay tools, static
   * scans — stay valid without knowing about loss at all.
   *
   * @return the loss this capture carries; never {@code null}
   */
  default TraceLoss loss() {
    return TraceLoss.none();
  }

  /**
   * Wall-clock span of the whole scenario: first entry to last exit, in nanoseconds.
   *
   * <p>INTENT: The single definition of "how long did this take", which {@code chapter.schema.json}
   * has always declared and which three exporters each used to re-derive as "the first root's
   * duration" — wrong the moment a scenario has more than one root, and item 29 made that the
   * ordinary case by putting adopted async work in the tree.
   *
   * <p>Summing is wrong at every level: a span's duration already contains its children's, so
   * adding siblings double-counts against their parent and adding roots repeats that one level up.
   * Overlapping concurrent work is therefore counted once, and a gap between two roots counts,
   * because both are what a wall clock would show.
   *
   * <p><b>@llmNote</b> Computed over <em>all</em> nodes, not roots alone: an adopted async child
   * can outlive the root that launched it. A captured tree always carries start times; a hand-built
   * one (the short {@link ai.narrativetrace.api.event.TraceNode} constructors, used by tests and
   * replay tools) may not, and a span cannot be derived from durations alone. Untimed nodes are
   * therefore skipped, and a tree with no timed node at all falls back to its longest root — the
   * tightest lower bound that data supports.
   *
   * <p><b>@edgeCase</b> {@code TraceNode.children()} is an undefended list, so a hand-built or
   * replayed tree is not guaranteed acyclic. The work queue below is not stack-recursive — a deep
   * chain can never overflow it — but without a visited guard a cyclic child list would keep
   * re-enqueueing the same nodes forever, growing {@code pending} without bound instead of merely
   * crashing. {@code visited} is identity-based (an {@link java.util.IdentityHashMap}-backed set,
   * not {@code equals}-based) for the same reason {@code TreeWalk} in {@code narrativetrace-core}
   * is: two distinct nodes that happen to be {@code equals} (a record with the same signature and
   * children) must not be mistaken for the same node revisited, and a node reached twice through a
   * genuine diamond (shared, not cyclic) is skipped the second time — safe, because {@code
   * Math.min}/{@code Math.max} are idempotent on a value already folded in.
   *
   * @return the span in nanoseconds, or {@code 0} when nothing was traced
   */
  default long durationNanos() {
    long earliest = Long.MAX_VALUE;
    long latest = Long.MIN_VALUE;
    var visited =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<TraceNode, Boolean>());
    var pending = new java.util.ArrayDeque<TraceNode>();
    for (var root : roots()) {
      if (visited.add(root)) {
        pending.add(root);
      }
    }
    while (!pending.isEmpty()) {
      var node = pending.poll();
      for (var child : node.children()) {
        if (visited.add(child)) {
          pending.add(child);
        }
      }
      if (node.startTimeNanos() == 0L) {
        continue;
      }
      earliest = Math.min(earliest, node.startTimeNanos());
      latest = Math.max(latest, node.startTimeNanos() + node.durationNanos());
    }
    if (earliest != Long.MAX_VALUE) {
      return latest - earliest;
    }
    return roots().stream().mapToLong(TraceNode::durationNanos).max().orElse(0L);
  }
}
