/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.tree;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Bounded, cycle-safe pre-order walk over a recursive node structure — {@code TraceNode} first, but
 * generic over any type that exposes its own children.
 *
 * <p>INTENT: every renderer or exporter that descends a {@code TraceTree} used to write its own
 * {@code for (var child : node.children())} recursion. A captured trace is not guaranteed to be
 * shallow — a real recursive business method produces a deep one — and {@code TraceNode} is a
 * public record with an undefended {@code List<TraceNode> children} field, so a hand-built,
 * replayed, or deserialized tree is not guaranteed to be a tree at all: nothing stops a child list
 * from containing an ancestor. This is the one walker every core/clarity/diagrams renderer shares,
 * so a bound fixed once here does not need re-discovering per renderer.
 *
 * <p><b>@llmNote</b> Cycle detection is <em>identity</em>-based ({@link IdentityHashMap}-backed),
 * not {@code equals}-based. {@code TraceNode} is a record, so two <em>different</em> nodes that
 * happen to carry the same signature and the same rendered children (two consecutive identical
 * calls, or two independently-generated test fixtures) are {@code equals} to each other; a plain
 * {@code HashSet} ancestor guard would misreport that as a cycle. The ancestor set holds only the
 * nodes on the <em>current path</em> — added before descending into a node's children, removed once
 * they are exhausted — so a node reached twice via two different, non-overlapping branches (a
 * shared subtree; a diamond, not a cycle) is not flagged: it has already left the ancestor set by
 * the time the second branch reaches it.
 *
 * <p><b>@llmNote</b> The walk is implemented with an explicit, heap-allocated {@link Deque} of
 * frames, not Java recursion. {@link #MAX_DEPTH} is deliberately generous (10 000, so a legitimate
 * deep call tree is never mistaken for the hostile case), and a bound implemented as recursive
 * method calls would itself risk {@code StackOverflowError} well before reaching it — trading one
 * stack overflow for another would not be a fix, so the walk itself cannot recurse.
 *
 * <p>A node beyond {@link #MAX_DEPTH}, or a node that is its own ancestor, is never handed to a
 * visitor — {@code onLimit} is called for it instead, once, with the {@link Reason}. The walk does
 * not descend past that node either way. No tree, however deep or however cyclic, makes this method
 * throw.
 *
 * @see Reason#marker() the exact text every renderer appends for a truncated walk
 */
public final class TreeWalk {

  /**
   * How many levels of a node the walk follows before it stops and says so.
   *
   * <p>Generous by design: a hand-written or generated call tree this deep is not a realistic
   * legitimate trace (stack-depth-bound business recursion bottoms out far lower in practice),
   * while staying far below what the heap-allocated frame stack here can hold safely. Exceeding it
   * is therefore reported the same way a cycle is — a marker, never a crash.
   */
  public static final int MAX_DEPTH = 10_000;

  private TreeWalk() {}

  /** Why the walk stopped descending at a node instead of visiting it. */
  public enum Reason {
    /** The node is more than {@link #MAX_DEPTH} levels below the root. */
    DEPTH_LIMIT("… (depth limit)"),
    /** The node is already an ancestor of itself on the current path. */
    CYCLE("… (cycle)");

    private final String marker;

    Reason(String marker) {
      this.marker = marker;
    }

    /** The exact text every renderer appends in place of the node the walk did not visit. */
    public String marker() {
      return marker;
    }
  }

  /** Called once, in pre-order, for every node the walk actually visits. */
  @FunctionalInterface
  public interface NodeVisitor<T> {
    void visit(T node, int depth);
  }

  /** Called once for every node the walk stopped at instead of visiting. */
  @FunctionalInterface
  public interface LimitVisitor<T> {
    void limitReached(T node, int depth, Reason reason);
  }

  /**
   * Walks {@code root} and its descendants in pre-order, depth-first, bounded to {@link #MAX_DEPTH}
   * and safe against reference cycles.
   *
   * @param root the node to start from
   * @param childrenOf how to read a node's children; called once per visited node
   * @param visitor invoked for every node actually visited, root first, in the order a plain
   *     recursive walk would visit them
   * @param onLimit invoked instead of {@code visitor}, at most once per node, for a node beyond
   *     {@link #MAX_DEPTH} or already on the current path
   */
  public static <T> void walk(
      T root, Function<T, List<T>> childrenOf, NodeVisitor<T> visitor, LimitVisitor<T> onLimit) {
    walk(root, childrenOf, visitor, noExit(), onLimit);
  }

  /**
   * The same walk as {@link #walk(Object, Function, NodeVisitor, LimitVisitor)}, with a second
   * callback invoked in post-order — once a node's children (or the limit that stopped them) have
   * all been handled — for a renderer whose output needs a closing step per node (a matching "exit"
   * event, a closing bracket) as well as an opening one.
   *
   * @param onExit invoked once per node actually visited, after every child (or limit) under it has
   *     been processed, in the order a plain recursive walk would return from it
   */
  public static <T> void walk(
      T root,
      Function<T, List<T>> childrenOf,
      NodeVisitor<T> onEnter,
      NodeVisitor<T> onExit,
      LimitVisitor<T> onLimit) {
    Set<T> onPath = Collections.newSetFromMap(new IdentityHashMap<>());
    Deque<Frame<T>> stack = new ArrayDeque<>();
    enter(root, 0, childrenOf, onEnter, onPath, stack);

    while (!stack.isEmpty()) {
      var frame = stack.peek();
      if (!frame.children.hasNext()) {
        onPath.remove(frame.node);
        onExit.visit(frame.node, frame.depth);
        stack.pop();
        continue;
      }
      var child = frame.children.next();
      int childDepth = frame.depth + 1;
      if (onPath.contains(child)) {
        onLimit.limitReached(child, childDepth, Reason.CYCLE);
      } else if (childDepth > MAX_DEPTH) {
        onLimit.limitReached(child, childDepth, Reason.DEPTH_LIMIT);
      } else {
        enter(child, childDepth, childrenOf, onEnter, onPath, stack);
      }
    }
  }

  private static <T> NodeVisitor<T> noExit() {
    return (node, depth) -> {};
  }

  private static <T> void enter(
      T node,
      int depth,
      Function<T, List<T>> childrenOf,
      NodeVisitor<T> visitor,
      Set<T> onPath,
      Deque<Frame<T>> stack) {
    onPath.add(node);
    visitor.visit(node, depth);
    stack.push(new Frame<>(node, depth, childrenOf.apply(node).iterator()));
  }

  private record Frame<T>(T node, int depth, Iterator<T> children) {}
}
