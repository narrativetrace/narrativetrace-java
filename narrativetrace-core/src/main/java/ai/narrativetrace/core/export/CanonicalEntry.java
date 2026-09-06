/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import java.util.List;

/**
 * Canonical flat representation of a single trace entry matching {@code entry.schema.json}.
 *
 * <p>INTENT: This is the interoperability boundary between the NarrativeTrace library and any
 * downstream consumer (NarrativeTrace backend, Datadog, Sumo Logic, etc.). Layer 1 (universal) +
 * Layer 2 (OTel semantic conventions) + Layer 3 (NarrativeTrace extensions) fields are all present
 * as flat record components.
 *
 * <p><b>@llmNote</b> Field names in this record use camelCase Java conventions. The {@link
 * CanonicalEntrySerializer} maps them to the dotted schema names (e.g. {@code codeNamespace} →
 * {@code "code.namespace"}, {@code ntEventType} → {@code "nt.eventType"}).
 *
 * @param timestamp ISO 8601 timestamp with millisecond precision
 * @param level log level: trace, warn, or error
 * @param message human-readable summary (e.g. "→ OrderService.placeOrder(customerId: C1)")
 * @param service service name (maps to OTel resource attribute service.name)
 * @param environment deployment environment (production, staging, test)
 * @param traceId W3C trace ID (32 lowercase hex characters)
 * @param spanId W3C span ID (16 lowercase hex characters)
 * @param parentSpanId parent span ID, or null for root spans
 * @param codeNamespace OTel semantic convention: class or module name
 * @param codeFunction OTel semantic convention: method or function name
 * @param ntEntryType always "entry" for atomic events
 * @param ntEventType event type: method_enter, method_exit, fork, join, async_dispatch
 * @param ntSchemaVersion always "1.0"
 * @param ntTraceName human-readable 3-word name derived from traceId
 * @param ntStoryId story identifier, or null if not yet derived
 * @param ntChapterId chapter identifier, or null if not yet derived
 * @param ntOutcome outcome of the method call: success, failure, incomplete, or null
 * @param ntForkId fork group identifier for fork/join events, or null
 * @param ntBranchIndex branch index within a fork group, or null
 * @param ntCausalId causal chain identifier, or null (deferred)
 * @param durationMs method duration in milliseconds, or null (present on exit events)
 * @param ntParameters method parameters as name-value pairs, or null
 * @param ntReturnValue serialized return value, or null
 * @param exceptionType exception class name, or null
 * @param exceptionMessage exception message, or null
 * @param ntNarrationTemplate raw {@code @Narrated} template with placeholders intact, or null;
 *     added in schema 1.1 so translated views can render per-locale template variants
 * @param ntPackage package of the declaring type behind {@code codeNamespace} (e.g. {@code
 *     "com.acme.billing"}), or null; added in schema 1.2 — {@code code.namespace} stays a simple
 *     name for readability, this field completes the identity
 * @param ntExceptionPackage package of the exception class behind {@code exceptionType}, or null;
 *     added in schema 1.2 — {@code exception.type} stays a simple name, this field completes the
 *     identity
 * @param ntReturnType declared return type in {@link Class#getTypeName()} form, or null; added in
 *     schema 1.2 — with parameter types this makes overloads distinguishable
 * @param threadName OTel semantic convention thread.name: executing thread's name at entry, or
 *     null; added in schema 1.2
 * @param threadId OTel semantic convention thread.id: executing thread's numeric id at entry, or
 *     null; added in schema 1.2
 * @param ntThreadVirtual whether the method ran on a virtual thread, or null when thread identity
 *     was not captured; added in schema 1.2
 * @param hostName OTel semantic convention host.name: auto-detected host, or null when {@code
 *     narrativetrace.capture.resource} is disabled or detection failed; added in schema 1.2
 * @param processPid OTel semantic convention process.pid, or null when resource capture is
 *     disabled; added in schema 1.2
 * @param runtimeVersion OTel semantic convention process.runtime.version, or null when resource
 *     capture is disabled; added in schema 1.2
 * @param ntInstanceId identity hash of the receiver object in lowercase hex, or null when {@code
 *     narrativetrace.capture.instanceIds} is disabled (the default) or the method is static; added
 *     in schema 1.2
 * @param codeFilepath OTel semantic convention code.filepath: source file of the entry, or null
 *     when {@code narrativetrace.capture.sourceLocation} is disabled (the default) or unknown;
 *     agent records the declaration site, proxy the call site; added in schema 1.2
 * @param codeLineno OTel semantic convention code.lineno: 1-based line number, or null; added in
 *     schema 1.2
 */
public record CanonicalEntry(
    String timestamp,
    String level,
    String message,
    String service,
    String environment,
    String traceId,
    String spanId,
    String parentSpanId,
    String codeNamespace,
    String codeFunction,
    String ntEntryType,
    String ntEventType,
    String ntSchemaVersion,
    String ntTraceName,
    String ntStoryId,
    String ntChapterId,
    String ntOutcome,
    String ntForkId,
    Integer ntBranchIndex,
    String ntCausalId,
    Long durationMs,
    List<ParameterEntry> ntParameters,
    String ntReturnValue,
    String exceptionType,
    String exceptionMessage,
    String ntNarrationTemplate,
    String ntPackage,
    String ntExceptionPackage,
    String ntReturnType,
    String threadName,
    Long threadId,
    Boolean ntThreadVirtual,
    String hostName,
    Long processPid,
    String runtimeVersion,
    String ntInstanceId,
    String codeFilepath,
    Integer codeLineno) {

  /**
   * Canonical entry schema version emitted by the library. 1.2 added the additive nullable identity
   * fields {@code nt.package}, {@code nt.exceptionPackage}, {@code nt.returnType}, the {@code type}
   * property on {@code nt.parameters} items, thread identity ({@code thread.name}, {@code
   * thread.id}, {@code nt.threadVirtual}), process resource identity ({@code host.name}, {@code
   * process.pid}, {@code process.runtime.version}), {@code nt.instanceId}, and source location
   * ({@code code.filepath}, {@code code.lineno}).
   */
  public static final String SCHEMA_VERSION = "1.2";

  /** Creates an empty builder; every field starts {@code null}. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns a builder pre-populated with this entry's fields, for selective override. */
  public Builder toBuilder() {
    return new Builder()
        .timestamp(timestamp)
        .level(level)
        .message(message)
        .service(service)
        .environment(environment)
        .traceId(traceId)
        .spanId(spanId)
        .parentSpanId(parentSpanId)
        .codeNamespace(codeNamespace)
        .codeFunction(codeFunction)
        .ntEntryType(ntEntryType)
        .ntEventType(ntEventType)
        .ntSchemaVersion(ntSchemaVersion)
        .ntTraceName(ntTraceName)
        .ntStoryId(ntStoryId)
        .ntChapterId(ntChapterId)
        .ntOutcome(ntOutcome)
        .ntForkId(ntForkId)
        .ntBranchIndex(ntBranchIndex)
        .ntCausalId(ntCausalId)
        .durationMs(durationMs)
        .ntParameters(ntParameters)
        .ntReturnValue(ntReturnValue)
        .exceptionType(exceptionType)
        .exceptionMessage(exceptionMessage)
        .ntNarrationTemplate(ntNarrationTemplate)
        .ntPackage(ntPackage)
        .ntExceptionPackage(ntExceptionPackage)
        .ntReturnType(ntReturnType)
        .threadName(threadName)
        .threadId(threadId)
        .ntThreadVirtual(ntThreadVirtual)
        .hostName(hostName)
        .processPid(processPid)
        .runtimeVersion(runtimeVersion)
        .ntInstanceId(ntInstanceId)
        .codeFilepath(codeFilepath)
        .codeLineno(codeLineno);
  }

  /**
   * Builder mirroring the record components one setter per field.
   *
   * <p>INTENT: The canonical constructor is wide and positional; construction sites (mappers,
   * projections, JSON readers, tests) use this builder so additive schema evolution (new nullable
   * fields per the 1.x contract) never rewrites every call site.
   */
  public static final class Builder {

    private String timestamp;
    private String level;
    private String message;
    private String service;
    private String environment;
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String codeNamespace;
    private String codeFunction;
    private String ntEntryType;
    private String ntEventType;
    private String ntSchemaVersion;
    private String ntTraceName;
    private String ntStoryId;
    private String ntChapterId;
    private String ntOutcome;
    private String ntForkId;
    private Integer ntBranchIndex;
    private String ntCausalId;
    private Long durationMs;
    private List<ParameterEntry> ntParameters;
    private String ntReturnValue;
    private String exceptionType;
    private String exceptionMessage;
    private String ntNarrationTemplate;
    private String ntPackage;
    private String ntExceptionPackage;
    private String ntReturnType;
    private String threadName;
    private Long threadId;
    private Boolean ntThreadVirtual;
    private String hostName;
    private Long processPid;
    private String runtimeVersion;
    private String ntInstanceId;
    private String codeFilepath;
    private Integer codeLineno;

    private Builder() {}

    public Builder timestamp(String timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder level(String level) {
      this.level = level;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder service(String service) {
      this.service = service;
      return this;
    }

    public Builder environment(String environment) {
      this.environment = environment;
      return this;
    }

    public Builder traceId(String traceId) {
      this.traceId = traceId;
      return this;
    }

    public Builder spanId(String spanId) {
      this.spanId = spanId;
      return this;
    }

    public Builder parentSpanId(String parentSpanId) {
      this.parentSpanId = parentSpanId;
      return this;
    }

    public Builder codeNamespace(String codeNamespace) {
      this.codeNamespace = codeNamespace;
      return this;
    }

    public Builder codeFunction(String codeFunction) {
      this.codeFunction = codeFunction;
      return this;
    }

    public Builder ntEntryType(String ntEntryType) {
      this.ntEntryType = ntEntryType;
      return this;
    }

    public Builder ntEventType(String ntEventType) {
      this.ntEventType = ntEventType;
      return this;
    }

    public Builder ntSchemaVersion(String ntSchemaVersion) {
      this.ntSchemaVersion = ntSchemaVersion;
      return this;
    }

    public Builder ntTraceName(String ntTraceName) {
      this.ntTraceName = ntTraceName;
      return this;
    }

    public Builder ntStoryId(String ntStoryId) {
      this.ntStoryId = ntStoryId;
      return this;
    }

    public Builder ntChapterId(String ntChapterId) {
      this.ntChapterId = ntChapterId;
      return this;
    }

    public Builder ntOutcome(String ntOutcome) {
      this.ntOutcome = ntOutcome;
      return this;
    }

    public Builder ntForkId(String ntForkId) {
      this.ntForkId = ntForkId;
      return this;
    }

    public Builder ntBranchIndex(Integer ntBranchIndex) {
      this.ntBranchIndex = ntBranchIndex;
      return this;
    }

    public Builder ntCausalId(String ntCausalId) {
      this.ntCausalId = ntCausalId;
      return this;
    }

    public Builder durationMs(Long durationMs) {
      this.durationMs = durationMs;
      return this;
    }

    public Builder ntParameters(List<ParameterEntry> ntParameters) {
      this.ntParameters = ntParameters;
      return this;
    }

    public Builder ntReturnValue(String ntReturnValue) {
      this.ntReturnValue = ntReturnValue;
      return this;
    }

    public Builder exceptionType(String exceptionType) {
      this.exceptionType = exceptionType;
      return this;
    }

    public Builder exceptionMessage(String exceptionMessage) {
      this.exceptionMessage = exceptionMessage;
      return this;
    }

    public Builder ntNarrationTemplate(String ntNarrationTemplate) {
      this.ntNarrationTemplate = ntNarrationTemplate;
      return this;
    }

    public Builder ntPackage(String ntPackage) {
      this.ntPackage = ntPackage;
      return this;
    }

    public Builder ntExceptionPackage(String ntExceptionPackage) {
      this.ntExceptionPackage = ntExceptionPackage;
      return this;
    }

    public Builder ntReturnType(String ntReturnType) {
      this.ntReturnType = ntReturnType;
      return this;
    }

    public Builder threadName(String threadName) {
      this.threadName = threadName;
      return this;
    }

    public Builder threadId(Long threadId) {
      this.threadId = threadId;
      return this;
    }

    public Builder ntThreadVirtual(Boolean ntThreadVirtual) {
      this.ntThreadVirtual = ntThreadVirtual;
      return this;
    }

    public Builder hostName(String hostName) {
      this.hostName = hostName;
      return this;
    }

    public Builder processPid(Long processPid) {
      this.processPid = processPid;
      return this;
    }

    public Builder runtimeVersion(String runtimeVersion) {
      this.runtimeVersion = runtimeVersion;
      return this;
    }

    public Builder ntInstanceId(String ntInstanceId) {
      this.ntInstanceId = ntInstanceId;
      return this;
    }

    public Builder codeFilepath(String codeFilepath) {
      this.codeFilepath = codeFilepath;
      return this;
    }

    public Builder codeLineno(Integer codeLineno) {
      this.codeLineno = codeLineno;
      return this;
    }

    /** Builds the {@link CanonicalEntry}. */
    public CanonicalEntry build() {
      return new CanonicalEntry(
          timestamp,
          level,
          message,
          service,
          environment,
          traceId,
          spanId,
          parentSpanId,
          codeNamespace,
          codeFunction,
          ntEntryType,
          ntEventType,
          ntSchemaVersion,
          ntTraceName,
          ntStoryId,
          ntChapterId,
          ntOutcome,
          ntForkId,
          ntBranchIndex,
          ntCausalId,
          durationMs,
          ntParameters,
          ntReturnValue,
          exceptionType,
          exceptionMessage,
          ntNarrationTemplate,
          ntPackage,
          ntExceptionPackage,
          ntReturnType,
          threadName,
          threadId,
          ntThreadVirtual,
          hostName,
          processPid,
          runtimeVersion,
          ntInstanceId,
          codeFilepath,
          codeLineno);
    }
  }
}
