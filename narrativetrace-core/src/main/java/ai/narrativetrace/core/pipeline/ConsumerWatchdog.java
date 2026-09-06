/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Monitors a heartbeat supplier and fires a callback when the heartbeat is stale beyond a
 * threshold. Uses a single daemon thread to check periodically.
 */
final class ConsumerWatchdog implements AutoCloseable {

  private final ScheduledExecutorService scheduler;

  @SuppressWarnings("PMD.DoNotUseThreads")
  ConsumerWatchdog(
      LongSupplier heartbeatNanos,
      long staleThresholdMillis,
      long checkIntervalMillis,
      Runnable callback) {
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              var t = new Thread(r, "narrative-trace-watchdog");
              t.setDaemon(true);
              return t;
            });
    scheduler.scheduleAtFixedRate(
        () -> checkHeartbeat(heartbeatNanos, staleThresholdMillis, callback),
        checkIntervalMillis,
        checkIntervalMillis,
        TimeUnit.MILLISECONDS);
  }

  private static void checkHeartbeat(
      LongSupplier heartbeatNanos, long staleThresholdMillis, Runnable callback) {
    long last = heartbeatNanos.getAsLong();
    if (last == 0) {
      return;
    }
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - last);
    if (elapsedMillis > staleThresholdMillis) {
      TraceBoundary.run(callback);
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }
}
