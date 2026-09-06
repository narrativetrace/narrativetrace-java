/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpanContextAttributeMapperTest {

  private InMemorySpanExporter spanExporter;
  private SdkTracerProvider tracerProvider;

  @BeforeEach
  void setUp() {
    spanExporter = InMemorySpanExporter.create();
    tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
  }

  @AfterEach
  void tearDown() {
    tracerProvider.close();
  }

  @Test
  void fallbackParsesQuotedStringAsStripped() {
    startAndSetParams(new ParameterCapture("name", "\"Alice\"", false));

    assertThat(getAttr(AttributeKey.stringKey("narrative.param.name"))).isEqualTo("Alice");
  }

  @Test
  void fallbackParsesLong() {
    startAndSetParams(new ParameterCapture("count", "42", false));

    assertThat(getAttr(AttributeKey.longKey("narrative.param.count"))).isEqualTo(42L);
  }

  @Test
  void fallbackParsesDouble() {
    startAndSetParams(new ParameterCapture("rate", "3.14", false));

    assertThat(getAttr(AttributeKey.doubleKey("narrative.param.rate"))).isEqualTo(3.14);
  }

  @Test
  void fallbackParsesBoolean() {
    startAndSetParams(new ParameterCapture("flag", "true", false));

    assertThat(getAttr(AttributeKey.booleanKey("narrative.param.flag"))).isTrue();
  }

  @Test
  void fallbackParsesFalse() {
    startAndSetParams(new ParameterCapture("flag", "false", false));

    assertThat(getAttr(AttributeKey.booleanKey("narrative.param.flag"))).isFalse();
  }

  @Test
  void fallbackUsesStringForUnparseable() {
    startAndSetParams(new ParameterCapture("label", "some-text", false));

    assertThat(getAttr(AttributeKey.stringKey("narrative.param.label"))).isEqualTo("some-text");
  }

  @Test
  void structuredInstantValUsesLongKey() {
    startAndSetParams(
        new ParameterCapture(
            "ts", "2025-03-16", false, new RenderedValue.InstantVal(1742134981123L)));

    assertThat(getAttr(AttributeKey.longKey("narrative.param.ts"))).isEqualTo(1742134981123L);
  }

  @Test
  void structuredNullValIsSkipped() {
    startAndSetParams(new ParameterCapture("x", "null", false, new RenderedValue.NullVal()));

    assertThat(getAttr(AttributeKey.stringKey("narrative.param.x"))).isNull();
    assertThat(getAttr(AttributeKey.longKey("narrative.param.x"))).isNull();
  }

  @Test
  void structuredObjectDepthExceededIsSkipped() {
    // 3 levels of nesting — 4th level should be skipped
    var level3 = new LinkedHashMap<String, RenderedValue>();
    level3.put("deep", new RenderedValue.StringVal("too-deep"));
    var level2 = new LinkedHashMap<String, RenderedValue>();
    level2.put("c", new RenderedValue.ObjectVal("L3", level3));
    var level1 = new LinkedHashMap<String, RenderedValue>();
    level1.put("b", new RenderedValue.ObjectVal("L2", level2));
    var root = new RenderedValue.ObjectVal("L1", level1);

    startAndSetParams(new ParameterCapture("obj", "...", false, root));

    // depth 0: narrative.param.obj → ObjectVal (flatten)
    // depth 1: narrative.param.obj.b → ObjectVal (flatten)
    // depth 2: narrative.param.obj.b.c → ObjectVal (flatten)
    // depth 3: narrative.param.obj.b.c.deep → StringVal at MAX_FLATTEN_DEPTH=3, should be set
    assertThat(getAttr(AttributeKey.stringKey("narrative.param.obj.b.c.deep")))
        .isEqualTo("too-deep");
  }

  @Test
  void structuredHomogeneousLongListUsesArrayKey() {
    var list =
        new RenderedValue.ListVal(
            List.of(new RenderedValue.LongVal(1), new RenderedValue.LongVal(2)));
    startAndSetParams(new ParameterCapture("ids", "[1, 2]", false, list));

    assertThat(getAttr(AttributeKey.longArrayKey("narrative.param.ids"))).containsExactly(1L, 2L);
  }

  @Test
  void structuredHomogeneousStringListUsesArrayKey() {
    var list =
        new RenderedValue.ListVal(
            List.of(new RenderedValue.StringVal("a"), new RenderedValue.StringVal("b")));
    startAndSetParams(new ParameterCapture("tags", "[a, b]", false, list));

    assertThat(getAttr(AttributeKey.stringArrayKey("narrative.param.tags")))
        .containsExactly("a", "b");
  }

  @Test
  void structuredHomogeneousDoubleListUsesArrayKey() {
    var list =
        new RenderedValue.ListVal(
            List.of(new RenderedValue.DoubleVal(1.1), new RenderedValue.DoubleVal(2.2)));
    startAndSetParams(new ParameterCapture("rates", "[1.1, 2.2]", false, list));

    assertThat(getAttr(AttributeKey.doubleArrayKey("narrative.param.rates")))
        .containsExactly(1.1, 2.2);
  }

  @Test
  void structuredHomogeneousBooleanListUsesArrayKey() {
    var list =
        new RenderedValue.ListVal(
            List.of(new RenderedValue.BooleanVal(true), new RenderedValue.BooleanVal(false)));
    startAndSetParams(new ParameterCapture("flags", "[true, false]", false, list));

    assertThat(getAttr(AttributeKey.booleanArrayKey("narrative.param.flags")))
        .containsExactly(true, false);
  }

  @Test
  void emptyListIsSkipped() {
    var list = new RenderedValue.ListVal(List.of());
    startAndSetParams(new ParameterCapture("items", "[]", false, list));

    assertThat(getAttr(AttributeKey.stringKey("narrative.param.items"))).isNull();
  }

  @Test
  void buildEventAttributesIncludesTypedParams() {
    var sig =
        new MethodSignature(
            "Svc",
            "process",
            List.of(new ParameterCapture("count", "42", false, new RenderedValue.LongVal(42L))));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.longKey("narrative.param.count"))).isEqualTo(42L);
    assertThat(attrs.get(AttributeKey.stringKey("narrative.outcome"))).isEqualTo("ok");
  }

  @Test
  void buildEventAttributesForVoidOmitsOutcomeAttribute() {
    var sig = new MethodSignature("AuditSink", "record", List.of());

    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned(null));

    assertThat(attrs.get(AttributeKey.stringKey("narrative.outcome"))).isNull();
  }

  @Test
  void buildEventAttributesForThrewIncludesErrorMessage() {
    var sig = new MethodSignature("Svc", "fail", List.of());
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(
            sig, new TraceOutcome.Threw(new RuntimeException("boom")));

    assertThat(attrs.get(AttributeKey.stringKey("narrative.outcome"))).isEqualTo("error: boom");
  }

  @Test
  void emitChildEventSetsNameAndCompletionTimestamp() {
    var tracer = tracerProvider.get("test");
    var parentSpan = tracer.spanBuilder("parent").startSpan();

    var child =
        new TraceNode(
            new MethodSignature("Svc", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("null"),
            50_000L,
            7777L,
            null);
    SpanContextAttributeMapper.emitChildEvent(parentSpan, child);
    parentSpan.end();

    var span = spanExporter.getFinishedSpanItems().get(0);
    assertThat(span.getEvents()).hasSize(1);
    assertThat(span.getEvents().get(0).getName()).isEqualTo("Svc.work");
    assertThat(span.getEvents().get(0).getEpochNanos()).isEqualTo(7777L + 50_000L);
  }

  @Test
  void buildEventAttributesFallbackTypedParamsWithoutStructured() {
    var sig = new MethodSignature("Svc", "calc", List.of(new ParameterCapture("n", "99", false)));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.longKey("narrative.param.n"))).isEqualTo(99L);
  }

  @Test
  void buildEventAttributesSkipsRedactedParams() {
    var sig =
        new MethodSignature(
            "Svc", "login", List.of(new ParameterCapture("pwd", "[REDACTED]", true)));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.stringKey("narrative.param.pwd"))).isNull();
  }

  @Test
  void buildEventAttributesSkipsSuppressedParams() {
    var sig = new MethodSignature("Svc", "work", List.of(new ParameterCapture("x", "", false)));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.stringKey("narrative.param.x"))).isNull();
  }

  @Test
  void eventAttributesWithStructuredDoubleParam() {
    var sig =
        new MethodSignature(
            "Svc",
            "calc",
            List.of(
                new ParameterCapture("rate", "3.14", false, new RenderedValue.DoubleVal(3.14))));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.doubleKey("narrative.param.rate"))).isEqualTo(3.14);
  }

  @Test
  void eventAttributesWithStructuredBooleanParam() {
    var sig =
        new MethodSignature(
            "Svc",
            "check",
            List.of(
                new ParameterCapture("active", "true", false, new RenderedValue.BooleanVal(true))));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.booleanKey("narrative.param.active"))).isTrue();
  }

  @Test
  void eventAttributesWithStructuredStringParam() {
    var sig =
        new MethodSignature(
            "Svc",
            "greet",
            List.of(
                new ParameterCapture(
                    "name", "\"Alice\"", false, new RenderedValue.StringVal("Alice"))));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.stringKey("narrative.param.name"))).isEqualTo("Alice");
  }

  @Test
  void eventAttributesWithStructuredInstantParam() {
    var sig =
        new MethodSignature(
            "Svc",
            "process",
            List.of(
                new ParameterCapture(
                    "ts", "...", false, new RenderedValue.InstantVal(1742134981123L))));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.longKey("narrative.param.ts"))).isEqualTo(1742134981123L);
  }

  @Test
  void eventAttributesWithStructuredObjectParam() {
    var fields = new LinkedHashMap<String, RenderedValue>();
    fields.put("id", new RenderedValue.StringVal("X"));
    fields.put("total", new RenderedValue.DoubleVal(99.9));
    var sig =
        new MethodSignature(
            "Svc",
            "place",
            List.of(
                new ParameterCapture(
                    "order", "...", false, new RenderedValue.ObjectVal("Order", fields))));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.stringKey("narrative.param.order.id"))).isEqualTo("X");
    assertThat(attrs.get(AttributeKey.doubleKey("narrative.param.order.total"))).isEqualTo(99.9);
  }

  @Test
  void eventAttributesFallbackParsesQuotedString() {
    var sig =
        new MethodSignature(
            "Svc", "greet", List.of(new ParameterCapture("name", "\"Bob\"", false)));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.stringKey("narrative.param.name"))).isEqualTo("Bob");
  }

  @Test
  void eventAttributesFallbackParsesDouble() {
    var sig =
        new MethodSignature("Svc", "calc", List.of(new ParameterCapture("rate", "2.5", false)));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.doubleKey("narrative.param.rate"))).isEqualTo(2.5);
  }

  @Test
  void eventAttributesFallbackParsesBoolean() {
    var sig =
        new MethodSignature("Svc", "check", List.of(new ParameterCapture("ok", "false", false)));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.booleanKey("narrative.param.ok"))).isFalse();
  }

  @Test
  void eventAttributesFallbackUsesStringForUnparseable() {
    var sig =
        new MethodSignature(
            "Svc", "process", List.of(new ParameterCapture("label", "abc-xyz", false)));
    var attrs =
        SpanContextAttributeMapper.buildEventAttributes(sig, new TraceOutcome.Returned("ok"));

    assertThat(attrs.get(AttributeKey.stringKey("narrative.param.label"))).isEqualTo("abc-xyz");
  }

  private void startAndSetParams(ParameterCapture... params) {
    var tracer = tracerProvider.get("test");
    var sig = new MethodSignature("Svc", "method", List.of(params));
    var span = tracer.spanBuilder("test").startSpan();
    SpanContextAttributeMapper.setSpanAttributes(sig, span);
    span.end();
  }

  // ── nt.* schema fields ───────────────────────────────────────────────────────

  @Test
  void setsNtSchemaFieldsOnSpan() {
    var sc =
        ai.narrativetrace.api.event.SpanContext.builder(
                ai.narrativetrace.api.event.SpanIdGenerator.traceId(),
                ai.narrativetrace.api.event.SpanIdGenerator.spanId())
            .storyId("OrderService.placeOrder")
            .chapterId("OrderService.placeOrder")
            .build();
    var tracer = tracerProvider.get("test");
    var span = tracer.spanBuilder("test").startSpan();

    SpanContextAttributeMapper.setNtSchemaAttributes(sc, span);
    span.end();

    assertThat(getAttr(AttributeKey.stringKey("nt.entryType"))).isEqualTo("entry");
    assertThat(getAttr(AttributeKey.stringKey("nt.schemaVersion"))).isEqualTo("1.0");
    assertThat(getAttr(AttributeKey.stringKey("nt.storyId"))).isEqualTo("OrderService.placeOrder");
    assertThat(getAttr(AttributeKey.stringKey("nt.chapterId")))
        .isEqualTo("OrderService.placeOrder");
  }

  @Test
  void ntSchemaFieldsOmittedWhenSpanContextNull() {
    var tracer = tracerProvider.get("test");
    var span = tracer.spanBuilder("test").startSpan();

    SpanContextAttributeMapper.setNtSchemaAttributes(null, span);
    span.end();

    assertThat(getAttr(AttributeKey.stringKey("nt.entryType"))).isNull();
  }

  @Test
  void ntSchemaFieldsOmitNullStoryId() {
    var sc =
        ai.narrativetrace.api.event.SpanContext.builder(
                ai.narrativetrace.api.event.SpanIdGenerator.traceId(),
                ai.narrativetrace.api.event.SpanIdGenerator.spanId())
            .build();
    var tracer = tracerProvider.get("test");
    var span = tracer.spanBuilder("test").startSpan();

    SpanContextAttributeMapper.setNtSchemaAttributes(sc, span);
    span.end();

    assertThat(getAttr(AttributeKey.stringKey("nt.entryType"))).isEqualTo("entry");
    assertThat(getAttr(AttributeKey.stringKey("nt.storyId"))).isNull();
    assertThat(getAttr(AttributeKey.stringKey("nt.chapterId"))).isNull();
  }

  @SuppressWarnings("unchecked")
  private <T> T getAttr(AttributeKey<T> key) {
    return spanExporter.getFinishedSpanItems().get(0).getAttributes().get(key);
  }
}
