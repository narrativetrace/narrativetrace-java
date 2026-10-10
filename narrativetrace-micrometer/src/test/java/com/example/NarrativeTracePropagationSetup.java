/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

// The framework table's Micrometer row embeds the region below as its wiring snippet — the
// doctor's config.micrometer-accessor fix line, llms-full.md's integration table.
// NarrativeTracePropagationSetupTest proves it wires: a captured snapshot carries the trace onto
// another thread.
// snippet:begin wiring
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.micrometer.NarrativeTraceThreadLocalAccessor;
import io.micrometer.context.ContextRegistry;

public final class NarrativeTracePropagationSetup {
  private NarrativeTracePropagationSetup() {}

  /** Call once at startup, with the context your traced services use. */
  public static void register(ThreadLocalNarrativeContext context) {
    ContextRegistry.getInstance()
        .registerThreadLocalAccessor(new NarrativeTraceThreadLocalAccessor(context));
  }
}
// snippet:end wiring
