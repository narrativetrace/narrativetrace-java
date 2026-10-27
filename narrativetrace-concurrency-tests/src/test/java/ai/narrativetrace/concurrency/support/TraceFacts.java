/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Reads facts out of an exported tree; the matrix asserts through these, never through indices. */
public final class TraceFacts {

  private static final Pattern REQUEST_ID = Pattern.compile("REQ-\\d{4}");

  private TraceFacts() {}

  static void collect(List<TraceNode> nodes, List<TraceNode> into) {
    for (var node : nodes) {
      into.add(node);
      collect(node.children(), into);
    }
  }

  /** The tree in pre-order, as {@link FlatNode}s. */
  public static List<FlatNode> flatten(TraceTree tree) {
    var flat = new ArrayList<FlatNode>();
    flatten(tree.roots(), 0, flat);
    return flat;
  }

  private static void flatten(List<TraceNode> nodes, int depth, List<FlatNode> into) {
    for (var node : nodes) {
      into.add(flat(node, into.size(), depth));
      flatten(node.children(), depth + 1, into);
    }
  }

  private static FlatNode flat(TraceNode node, int index, int depth) {
    var thread =
        node.thread() != null
            ? node.thread().threadName()
            : node.concurrency() != null ? node.concurrency().threadName() : "";
    var traceId =
        node.spanContext() != null && node.spanContext().traceId() != null
            ? node.spanContext().traceId().toString()
            : "";
    return new FlatNode(
        index,
        depth,
        node.signature().methodName(),
        firstArgument(node),
        returned(node),
        thread,
        traceId);
  }

  public static List<TraceNode> all(TraceTree tree) {
    var nodes = new ArrayList<TraceNode>();
    collect(tree.roots(), nodes);
    return nodes;
  }

  public static List<TraceNode> descendantsOf(TraceNode node) {
    var nodes = new ArrayList<TraceNode>();
    collect(node.children(), nodes);
    return nodes;
  }

  public static List<TraceNode> named(List<TraceNode> nodes, String methodName) {
    return nodes.stream().filter(n -> n.signature().methodName().equals(methodName)).toList();
  }

  /** The one node a request opens for {@code methodName}; fails when there is none or several. */
  public static TraceNode only(TraceTree tree, String methodName) {
    var found = named(all(tree), methodName);
    if (found.size() != 1) {
      throw new AssertionError(
          "expected exactly one "
              + methodName
              + " span, found "
              + found.size()
              + " in:\n"
              + tree.roots());
    }
    return found.get(0);
  }

  /** Every request id (REQ-0000 shape) that appears anywhere in the rendered trace. */
  public static Set<String> requestIdsIn(String text) {
    var ids = new TreeSet<String>();
    var matcher = REQUEST_ID.matcher(text);
    while (matcher.find()) {
      ids.add(matcher.group());
    }
    return ids;
  }

  /** The first parameter's rendered value of a call, quotes and all. */
  public static String firstArgument(TraceNode node) {
    return node.signature().parameters().isEmpty()
        ? ""
        : node.signature().parameters().get(0).renderedValue();
  }

  /** What the call returned, rendered; empty when it did not return. */
  public static String returned(TraceNode node) {
    return node.outcome() instanceof TraceOutcome.Returned returned
        ? String.valueOf(returned.renderedValue())
        : "";
  }
}
