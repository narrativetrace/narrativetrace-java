/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Where {@link ValueRenderer} currently is inside one object graph: the ancestors on the path it is
 * walking, and how deep that path has gone.
 *
 * <p>INTENT: The two are one concern, because both answer "may I follow this reference?" and both
 * must be unwound on the way back out. Keeping them in one object is what lets every recursive
 * method in the renderer take a single parameter, and what makes the depth cap a change to one
 * funnel rather than to twenty-six signatures.
 *
 * <p><b>@llmNote</b> The ancestor set is identity-based and scoped to the current path — added on
 * the way down, removed in a {@code finally} on the way back — so it catches cycles without
 * mistaking a shared object reached twice by different paths for one.
 *
 * <p><b>@llmNote</b> The set is created on the first {@link #add}, not with the walk. An {@code
 * IdentityHashMap} and its wrapper cost ~352 bytes, and the walks that never reach a reference
 * worth guarding — every scalar, every value with a custom {@code toString()}, every record of
 * scalars — were paying it for a set nothing read (four of them per traced proxy call, ~1.4 kB/op).
 *
 * <p><b>@edgeCase</b> The cycle guard alone does not bound a walk. A ten-thousand-node
 * <em>chain</em> is not a cycle, and following it recursively was a {@code StackOverflowError}
 * raised inside instrumentation — an observability failure turned into an application failure,
 * which the pipeline contract forbids. {@link #MAX_DEPTH} is the fourth cap beside the existing
 * string, collection and field limits.
 */
final class RenderWalk {

  /**
   * How many levels of complex value the renderer will follow before it stops and says so.
   *
   * <p>Chosen against what the other caps already allow: with five collection items and five object
   * fields per level, a depth-32 walk can already visit more nodes than any narrative is readable
   * at. Real DTO graphs are single digits deep; the shapes that reach this number are cyclic-shaped
   * data the guard above cannot see (a linked list, a parent-child tree, a JSON document mapped to
   * nested maps), and for those a marker is a better answer than a stack overflow.
   */
  static final int MAX_DEPTH = 32;

  private Set<Object> ancestors;

  private int depth;

  /**
   * Records {@code value} as an ancestor of the current path.
   *
   * @return whether it was not already one — {@code false} means a cycle
   */
  boolean add(Object value) {
    if (ancestors == null) {
      ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
    }
    return ancestors.add(value);
  }

  /**
   * Drops {@code value} from the path, on the way back out. Paired with a successful {@link #add}.
   */
  void remove(Object value) {
    if (ancestors != null) {
      ancestors.remove(value);
    }
  }

  /**
   * Takes one step down.
   *
   * @return whether the step was allowed; {@code false} means the caller must render a marker
   *     instead of descending, and must <em>not</em> call {@link #ascend()}
   */
  boolean descend() {
    if (depth >= MAX_DEPTH) {
      return false;
    }
    depth++;
    return true;
  }

  /** Takes one step back up. Always paired with a {@link #descend()} that returned {@code true}. */
  void ascend() {
    depth--;
  }
}
