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
package ai.narrativetrace.api.render;

import ai.narrativetrace.api.tree.TraceTree;

/**
 * Functional interface for turning a {@link TraceTree} into text.
 *
 * <p>INTENT: Prefer this as the extension point for custom output formats. Built-in renderers,
 * diagram adapters, and test support utilities all depend on this contract.
 *
 * <p><b>@pattern</b> Pure view transformation over an immutable trace tree.
 *
 * <pre>{@code
 * NarrativeRenderer renderer = new MarkdownRenderer();
 * String output = renderer.render(traceTree);
 * }</pre>
 *
 * <p>The shipped implementations — {@code MarkdownRenderer}, {@code ProseRenderer}, {@code
 * IndentedTextRenderer}, {@code StructuralTraceRenderer} — all live in the runtime module. Adding
 * one there can never change what an external renderer had to compile against.
 */
public interface NarrativeRenderer {

  /**
   * Renders the trace tree to a string.
   *
   * @param tree Immutable tree to render. Implementations should not mutate or retain it.
   * @return Fully rendered output.
   */
  String render(TraceTree tree);
}
