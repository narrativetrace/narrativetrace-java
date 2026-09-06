/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.export.RequestContextProvider;
import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import ai.narrativetrace.servlet.Slf4jTraceExporter;
import ai.narrativetrace.spring.EnableNarrativeTrace;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class NarrativeTraceWebConfigurationTest {

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    var logbackLogger = (Logger) LoggerFactory.getLogger("narrativetrace.export");
    logbackLogger.setLevel(Level.INFO);
    logbackLogger.detachAndStopAllAppenders();
    appender = new ListAppender<>();
    appender.start();
    logbackLogger.addAppender(appender);
  }

  @Configuration
  @EnableNarrativeTrace
  static class TestConfig {}

  @Test
  void configProvidesFilterBean() {
    try (var ctx =
        new AnnotationConfigApplicationContext(
            TestConfig.class, NarrativeTraceWebConfiguration.class)) {
      assertThat(ctx.getBean(NarrativeTraceFilter.class)).isNotNull();
    }
  }

  @Test
  void defaultExporterIsSlf4j() throws Exception {
    try (var ctx =
        new AnnotationConfigApplicationContext(
            TestConfig.class, NarrativeTraceWebConfiguration.class)) {
      var filter = ctx.getBean(NarrativeTraceFilter.class);
      var narrativeContext = ctx.getBean(NarrativeContext.class);

      narrativeContext.reset();
      filter.doFilter(
          new MockHttpServletRequest("GET", "/api/test"),
          new MockHttpServletResponse(),
          (req, res) -> {
            narrativeContext.enterMethod(new MethodSignature("Svc", "handle", List.of()));
            narrativeContext.exitMethodWithReturn("\"ok\"");
          });

      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }
  }

  @Configuration
  @EnableNarrativeTrace
  static class CustomExporterConfig {
    static final List<String> EXPORTED = new ArrayList<>();

    @Bean
    public TraceExporter customExporter() {
      return (tree, reqCtx) -> EXPORTED.add(String.valueOf(reqCtx.statusCode()));
    }
  }

  @Test
  void customExporterReplacesDefault() throws Exception {
    CustomExporterConfig.EXPORTED.clear();
    try (var ctx =
        new AnnotationConfigApplicationContext(
            CustomExporterConfig.class, NarrativeTraceWebConfiguration.class)) {
      var filter = ctx.getBean(NarrativeTraceFilter.class);
      var narrativeContext = ctx.getBean(NarrativeContext.class);

      narrativeContext.reset();
      filter.doFilter(
          new MockHttpServletRequest("GET", "/custom"),
          new MockHttpServletResponse(),
          (req, res) -> {
            narrativeContext.enterMethod(new MethodSignature("Svc", "handle", List.of()));
            narrativeContext.exitMethodWithReturn("\"ok\"");
          });

      assertThat(CustomExporterConfig.EXPORTED).containsExactly("200");
      assertThat(appender.list).isEmpty();
    }
  }

  @Configuration
  @EnableNarrativeTrace(loggerName = "myapp.traces")
  static class CustomLoggerConfig {}

  @Test
  void defaultExporterDerivesLoggerNameFromContext() throws Exception {
    var customExportLogger = (Logger) LoggerFactory.getLogger("myapp.traces.export");
    customExportLogger.setLevel(Level.INFO);
    customExportLogger.detachAndStopAllAppenders();
    var customAppender = new ListAppender<ILoggingEvent>();
    customAppender.start();
    customExportLogger.addAppender(customAppender);

    try (var ctx =
        new AnnotationConfigApplicationContext(
            CustomLoggerConfig.class, NarrativeTraceWebConfiguration.class)) {
      var filter = ctx.getBean(NarrativeTraceFilter.class);
      var narrativeContext = ctx.getBean(NarrativeContext.class);

      narrativeContext.reset();
      filter.doFilter(
          new MockHttpServletRequest("GET", "/api/test"),
          new MockHttpServletResponse(),
          (req, res) -> {
            narrativeContext.enterMethod(new MethodSignature("Svc", "handle", List.of()));
            narrativeContext.exitMethodWithReturn("\"ok\"");
          });

      assertThat(customAppender.list).hasSize(1);
      assertThat(customAppender.list.get(0).getLoggerName()).isEqualTo("myapp.traces.export");
      // Default export logger should NOT receive this event
      assertThat(appender.list).isEmpty();
    }
  }

  @Test
  void defaultExporterUsesLoggerNameWhenProvided() {
    Slf4jTraceExporter exporter = NarrativeTraceWebConfiguration.defaultExporter("myapp.traces");
    assertThat(exporter).isNotNull();
  }

  @Test
  void defaultExporterFallsBackForEmptyLoggerName() {
    Slf4jTraceExporter exporter = NarrativeTraceWebConfiguration.defaultExporter("");
    assertThat(exporter).isNotNull();
  }

  @Configuration
  @EnableNarrativeTrace
  static class ProviderConfig {
    static final List<String> EXPORTED_JSON = new ArrayList<>();

    @Bean
    public RequestContextProvider<HttpServletRequest> requestContextProvider() {
      return request ->
          new RequestContextProvider.UserContext("user-42", "session-abc", "tenant-xyz");
    }

    @Bean
    public TraceExporter customExporter() {
      return (tree, reqCtx) ->
          EXPORTED_JSON.add(
              tree.roots().get(0).spanContext().enduserId() != null
                  ? tree.roots().get(0).spanContext().enduserId().toString()
                  : null);
    }
  }

  @Test
  void springWebInjectsProvider() throws Exception {
    ProviderConfig.EXPORTED_JSON.clear();
    try (var ctx =
        new AnnotationConfigApplicationContext(
            ProviderConfig.class, NarrativeTraceWebConfiguration.class)) {
      var filter = ctx.getBean(NarrativeTraceFilter.class);
      var narrativeContext = ctx.getBean(NarrativeContext.class);

      narrativeContext.reset();
      filter.doFilter(
          new MockHttpServletRequest("GET", "/api/test"),
          new MockHttpServletResponse(),
          (req, res) -> {
            narrativeContext.enterMethod(new MethodSignature("Svc", "handle", List.of()));
            narrativeContext.exitMethodWithReturn("\"ok\"");
          });

      assertThat(ProviderConfig.EXPORTED_JSON).containsExactly("user-42");
    }
  }

  @Configuration
  @EnableNarrativeTrace
  static class MultipleExportersConfig {
    @Bean
    TraceExporter firstExporter() {
      return (tree, requestContext) -> {};
    }

    @Bean
    TraceExporter secondExporter() {
      return (tree, requestContext) -> {};
    }
  }

  @Test
  void multipleExporterBeansShouldNotPreventContextStartup() {
    assertThatCode(
            () -> {
              try (var ctx =
                  new AnnotationConfigApplicationContext(
                      MultipleExportersConfig.class, NarrativeTraceWebConfiguration.class)) {
                ctx.getBean(NarrativeTraceFilter.class);
              }
            })
        .doesNotThrowAnyException();
  }

  @Test
  void fullLifecycleIntegration() throws Exception {
    try (var ctx =
        new AnnotationConfigApplicationContext(
            TestConfig.class, NarrativeTraceWebConfiguration.class)) {
      var filter = ctx.getBean(NarrativeTraceFilter.class);
      var narrativeContext = ctx.getBean(NarrativeContext.class);

      narrativeContext.reset();
      var request = new MockHttpServletRequest("POST", "/api/orders");
      var response = new MockHttpServletResponse();
      response.setStatus(201);

      filter.doFilter(
          request,
          response,
          (req, res) -> {
            narrativeContext.enterMethod(
                new MethodSignature("OrderService", "placeOrder", List.of()));
            narrativeContext.exitMethodWithReturn("\"order-42\"");
          });

      assertThat(narrativeContext.captureTrace().isEmpty()).isTrue();
      assertThat(appender.list).hasSize(1);
      var message = appender.list.get(0).getFormattedMessage();
      assertThat(message).contains("POST");
      assertThat(message).contains("/api/orders");
      assertThat(message).contains("201");
      assertThat(message).contains("placeOrder");
    }
  }
}
