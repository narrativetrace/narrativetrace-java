/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

/**
 * Serializes a {@link CanonicalEntry} to JSON using the canonical dotted field names from {@code
 * entry.schema.json}.
 *
 * <p>INTENT: This is the JSON wire format for per-event entries. Field names use the canonical
 * schema conventions: {@code "code.namespace"}, {@code "nt.eventType"}, {@code "exception.type"},
 * etc. Null optional fields are omitted from the output.
 *
 * <p><b>@llmNote</b> This serializer is deliberately hand-rolled (no Jackson/Gson dependency) to
 * keep narrativetrace-core at zero runtime dependencies.
 */
public final class CanonicalEntrySerializer {

  private CanonicalEntrySerializer() {}

  /** Serializes the given {@link CanonicalEntry} to a JSON string. */
  public static String toJson(CanonicalEntry entry) {
    var sb = new StringBuilder();
    sb.append("{\n");
    boolean[] first = {true};

    writeUniversalFields(sb, first, entry);
    writeOtelFields(sb, first, entry);
    writeNarrativeTraceFields(sb, first, entry);

    sb.append('\n');
    sb.append("}");
    return sb.toString();
  }

  private static void writeUniversalFields(
      StringBuilder sb, boolean[] first, CanonicalEntry entry) {
    requiredString(sb, first, "timestamp", entry.timestamp());
    requiredString(sb, first, "level", entry.level());
    requiredString(sb, first, "message", entry.message());
    requiredString(sb, first, "service", entry.service());
    optionalString(sb, first, "environment", entry.environment());
    optionalString(sb, first, "host.name", entry.hostName());
    optionalLong(sb, first, "process.pid", entry.processPid());
    optionalString(sb, first, "process.runtime.version", entry.runtimeVersion());
  }

  private static void writeOtelFields(StringBuilder sb, boolean[] first, CanonicalEntry entry) {
    optionalString(sb, first, "trace_id", entry.traceId());
    optionalString(sb, first, "span_id", entry.spanId());
    optionalString(sb, first, "parent_span_id", entry.parentSpanId());
    optionalString(sb, first, "code.namespace", entry.codeNamespace());
    optionalString(sb, first, "code.function", entry.codeFunction());
    optionalString(sb, first, "code.filepath", entry.codeFilepath());
    optionalInteger(sb, first, "code.lineno", entry.codeLineno());
    optionalString(sb, first, "exception.type", entry.exceptionType());
    optionalString(sb, first, "exception.message", entry.exceptionMessage());
    optionalString(sb, first, "thread.name", entry.threadName());
    optionalLong(sb, first, "thread.id", entry.threadId());
    optionalLong(sb, first, "durationMs", entry.durationMs());
  }

  private static void writeNarrativeTraceFields(
      StringBuilder sb, boolean[] first, CanonicalEntry entry) {
    requiredString(sb, first, "nt.entryType", entry.ntEntryType());
    requiredString(sb, first, "nt.eventType", entry.ntEventType());
    requiredString(sb, first, "nt.schemaVersion", entry.ntSchemaVersion());
    optionalString(sb, first, "nt.package", entry.ntPackage());
    optionalString(sb, first, "nt.exceptionPackage", entry.ntExceptionPackage());
    optionalString(sb, first, "nt.returnType", entry.ntReturnType());
    optionalBoolean(sb, first, "nt.threadVirtual", entry.ntThreadVirtual());
    optionalString(sb, first, "nt.instanceId", entry.ntInstanceId());
    optionalString(sb, first, "nt.traceName", entry.ntTraceName());
    optionalString(sb, first, "nt.storyId", entry.ntStoryId());
    optionalString(sb, first, "nt.chapterId", entry.ntChapterId());
    optionalString(sb, first, "nt.outcome", entry.ntOutcome());
    optionalString(sb, first, "nt.forkId", entry.ntForkId());
    optionalInteger(sb, first, "nt.branchIndex", entry.ntBranchIndex());
    optionalString(sb, first, "nt.causalId", entry.ntCausalId());
    optionalString(sb, first, "nt.returnValue", entry.ntReturnValue());
    optionalString(sb, first, "nt.narrationTemplate", entry.ntNarrationTemplate());
    writeParameters(sb, first, entry);
  }

  private static void writeParameters(StringBuilder sb, boolean[] first, CanonicalEntry entry) {
    if (entry.ntParameters() == null) return;
    appendSeparator(sb, first);
    sb.append("  \"nt.parameters\": [\n");
    for (int i = 0; i < entry.ntParameters().size(); i++) {
      if (i > 0) sb.append(",\n");
      var p = entry.ntParameters().get(i);
      sb.append("    {");
      sb.append("\"name\": \"").append(escapeJson(p.name())).append("\", ");
      sb.append("\"value\": \"").append(escapeJson(p.value())).append("\", ");
      sb.append("\"redacted\": ").append(p.redacted());
      if (p.type() != null) {
        sb.append(", \"type\": \"").append(escapeJson(p.type())).append('"');
      }
      sb.append('}');
    }
    sb.append("\n  ]");
  }

  /**
   * Writes a field the schema marks required.
   *
   * <p><b>@llmNote</b> Throws rather than writing {@code null}: {@link JsonEscape#escape} renders a
   * null as the four-character text {@code null}, which is a <i>valid</i> JSON string and so passes
   * a {@code {"type": "string"}} schema check silently. That is how {@code "service": "null"}
   * survived the schema gate. A required field holding null is an upstream mapper bug — fail here
   * so the next one cannot hide.
   *
   * @throws IllegalStateException if {@code value} is null.
   */
  private static void requiredString(StringBuilder sb, boolean[] first, String key, String value) {
    if (value == null) {
      throw new IllegalStateException("Required canonical field is null: " + key);
    }
    appendSeparator(sb, first);
    sb.append("  \"").append(key).append("\": \"").append(escapeJson(value)).append('"');
  }

  private static void optionalString(StringBuilder sb, boolean[] first, String key, String value) {
    if (value == null) return;
    appendSeparator(sb, first);
    sb.append("  \"").append(key).append("\": \"").append(escapeJson(value)).append('"');
  }

  private static void optionalLong(StringBuilder sb, boolean[] first, String key, Long value) {
    if (value == null) return;
    appendSeparator(sb, first);
    sb.append("  \"").append(key).append("\": ").append(value);
  }

  private static void optionalBoolean(
      StringBuilder sb, boolean[] first, String key, Boolean value) {
    if (value == null) return;
    appendSeparator(sb, first);
    sb.append("  \"").append(key).append("\": ").append(value);
  }

  private static void optionalInteger(
      StringBuilder sb, boolean[] first, String key, Integer value) {
    if (value == null) return;
    appendSeparator(sb, first);
    sb.append("  \"").append(key).append("\": ").append(value);
  }

  private static void appendSeparator(StringBuilder sb, boolean[] first) {
    if (!first[0]) sb.append(",\n");
    first[0] = false;
  }

  private static String escapeJson(String s) {
    return JsonEscape.escape(s);
  }
}
