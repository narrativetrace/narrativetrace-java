/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.render.TraceNamer;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.List;

/**
 * The identity every entry of one tree shares: which trace it belongs to, and which story it tells.
 *
 * <p>INTENT: One rule, every exporter. {@link ChapterExporter} and {@link TraceTreeCanonicalMapper}
 * describe the same capture at two granularities; when each resolved identity for itself they
 * disagreed — the chapter scanned roots only and omitted the fields it could not find, the entry
 * mapper scanned depth-first and substituted a constant. Resolving here means a chapter and its own
 * entries can no longer name different traces.
 *
 * <p>The ladder is ADR-014's, taking the first rung that yields a value:
 *
 * <ol>
 *   <li><b>Inherit</b> — the first real {@link SpanContext} anywhere in the tree. A node that kept
 *       its context is the trace's own witness, so it outranks everything below.
 *   <li><b>Inherit from the capture</b> — the trace id the capturing context assigned to the tree
 *       ({@link TraceTree#traceId()}), for a tree whose nodes all lost their context.
 *   <li><b>Derive</b> — the story from the first root-level call, the chapter from the story, the
 *       trace name from the trace id.
 *   <li><b>Generate</b> — a fresh unique trace id, and only for a tree that has no trace id at all
 *       (hand-built in tests, replayed from an artifact). Never a shared constant: two unrelated
 *       captures that collide on one id are indistinguishable to every consumer downstream.
 * </ol>
 *
 * <p><b>@llmNote</b> Resolved once per tree and passed down, never recomputed per node — a
 * context-free child must not split its own trace's identity. Only {@code storyId}/{@code
 * chapterId} of an <em>empty</em> tree fall back to a constant: there is no root call to derive
 * from, and {@code chapter.schema.json} requires the fields regardless.
 *
 * @param inherited the first real span context in the tree, or {@code null} when it has none
 * @param traceId the trace this tree belongs to; never {@code null}
 * @param storyId the story this tree tells; never {@code null}
 * @param chapterId this service's chapter of that story; never {@code null}
 */
record TraceIdentity(SpanContext inherited, TraceId traceId, String storyId, String chapterId) {

  /**
   * Story and chapter of a tree with no root call to derive from — the same word {@link
   * ChapterExporter} titles such a chapter with.
   */
  static final String UNKNOWN_STORY = "unknown";

  /** Validates what every caller may then rely on: the three identity fields are always present. */
  TraceIdentity {
    if (traceId == null) {
      throw new IllegalArgumentException("traceId must not be null");
    }
    if (storyId == null || chapterId == null) {
      throw new IllegalArgumentException("storyId and chapterId must not be null");
    }
  }

  /**
   * Resolves the identity of a captured tree.
   *
   * @throws IllegalArgumentException if {@code tree} is null
   */
  static TraceIdentity of(TraceTree tree) {
    if (tree == null) {
      throw new IllegalArgumentException("tree must not be null");
    }
    var inherited = firstSpanContext(tree.roots());
    var derivedStory = storyOfFirstRoot(tree.roots());
    var story = inheritedOr(inherited != null ? inherited.storyId() : null, derivedStory);
    var chapter = inheritedOr(inherited != null ? inherited.chapterId() : null, story);
    var identity = new TraceIdentity(inherited, resolveTraceId(tree, inherited), story, chapter);
    assert identity.traceId().value().length() == 32 : "postcondition: W3C-shaped trace id";
    return identity;
  }

  /**
   * The trace name every entry of this tree carries, derived from the trace id (never generated).
   */
  String traceName() {
    return TraceNamer.name(traceId.value());
  }

  private static TraceId resolveTraceId(TraceTree tree, SpanContext inherited) {
    if (inherited != null) {
      return inherited.traceId();
    }
    return tree.traceId() != null ? tree.traceId() : SpanIdGenerator.traceId();
  }

  private static String inheritedOr(String inherited, String derived) {
    if (inherited != null) {
      return inherited;
    }
    return derived != null ? derived : UNKNOWN_STORY;
  }

  /**
   * The first real {@link SpanContext} anywhere in the tree, depth-first, or {@code null}.
   *
   * <p><b>@edgeCase</b> Walked through {@link TreeWalk}, bounded and cycle-safe: a hand-built,
   * replayed or deserialized tree is not guaranteed acyclic. {@code Found} is a stackless
   * control-flow signal — thrown the moment a span context turns up so the walk does not keep
   * visiting the rest of a large tree once the answer is already known, exactly as the original
   * depth-first recursion's early {@code return} did.
   */
  private static SpanContext firstSpanContext(List<TraceNode> nodes) {
    try {
      for (var root : nodes) {
        TreeWalk.walk(
            root,
            TraceNode::children,
            TraceIdentity::checkSpanContext,
            TraceIdentity::checkSpanContext);
      }
    } catch (Found found) {
      return found.spanContext;
    }
    return null;
  }

  private static void checkSpanContext(
      TraceNode node, int depth) { // NOPMD - depth is TreeWalk's fixed callback shape, unused here
    if (node.spanContext() != null) {
      throw new Found(node.spanContext());
    }
  }

  private static void checkSpanContext(
      TraceNode node, int depth, TreeWalk.Reason reason) { // NOPMD - fixed callback shape
    checkSpanContext(node, depth);
  }

  /** Stackless signal that {@link #firstSpanContext} found its answer and the walk can stop. */
  private static final class Found extends RuntimeException {
    private final transient SpanContext spanContext;

    Found(SpanContext spanContext) {
      super(null, null, false, false);
      this.spanContext = spanContext;
    }
  }

  /** {@code Class.method} of the first root-level call — the live context's own rule. */
  private static String storyOfFirstRoot(List<TraceNode> roots) {
    if (roots.isEmpty()) {
      return null;
    }
    var sig = roots.get(0).signature();
    return sig.className() + "." + sig.methodName();
  }
}
