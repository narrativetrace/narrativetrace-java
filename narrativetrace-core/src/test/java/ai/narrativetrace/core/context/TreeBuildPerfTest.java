/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.perf.PerfThresholds;
import com.github.noconnor.junitperf.JUnitPerfInterceptor;
import com.github.noconnor.junitperf.JUnitPerfReportingConfig;
import com.github.noconnor.junitperf.JUnitPerfTest;
import com.github.noconnor.junitperf.JUnitPerfTestActiveConfig;
import com.github.noconnor.junitperf.JUnitPerfTestRequirement;
import com.github.noconnor.junitperf.reporting.providers.HtmlReportGenerator;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Performance test for trace tree construction (enter nested methods + captureTrace). */
@Tag("perf")
@ExtendWith(JUnitPerfInterceptor.class)
class TreeBuildPerfTest {

  private static final MethodSignature OUTER =
      new MethodSignature("OrderService", "placeOrder", List.of());
  private static final MethodSignature INNER =
      new MethodSignature("InventoryService", "reserve", List.of());

  @JUnitPerfTestActiveConfig
  private static final JUnitPerfReportingConfig CONFIG =
      JUnitPerfReportingConfig.builder()
          .reportGenerator(new HtmlReportGenerator("build/reports/perf/core-tree-build.html"))
          .build();

  private final ThreadLocalNarrativeContext context = new ThreadLocalNarrativeContext();

  @Test
  @JUnitPerfTest(threads = 1, durationMs = 2_000, warmUpMs = 500)
  @JUnitPerfTestRequirement(
      percentiles = PerfThresholds.CORE_TREE_BUILD_PERCENTILES,
      executionsPerSec = PerfThresholds.CORE_TREE_BUILD_MIN_OPS,
      allowedErrorPercentage = 0.01f)
  void nestedEnterExitAndCaptureMeetsLatencyBudget() {
    SpanId outer = context.enterMethod(OUTER);
    SpanId inner = context.enterMethod(INNER);
    context.exitMethodWithReturn("\"ok\"", inner);
    context.exitMethodWithReturn("\"order-1\"", outer);
    context.captureTrace();
    context.reset();
  }
}
