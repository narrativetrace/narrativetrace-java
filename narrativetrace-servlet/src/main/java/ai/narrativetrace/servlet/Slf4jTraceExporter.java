/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.servlet;

import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.export.RequestContext;
import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.export.JsonExporter;
import ai.narrativetrace.core.render.TraceNamer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Request-boundary exporter that logs flattened JSON traces through SLF4J.
 *
 * <p>INTENT: Use this as the default production exporter when log aggregation is your storage and
 * search layer.
 *
 * <p><b>@sideEffects</b> Emits one INFO log entry per exported request. Sets MDC fields for
 * canonical schema Layer 3 ({@code nt.entryType}, {@code nt.storyId}, {@code nt.chapterId}, {@code
 * nt.traceName}, {@code nt.schemaVersion}, {@code trace_id}) during the log call and clears them
 * afterwards.
 *
 * <p><b>@edgeCase</b> If INFO logging is disabled for the target logger, export becomes a no-op. If
 * the trace tree has no root span context, nt.* MDC fields are not set.
 *
 * @see NarrativeTraceFilter
 * @see ai.narrativetrace.api.export.TraceExporter
 */
public class Slf4jTraceExporter implements TraceExporter {

  private static final String DEFAULT_LOGGER_NAME = "narrativetrace.export";

  private static final String[] MDC_KEYS = {
    "nt.entryType", "nt.schemaVersion", "nt.storyId", "nt.chapterId", "nt.traceName", "trace_id"
  };

  private final Logger logger;
  private final JsonExporter jsonExporter = new JsonExporter();

  /** Creates an exporter writing to the default logger name. */
  public Slf4jTraceExporter() {
    this(DEFAULT_LOGGER_NAME);
  }

  /**
   * Creates an exporter writing to one logger category.
   *
   * @param loggerName SLF4J logger category that receives the exported trace JSON
   */
  public Slf4jTraceExporter(String loggerName) {
    this.logger = LoggerFactory.getLogger(loggerName);
  }

  @Override
  public void export(TraceTree tree, RequestContext requestContext) {
    if (!logger.isInfoEnabled()) {
      return;
    }
    var json = jsonExporter.export(tree);
    var ownedKeys = populateMdc(tree.roots());
    try {
      logger.info(
          "[{}] {}ms — {}", requestContext.statusCode(), requestContext.durationMillis(), json);
    } finally {
      clearOwnedMdc(ownedKeys);
    }
  }

  /**
   * Populates MDC with canonical nt.* fields derived from the root span context.
   *
   * <p>Only keys that were absent before this call are set, so pre-existing MDC entries are never
   * overwritten. Returns the set of keys actually written so they can be removed after logging.
   *
   * @param roots root trace nodes of the current tree
   * @return array of MDC keys that this call set (subset of {@link #MDC_KEYS})
   */
  private static String[] populateMdc(List<TraceNode> roots) {
    var sc = findRootSpanContext(roots);
    if (sc == null) {
      return new String[0];
    }
    var owned = new java.util.ArrayList<String>(MDC_KEYS.length);
    putOwned("nt.entryType", "chapter", owned);
    putOwned("nt.schemaVersion", "1.0", owned);
    putOwned("nt.storyId", sc.storyId(), owned);
    putOwned("nt.chapterId", sc.chapterId(), owned);
    putOwned("trace_id", sc.traceId().toString(), owned);
    putOwned("nt.traceName", TraceNamer.name(sc.traceId().value()), owned);
    return owned.toArray(new String[0]);
  }

  private static SpanContext findRootSpanContext(List<TraceNode> roots) {
    for (var root : roots) {
      if (root.spanContext() != null) {
        return root.spanContext();
      }
    }
    return null;
  }

  /**
   * Sets an MDC key only when it has no pre-existing value and the supplied value is non-null. Adds
   * the key to {@code owned} so it can be removed after the log call.
   */
  private static void putOwned(String key, String value, java.util.List<String> owned) {
    if (value != null && MDC.get(key) == null) {
      MDC.put(key, value);
      owned.add(key);
    }
  }

  /** Removes only the MDC keys that this exporter set during the current export call. */
  private static void clearOwnedMdc(String[] ownedKeys) {
    for (var key : ownedKeys) {
      MDC.remove(key);
    }
  }
}
