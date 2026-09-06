/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.slf4j;

import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.render.ControlEscape;
import ai.narrativetrace.core.render.ExceptionMessage;
import ai.narrativetrace.core.render.TraceNamer;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

/**
 * Synchronous SLF4J listener for core trace events.
 *
 * <p>INTENT: Plug this into {@link ai.narrativetrace.core.pipeline.DualPathPipeline} as the inline
 * listener when you want every captured event mirrored to logs immediately.
 *
 * <p><b>@sideEffects</b> Temporarily populates MDC keys for each emitted log message. Span keys:
 * {@code traceId}, {@code spanId}, {@code parentSpanId}, {@code service.name}, {@code
 * service.version}, {@code service.environment}, {@code host.name}, {@code process.pid}, {@code
 * process.runtime.version}. Event keys: {@code nt.class}, {@code nt.method}, {@code nt.depth},
 * {@code nt.package}, {@code nt.threadVirtual} (when captured).
 *
 * <p><b>@llmNote</b> Depth tracking is local to the current thread. It is meant for readable log
 * indentation, not for reconstructing cross-thread parentage.
 */
public final class Slf4jTraceEventListener implements Consumer<TraceEvent> {

  /** Trace event buckets used when overriding SLF4J log levels. */
  public enum EventType {
    /** A method was entered. */
    ENTRY,
    /** A method returned a value, or returned from {@code void}. */
    RETURN,
    /** A method left by throwing. */
    EXCEPTION
  }

  private static final String DEFAULT_LOGGER_NAME = "narrativetrace";

  private final Logger logger;
  private final Level entryLevel;
  private final Level returnLevel;
  private final Level exceptionLevel;
  private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

  /** Creates a listener targeting the default logger name, {@code narrativetrace}. */
  public Slf4jTraceEventListener() {
    this(DEFAULT_LOGGER_NAME, Map.of());
  }

  /**
   * Creates a listener for one logger name with default event levels.
   *
   * @param loggerName SLF4J logger category that will receive event messages
   */
  public Slf4jTraceEventListener(String loggerName) {
    this(loggerName, Map.of());
  }

  /**
   * Creates a listener using the default logger and custom per-event levels.
   *
   * @param levelMappings overrides for the entry, return and exception levels
   */
  public Slf4jTraceEventListener(Map<EventType, Level> levelMappings) {
    this(DEFAULT_LOGGER_NAME, levelMappings);
  }

  /**
   * Creates a listener for one logger name and explicit per-event levels.
   *
   * @param loggerName SLF4J logger category that will receive event messages.
   * @param levelMappings Optional overrides for entry, return, and exception levels.
   */
  public Slf4jTraceEventListener(String loggerName, Map<EventType, Level> levelMappings) {
    this.logger = LoggerFactory.getLogger(loggerName);
    this.entryLevel = levelMappings.getOrDefault(EventType.ENTRY, Level.TRACE);
    this.returnLevel = levelMappings.getOrDefault(EventType.RETURN, Level.TRACE);
    this.exceptionLevel = levelMappings.getOrDefault(EventType.EXCEPTION, Level.WARN);
  }

  @Override
  public void accept(TraceEvent event) {
    if (event instanceof TraceEvent.EnterEvent enter) {
      logEnter(enter);
    } else if (event instanceof TraceEvent.ExitEvent exit) {
      logExit(exit);
    } else if (event instanceof TraceEvent.ForkCreatedEvent fork) {
      logger.atLevel(entryLevel).log("⑂ fork group created [groupId: {}]", fork.groupId());
    } else if (event instanceof TraceEvent.MergeEvent merge) {
      logger
          .atLevel(entryLevel)
          .log("⑃ fork joined [groupId: {}, members: {}]", merge.groupId(), merge.memberCount());
    } else if (event instanceof TraceEvent.FireAndForgetEvent ff) {
      logger.atLevel(entryLevel).log("⤳ fire-and-forget launched [groupId: {}]", ff.groupId());
    }
  }

  private void logEnter(TraceEvent.EnterEvent enter) {
    int currentDepth = depth.get() + 1;
    depth.set(currentDepth);
    var signature = enter.signature();
    String params =
        signature.parameters().stream()
            .map(p -> p.name() + ": " + (p.redacted() ? "[REDACTED]" : p.renderedValue()))
            .collect(Collectors.joining(", "));
    MDC.put("nt.class", signature.className());
    MDC.put("nt.method", signature.methodName());
    MDC.put("nt.depth", String.valueOf(currentDepth));
    setIdentityMdc(enter);
    boolean[] owned = setSpanMdc(enter.spanContext());
    try {
      logger
          .atLevel(entryLevel)
          .log("→ {}.{}({})", signature.className(), signature.methodName(), params);
    } finally {
      MDC.remove("nt.class");
      MDC.remove("nt.method");
      MDC.remove("nt.depth");
      clearIdentityMdc();
      clearSpanMdc(owned);
    }
  }

  /**
   * Captured identity that never enters the narrative message text (ADR-003): declaring package
   * beside nt.class/nt.method, and the virtual-thread flag (thread name/id are {@code %thread}
   * built-ins). Keys are set only when the event carries the data.
   */
  private static void setIdentityMdc(TraceEvent.EnterEvent enter) {
    if (enter.signature().packageName() != null) {
      MDC.put("nt.package", enter.signature().packageName());
    }
    if (enter.thread() != null) {
      MDC.put("nt.threadVirtual", String.valueOf(enter.thread().virtual()));
    }
  }

  private static void clearIdentityMdc() {
    MDC.remove("nt.package");
    MDC.remove("nt.threadVirtual");
  }

  private void logExit(TraceEvent.ExitEvent exit) {
    int currentDepth = Math.max(0, depth.get() - 1);
    depth.set(currentDepth);
    MDC.put("nt.depth", String.valueOf(currentDepth));
    boolean[] owned = setSpanMdc(exit.spanContext());
    try {
      if (exit.outcome() instanceof TraceOutcome.Returned returned) {
        if (returned.renderedValue() == null) {
          logger.atLevel(returnLevel).log("← completed");
        } else {
          logger.atLevel(returnLevel).log("← returned: {}", returned.renderedValue());
        }
      } else if (exit.outcome() instanceof TraceOutcome.Threw threw) {
        logException(threw.exception(), exit.errorContext());
      }
    } finally {
      MDC.remove("nt.depth");
      clearSpanMdc(owned);
    }
  }

  /**
   * Sets MDC fields for the current span event. Trace-level fields (traceId, service.*) are only
   * set if not already present — the filter owns these persistently. Span-level fields (spanId,
   * parentSpanId) are always set.
   *
   * @return boolean flags indicating which trace-level fields this call owns (and should remove)
   */
  private static boolean[] setSpanMdc(ai.narrativetrace.api.event.SpanContext sc) {
    boolean ownTraceId = setIfAbsent("traceId", sc.traceId().toString());
    boolean ownTraceName = setIfAbsent("traceName", TraceNamer.name(sc.traceId().value()));
    MDC.put("spanId", sc.spanId().toString());
    if (sc.parentSpanId() != null) {
      MDC.put("parentSpanId", sc.parentSpanId().toString());
    } else {
      MDC.remove("parentSpanId");
    }
    boolean ownServiceName = setIfAbsent("service.name", sc.serviceName());
    boolean ownServiceVersion = setIfAbsent("service.version", sc.serviceVersion());
    boolean ownServiceEnv = setIfAbsent("service.environment", sc.environment());
    boolean ownHost = setIfAbsent("host.name", sc.hostName());
    boolean ownPid =
        setIfAbsent("process.pid", sc.processPid() != null ? sc.processPid().toString() : null);
    boolean ownRuntime = setIfAbsent("process.runtime.version", sc.runtimeVersion());
    return new boolean[] {
      ownTraceId,
      ownTraceName,
      ownServiceName,
      ownServiceVersion,
      ownServiceEnv,
      ownHost,
      ownPid,
      ownRuntime
    };
  }

  private static void clearSpanMdc(boolean[] owned) {
    if (owned[0]) {
      MDC.remove("traceId");
    }
    if (owned[1]) {
      MDC.remove("traceName");
    }
    MDC.remove("spanId");
    MDC.remove("parentSpanId");
    if (owned[2]) {
      MDC.remove("service.name");
    }
    if (owned[3]) {
      MDC.remove("service.version");
    }
    if (owned[4]) {
      MDC.remove("service.environment");
    }
    if (owned[5]) {
      MDC.remove("host.name");
    }
    if (owned[6]) {
      MDC.remove("process.pid");
    }
    if (owned[7]) {
      MDC.remove("process.runtime.version");
    }
  }

  /**
   * Sets the MDC key only if it is not already present. Returns {@code true} if this call set the
   * value (i.e., owns it and should remove it later).
   */
  private static boolean setIfAbsent(String key, String value) {
    if (value == null) {
      return false;
    }
    if (MDC.get(key) != null) {
      return false;
    }
    MDC.put(key, value);
    return true;
  }

  private void logException(Throwable exception, String errorContext) {
    var type = exception.getClass().getSimpleName();
    var message = ExceptionMessage.text(exception);
    if (errorContext != null) {
      logger
          .atLevel(exceptionLevel)
          .log("!! {}: {} [{}]", type, message, ControlEscape.sanitize(errorContext));
    } else {
      logger.atLevel(exceptionLevel).log("!! {}: {}", type, message);
    }
  }
}
