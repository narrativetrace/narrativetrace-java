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

import java.nio.file.Path;
import java.util.List;

/**
 * Additive extension point invoked once at the end of a run with every accumulated trace.
 *
 * <p>INTENT: The seam for output that needs the whole run rather than one trace — cross-scenario
 * reports and derived artifacts. Implementations are discovered through {@link
 * java.util.ServiceLoader} and active by classpath presence; many can coexist and ordering between
 * them is undefined.
 *
 * <p><b>@llmNote</b> Single-threaded by construction: the caller invokes contributors in sequence
 * after the run's own artifacts are written, so no thread-safety contract applies. Each invocation
 * is isolated — a contributor that throws is reported and skipped, and neither the remaining
 * contributors nor the run itself fail.
 *
 * <p><b>@sideEffects</b> Implementations write files beneath {@code outputDir}. The directory
 * exists when the contributor is called.
 *
 * @see TraceEventListener
 */
public interface ReportContributor {

  /**
   * Contributes output derived from a completed run.
   *
   * @param traces every trace accumulated during the run, in capture order; never {@code null},
   *     possibly empty
   * @param outputDir the run's output directory, which already exists
   */
  void contribute(List<NamedTrace> traces, Path outputDir);
}
