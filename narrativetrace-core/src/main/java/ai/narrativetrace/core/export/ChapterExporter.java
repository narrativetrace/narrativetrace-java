/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.render.DurationFormat;
import ai.narrativetrace.core.render.TraceMetadata;
import ai.narrativetrace.core.tree.TreeWalk;
import java.time.Instant;
import java.util.List;

/**
 * Exports a trace tree as a flat chapter JSON object matching {@code chapter.schema.json}.
 *
 * <p>INTENT: This is the chapter-level export for the NarrativeTrace backend. The output is a flat
 * JSON object with Layer 1+2 fields plus chapter-specific Layer 3 fields. The {@code
 * nt.chapterTree} field embeds the full nested trace tree (produced by {@link JsonExporter}).
 *
 * <p><b>@llmNote</b> Entry count is computed recursively across all nested children. The outcome is
 * derived from the root node's {@link TraceOutcome}: Returned → success, Threw → failure,
 * Incomplete → partial.
 */
public final class ChapterExporter {

  private final JsonExporter jsonExporter = new JsonExporter();

  /**
   * Exports the trace tree as a chapter JSON string matching {@code chapter.schema.json}.
   *
   * @throws IllegalArgumentException if {@code tree} is null
   */
  public String exportChapter(TraceTree tree, TraceMetadata metadata) {
    var identity = TraceIdentity.of(tree);
    var chapterTree = jsonExporter.exportDocument(tree, metadata);
    return buildChapterJson(identity, tree, chapterTree);
  }

  private static String buildChapterJson(
      TraceIdentity identity, TraceTree tree, String chapterTree) {
    var roots = tree.roots();
    var rootNode = roots.isEmpty() ? null : roots.get(0);
    var outcome = deriveOutcome(rootNode);
    var title = deriveTitle(rootNode);
    var sb = new StringBuilder();
    sb.append("{\n");
    var first = new boolean[] {true};

    writeHeaderFields(sb, first, rootNode, outcome, title, identity);
    writeSchemaFields(sb, first, identity, outcome, title);
    writeDurationFields(sb, first, rootNode, roots, tree.durationNanos());
    writeChapterTree(sb, first, chapterTree);

    sb.append('\n');
    sb.append('}');
    return sb.toString();
  }

  private static void writeHeaderFields(
      StringBuilder sb,
      boolean[] first,
      TraceNode rootNode,
      String outcome,
      String title,
      TraceIdentity identity) {
    var level = "failure".equals(outcome) ? "error" : "info";
    long durationMs = rootNode != null ? rootNode.durationMillis() : 0;
    var message = "Chapter complete: " + title + " [" + durationMs + "ms] " + outcome;

    requiredString(sb, first, "timestamp", Instant.now().toString());
    requiredString(sb, first, "level", level);
    requiredString(sb, first, "message", message);
    requiredString(
        sb, first, "service", CanonicalEntryMapper.serviceNameOrUnknown(identity.inherited()));
    requiredString(sb, first, "trace_id", identity.traceId().toString());
  }

  private static void writeSchemaFields(
      StringBuilder sb, boolean[] first, TraceIdentity identity, String outcome, String title) {
    requiredString(sb, first, "nt.entryType", "chapter");
    requiredString(sb, first, "nt.storyId", identity.storyId());
    requiredString(sb, first, "nt.chapterId", identity.chapterId());
    requiredString(sb, first, "nt.title", title);
    requiredString(sb, first, "nt.outcome", outcome);
    requiredString(sb, first, "nt.completionStatus", "complete");
    requiredString(sb, first, "nt.traceName", identity.traceName());
    requiredString(sb, first, "nt.schemaVersion", CanonicalEntry.SCHEMA_VERSION);
  }

  private static void writeDurationFields(
      StringBuilder sb,
      boolean[] first,
      TraceNode rootNode,
      List<TraceNode> roots,
      long durationNanos) {
    if (rootNode != null) {
      appendSeparator(sb, first);
      // "Wall-clock duration from first entry to last exit" — what chapter.schema.json declares,
      // which the first root's own duration only happened to match while traces had one root.
      // Asked of the tree itself: an implementation that knows better than the default
      // first-entry-to-last-exit walk is entitled to answer for its own capture.
      sb.append("  \"nt.totalDurationMs\": ").append(DurationFormat.millis(durationNanos));
    }
    int entryCount = countEntries(roots);
    appendSeparator(sb, first);
    sb.append("  \"nt.entryCount\": ").append(entryCount);
  }

  private static void writeChapterTree(StringBuilder sb, boolean[] first, String chapterTree) {
    appendSeparator(sb, first);
    sb.append("  \"nt.chapterTree\": \"").append(escapeJson(chapterTree)).append('"');
  }

  private static String deriveOutcome(TraceNode rootNode) {
    if (rootNode == null) return "success";
    var outcome = rootNode.outcome();
    if (outcome instanceof TraceOutcome.Returned) return "success";
    if (outcome instanceof TraceOutcome.Threw) return "failure";
    if (outcome instanceof TraceOutcome.Incomplete) return "partial";
    return "success";
  }

  private static String deriveTitle(TraceNode rootNode) {
    if (rootNode == null) return "unknown";
    return rootNode.signature().className() + "." + rootNode.signature().methodName();
  }

  /**
   * Counts every node under {@code nodes}, bounded and cycle-safe via {@link TreeWalk}: a
   * hand-built, replayed or deserialized tree is not guaranteed acyclic, and a genuinely deep tree
   * is ordinary for a recursive business method. A node beyond the walk's bound still counts as
   * itself.
   */
  private static int countEntries(List<TraceNode> nodes) {
    var count = new int[] {0};
    for (var root : nodes) {
      TreeWalk.walk(
          root, TraceNode::children, (n, depth) -> count[0]++, (n, depth, reason) -> count[0]++);
    }
    return count[0];
  }

  /**
   * Writes a field {@code chapter.schema.json} marks required.
   *
   * <p><b>@llmNote</b> Same guard, same reason as {@link CanonicalEntrySerializer}: a null would
   * escape to the four-character text {@code null}, which passes a string-typed schema check
   * silently. Kept in parity with that class deliberately.
   *
   * @throws IllegalStateException if {@code value} is null.
   */
  private static void requiredString(StringBuilder sb, boolean[] first, String key, String value) {
    if (value == null) {
      throw new IllegalStateException("Required chapter field is null: " + key);
    }
    appendSeparator(sb, first);
    sb.append("  \"").append(key).append("\": \"").append(escapeJson(value)).append('"');
  }

  private static void appendSeparator(StringBuilder sb, boolean[] first) {
    if (!first[0]) sb.append(",\n");
    first[0] = false;
  }

  private static String escapeJson(String s) {
    return JsonEscape.escape(s);
  }
}
