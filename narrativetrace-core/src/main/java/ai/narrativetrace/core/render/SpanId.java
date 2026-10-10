/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.TraceNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The citable id of one span in a rendered trace: its position path in the tree, {@code #1}, {@code
 * #1.3}, {@code #1.3.2} (root call, its third child, that child's second child).
 *
 * <p>INTENT: An agent's report, a grader, an approval delta and a human review must be able to
 * point at the same span. The id is DERIVED from the tree by the renderer — never stored in the
 * event stream, never random — so two runs of the same flow give the same ids, and every flavour
 * prints the same id for the same span.
 */
public final class SpanId {

  /**
   * The order members of one fork or async group take their ids in: by {@code Class.method}, the
   * order the structural trace prints them in. Capture order across threads is the scheduler's, so
   * it can number nothing; a stable sort keeps equal signatures in capture order.
   */
  static final Comparator<TraceNode> CONCURRENT_ORDER =
      Comparator.comparing(n -> n.signature().className() + "." + n.signature().methodName());

  /** What {@link #idEnd} answers when no well-formed id starts there. */
  private static final int NO_ID = -1;

  private SpanId() {}

  /**
   * The id of every node in {@code siblings}, index for index, for a renderer that walks them in
   * capture order rather than in the order ids are given in.
   *
   * @param siblings one node's children, or a tree's roots
   * @param parent the id of the node they belong to; {@code null} for roots
   */
  public static List<String> idsOf(List<TraceNode> siblings, String parent) {
    var ids = new ArrayList<String>(siblings.size());
    var position = 0;
    for (var segment : ChildSegment.partition(siblings)) {
      addIdsInPositionOrder(segment.nodes, parent, position, ids);
      position += segment.positions();
    }
    return ids;
  }

  /**
   * A run of consecutive sibling ids as one citation: {@code #1.2–#1.4}, or the id itself when the
   * run is one span long.
   *
   * @throws IllegalArgumentException when {@code ids} is empty — an empty run cites nothing
   */
  static String range(List<String> ids) {
    if (ids.isEmpty()) {
      throw new IllegalArgumentException("a range cites at least one span");
    }
    var first = ids.get(0);
    var last = ids.get(ids.size() - 1);
    return ids.size() == 1 ? first : first + "–" + last;
  }

  /**
   * The ids of one segment's nodes, listed in capture order but numbered in {@link
   * #CONCURRENT_ORDER} — which, for a plain call or a launcher (a one-node segment), is the node's
   * own position.
   */
  private static void addIdsInPositionOrder(
      List<TraceNode> members, String parent, int positionBefore, List<String> ids) {
    var byRank = new ArrayList<Integer>(members.size());
    for (var i = 0; i < members.size(); i++) {
      byRank.add(i);
    }
    byRank.sort(Comparator.comparing(members::get, CONCURRENT_ORDER));
    var segmentIds = new String[members.size()];
    for (var rank = 0; rank < byRank.size(); rank++) {
      segmentIds[byRank.get(rank)] = child(parent, positionBefore + rank + 1);
    }
    ids.addAll(List.of(segmentIds));
  }

  /**
   * The id of a child at a 1-based {@code position} under {@code parent}; a root when {@code
   * parent} is {@code null}.
   */
  public static String child(String parent, int position) {
    return parent == null ? "#" + position : parent + "." + position;
  }

  /**
   * {@code line} without its leading span id: {@code " #1.2 - A.b()"} becomes {@code " - A.b()"}. A
   * line that carries no id — a marker, a header, a baseline written before ids existed — is
   * returned unchanged, which is what lets an id-free {@code .approved.nt} still compare.
   */
  public static String strip(String line) {
    var start = indentOf(line);
    var end = idEnd(line, start);
    return end == NO_ID ? line : line.substring(0, start) + line.substring(end + 1);
  }

  /**
   * Whether {@code text} is exactly one span id: {@code #}, then dot-separated runs of ASCII
   * digits.
   */
  public static boolean isWellFormed(String text) {
    return text != null && idEnd(text + " ", 0) == text.length();
  }

  /** The span id {@code line} opens with, or {@code null} when it carries none. */
  public static String of(String line) {
    var start = indentOf(line);
    var end = idEnd(line, start);
    return end == NO_ID ? null : line.substring(start, end);
  }

  private static int indentOf(String line) {
    var i = 0;
    while (i < line.length() && line.charAt(i) == ' ') {
      i++;
    }
    return i;
  }

  /**
   * Index of the space that ends an id starting at {@code start}, or {@code -1} when there is no
   * well-formed id there ({@code #}, then dot-separated runs of ASCII digits, then one space).
   */
  private static int idEnd(String line, int start) {
    if (start >= line.length() || line.charAt(start) != '#') {
      return NO_ID;
    }
    var i = start + 1;
    var digitsInRun = 0;
    while (i < line.length()) {
      var c = line.charAt(i);
      if (c >= '0' && c <= '9') {
        digitsInRun++;
      } else if (c == '.' && digitsInRun > 0) {
        digitsInRun = 0;
      } else {
        return c == ' ' && digitsInRun > 0 ? i : NO_ID;
      }
      i++;
    }
    return NO_ID;
  }
}
