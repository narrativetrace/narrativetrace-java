/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.TraceNode;
import java.util.Comparator;
import java.util.List;

/**
 * Detects sequential-async anti-patterns in fork-join groups.
 *
 * <p>When concurrent children under the same fork group have non-overlapping execution windows,
 * they were awaited sequentially — a missed parallelization opportunity.
 */
final class SequentialAsyncDetector {

  private SequentialAsyncDetector() {}

  static SequentialAsyncResult analyze(List<TraceNode> members) {
    if (members.size() < 2) {
      return SequentialAsyncResult.NONE;
    }
    var sorted =
        members.stream().sorted(Comparator.comparingLong(TraceNode::startTimeNanos)).toList();
    int overlapping = 0;
    for (int i = 1; i < sorted.size(); i++) {
      long prevEnd = sorted.get(i - 1).startTimeNanos() + sorted.get(i - 1).durationNanos();
      if (sorted.get(i).startTimeNanos() < prevEnd) {
        overlapping++;
      }
    }
    long totalNanos = members.stream().mapToLong(TraceNode::durationNanos).sum();
    long maxNanos = members.stream().mapToLong(TraceNode::durationNanos).max().orElse(0);
    if (overlapping == 0) {
      return new SequentialAsyncResult(
          SequentialAsyncResult.Classification.SEQUENTIAL_ASYNC, totalNanos, maxNanos);
    }
    if (overlapping < sorted.size() - 1) {
      return new SequentialAsyncResult(
          SequentialAsyncResult.Classification.MIXED, totalNanos, maxNanos);
    }
    return SequentialAsyncResult.NONE;
  }
}
