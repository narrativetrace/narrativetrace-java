/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.export.RequestContext;
import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.core.render.TraceNamer;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class Slf4jTraceExporterTest {

  private ListAppender<ILoggingEvent> appender;
  private Logger logbackLogger;

  @BeforeEach
  void setUp() {
    logbackLogger = (Logger) LoggerFactory.getLogger("narrativetrace.export");
    logbackLogger.setLevel(Level.INFO);
    logbackLogger.detachAndStopAllAppenders();
    appender = new ListAppender<>();
    appender.start();
    logbackLogger.addAppender(appender);
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void implementsTraceExporter() {
    assertThat(new Slf4jTraceExporter()).isInstanceOf(TraceExporter.class);
  }

  @Test
  void logsTraceJsonAtInfo() {
    var tree = singleNodeTree();
    var requestContext = new RequestContext(200, 10L);

    new Slf4jTraceExporter().export(tree, requestContext);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
  }

  @Test
  void logIncludesTraceJson() {
    var tree = singleNodeTree();
    var requestContext = new RequestContext(200, 10L);

    new Slf4jTraceExporter().export(tree, requestContext);

    var message = appender.list.get(0).getFormattedMessage();
    assertThat(message).contains("placeOrder");
    assertThat(message).contains("OrderService");
  }

  @Test
  void logIncludesRequestMetadata() {
    var tree = singleNodeTree();
    var requestContext = new RequestContext(201, 55L);

    new Slf4jTraceExporter().export(tree, requestContext);

    var message = appender.list.get(0).getFormattedMessage();
    assertThat(message).contains("201");
    assertThat(message).contains("55ms");
  }

  @Test
  void skipsRenderingWhenInfoDisabled() {
    logbackLogger.setLevel(Level.WARN);
    var tree = singleNodeTree();
    var requestContext = new RequestContext(200, 10L);

    new Slf4jTraceExporter().export(tree, requestContext);

    assertThat(appender.list).isEmpty();
  }

  @Test
  void customLoggerNameRoutesToNamedLogger() {
    var customLoggerName = "myapp.export";
    var customLogbackLogger = (Logger) LoggerFactory.getLogger(customLoggerName);
    customLogbackLogger.setLevel(Level.INFO);
    customLogbackLogger.detachAndStopAllAppenders();
    var customAppender = new ListAppender<ILoggingEvent>();
    customAppender.start();
    customLogbackLogger.addAppender(customAppender);

    var exporter = new Slf4jTraceExporter(customLoggerName);
    exporter.export(singleNodeTree(), new RequestContext(200, 10L));

    assertThat(customAppender.list).hasSize(1);
    assertThat(customAppender.list.get(0).getLoggerName()).isEqualTo(customLoggerName);
    assertThat(appender.list).isEmpty();
  }

  @Test
  void mdcContainsNtFieldsDuringExport() {
    var sc =
        ai.narrativetrace.api.event.SpanContext.builder(
                ai.narrativetrace.api.event.SpanIdGenerator.traceId(),
                ai.narrativetrace.api.event.SpanIdGenerator.spanId())
            .serviceName("order-svc")
            .storyId("OrderService.placeOrder")
            .chapterId("OrderService.placeOrder")
            .build();
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"order-42\""),
            10_000_000L,
            System.nanoTime(),
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(node));

    new Slf4jTraceExporter().export(tree, new RequestContext(200, 10L));

    var mdcMap = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdcMap).containsEntry("nt.entryType", "chapter");
    assertThat(mdcMap).containsEntry("nt.schemaVersion", "1.0");
    assertThat(mdcMap).containsEntry("nt.storyId", "OrderService.placeOrder");
    assertThat(mdcMap).containsEntry("nt.chapterId", "OrderService.placeOrder");
    assertThat(mdcMap).containsEntry("trace_id", sc.traceId().toString());
    assertThat(mdcMap).containsEntry("nt.traceName", TraceNamer.name(sc.traceId().value()));
  }

  @Test
  void mdcClearedAfterExport() {
    var sc =
        ai.narrativetrace.api.event.SpanContext.builder(
                ai.narrativetrace.api.event.SpanIdGenerator.traceId(),
                ai.narrativetrace.api.event.SpanIdGenerator.spanId())
            .storyId("OrderService.placeOrder")
            .chapterId("OrderService.placeOrder")
            .build();
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"order-42\""),
            10_000_000L,
            System.nanoTime(),
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(node));

    new Slf4jTraceExporter().export(tree, new RequestContext(200, 10L));

    assertThat(org.slf4j.MDC.get("nt.entryType")).isNull();
    assertThat(org.slf4j.MDC.get("nt.storyId")).isNull();
    assertThat(org.slf4j.MDC.get("trace_id")).isNull();
  }

  @Test
  void mdcFieldsAbsentWhenTreeHasNoSpanContext() {
    var tree = singleNodeTree();

    new Slf4jTraceExporter().export(tree, new RequestContext(200, 10L));

    var mdcMap = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdcMap).doesNotContainKey("nt.storyId");
    assertThat(mdcMap).doesNotContainKey("trace_id");
  }

  @Test
  void preexistingMdcFieldsArePreservedAfterExport() {
    MDC.put("nt.entryType", "persistent-entry");
    MDC.put("trace_id", "persistent-trace-id");
    var tree = singleNodeTree();

    new Slf4jTraceExporter().export(tree, new RequestContext(200, 10L));

    assertThat(MDC.get("nt.entryType")).isEqualTo("persistent-entry");
    assertThat(MDC.get("trace_id")).isEqualTo("persistent-trace-id");
  }

  private DefaultTraceTree singleNodeTree() {
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"order-42\""),
            10_000_000L);
    return new DefaultTraceTree(List.of(node));
  }
}
