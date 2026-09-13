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

/**
 * Additive extension point notified of a test-suite run's boundary and its identity.
 *
 * <p>INTENT: The seam an optional observability module — SLF4J's MDC, say — uses to attach
 * whole-run context without the JUnit integration module depending on it directly. Same shape as
 * {@link TraceEventListener} (per event) and {@link ReportContributor} (a run's accumulated
 * traces), this one for the run's own boundary: a JUnit 4 or JUnit 5 integration owns exactly one
 * run per test-suite execution and reports it here, and this module never needs to know what — if
 * anything — is listening. Implementations are discovered through {@link java.util.ServiceLoader}
 * and active by classpath presence; many can coexist and ordering between them is undefined.
 *
 * <p><b>@llmNote</b> {@link #runStarted} is called on the thread about to execute each test, not
 * once for the whole run: a run-scoped value set only once would not reach a thread a parallel test
 * runner creates afterward, and MDC (or any thread-local mechanism) offers no other way in.
 * Implementations MUST therefore be idempotent — the same {@code (runId, runName)} pair is passed
 * every time for one run — and cheap, since this runs on the hot path once per test.
 *
 * <p><b>@sideEffects</b> A listener that throws is reported and skipped by the caller; neither the
 * remaining listeners nor the run itself fail because of it.
 *
 * @see TraceEventListener
 * @see ReportContributor
 */
public interface RunListener {

  /**
   * Called on the thread about to execute a test, naming the run it belongs to.
   *
   * @param runId the run's own W3C-shaped id
   * @param runName the run's three-word phrase derived from {@code runId}
   */
  void runStarted(String runId, String runName);

  /** Called once, when the whole test-suite execution ends. No-op by default. */
  default void runEnded() {}
}
