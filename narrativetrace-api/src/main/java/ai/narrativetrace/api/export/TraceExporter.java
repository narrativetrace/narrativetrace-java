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
package ai.narrativetrace.api.export;

import ai.narrativetrace.api.tree.TraceTree;

/**
 * Request-boundary export hook.
 *
 * <p>INTENT: Framework integrations call this after a request or message-handling cycle completes
 * and a trace tree is ready to hand off to logging, storage, or telemetry code.
 *
 * <p><b>@layer</b> Integration boundary. Exporters consume completed traces; they do not
 * participate in capture.
 *
 * @see RequestContext
 */
@FunctionalInterface
public interface TraceExporter {

  /**
   * Exports a captured trace with request outcome metadata.
   *
   * @param tree Completed trace tree. Integrations typically skip the export call for empty trees.
   * @param requestContext Status and duration known only after request completion.
   */
  void export(TraceTree tree, RequestContext requestContext);
}
