/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NoopNarrativeContext;
import org.junit.jupiter.api.Test;

/** {@code GET /soak/stats} shape — what P2's oracles read every 30s. */
class SoakStatsCollectorTest {

  @Test
  void collectReflectsMetricsAndReportsAPlausibleJvmShape() {
    var metrics = new SoakMetrics();
    metrics.incrementRequest();
    metrics.incrementRequest();
    metrics.incrementEdgeRejection();
    metrics.incrementBusinessFailure();
    metrics.incrementPoisonException();
    var collector = new SoakStatsCollector(NoopNarrativeContext.INSTANCE, metrics);

    var stats = collector.collect();

    assertThat(stats.requestCount()).isEqualTo(2);
    assertThat(stats.edgeRejectionCount()).isEqualTo(1);
    assertThat(stats.businessFailureCount()).isEqualTo(1);
    assertThat(stats.poisonExceptionCount()).isEqualTo(1);
    assertThat(stats.exceptionCount()).isEqualTo(3);
    assertThat(stats.droppedEventCount()).isZero();
    assertThat(stats.bufferFill()).isNull();
    assertThat(stats.liveThreadCount()).isPositive();
    assertThat(stats.uptimeMillis()).isGreaterThanOrEqualTo(0);
    assertThat(stats.heapUsedAfterLastGcBytes()).isGreaterThanOrEqualTo(0);
    // -1 on a non-Unix JVM, a real (non-negative) count on Linux/macOS HotSpot.
    assertThat(stats.openFileDescriptorCount()).isGreaterThanOrEqualTo(-1);
  }
}
