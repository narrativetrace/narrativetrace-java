/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ServiceIdentity;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class NarrativeTraceContextFactoryTest {

  @Test
  void contextFactoryCreatesContextWithServiceIdentity() {
    var identity = new ServiceIdentity("my-svc", "2.0.0", "prod");
    var context = NarrativeTraceContextFactory.createContext("", identity);
    context.enterMethod(new MethodSignature("Svc", "run", List.of()));
    context.exitMethodWithReturn("\"ok\"");
    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.spanContext().serviceName()).isEqualTo("my-svc");
    assertThat(root.spanContext().serviceVersion()).isEqualTo("2.0.0");
    assertThat(root.spanContext().environment()).isEqualTo("prod");
  }

  @Test
  void factoryWithLoggerNameProducesSlf4jLogs() {
    var logbackLogger = (Logger) LoggerFactory.getLogger("factory-test");
    logbackLogger.setLevel(Level.ALL);
    logbackLogger.detachAndStopAllAppenders();
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logbackLogger.addAppender(appender);

    var context = NarrativeTraceContextFactory.createContext("factory-test");
    context.enterMethod(new MethodSignature("Svc", "run", List.of()));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("Svc.run");
  }
}
