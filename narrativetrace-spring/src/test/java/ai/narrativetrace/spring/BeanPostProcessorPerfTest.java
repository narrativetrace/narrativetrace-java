/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.perf.PerfThresholds;
import ai.narrativetrace.spring.test.DefaultGreetingService;
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

/** Performance test for NarrativeTraceBeanPostProcessor proxy wrapping. */
@Tag("perf")
@ExtendWith(JUnitPerfInterceptor.class)
class BeanPostProcessorPerfTest {

  @JUnitPerfTestActiveConfig
  private static final JUnitPerfReportingConfig CONFIG =
      JUnitPerfReportingConfig.builder()
          .reportGenerator(new HtmlReportGenerator("build/reports/perf/spring-bpp.html"))
          .build();

  private final NarrativeTraceBeanPostProcessor processor =
      new NarrativeTraceBeanPostProcessor(
          new ThreadLocalNarrativeContext(), List.of("ai.narrativetrace.spring.test"));

  @Test
  @JUnitPerfTest(threads = 1, durationMs = 2_000, warmUpMs = 500)
  @JUnitPerfTestRequirement(
      percentiles = PerfThresholds.SPRING_BPP_PERCENTILES,
      executionsPerSec = PerfThresholds.SPRING_BPP_MIN_OPS,
      allowedErrorPercentage = 0.01f)
  void postProcessMeetsLatencyBudget() {
    processor.postProcessAfterInitialization(new DefaultGreetingService(), "greetingService");
  }
}
