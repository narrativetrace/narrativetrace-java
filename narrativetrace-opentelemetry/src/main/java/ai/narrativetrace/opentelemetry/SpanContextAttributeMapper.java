/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.context.ContextExport;
import ai.narrativetrace.core.render.ExceptionMessage;
import ai.narrativetrace.core.render.TraceNamer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Maps {@link SpanContext} and {@link MethodSignature} fields to OTel span attributes.
 *
 * <p>INTENT: Single source of truth for the field-to-attribute mapping shared by {@link
 * TraceSpanExporter} (batch) and {@link OtelTraceEventListener} (live). Trace-level attributes are
 * only applied to root spans; span-level attributes (class, method, params) apply to every span.
 *
 * <p>When {@link RenderedValue} structured values are available on parameters or return values,
 * they are flattened to typed OTel attributes (long, double, boolean). Object fields are
 * dot-separated up to depth 3. When structured values are null (manual construction, test
 * fixtures), a smart type detection fallback parses the rendered string.
 */
final class SpanContextAttributeMapper {

  private static final int MAX_FLATTEN_DEPTH = 3;

  private static final Map<String, Function<SpanContext, Object>> TRACE_LEVEL_FIELDS =
      Map.ofEntries(
          Map.entry("narrative.service.name", SpanContext::serviceName),
          Map.entry("narrative.service.version", SpanContext::serviceVersion),
          Map.entry("narrative.service.environment", SpanContext::environment),
          Map.entry("narrative.http.method", SpanContext::httpMethod),
          Map.entry("narrative.http.route", SpanContext::httpRoute),
          Map.entry("narrative.client_ip", SpanContext::clientIp),
          Map.entry("narrative.enduser.id", SpanContext::enduserId),
          Map.entry("narrative.session.id", SpanContext::sessionId),
          Map.entry("narrative.tenant.id", SpanContext::tenantId));

  private SpanContextAttributeMapper() {}

  /** Sets span-level attributes (class, method, params) from the given signature. */
  static void setSpanAttributes(MethodSignature sig, Span span) {
    span.setAttribute(AttributeKey.stringKey("narrative.class"), sig.className());
    span.setAttribute(AttributeKey.stringKey("narrative.method"), sig.methodName());
    for (var param : sig.parameters()) {
      setParamAttribute(span, "narrative.param." + param.name(), param);
    }
  }

  /** Sets concurrency attributes from the given {@link ConcurrencyInfo} if present. */
  static void setConcurrencyAttributes(ConcurrencyInfo info, Span span) {
    if (info == null) {
      return;
    }
    span.setAttribute(AttributeKey.stringKey("narrative.concurrency.groupId"), info.groupId());
    span.setAttribute(AttributeKey.stringKey("narrative.concurrency.kind"), info.kind().name());
    span.setAttribute(AttributeKey.longKey("narrative.concurrency.threadId"), info.threadId());
    span.setAttribute(
        AttributeKey.stringKey("narrative.concurrency.threadName"), info.threadName());
    span.setAttribute(AttributeKey.booleanKey("narrative.concurrency.virtual"), info.virtual());
  }

  /** Sets outcome attributes from the given {@link TraceOutcome}. */
  static void setOutcomeAttributes(TraceOutcome outcome, Span span) {
    if (outcome instanceof TraceOutcome.Returned r) {
      span.setAttribute(AttributeKey.stringKey("narrative.outcome"), r.renderedValue());
    } else if (outcome instanceof TraceOutcome.Threw t) {
      span.setStatus(StatusCode.ERROR, ExceptionMessage.text(t.exception()));
      span.recordException(t.exception());
    } else if (outcome instanceof TraceOutcome.Incomplete) {
      span.setAttribute(AttributeKey.stringKey("narrative.outcome"), "in-flight");
    }
  }

  /**
   * Sets the NarrativeTrace identity attributes ({@code narrative.trace_id} and {@code
   * narrative.trace_name}) from the given {@link SpanContext}. Applied to every span so that any
   * span can be correlated back to its NarrativeTrace even without a parent link.
   */
  static void setTraceIdentityAttributes(SpanContext sc, Span span) {
    if (sc == null) {
      return;
    }
    span.setAttribute(AttributeKey.stringKey("narrative.trace_id"), sc.traceId().toString());
    span.setAttribute(
        AttributeKey.stringKey("narrative.trace_name"), TraceNamer.name(sc.traceId().value()));
  }

  /**
   * Sets canonical schema {@code nt.*} attributes onto the span. Always sets {@code nt.entryType}
   * and {@code nt.schemaVersion}; conditionally sets {@code nt.storyId} and {@code nt.chapterId}
   * when available on the {@link SpanContext}.
   */
  static void setNtSchemaAttributes(SpanContext sc, Span span) {
    if (sc == null) {
      return;
    }
    span.setAttribute(AttributeKey.stringKey("nt.entryType"), "entry");
    span.setAttribute(AttributeKey.stringKey("nt.schemaVersion"), "1.0");
    if (sc.storyId() != null) {
      span.setAttribute(AttributeKey.stringKey("nt.storyId"), sc.storyId());
    }
    if (sc.chapterId() != null) {
      span.setAttribute(AttributeKey.stringKey("nt.chapterId"), sc.chapterId());
    }
  }

  /**
   * Sets trace-level attributes from the given {@link SpanContext} onto the OTel span.
   *
   * <p><b>@edgeCase</b> Every value is normalised by {@link ContextExport} on the way out, even
   * though the HTTP filters already normalise what they read off a request. The two layers cover
   * different holes: a value set programmatically through {@code setRequestContext} — by a custom
   * integration, a test, or a framework this project does not ship a filter for — never passed
   * through a filter at all, and this is the last point before it reaches a telemetry backend as an
   * unbounded, high-cardinality, possibly newline-bearing attribute. Found by the 2026-09-02
   * adversarial audit (finding 6).
   */
  static void setTraceLevelAttributes(SpanContext sc, Span span) {
    if (sc == null) {
      return;
    }
    TRACE_LEVEL_FIELDS.forEach(
        (key, extractor) -> {
          var value = ContextExport.normalized(extractor.apply(sc));
          if (value != null) {
            span.setAttribute(AttributeKey.stringKey(key), value);
          }
        });
  }

  /** Emits a child method completion as a timestamped event on the parent span. */
  static void emitChildEvent(Span parentSpan, TraceNode child) {
    var attrs = buildEventAttributes(child.signature(), child.outcome());
    var name = child.signature().className() + "." + child.signature().methodName();
    parentSpan.addEvent(
        name, attrs, child.startTimeNanos() + child.durationNanos(), TimeUnit.NANOSECONDS);
  }

  /** Builds OTel event attributes from a method signature and outcome. */
  static Attributes buildEventAttributes(MethodSignature sig, TraceOutcome outcome) {
    var builder = Attributes.builder();
    for (var param : sig.parameters()) {
      if (param.redacted() || param.renderedValue().isEmpty()) {
        continue;
      }
      addTypedAttribute(builder, "narrative.param." + param.name(), param);
    }
    addOutcomeToEventAttributes(builder, outcome);
    return builder.build();
  }

  private static void setParamAttribute(Span span, String prefix, ParameterCapture param) {
    if (param.redacted() || param.renderedValue().isEmpty()) {
      return;
    }
    var structured = param.structuredValue();
    if (structured == null) {
      setTypedFromString(span, prefix, param.renderedValue());
      return;
    }
    flattenRenderedValue(span, prefix, structured, 0);
  }

  private static void addTypedAttribute(
      AttributesBuilder builder, String prefix, ParameterCapture param) {
    var structured = param.structuredValue();
    if (structured == null) {
      addTypedFromString(builder, prefix, param.renderedValue());
      return;
    }
    flattenToBuilder(builder, prefix, structured, 0);
  }

  private static void flattenRenderedValue(
      Span span, String prefix, RenderedValue value, int depth) {
    if (setScalarAttribute(span, prefix, value)) {
      return;
    }
    if (value instanceof RenderedValue.ObjectVal o && depth < MAX_FLATTEN_DEPTH) {
      flattenObjectToSpan(span, prefix, o, depth);
    } else if (value instanceof RenderedValue.ListVal lv) {
      setListAttribute(span, prefix, lv);
    }
    // NullVal and depth-exceeded ObjectVal are skipped
  }

  private static boolean setScalarAttribute(Span span, String prefix, RenderedValue value) {
    if (value instanceof RenderedValue.StringVal s) {
      span.setAttribute(AttributeKey.stringKey(prefix), s.value());
    } else if (value instanceof RenderedValue.LongVal l) {
      span.setAttribute(AttributeKey.longKey(prefix), l.value());
    } else if (value instanceof RenderedValue.DoubleVal d) {
      span.setAttribute(AttributeKey.doubleKey(prefix), d.value());
    } else if (value instanceof RenderedValue.BooleanVal b) {
      span.setAttribute(AttributeKey.booleanKey(prefix), b.value());
    } else if (value instanceof RenderedValue.InstantVal i) {
      span.setAttribute(AttributeKey.longKey(prefix), i.epochMillis());
    } else {
      return false;
    }
    return true;
  }

  private static void flattenObjectToSpan(
      Span span, String prefix, RenderedValue.ObjectVal obj, int depth) {
    for (var entry : obj.fields().entrySet()) {
      flattenRenderedValue(span, prefix + "." + entry.getKey(), entry.getValue(), depth + 1);
    }
  }

  private static void flattenToBuilder(
      AttributesBuilder builder, String prefix, RenderedValue value, int depth) {
    if (value instanceof RenderedValue.StringVal s) {
      builder.put(AttributeKey.stringKey(prefix), s.value());
    } else if (value instanceof RenderedValue.LongVal l) {
      builder.put(AttributeKey.longKey(prefix), l.value());
    } else if (value instanceof RenderedValue.DoubleVal d) {
      builder.put(AttributeKey.doubleKey(prefix), d.value());
    } else if (value instanceof RenderedValue.BooleanVal b) {
      builder.put(AttributeKey.booleanKey(prefix), b.value());
    } else if (value instanceof RenderedValue.InstantVal i) {
      builder.put(AttributeKey.longKey(prefix), i.epochMillis());
    } else if (value instanceof RenderedValue.ObjectVal o && depth < MAX_FLATTEN_DEPTH) {
      for (var entry : o.fields().entrySet()) {
        flattenToBuilder(builder, prefix + "." + entry.getKey(), entry.getValue(), depth + 1);
      }
    }
    // NullVal, ListVal in events, depth-exceeded are skipped
  }

  private static void setListAttribute(Span span, String prefix, RenderedValue.ListVal lv) {
    if (lv.elements().isEmpty()) {
      return;
    }
    var first = lv.elements().get(0);
    if (allSameType(lv, first)) {
      setHomogeneousListAttribute(span, prefix, lv, first);
    }
    // Heterogeneous lists are skipped
  }

  private static boolean allSameType(RenderedValue.ListVal lv, RenderedValue first) {
    return lv.elements().stream().allMatch(e -> e.getClass() == first.getClass());
  }

  private static void setHomogeneousListAttribute(
      Span span, String prefix, RenderedValue.ListVal lv, RenderedValue first) {
    if (first instanceof RenderedValue.StringVal) {
      var values = lv.elements().stream().map(e -> ((RenderedValue.StringVal) e).value()).toList();
      span.setAttribute(AttributeKey.stringArrayKey(prefix), values);
    } else if (first instanceof RenderedValue.LongVal) {
      var values = lv.elements().stream().map(e -> ((RenderedValue.LongVal) e).value()).toList();
      span.setAttribute(AttributeKey.longArrayKey(prefix), values);
    } else if (first instanceof RenderedValue.DoubleVal) {
      var values = lv.elements().stream().map(e -> ((RenderedValue.DoubleVal) e).value()).toList();
      span.setAttribute(AttributeKey.doubleArrayKey(prefix), values);
    } else if (first instanceof RenderedValue.BooleanVal) {
      var values = lv.elements().stream().map(e -> ((RenderedValue.BooleanVal) e).value()).toList();
      span.setAttribute(AttributeKey.booleanArrayKey(prefix), values);
    }
  }

  private static void setTypedFromString(Span span, String prefix, String rendered) {
    if ("true".equals(rendered) || "false".equals(rendered)) {
      span.setAttribute(AttributeKey.booleanKey(prefix), Boolean.parseBoolean(rendered));
    } else if (tryParseLong(rendered)) {
      span.setAttribute(AttributeKey.longKey(prefix), Long.parseLong(rendered));
    } else if (tryParseDouble(rendered)) {
      span.setAttribute(AttributeKey.doubleKey(prefix), Double.parseDouble(rendered));
    } else if (rendered.startsWith("\"") && rendered.endsWith("\"") && rendered.length() >= 2) {
      span.setAttribute(
          AttributeKey.stringKey(prefix), rendered.substring(1, rendered.length() - 1));
    } else {
      span.setAttribute(AttributeKey.stringKey(prefix), rendered);
    }
  }

  private static void addTypedFromString(AttributesBuilder builder, String prefix, String value) {
    if ("true".equals(value) || "false".equals(value)) {
      builder.put(AttributeKey.booleanKey(prefix), Boolean.parseBoolean(value));
    } else if (tryParseLong(value)) {
      builder.put(AttributeKey.longKey(prefix), Long.parseLong(value));
    } else if (tryParseDouble(value)) {
      builder.put(AttributeKey.doubleKey(prefix), Double.parseDouble(value));
    } else if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
      builder.put(AttributeKey.stringKey(prefix), value.substring(1, value.length() - 1));
    } else {
      builder.put(AttributeKey.stringKey(prefix), value);
    }
  }

  private static void addOutcomeToEventAttributes(AttributesBuilder builder, TraceOutcome outcome) {
    if (outcome instanceof TraceOutcome.Returned r && r.renderedValue() != null) {
      builder.put(AttributeKey.stringKey("narrative.outcome"), r.renderedValue());
    } else if (outcome instanceof TraceOutcome.Threw t) {
      builder.put(
          AttributeKey.stringKey("narrative.outcome"),
          "error: " + ExceptionMessage.text(t.exception()));
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static boolean tryParseLong(String s) {
    try {
      Long.parseLong(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static boolean tryParseDouble(String s) {
    try {
      Double.parseDouble(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
