/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotationMetadata;

class EnableNarrativeTraceTest {

  @Configuration
  @EnableNarrativeTrace(basePackages = "ai.narrativetrace.spring.test")
  static class TestConfig {}

  @Test
  void enableNarrativeTraceRegistersContextBean() {
    try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
      assertThat(ctx.getBean(NarrativeContext.class)).isNotNull();
    }
  }

  @Test
  void enableNarrativeTraceRegistersBeanPostProcessor() {
    try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
      assertThat(ctx.getBean(NarrativeTraceBeanPostProcessor.class)).isNotNull();
    }
  }

  @Test
  void autoWrapsWithSlf4jPipelineWhenOnClasspath() {
    var logbackLogger = (Logger) LoggerFactory.getLogger("narrativetrace");
    logbackLogger.setLevel(Level.ALL);
    logbackLogger.detachAndStopAllAppenders();
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logbackLogger.addAppender(appender);

    try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
      var context = ctx.getBean(NarrativeContext.class);
      context.enterMethod(new MethodSignature("Svc", "run", List.of()));
      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.get(0).getFormattedMessage()).contains("Svc.run");
    }
  }

  @Configuration
  @EnableNarrativeTrace(basePackages = "ai.narrativetrace.spring.test", loggerName = "")
  static class NoSlf4jConfig {}

  @Test
  void emptyLoggerNameDisablesSlf4jLogging() {
    var logbackLogger = (Logger) LoggerFactory.getLogger("narrativetrace");
    logbackLogger.setLevel(Level.ALL);
    logbackLogger.detachAndStopAllAppenders();
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logbackLogger.addAppender(appender);

    try (var ctx = new AnnotationConfigApplicationContext(NoSlf4jConfig.class)) {
      var context = ctx.getBean(NarrativeContext.class);
      context.enterMethod(new MethodSignature("Svc", "run", List.of()));
      assertThat(appender.list).isEmpty();
    }
  }

  @Test
  void registrarHandlesClassWithoutAnnotation() {
    var registrar = new NarrativeTraceRegistrar();
    var registry = new DefaultListableBeanFactory();

    assertThatNoException()
        .isThrownBy(
            () ->
                registrar.registerBeanDefinitions(
                    AnnotationMetadata.introspect(Object.class), registry));
  }

  @Configuration
  @EnableNarrativeTrace
  static class CustomContextConfig {
    @Bean
    NarrativeContext narrativeContext() {
      return new ThreadLocalNarrativeContext();
    }
  }

  @Test
  void userDefinedContextBeanOverridesAutoCreated() {
    try (var ctx = new AnnotationConfigApplicationContext(CustomContextConfig.class)) {
      var context = ctx.getBean(NarrativeContext.class);
      assertThat(context).isInstanceOf(ThreadLocalNarrativeContext.class);
    }
  }

  @Test
  void registrarExposesLoggerNameBean() {
    try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
      var loggerName = ctx.getBean("narrativeTraceLoggerName", String.class);
      assertThat(loggerName).isEqualTo("narrativetrace");
    }
  }

  @Configuration
  @EnableNarrativeTrace(
      serviceName = "order-service",
      serviceVersion = "1.2.3",
      environment = "staging")
  static class ServiceIdentityConfig {}

  @Test
  void registrarPassesServiceIdentityToFactory() {
    try (var ctx = new AnnotationConfigApplicationContext(ServiceIdentityConfig.class)) {
      var context = ctx.getBean(NarrativeContext.class);
      context.enterMethod(new MethodSignature("Svc", "run", List.of()));
      context.exitMethodWithReturn("\"ok\"");
      var tree = context.captureTrace();
      var root = tree.roots().get(0);
      assertThat(root.spanContext().serviceName()).isEqualTo("order-service");
      assertThat(root.spanContext().serviceVersion()).isEqualTo("1.2.3");
      assertThat(root.spanContext().environment()).isEqualTo("staging");
    }
  }

  @Test
  void enableNarrativeTraceServiceIdentityAttrs() {
    var ann = ServiceIdentityConfig.class.getAnnotation(EnableNarrativeTrace.class);
    assertThat(ann.serviceName()).isEqualTo("order-service");
    assertThat(ann.serviceVersion()).isEqualTo("1.2.3");
    assertThat(ann.environment()).isEqualTo("staging");
  }

  @Configuration
  @EnableNarrativeTrace(loggerName = "custom.logger")
  static class CustomLoggerNameConfig {}

  @Test
  void registrarExposesCustomLoggerNameBean() {
    try (var ctx = new AnnotationConfigApplicationContext(CustomLoggerNameConfig.class)) {
      var loggerName = ctx.getBean("narrativeTraceLoggerName", String.class);
      assertThat(loggerName).isEqualTo("custom.logger");
    }
  }
}
