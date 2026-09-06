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
/**
 * Immutable event and tree model used throughout NarrativeTrace.
 *
 * <p>{@link ai.narrativetrace.api.event.TraceEvent} is the append-only capture format. {@link
 * ai.narrativetrace.api.event.TraceNode} is the query format built from those events. The same
 * package also carries eager parameter captures, method signatures, span correlation data, and
 * concurrency metadata.
 *
 * <p>Captured values are already rendered to strings before they reach this package. That keeps the
 * event model stable for exporters and renderers: they never inspect live objects.
 *
 * <p>{@code TraceEvent} is a sealed interface with variants: {@code EnterEvent}, {@code ExitEvent},
 * {@code ForkCreatedEvent}, {@code MergeEvent}, and {@code FireAndForgetEvent}. The diagram below
 * covers the tree model that is built from those events.
 *
 * <h2>Domain model</h2>
 *
 * <pre>{@code
 * ┌─────────────────────────────────────────────────────────┐
 * │                       TraceTree                         │
 * │  (interface — result of context.captureTrace())         │
 * │─────────────────────────────────────────────────────────│
 * │  roots(): List<TraceNode>                               │
 * │  isEmpty(): boolean                                     │
 * └──────────────────────┬──────────────────────────────────┘
 *                        │ 0..*
 *                        ▼
 * ┌─────────────────────────────────────────────────────────┐
 * │                   TraceNode (record)                    │
 * │  One method invocation in the call tree                 │
 * │─────────────────────────────────────────────────────────│
 * │  signature:      MethodSignature                        │
 * │  children:       List<TraceNode>  ◄── recursive tree    │
 * │  outcome:        TraceOutcome?         (nullable for    │
 * │                                          synthetic      │
 * │                                          launcher nodes)│
 * │  durationNanos:  long                                   │
 * │  startTimeNanos: long                                   │
 * │  concurrency:    ConcurrencyInfo?      (nullable)       │
 * │  spanContext:    SpanContext?          (nullable)       │
 * └───┬──────────────────┬──────────────────┬───────────────┘
 *     │                  │                  │                  │
 *     ▼                  ▼                  ▼                  ▼
 * ┌──────────────┐ ┌───────────────┐ ┌─────────────────────┐ ┌──────────────┐
 * │MethodSignature│ │ TraceOutcome  │ │  ConcurrencyInfo    │ │ SpanContext  │
 * │   (record)   │ │  (sealed)     │ │     (record)        │ │   (record)   │
 * │──────────────│ │───────────────│ │─────────────────────│ │──────────────│
 * │ className    │ │ Returned      │ │ groupId: String     │ │ traceId      │
 * │ methodName   │ │  renderedValue│ │ threadName: String  │ │ spanId       │
 * │ parameters   │ │ Threw         │ │ threadId: long      │ │ parentSpanId │
 * │ narration    │ │  exception    │ │ virtual: boolean    │ │ service/http │
 * │ errorContext │ │ Incomplete    │ │ kind: ConcurrencyKind│ │ user fields  │
 * └──────┬───────┘ └───────────────┘ └──────────┬──────────┘ └──────────────┘
 *        │ 0..*                                 │
 *        ▼                                      ▼
 * ┌────────────────┐                 ┌──────────────────┐
 * │ParameterCapture│                 │ ConcurrencyKind  │
 * │   (record)     │                 │    (enum)        │
 * │────────────────│                 │──────────────────│
 * │ name: String   │                 │ FORK_JOIN        │
 * │ renderedValue  │                 │ FIRE_AND_FORGET  │
 * │ redacted: bool │                 └──────────────────┘
 * └────────────────┘
 * }</pre>
 */
package ai.narrativetrace.api.event;
