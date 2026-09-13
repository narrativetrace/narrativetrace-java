/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 * Captures every narration line a real consumer following "Send it to your logger"
 * (configuration-guide.md §7) would see, with zero application wiring beyond {@code
 * narrativetrace-slf4j} being on the classpath — which every probe here already has, so attaching
 * this appender is the whole probe setup for anything the SLF4J narration text observably carries
 * (redaction, error markers, native-stringification, platform-type carve-out).
 */
final class SlfCapture {

  private SlfCapture() {}

  static ListAppender<ILoggingEvent> attach() {
    Logger logger = (Logger) LoggerFactory.getLogger(PipelineBootstrap.DEFAULT_LOGGER_NAME);
    logger.setLevel(Level.ALL);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  static String allMessages(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .collect(Collectors.joining("\n"));
  }
}
