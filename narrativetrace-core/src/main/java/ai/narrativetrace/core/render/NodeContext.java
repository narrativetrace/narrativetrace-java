/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Per-node rendering context for a {@link ai.narrativetrace.core.tree.TreeWalk}-driven renderer
 * whose own recursive structure carries more than {@code (node, depth)} — a line prefix, an indent
 * level, a span id chain entry — computed once, by the code that lists a node's children, and
 * consumed later by the code that visits it.
 *
 * <p>INTENT: A plain {@code IdentityHashMap<K, V>} works for that hand-off <em>until</em> a genuine
 * cycle puts two occurrences of the same node identity in flight at once: the outer occurrence is
 * still on the ancestor path, not yet exited, when the walk discovers the cycle-closing occurrence
 * as one of its own descendants. Overwriting a single map slot loses the outer occurrence's context
 * before it is ever read. Each key therefore keeps a LIFO stack of pending values — {@link #push}
 * called once per occurrence in traversal order, {@link #pop} once per occurrence, in the reverse
 * order the walk finishes with them — which is exactly the nesting a cycle produces: the
 * cycle-closing occurrence's context is pushed and popped entirely between the outer occurrence's
 * own push and pop, so the outer context is still there, undisturbed, underneath.
 *
 * @param <K> the node type; compared by identity, not {@code equals} — see {@link
 *     ai.narrativetrace.core.tree.TreeWalk}'s own cycle detection for why: two distinct nodes that
 *     happen to be {@code equals} must not share a slot.
 * @param <V> the context carried per occurrence
 */
final class NodeContext<K, V> {

  private final Map<K, Deque<V>> byKey = new IdentityHashMap<>();

  /** Records one occurrence's context, to be consumed later by {@link #peek} and {@link #pop}. */
  void push(K key, V value) {
    byKey.computeIfAbsent(key, k -> new ArrayDeque<>()).addFirst(value);
  }

  /**
   * The most recently pushed, not-yet-popped context for {@code key} — read without consuming it.
   */
  V peek(K key) {
    return byKey.get(key).peekFirst();
  }

  /** Consumes and returns the most recently pushed, not-yet-popped context for {@code key}. */
  V pop(K key) {
    var deque = byKey.get(key);
    var value = deque.pollFirst();
    if (deque.isEmpty()) {
      byKey.remove(key);
    }
    return value;
  }
}
