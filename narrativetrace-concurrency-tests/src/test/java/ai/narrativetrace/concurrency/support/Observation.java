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

/** What a cell observed of one request's exported trace — in this JVM, or read back from a fork. */
public interface Observation {

  String requestId();

  /** The rendered trace, the form a reader (or an agent) is given. */
  String text();

  /** Every distinct trace id carried by a span. */
  Set<String> traceIds();

  /** Every span, pre-order. */
  List<FlatNode> nodes();

  /** The one span a request opens for {@code method}; fails when there is none or several. */
  default FlatNode only(String method) {
    var found = named(method);
    if (found.size() != 1) {
      throw new AssertionError(
          "expected exactly one " + method + " span, found " + found.size() + " in:\n" + text());
    }
    return found.get(0);
  }

  default List<FlatNode> named(String method) {
    return nodes().stream().filter(n -> n.method().equals(method)).toList();
  }

  /** The spans below {@code parent}: those after it in pre-order with a greater depth. */
  default List<FlatNode> below(FlatNode parent) {
    var below = new ArrayList<FlatNode>();
    var all = nodes();
    for (int i = parent.index() + 1; i < all.size() && all.get(i).depth() > parent.depth(); i++) {
      below.add(all.get(i));
    }
    return below;
  }
}
