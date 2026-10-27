/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.export.RequestContext;
import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** The exporter every cell plugs in: records what the filter exports, keyed by request route. */
public final class RecordingExporter implements TraceExporter {

  private final Map<String, Exported> byRequest = new ConcurrentHashMap<>();
  private final List<Exported> all = new CopyOnWriteArrayList<>();

  @Override
  public void export(TraceTree tree, RequestContext requestContext) {
    var recorded = describe(tree);
    all.add(recorded);
    byRequest.put(recorded.requestId(), recorded);
  }

  /** The export of one request; fails the calling test when the request exported nothing. */
  public Exported of(String requestId) {
    var found = byRequest.get(requestId);
    if (found == null) {
      throw new AssertionError(
          "request " + requestId + " exported no trace; exported: " + byRequest.keySet());
    }
    return found;
  }

  /** Every export, in arrival order. */
  public List<Exported> all() {
    return List.copyOf(all);
  }

  public void clear() {
    all.clear();
    byRequest.clear();
  }

  public static Exported describe(TraceTree tree) {
    var ids = new TreeSet<String>();
    var nodes = new ArrayList<TraceNode>();
    TraceFacts.collect(tree.roots(), nodes);
    for (var node : nodes) {
      if (node.spanContext() != null && node.spanContext().traceId() != null) {
        ids.add(node.spanContext().traceId().toString());
      }
    }
    var route = routeOf(tree);
    return new Exported(
        route.substring(route.lastIndexOf('/') + 1),
        tree,
        new IndentedTextRenderer().render(tree),
        ids);
  }

  private static String routeOf(TraceTree tree) {
    for (var root : tree.roots()) {
      if (root.spanContext() != null && root.spanContext().httpRoute() != null) {
        return root.spanContext().httpRoute().toString();
      }
    }
    return "/unknown";
  }
}
