/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.TraceNode;
import java.lang.reflect.Method;

/** Shared utilities for concurrency groups (fork-join and fire-and-forget). */
final class ConcurrencySupport {

  private static final Method IS_VIRTUAL = resolveIsVirtual();

  private ConcurrencySupport() {}

  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static Method resolveIsVirtual() {
    try {
      return Thread.class.getMethod("isVirtual");
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  static boolean isVirtual(Thread thread) {
    if (IS_VIRTUAL == null) {
      return false;
    }
    try {
      return (boolean) IS_VIRTUAL.invoke(thread);
    } catch (ReflectiveOperationException e) {
      return false;
    }
  }

  static TraceNode withConcurrency(TraceNode node, ConcurrencyInfo info) {
    return new TraceNode(
        node.signature(),
        node.children(),
        node.outcome(),
        node.durationNanos(),
        node.startTimeNanos(),
        info,
        node.spanContext());
  }
}
