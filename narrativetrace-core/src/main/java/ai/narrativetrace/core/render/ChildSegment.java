/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.TraceNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Groups consecutive children by their concurrency groupId for rendering.
 *
 * <p>Sequential children (no {@link ai.narrativetrace.api.event.ConcurrencyInfo}) form single-node
 * segments with {@code null} groupId. Consecutive concurrent children sharing the same groupId are
 * collected into a single segment.
 */
final class ChildSegment {

  final String groupId;
  final List<TraceNode> nodes = new ArrayList<>();

  private ChildSegment(String groupId) {
    this.groupId = groupId;
  }

  boolean isFireAndForget() {
    return kindIs(ConcurrencyKind.FIRE_AND_FORGET);
  }

  /** Work adopted from a propagated snapshot: concurrent, but launched outside any helper. */
  boolean isAsync() {
    return kindIs(ConcurrencyKind.ASYNC);
  }

  private boolean kindIs(ConcurrencyKind kind) {
    var first = nodes.get(0);
    return first.concurrency() != null && first.concurrency().kind() == kind;
  }

  /**
   * How many sibling positions — and so how many {@link SpanId}s — this segment takes: one per
   * member of a fork or async group, one for a plain call, and one for a fire-and-forget launch,
   * whose launcher owns the id its launched children nest under.
   */
  int positions() {
    return isFireAndForget() ? 1 : nodes.size();
  }

  /**
   * The concurrency group {@code node} is laid out under, or {@code null} for a plain sibling.
   *
   * <p><b>@edgeCase</b> A fire-and-forget WORKER root — a {@code FireAndForgetGroup.childRoots()}
   * entry, tagged with its group's id and an outcome of its own — is a plain node. Only the
   * synthetic launcher, which has no outcome, opens a fire-and-forget segment; grouping the workers
   * under the launcher's rule rendered the first one as a bare marker and dropped the rest.
   */
  private static String segmentedBy(TraceNode node) {
    var info = node.concurrency();
    if (info == null) {
      return null;
    }
    var worker = info.kind() == ConcurrencyKind.FIRE_AND_FORGET && node.outcome() != null;
    return worker ? null : info.groupId();
  }

  static List<ChildSegment> partition(List<TraceNode> children) {
    var segments = new ArrayList<ChildSegment>();
    for (var child : children) {
      var groupId = segmentedBy(child);
      var last = segments.isEmpty() ? null : segments.get(segments.size() - 1);
      if (groupId != null && last != null && groupId.equals(last.groupId)) {
        last.nodes.add(child);
      } else {
        var segment = new ChildSegment(groupId);
        segment.nodes.add(child);
        segments.add(segment);
      }
    }
    return segments;
  }
}
