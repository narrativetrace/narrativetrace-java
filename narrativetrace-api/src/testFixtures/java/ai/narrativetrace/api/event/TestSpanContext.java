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

/** Test utility: creates valid SpanContext instances with random IDs. */
public final class TestSpanContext {

  private TestSpanContext() {}

  /** Returns a root SpanContext with random traceId/spanId and no parent. */
  public static SpanContext create() {
    return SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
  }

  /** Returns a child SpanContext sharing the given parent's traceId. */
  public static SpanContext childOf(SpanContext parent) {
    return SpanContext.builder(parent.traceId(), SpanIdGenerator.spanId())
        .parentSpanId(parent.spanId())
        .build();
  }
}
