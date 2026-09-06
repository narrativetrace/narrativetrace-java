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
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.EventPipeline;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Proves the composition root's owner-stated acceptance criterion: with the slf4j module on the
 * classpath, narration is the assumed default rather than something each integration wires by hand.
 *
 * <p>This lives here, not in core, because core carries no slf4j dependency — the reflective lookup
 * inside the bootstrap can only ever fail there, so only this module can prove it succeeds.
 */
class PipelineBootstrapNarrationTest {

  private EventPipeline pipeline;

  @AfterEach
  void cleanUp() {
    if (pipeline != null) {
      pipeline.close();
    }
    System.clearProperty("narrativetrace.narration");
  }

  @Test
  void composesTheSlf4jListenerWhenTheModuleIsOnTheClasspath() {
    var appender = attachAppenderTo(PipelineBootstrap.DEFAULT_LOGGER_NAME);
    pipeline = PipelineBootstrap.createDefault();

    pipeline.publish(enterEvent());

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("Svc.run");
  }

  @Test
  void narrationVetoLeavesTheDefaultTopologySilentButStillCapturing() {
    System.setProperty("narrativetrace.narration", "off");
    var appender = attachAppenderTo(PipelineBootstrap.DEFAULT_LOGGER_NAME);
    pipeline = PipelineBootstrap.createDefault();

    pipeline.publish(enterEvent());
    pipeline.flush();

    assertThat(appender.list).isEmpty();
    assertThat(pipeline.events()).hasSize(1);
  }

  @Test
  void narrationSurvivesAndCaptureStillWorksThroughADefaultContext() {
    var appender = attachAppenderTo(PipelineBootstrap.DEFAULT_LOGGER_NAME);
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig());

    context.enterMethod(new MethodSignature("Svc", "run", List.of()));
    context.exitMethodWithReturn("\"ok\"");

    assertThat(appender.list).isNotEmpty();
    assertThat(context.captureTrace().roots()).hasSize(1);
  }

  private static ListAppender<ILoggingEvent> attachAppenderTo(String loggerName) {
    var logger = (Logger) LoggerFactory.getLogger(loggerName);
    logger.setLevel(Level.ALL);
    logger.detachAndStopAllAppenders();
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private static TraceEvent.EnterEvent enterEvent() {
    var spanContext =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    return new TraceEvent.EnterEvent(
        spanContext, System.nanoTime(), new MethodSignature("Svc", "run", List.of()));
  }
}
