/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

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

/** Performance test for ValueRenderer across common value types. */
@Tag("perf")
@ExtendWith(JUnitPerfInterceptor.class)
class ValueRendererPerfTest {

  @JUnitPerfTestActiveConfig
  private static final JUnitPerfReportingConfig CONFIG =
      JUnitPerfReportingConfig.builder()
          .reportGenerator(new HtmlReportGenerator("build/reports/perf/core-value-renderer.html"))
          .build();

  private final ValueRenderer renderer = new ValueRenderer();

  @Test
  @JUnitPerfTest(threads = 1, durationMs = 2_000, warmUpMs = 500)
  @JUnitPerfTestRequirement(
      percentiles = PerfThresholds.CORE_VALUE_RENDER_PERCENTILES,
      executionsPerSec = PerfThresholds.CORE_VALUE_RENDER_MIN_OPS,
      allowedErrorPercentage = 0.01f)
  void renderMixedTypesMeetsLatencyBudget() {
    renderer.render("hello");
    renderer.render(42);
    renderer.render(List.of("a", "b", "c"));
  }
}
