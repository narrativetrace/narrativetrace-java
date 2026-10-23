/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.util.IdentityHashMap;
import java.util.Map;

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
 * <p><b>@llmNote</b> The map is created on the first {@link #add} (or the first remembered
 * rendering, which shares it), not with the walk. An {@code IdentityHashMap} costs ~352 bytes, and
 * the walks that never reach a reference worth guarding — every scalar, every value with a custom
 * {@code toString()}, every record of scalars — were paying it for a set nothing read (four of them
 * per traced proxy call, ~1.4 kB/op).
 *
 * <p><b>@edgeCase</b> The cycle guard alone does not bound a walk. A ten-thousand-node
 * <em>chain</em> is not a cycle, and following it recursively was a {@code StackOverflowError}
 * raised inside instrumentation — an observability failure turned into an application failure,
 * which the pipeline contract forbids. {@link #MAX_DEPTH} is the fourth cap beside the existing
 * string, collection and field limits.
 *
 * <p><b>@edgeCase</b> Neither guard bounds a walk's TOTAL either. Dropping an ancestor on the way
 * out is what lets a shared object render twice, and when the sharing repeats down the graph (a
 * ladder whose two fields hold the same next rung) every rung doubles the walk: {@code n + 1}
 * objects rendered as 2<sup>n</sup> nodes, ~2.3 &times; 10<sup>11</sup> characters at {@link
 * #MAX_DEPTH} (ADV-2026-10-23-1). Two answers sit here, both consulted by the one funnel each
 * render path has, so the flat and structured paths make the same decision: a completed rendering
 * of at least {@link #REPEAT_MIN_NODES} values is remembered by identity and a later reference to
 * the same instance renders as a back-reference ({@link #isRepeat}), and every value the walk
 * renders counts against {@link #MAX_NODES} ({@link #count}), the fifth cap — the guarantee for any
 * graph, shared or not (the corpus row {@code shared-subgraph-ladder} pins both).
 *
 * <p><b>@llmNote</b> The walk also carries the narrative-summary hook's answers for one capture
 * ({@link #cachedSummary}), so a hook runs at most once per instance per capture: {@code
 * ValueRenderer.renderForCapture} hands the flat walk's cache to the structured walk through {@link
 * #nextPath}.
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

  /**
   * How many values one walk renders, at most, before every further value renders as the
   * node-budget marker instead.
   *
   * <p>Chosen on the scale of the per-level caps: five items or fields per level, and a full
   * five-wide tree four levels deep is 781 values — already more than a reader scans in one
   * parameter; 1024 is the next round number above it. With the 200-character string cap a
   * 1024-value rendering stays near a megabyte flat at worst. Scalars count too: the two render
   * paths classify a few platform leaves differently (an {@code Instant} is a structured scalar and
   * a flat leaf), and counting every value rendered, before that split, is what keeps the two
   * paths' counts identical.
   */
  static final int MAX_NODES = 1024;

  /**
   * The smallest completed rendering, in values, that a later reference to the same instance points
   * back to instead of repeating. A smaller one — a record of scalars, a short list, a summary's
   * text — is as cheap to repeat as to point at, and reads better repeated.
   */
  static final int REPEAT_MIN_NODES = 8;

  /**
   * Identity-keyed: {@link Boolean#TRUE} for an ancestor on the current path, {@link Boolean#FALSE}
   * for an instance whose rendering completed earlier in this walk.
   */
  private Map<Object, Boolean> seen;

  /** The narrative-summary hook's answers, by instance; shared across one capture's two walks. */
  private Map<Object, String> summaries;

  private int depth;

  private int nodes;

  RenderWalk() {}

  private RenderWalk(Map<Object, String> summaries) {
    this.summaries = summaries;
  }

  /**
   * A fresh walk for the second render path of the same capture: nothing of this walk's path, memo
   * or budget, only the summary hook's answers.
   */
  RenderWalk nextPath() {
    return new RenderWalk(summaries);
  }

  /**
   * Records {@code value} as an ancestor of the current path.
   *
   * @return whether it was not already one — {@code false} means a cycle
   */
  boolean add(Object value) {
    if (seen == null) {
      seen = new IdentityHashMap<>();
    }
    return !Boolean.TRUE.equals(seen.put(value, Boolean.TRUE));
  }

  /**
   * Drops {@code value} from the path, on the way back out. Paired with a successful {@link #add}.
   */
  void remove(Object value) {
    if (seen != null) {
      seen.remove(value);
    }
  }

  /** Whether {@code value} already rendered in full earlier in this walk. */
  boolean isRepeat(Object value) {
    return seen != null && Boolean.FALSE.equals(seen.get(value));
  }

  /**
   * Remembers that {@code value} rendered to completion, when that rendering was large enough to be
   * worth pointing back to. The root is never remembered: it cannot be reached again except as a
   * cycle, which the ancestor half already answers.
   *
   * @param nodesBefore {@link #nodes()} as it stood when the value's own rendering began
   */
  void rendered(Object value, int nodesBefore) {
    if (depth == 0 || nodes - nodesBefore + 1 < REPEAT_MIN_NODES) {
      return;
    }
    if (seen == null) {
      seen = new IdentityHashMap<>();
    }
    seen.putIfAbsent(value, Boolean.FALSE);
  }

  /**
   * Counts one more rendered value against {@link #MAX_NODES}.
   *
   * @return whether the budget allowed it; {@code false} means the caller must render the
   *     node-budget marker instead of the value
   */
  boolean count() {
    if (nodes >= MAX_NODES) {
      return false;
    }
    nodes++;
    return true;
  }

  /** How many values this walk has counted so far. */
  int nodes() {
    return nodes;
  }

  /** The summary hook's answer for {@code value} earlier in this capture, or {@code null}. */
  String cachedSummary(Object value) {
    return summaries == null ? null : summaries.get(value);
  }

  /** Records the summary hook's answer for {@code value}, for the rest of this capture. */
  void cacheSummary(Object value, String text) {
    if (summaries == null) {
      summaries = new IdentityHashMap<>();
    }
    summaries.put(value, text);
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
