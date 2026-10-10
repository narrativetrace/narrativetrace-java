/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The OpenTelemetry row's wiring snippet, run: a call traced through the context the doctor's fix
 * line and the docs build becomes a finished span named after the method.
 */
class NarrativeTraceOtelSetupTest {

  @Test
  void aTracedCallBecomesAFinishedSpan() {
    var spans = InMemorySpanExporter.create();
    try (var tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build()) {
      var openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
      var context = NarrativeTraceOtelSetup.tracedContext(openTelemetry);

      context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
      context.exitMethodWithReturn("\"ok\"");

      assertThat(spans.getFinishedSpanItems())
          .singleElement()
          .satisfies(span -> assertThat(span.getName()).isEqualTo("OrderService.placeOrder"));
    }
  }
}
