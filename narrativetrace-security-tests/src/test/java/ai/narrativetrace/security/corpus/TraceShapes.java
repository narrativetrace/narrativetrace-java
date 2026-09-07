/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a declarative {@link TraceShapeCase} into a live {@code TraceTree} whose walk every
 * bounded, cycle-safe renderer/exporter must survive.
 *
 * <p>INTENT: {@link HostileGraphs} builds arbitrary object graphs for the value renderer; this
 * builds {@code TraceNode} call trees specifically, for the separate walker ({@code
 * ai.narrativetrace.core.tree.TreeWalk}) every recursive renderer and exporter shares. The corpus
 * stays data — every runtime copies {@code trace-shapes.json} verbatim and writes its own builder.
 */
public final class TraceShapes {

  private TraceShapes() {}

  /** Builds the tree {@code shapeCase} describes. */
  public static DefaultTraceTree build(TraceShapeCase shapeCase) {
    return new DefaultTraceTree(List.of(root(shapeCase)));
  }

  private static TraceNode root(TraceShapeCase shapeCase) {
    return switch (shapeCase.kind()) {
      case "chain" -> chain(shapeCase.n());
      case "cycle" -> ring(shapeCase.n());
      default ->
          throw new IllegalArgumentException("unknown trace shape kind: " + shapeCase.kind());
    };
  }

  /** A linear chain {@code depth} nodes deep, innermost leaf first. */
  private static TraceNode chain(int depth) {
    var current = node("leaf", List.of());
    for (var i = 0; i < depth; i++) {
      current = node("call" + i, List.of(current));
    }
    return current;
  }

  /** A ring of {@code length} nodes, each holding the next; {@code length == 1} holds itself. */
  private static TraceNode ring(int length) {
    var children = new ArrayList<List<TraceNode>>(length);
    var nodes = new ArrayList<TraceNode>(length);
    for (var i = 0; i < length; i++) {
      var ownChildren = new ArrayList<TraceNode>();
      children.add(ownChildren);
      nodes.add(node("n" + i, ownChildren));
    }
    for (var i = 0; i < length; i++) {
      children.get(i).add(nodes.get((i + 1) % length));
    }
    return nodes.get(0);
  }

  private static TraceNode node(String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature("HostileTraceShape", methodName, List.of()),
        children,
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L);
  }
}
