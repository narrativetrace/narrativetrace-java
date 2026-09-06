/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring.web;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.perf.PerfThresholds;
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import ai.narrativetrace.spring.EnableNarrativeTrace;
import com.github.noconnor.junitperf.JUnitPerfInterceptor;
import com.github.noconnor.junitperf.JUnitPerfReportingConfig;
import com.github.noconnor.junitperf.JUnitPerfTest;
import com.github.noconnor.junitperf.JUnitPerfTestActiveConfig;
import com.github.noconnor.junitperf.JUnitPerfTestRequirement;
import com.github.noconnor.junitperf.reporting.providers.HtmlReportGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Performance test for the Spring Web filter integration (end-to-end request lifecycle). */
@Tag("perf")
@ExtendWith(JUnitPerfInterceptor.class)
class WebFilterPerfTest {

  @Configuration
  @EnableNarrativeTrace
  static class TestConfig {}

  @JUnitPerfTestActiveConfig
  private static final JUnitPerfReportingConfig CONFIG =
      JUnitPerfReportingConfig.builder()
          .reportGenerator(new HtmlReportGenerator("build/reports/perf/spring-web-filter.html"))
          .build();

  private final AnnotationConfigApplicationContext appCtx =
      new AnnotationConfigApplicationContext(
          TestConfig.class, NarrativeTraceWebConfiguration.class);
  private final NarrativeTraceFilter filter = appCtx.getBean(NarrativeTraceFilter.class);
  private final NarrativeContext context = appCtx.getBean(NarrativeContext.class);

  @Test
  @JUnitPerfTest(threads = 1, durationMs = 2_000, warmUpMs = 500)
  @JUnitPerfTestRequirement(
      percentiles = PerfThresholds.SPRING_WEB_FILTER_PERCENTILES,
      executionsPerSec = PerfThresholds.SPRING_WEB_FILTER_MIN_OPS,
      allowedErrorPercentage = 0.01f)
  void springWebFilterMeetsLatencyBudget() throws Exception {
    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/test"),
        new MockHttpServletResponse(),
        (req, res) -> {});
  }
}
