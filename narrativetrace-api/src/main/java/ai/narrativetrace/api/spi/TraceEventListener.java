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
package ai.narrativetrace.api.spi;

import ai.narrativetrace.api.event.TraceEvent;

/**
 * Additive extension point receiving every published {@link TraceEvent}.
 *
 * <p>INTENT: The seam for observers that need the live event stream — exporters, metrics, streaming
 * analysis. Many listeners coexist, ordering between them is undefined, and one failing must not
 * affect the others or the host application. Presence on the classpath is the deployment's
 * statement of intent: implementations are discovered through {@link java.util.ServiceLoader} and
 * are active without further configuration.
 *
 * <p><b>@llmNote</b> Implementations MUST be thread-safe. Where a listener is attached is the
 * pipeline topology's decision, not the listener's: under the default dual-path topology it runs on
 * every caller thread, under a ring-buffer or queue topology on a single consumer or tailer thread.
 * A listener written against this contract keeps working when the topology changes.
 *
 * <p><b>@sideEffects</b> Runs on the caller's critical path under the default topology. Keep the
 * implementation fast and non-blocking; a listener that throws is disabled for the remainder of the
 * JVM's life after one diagnostic line, and the exception never reaches application code.
 *
 * @see ReportContributor
 */
@FunctionalInterface
public interface TraceEventListener {

  /**
   * Receives one published event.
   *
   * @param event the published event, never {@code null}
   */
  void onEvent(TraceEvent event);
}
