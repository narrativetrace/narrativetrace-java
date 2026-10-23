/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import ai.narrativetrace.core.render.ExceptionMessage;
import ai.narrativetrace.core.render.RedactionPolicy;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Desired-contract reproducers for the 2026-10-22 adversarial review; failures are intentional. */
class AdversarialOtelReviewTest {

  private final InMemorySpanExporter output = InMemorySpanExporter.create();
  private final SdkTracerProvider provider =
      SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(output)).build();

  @AfterEach
  void closeProvider() {
    provider.close();
  }

  @Test
  void liveSpansMustUseEpochTimestampsForEventsCapturedByTheRealContext() {
    var listener = new OtelTraceEventListener(provider.get("review"));
    try (var pipeline = DualPathPipeline.narrationOnly(listener)) {
      var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
      long earliestEpochNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis() - 60_000);
      try {
        var handle = context.enterMethod(new MethodSignature("Service", "work", List.of()));
        context.exitMethodWithReturn("42", handle);
        var span = output.getFinishedSpanItems().get(0);
        long latestEpochNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis() + 60_000);

        assertThat(span.getStartEpochNanos())
            .as("a call made now must not be exported near the Unix epoch")
            .isBetween(earliestEpochNanos, latestEpochNanos);
      } finally {
        context.reset();
      }
    }
  }

  @Test
  void lateAsyncChildMustRemainAttachedToItsFinishedLocalParent() {
    var listener = new OtelTraceEventListener(provider.get("review"));
    try (var pipeline = DualPathPipeline.narrationOnly(listener)) {
      var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
      try {
        var parentHandle = context.enterMethod(new MethodSignature("Service", "parent", List.of()));
        var continuation = context.snapshot();
        context.exitMethodWithReturn("42", parentHandle);
        // A scheduled task starts after the launching method has returned.
        continuation
            .wrap(
                (Runnable)
                    () -> {
                      var childHandle =
                          context.enterMethod(new MethodSignature("Worker", "child", List.of()));
                      context.exitMethodWithReturn("43", childHandle);
                    })
            .run();

        var spans = output.getFinishedSpanItems();
        assertThat(spans).hasSize(2);
        var parent = spans.get(0);
        var child = spans.get(1);
        assertThat(parent.getName()).isEqualTo("Service.parent");
        assertThat(child.getName()).isEqualTo("Worker.child");
        assertThat(child.getParentSpanId())
            .as("a completed local parent is not an unknown remote parent")
            .isEqualTo(parent.getSpanId());
        assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
      } finally {
        context.reset();
      }
    }
  }

  @Test
  void exceptionEventsMustRespectTheSameRedactionAsStatusDescriptions() {
    // Public test PAN, not a real credential. The policy explicitly recognizes these bytes.
    var sensitive = "4111111111111111";
    var failure = new IllegalStateException(sensitive);
    failure.setStackTrace(new StackTraceElement[0]);
    assertThat(ExceptionMessage.text(failure)).isEqualTo(RedactionPolicy.MARKER);
    var node =
        new TraceNode(
            new MethodSignature("Payment", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(failure));

    new TraceSpanExporter(provider.get("review")).export(List.of(node));

    var span = output.getFinishedSpanItems().get(0);
    assertThat(span.getStatus().getDescription()).isEqualTo(RedactionPolicy.MARKER);
    assertThat(span.getEvents()).hasSize(1);
    assertThat(span.getEvents().get(0).getAttributes().toString())
        .as("exception.message and exception.stacktrace must not reintroduce redacted bytes")
        .doesNotContain(sensitive);
  }
}
