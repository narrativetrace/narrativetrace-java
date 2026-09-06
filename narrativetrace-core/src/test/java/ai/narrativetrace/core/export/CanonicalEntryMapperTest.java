/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.render.TraceNamer;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalEntryMapperTest {

  private static final TraceId TRACE_ID = TraceId.of("0123456789abcdef0123456789abcdef");
  private static final SpanId SPAN_ID = SpanId.of("0123456789abcdef");
  private static final SpanId PARENT_SPAN_ID = SpanId.of("fedcba9876543210");

  private static SpanContext rootSpanContext() {
    return SpanContext.builder(TRACE_ID, SPAN_ID)
        .serviceName("order-service")
        .environment("test")
        .storyId("OrderService.placeOrder")
        .chapterId("OrderService.placeOrder")
        .build();
  }

  private static SpanContext childSpanContext() {
    return SpanContext.builder(TRACE_ID, SPAN_ID)
        .parentSpanId(PARENT_SPAN_ID)
        .serviceName("order-service")
        .storyId("OrderService.placeOrder")
        .chapterId("OrderService.placeOrder")
        .build();
  }

  // ── EnterEvent ───────────────────────────────────────────────────────────────

  @Test
  void enterEventSetsSchemaFields() {
    var entry = mapEnterEvent();

    assertThat(entry.ntEntryType()).isEqualTo("entry");
    assertThat(entry.ntEventType()).isEqualTo("method_enter");
    assertThat(entry.ntSchemaVersion()).isEqualTo("1.2");
    assertThat(entry.level()).isEqualTo("trace");
  }

  @Test
  void enterEventSetsCodeFields() {
    var entry = mapEnterEvent();

    assertThat(entry.codeNamespace()).isEqualTo("OrderService");
    assertThat(entry.codeFunction()).isEqualTo("placeOrder");
    assertThat(entry.traceId()).isEqualTo(TRACE_ID.toString());
    assertThat(entry.spanId()).isEqualTo(SPAN_ID.toString());
    assertThat(entry.parentSpanId()).isNull();
  }

  @Test
  void enterEventSetsContextFields() {
    var entry = mapEnterEvent();

    assertThat(entry.service()).isEqualTo("order-service");
    assertThat(entry.environment()).isEqualTo("test");
    assertThat(entry.ntStoryId()).isEqualTo("OrderService.placeOrder");
    assertThat(entry.ntChapterId()).isEqualTo("OrderService.placeOrder");
    assertThat(entry.ntTraceName()).isEqualTo(TraceNamer.name(TRACE_ID.value()));
  }

  @Test
  void enterEventSetsParameters() {
    var entry = mapEnterEvent();

    assertThat(entry.ntParameters()).hasSize(1);
    assertThat(entry.ntParameters().get(0).name()).isEqualTo("customerId");
    assertThat(entry.ntParameters().get(0).value()).isEqualTo("\"C-123\"");
    assertThat(entry.ntParameters().get(0).redacted()).isFalse();
  }

  @Test
  void enterEventOmitsExitFields() {
    var entry = mapEnterEvent();

    assertThat(entry.ntOutcome()).isNull();
    assertThat(entry.ntReturnValue()).isNull();
    assertThat(entry.durationMs()).isNull();
    assertThat(entry.exceptionType()).isNull();
    assertThat(entry.exceptionMessage()).isNull();
  }

  private static CanonicalEntry mapEnterEvent() {
    var sig =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(new ParameterCapture("customerId", "\"C-123\"", false)));
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);
    return CanonicalEntryMapper.fromEvent(event);
  }

  @Test
  void enterEventCarriesTheRawNarrationTemplate() {
    var sig =
        new MethodSignature(
            "OverdraftService",
            "openAccount",
            List.of(new ParameterCapture("customerId", "\"C-123\"", false)),
            "Opening for C-123",
            null,
            "Opening for {customerId}");
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntNarrationTemplate()).isEqualTo("Opening for {customerId}");
  }

  @Test
  void enterEventWithoutAnnotationHasNullNarrationTemplate() {
    assertThat(mapEnterEvent().ntNarrationTemplate()).isNull();
  }

  @Test
  void enterEventWithRedactedParameter() {
    var sig =
        new MethodSignature(
            "AuthService",
            "login",
            List.of(
                new ParameterCapture("username", "\"admin\"", false),
                new ParameterCapture("password", "[REDACTED]", true)));
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntParameters()).hasSize(2);
    assertThat(entry.ntParameters().get(1).name()).isEqualTo("password");
    assertThat(entry.ntParameters().get(1).value()).isEqualTo("[REDACTED]");
    assertThat(entry.ntParameters().get(1).redacted()).isTrue();
  }

  @Test
  void enterEventWithNoParameters() {
    var sig = new MethodSignature("HealthCheck", "ping", List.of());
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntParameters()).isNull();
  }

  @Test
  void enterEventWithChildSpanContext() {
    var sig = new MethodSignature("InventoryService", "reserve", List.of());
    var event = new TraceEvent.EnterEvent(childSpanContext(), System.nanoTime(), sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.parentSpanId()).isEqualTo(PARENT_SPAN_ID.toString());
  }

  @Test
  void enterEventMessageFormat() {
    var sig =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(new ParameterCapture("customerId", "\"C-123\"", false)));
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.message()).isEqualTo("\u2192 OrderService.placeOrder(customerId: \"C-123\")");
  }

  // ── ExitEvent ────────────────────────────────────────────────────────────────

  @Test
  void exitEventReturnedMapsToSuccess() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(sc, System.nanoTime(), new TraceOutcome.Returned("42"), null);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntEventType()).isEqualTo("method_exit");
    assertThat(entry.level()).isEqualTo("trace");
    assertThat(entry.ntOutcome()).isEqualTo("success");
    assertThat(entry.ntReturnValue()).isEqualTo("42");
    assertThat(entry.exceptionType()).isNull();
    assertThat(entry.exceptionMessage()).isNull();
    assertThat(entry.ntEntryType()).isEqualTo("entry");
    assertThat(entry.ntSchemaVersion()).isEqualTo("1.2");
  }

  @Test
  void exitEventThrewMapsToFailure() {
    var sc = rootSpanContext();
    var ex = new IllegalArgumentException("bad input");
    var event = new TraceEvent.ExitEvent(sc, System.nanoTime(), new TraceOutcome.Threw(ex), null);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntEventType()).isEqualTo("method_exit");
    assertThat(entry.level()).isEqualTo("error");
    assertThat(entry.ntOutcome()).isEqualTo("failure");
    assertThat(entry.exceptionType()).isEqualTo("IllegalArgumentException");
    assertThat(entry.exceptionMessage()).isEqualTo("bad input");
    assertThat(entry.ntReturnValue()).isNull();
  }

  @Test
  void exitEventIncompleteMapsToIncomplete() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(sc, System.nanoTime(), new TraceOutcome.Incomplete(), null);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntEventType()).isEqualTo("method_exit");
    assertThat(entry.level()).isEqualTo("trace");
    assertThat(entry.ntOutcome()).isEqualTo("incomplete");
  }

  @Test
  void exitEventPreservesSpanFields() {
    var sc = rootSpanContext();
    var event =
        new TraceEvent.ExitEvent(sc, System.nanoTime(), new TraceOutcome.Returned(null), null);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.traceId()).isEqualTo(TRACE_ID.toString());
    assertThat(entry.spanId()).isEqualTo(SPAN_ID.toString());
    assertThat(entry.service()).isEqualTo("order-service");
    assertThat(entry.ntStoryId()).isEqualTo("OrderService.placeOrder");
    assertThat(entry.ntTraceName()).isEqualTo(TraceNamer.name(TRACE_ID.value()));
  }

  // ── ForkCreatedEvent ─────────────────────────────────────────────────────────

  @Test
  void forkEventMapsToFork() {
    var event = new TraceEvent.ForkCreatedEvent("group-1", System.nanoTime());

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntEventType()).isEqualTo("fork");
    assertThat(entry.ntEntryType()).isEqualTo("entry");
    assertThat(entry.ntSchemaVersion()).isEqualTo("1.2");
    assertThat(entry.ntForkId()).isEqualTo("group-1");
    assertThat(entry.level()).isEqualTo("trace");
    assertThat(entry.traceId()).isNull();
    assertThat(entry.spanId()).isNull();
    assertThat(entry.service()).isEqualTo(CanonicalEntryMapper.UNKNOWN_SERVICE);
  }

  // ── MergeEvent ───────────────────────────────────────────────────────────────

  @Test
  void mergeEventMapsToJoin() {
    var event = new TraceEvent.MergeEvent("group-1", 3, System.nanoTime());

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntEventType()).isEqualTo("join");
    assertThat(entry.ntForkId()).isEqualTo("group-1");
    assertThat(entry.ntEntryType()).isEqualTo("entry");
  }

  // ── FireAndForgetEvent ───────────────────────────────────────────────────────

  @Test
  void fireAndForgetEventMapsToAsyncDispatch() {
    var event = new TraceEvent.FireAndForgetEvent("async-1", System.nanoTime());

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntEventType()).isEqualTo("async_dispatch");
    assertThat(entry.ntForkId()).isEqualTo("async-1");
    assertThat(entry.ntEntryType()).isEqualTo("entry");
    assertThat(entry.ntSchemaVersion()).isEqualTo("1.2");
  }

  // ── Direct method calls ───────────────────────────────────────────────────────

  @Test
  void fromEnterEventDirectCall() {
    var sig = new MethodSignature("Svc", "m", List.of());
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);
    var entry = CanonicalEntryMapper.fromEnterEvent(event);
    assertThat(entry.ntEventType()).isEqualTo("method_enter");
  }

  @Test
  void exitEventWithSignatureUsesRealIdentityNotSpanNameParsing() {
    var sc =
        SpanContext.builder(TRACE_ID, SPAN_ID)
            .spanName("com.acme.billing.OrderService.placeOrder")
            .build();
    var signature =
        new ai.narrativetrace.api.event.MethodSignature(
            "OrderService", "placeOrder", java.util.List.of());
    var event =
        new TraceEvent.ExitEvent(
            sc, System.nanoTime(), new TraceOutcome.Returned("42"), null, signature);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.codeNamespace()).isEqualTo("OrderService");
    assertThat(entry.codeFunction()).isEqualTo("placeOrder");
  }

  // ── Type identity (schema 1.2) ───────────────────────────────────────────────

  @Test
  void enterEventCarriesDeclaredParameterAndReturnTypes() {
    var sig =
        new MethodSignature(
            "OrderService",
            "find",
            List.of(new ParameterCapture("id", "\"X\"", false, null, "java.lang.String")),
            null,
            null,
            null,
            "com.acme",
            "java.lang.String",
            null);
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntReturnType()).isEqualTo("java.lang.String");
    assertThat(entry.ntParameters().get(0).type()).isEqualTo("java.lang.String");
  }

  @Test
  void enterEventWithoutTypesLeavesTypeFieldsNull() {
    var entry = mapEnterEvent();

    assertThat(entry.ntReturnType()).isNull();
    assertThat(entry.ntParameters().get(0).type()).isNull();
  }

  // ── Source location (schema 1.2) ─────────────────────────────────────────────

  @Test
  void enterEventCarriesSourceLocationFromTheSignature() {
    var sig =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(),
            null,
            null,
            null,
            "com.acme",
            null,
            null,
            new ai.narrativetrace.api.event.SourceLocation("OrderService.java", 42));
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.codeFilepath()).isEqualTo("OrderService.java");
    assertThat(entry.codeLineno()).isEqualTo(42);
  }

  @Test
  void enterEventWithoutSourceLocationLeavesFieldsNull() {
    var entry = mapEnterEvent();

    assertThat(entry.codeFilepath()).isNull();
    assertThat(entry.codeLineno()).isNull();
  }

  // ── Instance identity (schema 1.2) ───────────────────────────────────────────

  @Test
  void enterEventCarriesInstanceIdFromTheSignature() {
    var sig =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(),
            null,
            null,
            null,
            "com.acme",
            null,
            "1a2b3c4d");
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntInstanceId()).isEqualTo("1a2b3c4d");
  }

  @Test
  void enterEventWithoutInstanceIdLeavesFieldNull() {
    assertThat(mapEnterEvent().ntInstanceId()).isNull();
  }

  // ── Resource identity (schema 1.2) ───────────────────────────────────────────

  @Test
  void entriesCarryResourceIdentityFromTheSpanContext() {
    var sc =
        SpanContext.builder(TRACE_ID, SPAN_ID)
            .resourceIdentity(
                new ai.narrativetrace.api.event.ResourceIdentity("web-1", 4242L, "17.0.10+7"))
            .build();
    var event =
        new TraceEvent.EnterEvent(
            sc, System.nanoTime(), new MethodSignature("Svc", "m", List.of()));

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.hostName()).isEqualTo("web-1");
    assertThat(entry.processPid()).isEqualTo(4242L);
    assertThat(entry.runtimeVersion()).isEqualTo("17.0.10+7");
  }

  @Test
  void entriesWithoutResourceIdentityLeaveResourceFieldsNull() {
    var entry = mapEnterEvent();

    assertThat(entry.hostName()).isNull();
    assertThat(entry.processPid()).isNull();
    assertThat(entry.runtimeVersion()).isNull();
  }

  // ── Thread identity (schema 1.2) ─────────────────────────────────────────────

  @Test
  void enterEventCarriesThreadIdentity() {
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());
    var thread = new ai.narrativetrace.api.event.ThreadInfo("worker-3", 42L, true);
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig, null, thread);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.threadName()).isEqualTo("worker-3");
    assertThat(entry.threadId()).isEqualTo(42L);
    assertThat(entry.ntThreadVirtual()).isTrue();
  }

  @Test
  void enterEventWithoutThreadLeavesThreadFieldsNull() {
    var entry = mapEnterEvent();

    assertThat(entry.threadName()).isNull();
    assertThat(entry.threadId()).isNull();
    assertThat(entry.ntThreadVirtual()).isNull();
  }

  // ── Package identity (schema 1.2) ────────────────────────────────────────────

  @Test
  void enterEventCarriesDeclaringPackage() {
    var sig =
        new MethodSignature(
            "OrderService", "placeOrder", List.of(), null, null, null, "com.acme.billing");
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntPackage()).isEqualTo("com.acme.billing");
  }

  @Test
  void enterEventWithoutPackageHasNullPackage() {
    var entry = mapEnterEvent();

    assertThat(entry.ntPackage()).isNull();
  }

  @Test
  void exitEventCarriesDeclaringPackageFromEnteringSignature() {
    var sig =
        new MethodSignature(
            "OrderService", "placeOrder", List.of(), null, null, null, "com.acme.billing");
    var event =
        new TraceEvent.ExitEvent(
            rootSpanContext(), System.nanoTime(), new TraceOutcome.Returned("42"), null, sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntPackage()).isEqualTo("com.acme.billing");
  }

  @Test
  void exitEventThrewCarriesExceptionPackage() {
    var ex = new IllegalArgumentException("bad input");
    var event =
        new TraceEvent.ExitEvent(
            rootSpanContext(), System.nanoTime(), new TraceOutcome.Threw(ex), null);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.exceptionType()).isEqualTo("IllegalArgumentException");
    assertThat(entry.ntExceptionPackage()).isEqualTo("java.lang");
  }

  @Test
  void defaultPackageIsAbsentNotBlank() {
    assertThat(CanonicalEntryMapper.emptyToNull("")).isNull();
    assertThat(CanonicalEntryMapper.emptyToNull(null)).isNull();
    assertThat(CanonicalEntryMapper.emptyToNull("com.acme")).isEqualTo("com.acme");
  }

  @Test
  void exitEventReturnedHasNullExceptionPackage() {
    var event =
        new TraceEvent.ExitEvent(
            rootSpanContext(), System.nanoTime(), new TraceOutcome.Returned("42"), null);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.ntExceptionPackage()).isNull();
  }

  @Test
  void fromExitEventDirectCall() {
    var event =
        new TraceEvent.ExitEvent(
            rootSpanContext(), System.nanoTime(), new TraceOutcome.Returned("x"), null);
    var entry = CanonicalEntryMapper.fromExitEvent(event);
    assertThat(entry.ntEventType()).isEqualTo("method_exit");
  }

  @Test
  void fromForkEventDirectCall() {
    var entry = CanonicalEntryMapper.fromForkEvent(new TraceEvent.ForkCreatedEvent("g", 0));
    assertThat(entry.ntEventType()).isEqualTo("fork");
  }

  @Test
  void fromMergeEventDirectCall() {
    var entry = CanonicalEntryMapper.fromMergeEvent(new TraceEvent.MergeEvent("g", 2, 0));
    assertThat(entry.ntEventType()).isEqualTo("join");
  }

  @Test
  void fromFireAndForgetEventDirectCall() {
    var entry =
        CanonicalEntryMapper.fromFireAndForgetEvent(new TraceEvent.FireAndForgetEvent("g", 0));
    assertThat(entry.ntEventType()).isEqualTo("async_dispatch");
  }

  @Test
  void exitEventWithNullReturnValue() {
    var event =
        new TraceEvent.ExitEvent(
            rootSpanContext(), System.nanoTime(), new TraceOutcome.Returned(null), null);
    var entry = CanonicalEntryMapper.fromEvent(event);
    assertThat(entry.ntOutcome()).isEqualTo("success");
    assertThat(entry.ntReturnValue()).isNull();
  }

  // ── Timestamp ────────────────────────────────────────────────────────────────

  @Test
  void timestampIsIso8601() {
    var sig = new MethodSignature("Svc", "method", List.of());
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.timestamp()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z");
  }

  @Test
  void timestampReflectsWallClockNotNanoTimeEpoch() {
    var sig = new MethodSignature("Svc", "method", List.of());
    var event = new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), sig);
    var before = java.time.Instant.now().minusSeconds(5);

    var entry = CanonicalEntryMapper.fromEvent(event);

    var timestamp = java.time.Instant.parse(entry.timestamp());
    assertThat(timestamp)
        .as("event timestamp must be real wall-clock time, not System.nanoTime() read as epoch")
        .isAfter(before)
        .isBefore(java.time.Instant.now().plusSeconds(5));
  }

  @Test
  void timestampPrefersTheTraceAnchorOverTheStaticJvmAnchor() {
    long eventNanos = System.nanoTime();
    // Anchor pins "5 seconds before the event" to a fixed epoch; the mapper must derive the
    // timestamp from it instead of the class-load anchor (which drifts under NTP over uptime).
    var anchor =
        new ai.narrativetrace.api.event.TraceAnchor(
            1_000_000_000_000L, eventNanos - 5_000_000_000L);
    var sc = SpanContext.builder(TRACE_ID, SPAN_ID).traceAnchor(anchor).build();
    var event =
        new TraceEvent.EnterEvent(sc, eventNanos, new MethodSignature("Svc", "m", List.of()));

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.timestamp())
        .isEqualTo(java.time.Instant.ofEpochMilli(1_000_000_005_000L).toString());
  }

  // ── service fallback ─────────────────────────────────────────────────────────

  @Test
  void enterEventWithoutSpanContextFallsBackToUnknownService() {
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());
    var event = new TraceEvent.EnterEvent(null, System.nanoTime(), sig);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.service()).isEqualTo("unknown_service:java");
  }

  @Test
  void exitEventWithoutSpanContextFallsBackToUnknownService() {
    var event =
        new TraceEvent.ExitEvent(null, System.nanoTime(), new TraceOutcome.Returned("42"), null);

    var entry = CanonicalEntryMapper.fromEvent(event);

    assertThat(entry.service()).isEqualTo("unknown_service:java");
  }

  @Test
  void spanContextWithoutServiceNameFallsBackToUnknownService() {
    var sc = SpanContext.builder(TRACE_ID, SPAN_ID).environment("test").build();
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());

    var entry = CanonicalEntryMapper.fromEvent(new TraceEvent.EnterEvent(sc, 1L, sig));

    assertThat(entry.service()).isEqualTo("unknown_service:java");
  }

  @Test
  void blankServiceNameFallsBackToUnknownService() {
    // Micronaut binds an unset narrativetrace.serviceName to "" rather than null.
    var sc = SpanContext.builder(TRACE_ID, SPAN_ID).serviceName("   ").build();
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());

    var entry = CanonicalEntryMapper.fromEvent(new TraceEvent.EnterEvent(sc, 1L, sig));

    assertThat(entry.service()).isEqualTo("unknown_service:java");
  }

  @Test
  void configuredServiceNameSurvivesTheFallback() {
    var entry = mapEnterEvent();

    assertThat(entry.service()).isEqualTo("order-service");
  }

  @Test
  void mergeEventFallsBackToUnknownService() {
    var event = new TraceEvent.MergeEvent("group-1", 3, System.nanoTime());

    assertThat(CanonicalEntryMapper.fromEvent(event).service()).isEqualTo("unknown_service:java");
  }

  @Test
  void fireAndForgetEventFallsBackToUnknownService() {
    var event = new TraceEvent.FireAndForgetEvent("group-1", System.nanoTime());

    assertThat(CanonicalEntryMapper.fromEvent(event).service()).isEqualTo("unknown_service:java");
  }

  @Test
  void unknownServiceConstantCarriesTheOtelRuntimeSuffix() {
    assertThat(CanonicalEntryMapper.UNKNOWN_SERVICE).isEqualTo("unknown_service:java");
  }
}
