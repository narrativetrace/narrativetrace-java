/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.slf4j;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PipelineSlf4jIntegrationTest {

  private ListAppender<ILoggingEvent> appender;
  private DualPathPipeline pipeline;

  @BeforeEach
  void setUp() {
    var logbackLogger = (Logger) LoggerFactory.getLogger("narrativetrace");
    logbackLogger.setLevel(Level.ALL);
    logbackLogger.detachAndStopAllAppenders();
    appender = new ListAppender<>();
    appender.start();
    logbackLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    if (pipeline != null) {
      pipeline.close();
    }
  }

  @Test
  void enterMethodProducesSl4jLogViaPipeline() {
    pipeline = new DualPathPipeline(new Slf4jTraceEventListener());
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);

    context.enterMethod(
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(new ParameterCapture("orderId", "\"42\"", false))));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .isEqualTo("→ OrderService.placeOrder(orderId: \"42\")");
  }

  @Test
  void fullMethodCallProducesEnterAndExitLogs() {
    pipeline = new DualPathPipeline(new Slf4jTraceEventListener());
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);

    context.enterMethod(new MethodSignature("Svc", "run", List.of()));
    context.exitMethodWithReturn("\"done\"");

    assertThat(appender.list).hasSize(2);
    assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("→ Svc.run()");
    assertThat(appender.list.get(1).getFormattedMessage()).isEqualTo("← returned: \"done\"");
  }

  @Test
  void exceptionExitProducesWarnLog() {
    pipeline = new DualPathPipeline(new Slf4jTraceEventListener());
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);

    context.enterMethod(new MethodSignature("Svc", "run", List.of()));
    context.exitMethodWithException(new IllegalStateException("broken"), null);

    assertThat(appender.list).hasSize(2);
    assertThat(appender.list.get(1).getFormattedMessage())
        .isEqualTo("!! IllegalStateException: broken");
    assertThat(appender.list.get(1).getLevel()).isEqualTo(Level.WARN);
  }

  @Test
  void pipelineAndTraceCaptureBothWork() {
    pipeline = new DualPathPipeline(new Slf4jTraceEventListener());
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);

    context.enterMethod(new MethodSignature("Svc", "run", List.of()));
    context.exitMethodWithReturn("\"ok\"");

    assertThat(appender.list).hasSize(2);
    var trace = context.captureTrace();
    assertThat(trace.roots()).hasSize(1);
    assertThat(trace.roots().get(0).signature().methodName()).isEqualTo("run");
  }
}
