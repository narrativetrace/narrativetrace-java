/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a class matches list 2: the configurable, package-named exclusion list.
 *
 * <p>INTENT: {@code exclude=} always wins over {@code packages=} — a class matching both is not
 * instrumented. {@link AgentConfig#exclude()} already carries the fully resolved list (the caller's
 * explicit value, or the shipped default), normalized exactly like {@code packages}; this class
 * only matches against it and reports the decision.
 *
 * <p><b>@llmNote</b> Reporting is once per <em>matched prefix</em>, never per class and never for a
 * prefix that never fires — the same "report once" shape {@link LoaderVisibility} uses for a
 * different gate, for the same reason: a hot package must not flood a host's error stream to say
 * one thing once.
 *
 * <p><b>@threadSafety</b> Called from the class-loading path of every thread, concurrently; {@link
 * #reported} is a concurrent set so two threads racing to exclude the first class under a prefix
 * both see a consistent "already reported" answer.
 */
final class ExcludedPackages {

  private final List<String> prefixes;
  private final Set<String> reported = ConcurrentHashMap.newKeySet();

  ExcludedPackages(List<String> prefixes) {
    this.prefixes = prefixes;
  }

  /**
   * @param className the internal name of the class the package filter already matched
   * @return {@code true} when a configured prefix excludes it, having reported that prefix once
   */
  boolean isExcluded(String className) {
    for (var prefix : prefixes) {
      if (className.startsWith(prefix)) {
        reportOnce(prefix, className);
        return true;
      }
    }
    return false;
  }

  private void reportOnce(String prefix, String className) {
    if (reported.add(prefix)) {
      System.err.println( // NOPMD - the agent's only channel from inside a class transformer
          "narrativetrace-agent: cannot instrument "
              + className
              + " (excluded package "
              + prefix
              + "); classes under that package load uninstrumented");
    }
  }
}
