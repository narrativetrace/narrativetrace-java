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
 * Outcome attached to a {@link TraceNode} or exit event.
 *
 * <p>INTENT: Renderers and exporters pattern-match on this sealed hierarchy instead of inferring
 * success or failure from raw exceptions or missing events.
 *
 * <p><b>@llmNote</b> Do not assume every node has a terminal success or failure value. {@link
 * Incomplete} means an enter event was observed without a matching exit, and some synthetic nodes
 * such as fire-and-forget launchers may carry a {@code null} outcome instead.
 */
public sealed interface TraceOutcome {

  /**
   * Normal method completion with an eager rendered return value.
   *
   * @param renderedValue the return value pre-rendered to String, or {@code null} for void methods
   * @param structuredValue optional type-preserving representation for OTel typed attributes, null
   *     when not captured
   */
  record Returned(String renderedValue, RenderedValue structuredValue) implements TraceOutcome {

    /** Backwards-compatible constructor for code that does not capture structured values. */
    public Returned(String renderedValue) {
      this(renderedValue, null);
    }
  }

  /**
   * Exceptional method completion with the original throwable attached.
   *
   * @param exception the thrown exception captured at the exit boundary
   */
  record Threw(Throwable exception) implements TraceOutcome {}

  /** A method still in-flight at snapshot time because no matching exit event was seen. */
  record Incomplete() implements TraceOutcome {}
}
