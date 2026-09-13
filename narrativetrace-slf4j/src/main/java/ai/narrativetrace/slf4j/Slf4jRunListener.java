/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.slf4j;

import ai.narrativetrace.api.spi.RunListener;
import org.slf4j.MDC;

/**
 * Attaches the enclosing test-suite run's phrase to MDC as {@code runName} — discovered through
 * {@link java.util.ServiceLoader} the moment this module is on the classpath, exactly like {@link
 * Slf4jTraceEventListener}'s narration is composed automatically by {@code PipelineBootstrap}.
 *
 * <p>INTENT: A JUnit integration owns the run's lifecycle but must not depend on SLF4J to report it
 * (see {@link ai.narrativetrace.api.spi.RunListener}'s own note); this is the other half of that
 * seam. {@code runName} stays set for every log line any test in the run emits — "for the whole
 * run" (2026-09-13 ruling, item 2) — one grep finds one run's log lines, the same way {@code
 * traceId}/{@code traceName} let a grep find one trace's.
 */
public final class Slf4jRunListener implements RunListener {

  @Override
  public void runStarted(String runId, String runName) {
    MDC.put("runName", runName);
  }

  @Override
  public void runEnded() {
    MDC.remove("runName");
  }
}
