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
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class Slf4jTraceEventListenerTest {

  private ListAppender<ILoggingEvent> appender;
  private Slf4jTraceEventListener listener;

  @BeforeEach
  void setUp() {
    var logbackLogger = (Logger) LoggerFactory.getLogger("narrativetrace");
    logbackLogger.setLevel(Level.ALL);
    logbackLogger.detachAndStopAllAppenders();
    appender = new ListAppender<>();
    appender.start();
    logbackLogger.addAppender(appender);

    listener = new Slf4jTraceEventListener();
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void enterEventLogsMethodEntry() {
    var signature =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(new ParameterCapture("orderId", "\"42\"", false)));
    var sc = rootSpanContext();
    var event = new TraceEvent.EnterEvent(sc, System.nanoTime(), signature);

    listener.accept(event);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .isEqualTo("→ OrderService.placeOrder(orderId: \"42\")");
  }

  @Test
  void exitOfVoidMethodLogsCompletedInsteadOfReturnedNull() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(sc, System.nanoTime(), new TraceOutcome.Returned(null), null);

    listener.accept(event);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("← completed");
  }

  @Test
  void exitReturnedLogsReturnValue() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(sc, System.nanoTime(), new TraceOutcome.Returned("\"ok\""), null);

    listener.accept(event);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("← returned: \"ok\"");
  }

  @Test
  void exitThrewLogsException() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(
            sc,
            System.nanoTime(),
            new TraceOutcome.Threw(new IllegalStateException("out of stock")),
            null);

    listener.accept(event);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .isEqualTo("!! IllegalStateException: out of stock");
  }

  @Test
  void exitThrewSanitizesControlCharactersInExceptionMessage() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(
            sc,
            System.nanoTime(),
            new TraceOutcome.Threw(new IllegalStateException("boom\nINFO forged")),
            null);

    listener.accept(event);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .isEqualTo("!! IllegalStateException: boom\\nINFO forged")
        .doesNotContain("\n");
  }

  @Test
  void exitThrewWithErrorContextIncludesContext() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(
            sc,
            System.nanoTime(),
            new TraceOutcome.Threw(new RuntimeException("timeout")),
            "placeOrder");

    listener.accept(event);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .isEqualTo("!! RuntimeException: timeout [placeOrder]");
  }

  @Test
  void exitThrewSanitizesControlCharactersInErrorContext() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(
            sc,
            System.nanoTime(),
            new TraceOutcome.Threw(new RuntimeException("timeout")),
            "ctx\nforged");

    listener.accept(event);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .isEqualTo("!! RuntimeException: timeout [ctx\\nforged]")
        .doesNotContain("\n");
  }

  @Test
  void enterEventSetsMdcKeys() {
    var parentSc = rootSpanContext();
    var childSc = childSpanContext(parentSc);
    var signature =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(new ParameterCapture("orderId", "\"42\"", false)));
    var event = new TraceEvent.EnterEvent(childSc, System.nanoTime(), signature);

    listener.accept(event);

    var mdc = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdc).containsEntry("nt.class", "OrderService");
    assertThat(mdc).containsEntry("nt.method", "placeOrder");
    assertThat(mdc).containsEntry("spanId", childSc.spanId().toString());
    assertThat(mdc).containsEntry("parentSpanId", parentSc.spanId().toString());
  }

  @Test
  void enterEventSetsIdentityMdcKeysWhenCaptured() {
    var sc =
        ai.narrativetrace.api.event.SpanContext.builder(
                rootSpanContext().traceId(), rootSpanContext().spanId())
            .resourceIdentity(
                new ai.narrativetrace.api.event.ResourceIdentity("web-1", 4242L, "17.0.10+7"))
            .build();
    var signature =
        new MethodSignature(
            "OrderService", "placeOrder", List.of(), null, null, null, "com.acme.billing");
    var event =
        new TraceEvent.EnterEvent(
            sc,
            System.nanoTime(),
            signature,
            null,
            new ai.narrativetrace.api.event.ThreadInfo("worker-3", 42L, true));

    listener.accept(event);

    var mdc = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdc).containsEntry("nt.package", "com.acme.billing");
    assertThat(mdc).containsEntry("nt.threadVirtual", "true");
    assertThat(mdc).containsEntry("host.name", "web-1");
    assertThat(mdc).containsEntry("process.pid", "4242");
    assertThat(mdc).containsEntry("process.runtime.version", "17.0.10+7");
  }

  @Test
  void identityMdcKeysAbsentWhenNotCaptured() {
    var signature = new MethodSignature("Svc", "run", List.of());
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), signature);

    listener.accept(event);

    var mdc = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdc).doesNotContainKey("nt.package");
    assertThat(mdc).doesNotContainKey("nt.threadVirtual");
    assertThat(mdc).doesNotContainKey("host.name");
    assertThat(mdc).doesNotContainKey("process.pid");
    assertThat(mdc).doesNotContainKey("process.runtime.version");
  }

  @Test
  void identityMdcKeysAreClearedAfterLogging() {
    var sc =
        ai.narrativetrace.api.event.SpanContext.builder(
                rootSpanContext().traceId(), rootSpanContext().spanId())
            .resourceIdentity(
                new ai.narrativetrace.api.event.ResourceIdentity("web-1", 4242L, "17.0.10+7"))
            .build();
    var signature =
        new MethodSignature("Svc", "run", List.of(), null, null, null, "com.acme.billing");
    var event =
        new TraceEvent.EnterEvent(
            sc,
            System.nanoTime(),
            signature,
            null,
            new ai.narrativetrace.api.event.ThreadInfo("worker-3", 42L, false));

    listener.accept(event);

    assertThat(org.slf4j.MDC.get("nt.package")).isNull();
    assertThat(org.slf4j.MDC.get("nt.threadVirtual")).isNull();
    assertThat(org.slf4j.MDC.get("host.name")).isNull();
    assertThat(org.slf4j.MDC.get("process.pid")).isNull();
    assertThat(org.slf4j.MDC.get("process.runtime.version")).isNull();
  }

  @Test
  void enterEventClearsMdcAfterLogging() {
    var sc = rootSpanContext();
    var signature = new MethodSignature("Svc", "run", List.of());
    var event = new TraceEvent.EnterEvent(sc, System.nanoTime(), signature);

    listener.accept(event);

    assertThat(org.slf4j.MDC.get("nt.class")).isNull();
    assertThat(org.slf4j.MDC.get("nt.method")).isNull();
    assertThat(org.slf4j.MDC.get("nt.depth")).isNull();
    assertThat(org.slf4j.MDC.get("traceId")).isNull();
    assertThat(org.slf4j.MDC.get("spanId")).isNull();
    assertThat(org.slf4j.MDC.get("parentSpanId")).isNull();
  }

  @Test
  void exitEventSetsMdcHandle() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(sc, System.nanoTime(), new TraceOutcome.Returned("\"ok\""), null);

    listener.accept(event);

    assertThat(appender.list.get(0).getMDCPropertyMap())
        .containsEntry("spanId", sc.spanId().toString());
    assertThat(org.slf4j.MDC.get("spanId")).isNull();
  }

  @Test
  void enterEventUsesTraceLevel() {
    var sc = rootSpanContext();
    var signature = new MethodSignature("Svc", "run", List.of());
    listener.accept(new TraceEvent.EnterEvent(sc, System.nanoTime(), signature));

    assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.TRACE);
  }

  @Test
  void exceptionEventUsesWarnLevel() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(
            sc, System.nanoTime(), new TraceOutcome.Threw(new RuntimeException("fail")), null);
    listener.accept(event);

    assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
  }

  @Test
  void enterEventSetsMdcDepth() {
    var parentSc = rootSpanContext();
    var childSc = childSpanContext(parentSc);
    listener.accept(enterEvent(parentSc, "Svc", "a"));
    listener.accept(enterEvent(childSc, "Svc", "b"));

    assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("nt.depth", "1");
    assertThat(appender.list.get(1).getMDCPropertyMap()).containsEntry("nt.depth", "2");
  }

  @Test
  void exitDecrementsDepth() {
    var sc1 = rootSpanContext();
    var sc2 = childSpanContext(sc1);
    var sc3 = rootSpanContext();
    listener.accept(enterEvent(sc1, "Svc", "a"));
    listener.accept(enterEvent(sc2, "Svc", "b"));
    listener.accept(exitReturn(sc2, "\"ok\""));
    listener.accept(exitReturn(sc1, "\"done\""));
    listener.accept(enterEvent(sc3, "Svc", "c"));

    assertThat(appender.list.get(4).getMDCPropertyMap()).containsEntry("nt.depth", "1");
  }

  @Test
  void enterEventWithNoParametersLogsEmptyParens() {
    var sc = rootSpanContext();
    var signature = new MethodSignature("Svc", "run", List.of());
    listener.accept(new TraceEvent.EnterEvent(sc, System.nanoTime(), signature));

    assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("→ Svc.run()");
  }

  @Test
  void redactedParameterShowsRedactedMarker() {
    var sc = rootSpanContext();
    var signature =
        new MethodSignature(
            "AuthService",
            "login",
            List.of(
                new ParameterCapture("username", "\"alice\"", false),
                new ParameterCapture("password", "\"secret\"", true)));
    var event = new TraceEvent.EnterEvent(sc, System.nanoTime(), signature);

    listener.accept(event);

    assertThat(appender.list.get(0).getFormattedMessage())
        .isEqualTo("→ AuthService.login(username: \"alice\", password: [REDACTED])");
  }

  @Test
  void customLogLevels() {
    var customListener =
        new Slf4jTraceEventListener(
            Map.of(
                Slf4jTraceEventListener.EventType.ENTRY, org.slf4j.event.Level.DEBUG,
                Slf4jTraceEventListener.EventType.RETURN, org.slf4j.event.Level.INFO,
                Slf4jTraceEventListener.EventType.EXCEPTION, org.slf4j.event.Level.ERROR));
    var sc1 = rootSpanContext();
    var sc2 = rootSpanContext();
    customListener.accept(enterEvent(sc1, "Svc", "a"));
    customListener.accept(exitReturn(sc1, "\"ok\""));
    customListener.accept(
        new TraceEvent.ExitEvent(
            sc2, System.nanoTime(), new TraceOutcome.Threw(new RuntimeException("fail")), null));

    assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.DEBUG);
    assertThat(appender.list.get(1).getLevel()).isEqualTo(Level.INFO);
    assertThat(appender.list.get(2).getLevel()).isEqualTo(Level.ERROR);
  }

  @Test
  void customLoggerNameRoutesToCorrectLogger() {
    var customLogger = (Logger) LoggerFactory.getLogger("custom.trace");
    customLogger.setLevel(Level.ALL);
    customLogger.detachAndStopAllAppenders();
    var customAppender = new ListAppender<ILoggingEvent>();
    customAppender.start();
    customLogger.addAppender(customAppender);

    var customListener = new Slf4jTraceEventListener("custom.trace");
    customListener.accept(enterEvent(rootSpanContext(), "Svc", "run"));

    assertThat(customAppender.list).hasSize(1);
    assertThat(appender.list).isEmpty();
  }

  @Test
  void forkCreatedEventLogsGroupId() {
    listener.accept(new TraceEvent.ForkCreatedEvent("fork-1", System.nanoTime()));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .isEqualTo("⑂ fork group created [groupId: fork-1]");
  }

  @Test
  void mergeEventLogsMemberCount() {
    listener.accept(new TraceEvent.MergeEvent("fork-1", 3, System.nanoTime()));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .isEqualTo("⑃ fork joined [groupId: fork-1, members: 3]");
  }

  @Test
  void fireAndForgetEventLogsGroupId() {
    listener.accept(new TraceEvent.FireAndForgetEvent("ff-1", System.nanoTime()));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .isEqualTo("⤳ fire-and-forget launched [groupId: ff-1]");
  }

  @Test
  void exitClearsMdcSpanKeys() {
    var sc = rootSpanContext();
    listener.accept(exitReturn(sc, "\"ok\""));

    assertThat(org.slf4j.MDC.get("traceId")).isNull();
    assertThat(org.slf4j.MDC.get("spanId")).isNull();
    assertThat(org.slf4j.MDC.get("parentSpanId")).isNull();
  }

  @Test
  void noLegacyHandleMdcKeys() {
    var parentSc = rootSpanContext();
    var childSc = childSpanContext(parentSc);
    listener.accept(enterEvent(childSc, "Svc", "work"));

    var mdc = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdc).doesNotContainKey("nt.handle");
    assertThat(mdc).doesNotContainKey("nt.parent");
  }

  @Test
  void enterSetsTraceIdSpanIdMdc() {
    var parentSc = rootSpanContext();
    var childSc = childSpanContext(parentSc);
    listener.accept(enterEvent(childSc, "Svc", "work"));

    var mdc = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdc).containsEntry("traceId", childSc.traceId().toString());
    assertThat(mdc).containsEntry("spanId", childSc.spanId().toString());
    assertThat(mdc).containsEntry("parentSpanId", parentSc.spanId().toString());
  }

  @Test
  void enterSetsServiceMdcKeys() {
    var sc =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId())
            .serviceName("order-svc")
            .serviceVersion("1.2.3")
            .environment("production")
            .build();
    listener.accept(enterEvent(sc, "Svc", "work"));

    var mdc = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdc).containsEntry("service.name", "order-svc");
    assertThat(mdc).containsEntry("service.version", "1.2.3");
    assertThat(mdc).containsEntry("service.environment", "production");
  }

  @Test
  void enterOmitsServiceMdcKeysWhenNull() {
    var sc = rootSpanContext();
    listener.accept(enterEvent(sc, "Svc", "work"));

    var mdc = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdc).doesNotContainKey("service.name");
    assertThat(mdc).doesNotContainKey("service.version");
    assertThat(mdc).doesNotContainKey("service.environment");
  }

  private static SpanContext rootSpanContext() {
    return SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
  }

  private static SpanContext childSpanContext(SpanContext parent) {
    return SpanContext.builder(parent.traceId(), SpanIdGenerator.spanId())
        .parentSpanId(parent.spanId())
        .build();
  }

  private static TraceEvent.EnterEvent enterEvent(
      SpanContext spanContext, String className, String methodName) {
    return new TraceEvent.EnterEvent(
        spanContext, System.nanoTime(), new MethodSignature(className, methodName, List.of()));
  }

  @Test
  void persistentTraceIdNotOverwrittenByEnter() {
    org.slf4j.MDC.put("traceId", "persistent-trace-id");
    var sc = rootSpanContext();
    listener.accept(enterEvent(sc, "Svc", "work"));

    var mdc = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdc).containsEntry("traceId", "persistent-trace-id");
  }

  @Test
  void persistentTraceIdNotRemovedAfterEnter() {
    org.slf4j.MDC.put("traceId", "persistent-trace-id");
    var sc = rootSpanContext();
    listener.accept(enterEvent(sc, "Svc", "work"));

    assertThat(org.slf4j.MDC.get("traceId")).isEqualTo("persistent-trace-id");
  }

  @Test
  void persistentTraceIdNotRemovedAfterExit() {
    org.slf4j.MDC.put("traceId", "persistent-trace-id");
    var sc = rootSpanContext();
    listener.accept(exitReturn(sc, "\"ok\""));

    assertThat(org.slf4j.MDC.get("traceId")).isEqualTo("persistent-trace-id");
  }

  @Test
  void persistentServiceFieldsNotRemovedAfterEnter() {
    org.slf4j.MDC.put("service.name", "persistent-svc");
    var sc = rootSpanContext();
    listener.accept(enterEvent(sc, "Svc", "work"));

    assertThat(org.slf4j.MDC.get("service.name")).isEqualTo("persistent-svc");
  }

  @Test
  void spanIdAlwaysSetPerMessage() {
    org.slf4j.MDC.put("traceId", "persistent-trace-id");
    var sc = rootSpanContext();
    listener.accept(enterEvent(sc, "Svc", "work"));

    var mdc = appender.list.get(0).getMDCPropertyMap();
    assertThat(mdc).containsEntry("spanId", sc.spanId().toString());
    // spanId is span-level — always cleared after message
    assertThat(org.slf4j.MDC.get("spanId")).isNull();
  }

  @Test
  void exitEventShouldIncludeDepthInMdcLikeDocumented() {
    var sc = rootSpanContext();

    listener.accept(exitReturn(sc, "\"ok\""));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getMDCPropertyMap()).containsKey("nt.depth");
  }

  @Test
  void rootSpanLogShouldNotLeakStaleParentSpanIdFromExistingMdc() {
    MDC.put("parentSpanId", "stale-parent");
    var sc = rootSpanContext();

    listener.accept(enterEvent(sc, "Svc", "run"));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getMDCPropertyMap()).doesNotContainKey("parentSpanId");
  }

  private static TraceEvent.ExitEvent exitReturn(SpanContext spanContext, String value) {
    return new TraceEvent.ExitEvent(
        spanContext, System.nanoTime(), new TraceOutcome.Returned(value), null);
  }
}
