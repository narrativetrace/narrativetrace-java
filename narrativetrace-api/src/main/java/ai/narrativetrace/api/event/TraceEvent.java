/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.api.event;

/**
 * Append-only event emitted during capture.
 *
 * <p>INTENT: This is the storage and transport form of tracing. Context implementations publish
 * events as work happens, and {@code TraceTreeBuilder} later reconstructs immutable call trees from
 * them.
 *
 * <p><b>@pattern</b> Event-sourced trace model with explicit enter and exit events plus concurrency
 * lifecycle markers.
 */
public sealed interface TraceEvent {

  /**
   * Method-entry event. {@code thread} is the identity of the executing thread, captured at entry
   * on every path (plain calls included, not only concurrency events); {@code null} when the
   * emitting context predates it.
   */
  record EnterEvent(
      SpanContext spanContext,
      long timestampNanos,
      MethodSignature signature,
      ConcurrencyInfo concurrency,
      ThreadInfo thread)
      implements TraceEvent {

    public EnterEvent(SpanContext spanContext, long timestampNanos, MethodSignature signature) {
      this(spanContext, timestampNanos, signature, null, null);
    }

    /** Compatibility constructor for emitters that carry no thread identity. */
    public EnterEvent(
        SpanContext spanContext,
        long timestampNanos,
        MethodSignature signature,
        ConcurrencyInfo concurrency) {
      this(spanContext, timestampNanos, signature, concurrency, null);
    }
  }

  /**
   * Method-exit event. {@code signature} is the signature recorded at the matching enter, so exit
   * consumers (canonical mapping, @OnError templates, translation) read real identity instead of
   * re-parsing span names; {@code null} when the emitting context cannot supply it.
   */
  record ExitEvent(
      SpanContext spanContext,
      long timestampNanos,
      TraceOutcome outcome,
      String errorContext,
      MethodSignature signature)
      implements TraceEvent {

    /** Compatibility constructor for emitters that carry no entering signature. */
    public ExitEvent(
        SpanContext spanContext, long timestampNanos, TraceOutcome outcome, String errorContext) {
      this(spanContext, timestampNanos, outcome, errorContext, null);
    }
  }

  /**
   * A fork-join group was created. {@code ownerSpanId} is the span current on the launching thread
   * when the group was created — the request the event belongs to — or {@code null} when the group
   * was created outside every span (such an event belongs to no request and is never removed with
   * one).
   */
  record ForkCreatedEvent(String groupId, long timestampNanos, SpanId ownerSpanId)
      implements TraceEvent {

    /** Compatibility constructor for emitters that carry no owning span. */
    public ForkCreatedEvent(String groupId, long timestampNanos) {
      this(groupId, timestampNanos, null);
    }
  }

  /**
   * A fork-join group's members were merged. {@code ownerSpanId} is the span current on the merging
   * (launching) thread, or {@code null} outside every span.
   */
  record MergeEvent(String groupId, int memberCount, long timestampNanos, SpanId ownerSpanId)
      implements TraceEvent {

    /** Compatibility constructor for emitters that carry no owning span. */
    public MergeEvent(String groupId, int memberCount, long timestampNanos) {
      this(groupId, memberCount, timestampNanos, null);
    }
  }

  /**
   * Fire-and-forget work was launched. {@code ownerSpanId} is the span current on the launching
   * thread, or {@code null} outside every span.
   */
  record FireAndForgetEvent(String groupId, long timestampNanos, SpanId ownerSpanId)
      implements TraceEvent {

    /** Compatibility constructor for emitters that carry no owning span. */
    public FireAndForgetEvent(String groupId, long timestampNanos) {
      this(groupId, timestampNanos, null);
    }
  }

  /**
   * Returns the span id an event belongs to: an enter or exit event's own span, a group lifecycle
   * event's owning span. {@code null} when there is none — an event whose span context is absent, a
   * group event created outside every span.
   *
   * <p>INTENT: Retention keys on this. A request's end removes every event whose span id is one of
   * the request's spans, so a group event with an owner leaves with the request that launched it.
   */
  static SpanId spanIdOf(TraceEvent event) {
    if (event instanceof EnterEvent e)
      return e.spanContext() != null ? e.spanContext().spanId() : null;
    if (event instanceof ExitEvent e)
      return e.spanContext() != null ? e.spanContext().spanId() : null;
    if (event instanceof ForkCreatedEvent e) return e.ownerSpanId();
    if (event instanceof MergeEvent e) return e.ownerSpanId();
    if (event instanceof FireAndForgetEvent e) return e.ownerSpanId();
    return null;
  }
}
