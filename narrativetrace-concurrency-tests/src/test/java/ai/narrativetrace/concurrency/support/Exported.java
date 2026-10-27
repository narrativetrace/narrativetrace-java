/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

import ai.narrativetrace.api.tree.TraceTree;
import java.util.List;
import java.util.Set;

/**
 * One request's exported trace, as the exporter received it in this JVM.
 *
 * @param requestId the request the export belongs to (read from the root span's route)
 * @param tree the exported tree
 * @param text the tree rendered as text — the form a reader (or an agent) is given
 * @param traceIds every distinct trace id carried by a span in the tree
 */
public record Exported(String requestId, TraceTree tree, String text, Set<String> traceIds)
    implements Observation {

  @Override
  public List<FlatNode> nodes() {
    return TraceFacts.flatten(tree);
  }
}
