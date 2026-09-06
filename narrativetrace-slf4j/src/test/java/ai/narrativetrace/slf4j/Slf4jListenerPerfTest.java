/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.slf4j;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
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

/** Performance test for Slf4jTraceEventListener enter/exit event processing. */
@Tag("perf")
@ExtendWith(JUnitPerfInterceptor.class)
class Slf4jListenerPerfTest {

  private static final MethodSignature SIG = new MethodSignature("Service", "process", List.of());
  private static final SpanContext SC =
      SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();

  private static final TraceEvent ENTER = new TraceEvent.EnterEvent(SC, System.nanoTime(), SIG);
  private static final TraceEvent EXIT =
      new TraceEvent.ExitEvent(SC, System.nanoTime(), new TraceOutcome.Returned("\"ok\""), null);

  @JUnitPerfTestActiveConfig
  private static final JUnitPerfReportingConfig CONFIG =
      JUnitPerfReportingConfig.builder()
          .reportGenerator(new HtmlReportGenerator("build/reports/perf/slf4j-listener.html"))
          .build();

  private final Slf4jTraceEventListener listener = new Slf4jTraceEventListener();

  @Test
  @JUnitPerfTest(threads = 1, durationMs = 2_000, warmUpMs = 500)
  @JUnitPerfTestRequirement(
      percentiles = PerfThresholds.SLF4J_EVENT_LISTENER_PERCENTILES,
      executionsPerSec = PerfThresholds.SLF4J_EVENT_LISTENER_MIN_OPS,
      allowedErrorPercentage = 0.01f)
  void acceptEnterExitMeetsLatencyBudget() {
    listener.accept(ENTER);
    listener.accept(EXIT);
  }
}
