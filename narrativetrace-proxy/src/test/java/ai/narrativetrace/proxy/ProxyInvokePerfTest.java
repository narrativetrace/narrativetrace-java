/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.perf.PerfThresholds;
import com.github.noconnor.junitperf.JUnitPerfInterceptor;
import com.github.noconnor.junitperf.JUnitPerfReportingConfig;
import com.github.noconnor.junitperf.JUnitPerfTest;
import com.github.noconnor.junitperf.JUnitPerfTestActiveConfig;
import com.github.noconnor.junitperf.JUnitPerfTestRequirement;
import com.github.noconnor.junitperf.reporting.providers.HtmlReportGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Performance test for NarrativeTraceProxy method invocation overhead. */
@Tag("perf")
@ExtendWith(JUnitPerfInterceptor.class)
class ProxyInvokePerfTest {

  interface GreetingService {
    String greet(String name);
  }

  @JUnitPerfTestActiveConfig
  private static final JUnitPerfReportingConfig CONFIG =
      JUnitPerfReportingConfig.builder()
          .reportGenerator(new HtmlReportGenerator("build/reports/perf/proxy-invoke.html"))
          .build();

  private final ThreadLocalNarrativeContext context = new ThreadLocalNarrativeContext();
  private final GreetingService proxy =
      NarrativeTraceProxy.trace((name) -> "Hello, " + name, GreetingService.class, context);

  @Test
  @JUnitPerfTest(threads = 1, durationMs = 2_000, warmUpMs = 500)
  @JUnitPerfTestRequirement(
      percentiles = PerfThresholds.PROXY_INVOKE_PERCENTILES,
      executionsPerSec = PerfThresholds.PROXY_INVOKE_MIN_OPS,
      allowedErrorPercentage = 0.01f)
  void proxyInvocationMeetsLatencyBudget() {
    proxy.greet("World");
    context.reset();
  }
}
