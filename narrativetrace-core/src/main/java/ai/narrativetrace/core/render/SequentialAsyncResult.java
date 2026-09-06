/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

/**
 * Result of sequential-async detection for a fork-join group.
 *
 * @param classification how the group's concurrency was classified
 * @param totalNanos sum of all member durations (sequential cost)
 * @param maxNanos longest member duration (parallelizable cost)
 */
record SequentialAsyncResult(Classification classification, long totalNanos, long maxNanos) {

  enum Classification {
    NONE,
    SEQUENTIAL_ASYNC,
    MIXED
  }

  static final SequentialAsyncResult NONE = new SequentialAsyncResult(Classification.NONE, 0, 0);

  boolean isSequentialAsync() {
    return classification == Classification.SEQUENTIAL_ASYNC;
  }

  boolean isMixed() {
    return classification == Classification.MIXED;
  }

  long savingsMillis() {
    return (totalNanos - maxNanos) / 1_000_000;
  }

  long totalMillis() {
    return totalNanos / 1_000_000;
  }

  long parallelizableMillis() {
    return maxNanos / 1_000_000;
  }
}
