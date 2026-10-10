/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

// The framework table's OpenTelemetry row embeds the region below as its wiring snippet — the
// doctor's config.otel-listener fix line, llms-full.md's integration table.
// NarrativeTraceOtelSetupTest proves it wires: a traced call becomes a finished span.
// snippet:begin wiring
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import ai.narrativetrace.opentelemetry.OtelTraceEventListener;
import io.opentelemetry.api.OpenTelemetry;

public final class NarrativeTraceOtelSetup {
  private NarrativeTraceOtelSetup() {}

  /** The context your traced services use, with every call also exported as a span. */
  public static ThreadLocalNarrativeContext tracedContext(OpenTelemetry openTelemetry) {
    var listener = new OtelTraceEventListener(openTelemetry.getTracer("narrativetrace"));
    return new ThreadLocalNarrativeContext(
        new NarrativeTraceConfig(), new DualPathPipeline(listener));
  }
}
// snippet:end wiring
