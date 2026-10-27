/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * An {@link Observation} read back from a file a forked JVM wrote: the agent route's exports are
 * produced in another process, so the parent sees them only as this text.
 */
public record Observed(String requestId, String text, Set<String> traceIds, List<FlatNode> nodes)
    implements Observation {

  private static final String NODES_MARK = "=== nodes";
  private static final String TEXT_MARK = "=== text";

  /** The file form: request id, nodes (one per line), then the rendered text. */
  public static String write(Observation observation) {
    var out = new StringBuilder();
    out.append(observation.requestId()).append('\n');
    out.append(NODES_MARK).append('\n');
    observation.nodes().forEach(n -> out.append(n.toLine()).append('\n'));
    out.append(TEXT_MARK).append('\n').append(observation.text());
    return out.toString();
  }

  public static Observed read(String content) {
    var lines = content.split("\n", -1);
    var nodes = new ArrayList<FlatNode>();
    var ids = new TreeSet<String>();
    int at = 2;
    while (!lines[at].equals(TEXT_MARK)) {
      var node = FlatNode.parse(lines[at]);
      nodes.add(node);
      if (!node.traceId().isEmpty()) {
        ids.add(node.traceId());
      }
      at++;
    }
    var text = String.join("\n", List.of(lines).subList(at + 1, lines.length));
    return new Observed(lines[0], text, ids, nodes);
  }
}
