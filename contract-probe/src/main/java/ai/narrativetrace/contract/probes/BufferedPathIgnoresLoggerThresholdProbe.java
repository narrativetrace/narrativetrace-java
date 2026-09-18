/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/**
 * {@code config-shape}: the buffered path never consults the SLF4J logger's threshold. Silences the
 * {@code narrativetrace} logger entirely (level {@code OFF}), traces one call at the default {@code
 * DETAIL} tracing level, then confirms the synchronous path logged nothing while {@code
 * captureTrace()} still returns the full event, parameter value included.
 */
public final class BufferedPathIgnoresLoggerThresholdProbe {

  private BufferedPathIgnoresLoggerThresholdProbe() {}

  interface Greeter {
    String greet(String name);
  }

  public static String observe() {
    Logger logger = (Logger) LoggerFactory.getLogger(PipelineBootstrap.DEFAULT_LOGGER_NAME);
    logger.setLevel(Level.OFF);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    var context = new ThreadLocalNarrativeContext();
    Greeter greeter =
        NarrativeTraceProxy.trace((Greeter) name -> "hello " + name, Greeter.class, context);
    greeter.greet("world");

    boolean syncPathSilenced = appender.list.isEmpty();
    var roots = context.captureTrace().roots();
    boolean bufferedPathCaptured =
        !roots.isEmpty()
            && roots.get(0).signature().parameters().stream()
                .anyMatch(p -> p.renderedValue() != null && p.renderedValue().contains("world"));
    return String.valueOf(syncPathSilenced && bufferedPathCaptured);
  }
}
