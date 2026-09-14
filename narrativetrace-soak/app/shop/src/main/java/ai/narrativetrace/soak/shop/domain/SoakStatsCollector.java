/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import ai.narrativetrace.core.context.NarrativeContext;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import org.springframework.stereotype.Component;

/** Builds the {@code GET /soak/stats} snapshot from the JVM's own management beans. */
@Component
public class SoakStatsCollector {

  private final NarrativeContext context;
  private final SoakMetrics metrics;

  public SoakStatsCollector(NarrativeContext context, SoakMetrics metrics) {
    this.context = context;
    this.metrics = metrics;
  }

  public SoakStats collect() {
    return new SoakStats(
        context.traceLoss().droppedEvents(),
        null,
        heapUsedAfterLastGc(),
        ManagementFactory.getThreadMXBean().getThreadCount(),
        openFileDescriptorCount(),
        ManagementFactory.getRuntimeMXBean().getUptime(),
        metrics.requestCount(),
        metrics.exceptionCount(),
        metrics.edgeRejectionCount(),
        metrics.businessFailureCount(),
        metrics.poisonExceptionCount());
  }

  /**
   * Sums {@link MemoryPoolMXBean#getCollectionUsage()} across every pool that reports one — the
   * JDK's own "usage as of the most recent collection" figure, not a live heap sample.
   */
  private static long heapUsedAfterLastGc() {
    long total = 0;
    for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
      var usage = pool.getCollectionUsage();
      if (usage != null) {
        total += usage.getUsed();
      }
    }
    return total;
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException") // best-effort on non-Unix JVMs
  private static long openFileDescriptorCount() {
    var os = ManagementFactory.getOperatingSystemMXBean();
    if (os instanceof com.sun.management.UnixOperatingSystemMXBean unix) {
      return unix.getOpenFileDescriptorCount();
    }
    return -1L;
  }
}
