/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.TraceAnchor;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.render.ExceptionMessage;
import ai.narrativetrace.core.render.TraceNamer;
import java.time.Instant;

/**
 * Maps {@link TraceEvent} instances to the canonical flat {@link CanonicalEntry} schema.
 *
 * <p>INTENT: Single source of truth for the event-to-schema mapping. Each event type produces one
 * {@link CanonicalEntry} with the appropriate {@code nt.eventType}, level, and outcome fields set.
 *
 * <p><b>@llmNote</b> Fork, merge, and fire-and-forget events have no {@link SpanContext}, so their
 * trace/span/service fields are null. The dispatcher method {@link #fromEvent(TraceEvent)} handles
 * all five sealed subtypes.
 */
public final class CanonicalEntryMapper {

  private static final String ENTRY_TYPE = "entry";

  /**
   * Service name stamped on entries when the host pins none.
   *
   * <p>INTENT: {@code service} is required by {@code entry.schema.json}, so it must never be absent
   * or blank. This is OpenTelemetry's convention for "nobody said": {@code unknown_service:} plus
   * the runtime's executable name, which on the JVM is always {@code java} — the same literal the
   * OpenTelemetry Java SDK stamps on its default {@code Resource}.
   *
   * <p><b>@llmNote</b> Cross-port contract: every NarrativeTrace port emits {@code
   * unknown_service:<runtime>} with its own runtime suffix ({@code :node}, {@code :python}, {@code
   * :dotnet}, {@code :swift}). The suffix is a fixed per-port literal, not a lookup of the running
   * executable, so the value stays deterministic across restarts and deployments; conformance
   * fixtures normalize the suffix away before comparing goldens across platforms.
   */
  public static final String UNKNOWN_SERVICE = "unknown_service:java";

  // Events carry System.nanoTime() readings (a monotonic clock with an arbitrary origin, correct
  // for durations but meaningless as an epoch). Timestamps prefer the per-trace TraceAnchor on the
  // SpanContext (drift bounded by trace duration); this class-load anchor is the fallback for
  // events without one (concurrency markers, contexts predating anchoring) and drifts under NTP
  // over long process uptimes.
  private static final TraceAnchor JVM_ANCHOR = TraceAnchor.now();

  private CanonicalEntryMapper() {}

  /** Maps any {@link TraceEvent} subtype to a {@link CanonicalEntry}. */
  public static CanonicalEntry fromEvent(TraceEvent event) {
    if (event instanceof TraceEvent.EnterEvent e) return fromEnterEvent(e);
    if (event instanceof TraceEvent.ExitEvent e) return fromExitEvent(e);
    if (event instanceof TraceEvent.ForkCreatedEvent e) return fromForkEvent(e);
    if (event instanceof TraceEvent.MergeEvent e) return fromMergeEvent(e);
    if (event instanceof TraceEvent.FireAndForgetEvent e) return fromFireAndForgetEvent(e);
    throw new IllegalArgumentException("Unknown TraceEvent type: " + event.getClass().getName());
  }

  /** Maps an {@link TraceEvent.EnterEvent} to a method_enter canonical entry. */
  public static CanonicalEntry fromEnterEvent(TraceEvent.EnterEvent event) {
    var sc = event.spanContext();
    var sig = event.signature();
    var thread = event.thread();
    return commonFields(CanonicalEntry.builder(), sc)
        .threadName(thread != null ? thread.threadName() : null)
        .threadId(thread != null ? thread.threadId() : null)
        .ntThreadVirtual(thread != null ? thread.virtual() : null)
        .timestamp(formatTimestamp(event.timestampNanos(), sc))
        .level("trace")
        .message(formatEnterMessage(sig))
        .codeNamespace(sig.className())
        .codeFunction(sig.methodName())
        .ntPackage(sig.packageName())
        .ntReturnType(sig.returnType())
        .ntInstanceId(sig.instanceId())
        .codeFilepath(sig.source() != null ? sig.source().file() : null)
        .codeLineno(sig.source() != null ? sig.source().line() : null)
        .ntEventType("method_enter")
        .ntParameters(mapParameters(sig))
        .ntNarrationTemplate(sig.narrationTemplate())
        .build();
  }

  /** Maps an {@link TraceEvent.ExitEvent} to a method_exit canonical entry. */
  public static CanonicalEntry fromExitEvent(TraceEvent.ExitEvent event) {
    var sc = event.spanContext();
    var outcome = event.outcome();
    // The entering signature is the real identity; the span-name stub is a legacy fallback for
    // emitters that predate ExitEvent.signature (its first-dot split mangles package-qualified
    // span names).
    var sig = event.signature() != null ? event.signature() : extractSignatureStub(sc);
    String level = outcome instanceof TraceOutcome.Threw ? "error" : "trace";
    String ntOutcome = mapOutcome(outcome);
    String returnValue = null;
    String exType = null;
    String exMsg = null;
    String exPackage = null;

    if (outcome instanceof TraceOutcome.Returned r) {
      returnValue = r.renderedValue();
    } else if (outcome instanceof TraceOutcome.Threw t) {
      exType = t.exception().getClass().getSimpleName();
      exMsg = ExceptionMessage.of(t.exception());
      exPackage = packageOf(t.exception().getClass());
    }

    return commonFields(CanonicalEntry.builder(), sc)
        .timestamp(formatTimestamp(event.timestampNanos(), sc))
        .level(level)
        .message(formatExitMessage(sc, outcome))
        .codeNamespace(sig.className())
        .codeFunction(sig.methodName())
        .ntPackage(sig.packageName())
        .ntEventType("method_exit")
        .ntOutcome(ntOutcome)
        .ntReturnValue(returnValue)
        .exceptionType(exType)
        .exceptionMessage(exMsg)
        .ntExceptionPackage(exPackage)
        .build();
  }

  /** Maps a {@link TraceEvent.ForkCreatedEvent} to a fork canonical entry. */
  public static CanonicalEntry fromForkEvent(TraceEvent.ForkCreatedEvent event) {
    return concurrencyEntry(event.timestampNanos(), "fork", event.groupId());
  }

  /** Maps a {@link TraceEvent.MergeEvent} to a join canonical entry. */
  public static CanonicalEntry fromMergeEvent(TraceEvent.MergeEvent event) {
    return concurrencyEntry(event.timestampNanos(), "join", event.groupId());
  }

  /** Maps a {@link TraceEvent.FireAndForgetEvent} to an async_dispatch canonical entry. */
  public static CanonicalEntry fromFireAndForgetEvent(TraceEvent.FireAndForgetEvent event) {
    return concurrencyEntry(event.timestampNanos(), "async_dispatch", event.groupId());
  }

  private static CanonicalEntry concurrencyEntry(
      long timestampNanos, String eventType, String forkId) {
    return CanonicalEntry.builder()
        .timestamp(formatTimestamp(timestampNanos))
        .service(UNKNOWN_SERVICE)
        .level("trace")
        .message(eventType + " [" + forkId + "]")
        .ntEntryType(ENTRY_TYPE)
        .ntEventType(eventType)
        .ntSchemaVersion(CanonicalEntry.SCHEMA_VERSION)
        .ntForkId(forkId)
        .build();
  }

  /** Span-context-derived correlation fields shared by enter and exit entries. */
  private static CanonicalEntry.Builder commonFields(CanonicalEntry.Builder b, SpanContext sc) {
    return b.service(serviceNameOrUnknown(sc))
        .environment(environment(sc))
        .hostName(sc != null ? sc.hostName() : null)
        .processPid(sc != null ? sc.processPid() : null)
        .runtimeVersion(sc != null ? sc.runtimeVersion() : null)
        .traceId(traceIdString(sc))
        .spanId(spanIdString(sc))
        .parentSpanId(parentSpanIdString(sc))
        .ntEntryType(ENTRY_TYPE)
        .ntSchemaVersion(CanonicalEntry.SCHEMA_VERSION)
        .ntTraceName(traceName(sc))
        .ntStoryId(storyId(sc))
        .ntChapterId(chapterId(sc));
  }

  private static String formatTimestamp(long nanos) {
    return formatTimestamp(nanos, null);
  }

  private static String formatTimestamp(long nanos, SpanContext sc) {
    var anchor = sc != null && sc.traceAnchor() != null ? sc.traceAnchor() : JVM_ANCHOR;
    return Instant.ofEpochMilli(anchor.toEpochMillis(nanos)).toString();
  }

  private static String formatEnterMessage(MethodSignature sig) {
    var sb = new StringBuilder();
    sb.append("\u2192 ").append(sig.className()).append('.').append(sig.methodName()).append('(');
    var params = sig.parameters();
    for (int i = 0; i < params.size(); i++) {
      if (i > 0) sb.append(", ");
      var p = params.get(i);
      sb.append(p.name()).append(": ");
      sb.append(p.redacted() ? "[REDACTED]" : p.renderedValue());
    }
    sb.append(')');
    return sb.toString();
  }

  private static String formatExitMessage(SpanContext sc, TraceOutcome outcome) {
    if (outcome instanceof TraceOutcome.Threw t) {
      return "!! "
          + t.exception().getClass().getSimpleName()
          + ": "
          + ExceptionMessage.text(t.exception());
    }
    var name = sc != null && sc.spanName() != null ? sc.spanName() : "method";
    if (outcome instanceof TraceOutcome.Returned r && r.renderedValue() != null) {
      return "\u2190 " + name + " returned " + r.renderedValue();
    }
    if (outcome instanceof TraceOutcome.Incomplete) {
      return "\u2190 " + name + " incomplete";
    }
    return "\u2190 " + name + " returned";
  }

  private static String mapOutcome(TraceOutcome outcome) {
    if (outcome instanceof TraceOutcome.Returned) return "success";
    if (outcome instanceof TraceOutcome.Threw) return "failure";
    if (outcome instanceof TraceOutcome.Incomplete) return "incomplete";
    return null;
  }

  private static java.util.List<ParameterEntry> mapParameters(MethodSignature sig) {
    if (sig.parameters().isEmpty()) return null;
    return sig.parameters().stream()
        .map(
            p ->
                new ParameterEntry(
                    p.name(),
                    p.redacted() ? "[REDACTED]" : p.renderedValue(),
                    p.redacted(),
                    p.type()))
        .toList();
  }

  /** Package of a class, or {@code null} for the default package (empty is never emitted). */
  static String packageOf(Class<?> type) {
    return emptyToNull(type.getPackageName());
  }

  /** Maps the empty string to {@code null} so default-package identity is absent, not blank. */
  static String emptyToNull(String packageName) {
    return packageName == null || packageName.isEmpty() ? null : packageName;
  }

  private static MethodSignature extractSignatureStub(SpanContext sc) {
    var name = sc != null && sc.spanName() != null ? sc.spanName() : "";
    var dot = name.indexOf('.');
    var className = dot > 0 ? name.substring(0, dot) : "";
    var methodName = dot > 0 ? name.substring(dot + 1) : name;
    return new MethodSignature(className, methodName, java.util.List.of());
  }

  /**
   * Service name, or {@link #UNKNOWN_SERVICE} when the host pinned none.
   *
   * <p>INTENT: One definition of the fallback for every emitter of a schema-<i>required</i> {@code
   * service} field. {@link ChapterExporter} shares it; emitters whose schema leaves the field
   * optional must not — there, absence is the honest answer.
   */
  static String serviceNameOrUnknown(SpanContext sc) {
    var name = sc != null ? sc.serviceName() : null;
    return name == null || name.isBlank() ? UNKNOWN_SERVICE : name;
  }

  private static String environment(SpanContext sc) {
    return sc != null ? sc.environment() : null;
  }

  private static String traceIdString(SpanContext sc) {
    return sc != null ? sc.traceId().toString() : null;
  }

  private static String spanIdString(SpanContext sc) {
    return sc != null ? sc.spanId().toString() : null;
  }

  private static String parentSpanIdString(SpanContext sc) {
    return sc != null && sc.parentSpanId() != null ? sc.parentSpanId().toString() : null;
  }

  private static String traceName(SpanContext sc) {
    return sc != null ? TraceNamer.name(sc.traceId().value()) : null;
  }

  private static String storyId(SpanContext sc) {
    return sc != null ? sc.storyId() : null;
  }

  private static String chapterId(SpanContext sc) {
    return sc != null ? sc.chapterId() : null;
  }
}
