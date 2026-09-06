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

import java.util.List;

/**
 * Immutable node in a captured trace tree.
 *
 * <p>INTENT: This is the main read model for renderers, exporters, and tests. One node usually
 * represents one method invocation, although synthetic launcher nodes may also appear for
 * fire-and-forget coordination.
 *
 * <p>Each node carries the captured signature, child calls, outcome, timing, optional concurrency
 * metadata, and optional {@link SpanContext} for correlation.
 *
 * @param signature Semantic identity of the call, including eager parameter captures and resolved
 *     narration or error context.
 * @param children Nested calls that occurred within this node's lifetime.
 * @param outcome Completion state, {@code null} for synthetic launcher nodes, or {@link
 *     TraceOutcome.Incomplete} when capture ended before exit.
 * @param durationNanos Wall-clock duration in nanoseconds. Zero may mean either "instant" or "not
 *     yet known", depending on the node type.
 * @param startTimeNanos Start timestamp derived from {@link System#nanoTime()}.
 * @param concurrency Concurrency metadata used by renderers to group forked or background work.
 * @param spanContext Correlation and request metadata attached to the node's span.
 * @param thread Identity of the thread that executed the entry, or {@code null} when the source
 *     events predate thread capture.
 */
public record TraceNode(
    MethodSignature signature,
    List<TraceNode> children,
    TraceOutcome outcome,
    long durationNanos,
    long startTimeNanos,
    ConcurrencyInfo concurrency,
    SpanContext spanContext,
    ThreadInfo thread) {

  public TraceNode(MethodSignature signature, List<TraceNode> children, TraceOutcome outcome) {
    this(signature, children, outcome, 0L, 0L, null, null, null);
  }

  public TraceNode(
      MethodSignature signature,
      List<TraceNode> children,
      TraceOutcome outcome,
      long durationNanos) {
    this(signature, children, outcome, durationNanos, 0L, null, null, null);
  }

  public TraceNode(
      MethodSignature signature,
      List<TraceNode> children,
      TraceOutcome outcome,
      long durationNanos,
      long startTimeNanos,
      ConcurrencyInfo concurrency) {
    this(signature, children, outcome, durationNanos, startTimeNanos, concurrency, null, null);
  }

  /** Compatibility constructor for call sites that carry no thread identity. */
  public TraceNode(
      MethodSignature signature,
      List<TraceNode> children,
      TraceOutcome outcome,
      long durationNanos,
      long startTimeNanos,
      ConcurrencyInfo concurrency,
      SpanContext spanContext) {
    this(
        signature,
        children,
        outcome,
        durationNanos,
        startTimeNanos,
        concurrency,
        spanContext,
        null);
  }

  /** Returns {@link #durationNanos()} truncated to whole milliseconds. */
  public long durationMillis() {
    return durationNanos / 1_000_000;
  }
}
