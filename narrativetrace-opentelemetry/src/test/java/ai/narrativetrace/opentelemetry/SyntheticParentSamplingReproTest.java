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
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Reproduction: does a late child parented on a reconstructed context survive the SDK's default
 * sampler? Sampler used: {@code Sampler.parentBased(Sampler.alwaysOn())}, set explicitly (it is the
 * SDK default).
 */
class SyntheticParentSamplingReproTest {

  private final InMemorySpanExporter output = InMemorySpanExporter.create();
  private final List<Integer> childFlags = new ArrayList<>();
  private SdkTracerProvider provider;

  @AfterEach
  void closeProvider() {
    if (provider != null) {
      provider.close();
    }
  }

  private SdkTracerProvider provider(boolean mirrored) {
    var builder =
        SdkTracerProvider.builder()
            .setSampler(Sampler.parentBased(Sampler.alwaysOn()))
            .addSpanProcessor(SimpleSpanProcessor.create(output));
    if (mirrored) {
      builder.setIdGenerator(new NarrativeTraceIdGenerator());
    }
    provider = builder.build();
    return provider;
  }

  private void parentThenLateChild(OtelTraceEventListener listener) {
    java.util.function.Consumer<TraceEvent> spy =
        event -> {
          if (event instanceof TraceEvent.EnterEvent e
              && e.signature().className().equals("Worker")) {
            childFlags.add(e.spanContext().traceFlags());
          }
          listener.accept(event);
        };
    try (var pipeline = DualPathPipeline.narrationOnly(spy::accept)) {
      var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
      try {
        var parent = context.enterMethod(new MethodSignature("Service", "parent", List.of()));
        var continuation = context.snapshot();
        context.exitMethodWithReturn("1", parent);
        var other = context.enterMethod(new MethodSignature("Service", "other", List.of()));
        context.exitMethodWithReturn("2", other);
        continuation
            .wrap(
                (Runnable)
                    () -> {
                      var child =
                          context.enterMethod(new MethodSignature("Worker", "child", List.of()));
                      context.exitMethodWithReturn("3", child);
                    })
            .run();
      } finally {
        context.reset();
      }
    }
  }

  private SpanData find(String name) {
    return output.getFinishedSpanItems().stream()
        .filter(s -> s.getName().equals(name))
        .findFirst()
        .orElse(null);
  }

  private void assertChildExportedUnderParent() {
    var parent = find("Service.parent");
    var child = find("Worker.child");
    assertThat(parent).as("parent exported").isNotNull();
    assertThat(child)
        .as("late child exported (child flags seen by listener: %s)", childFlags)
        .isNotNull();
    if (child.getAttributes().get(OtelTraceEventListener.PARENT_SYNTHETIC) != null) {
      // not-mirrored, past memory: the parent is the reconstructed NarrativeTrace id by design
      assertThat(child.getParentSpanId())
          .isEqualTo(child.getAttributes().get(OtelTraceEventListener.PARENT_SPAN_ID));
    } else {
      assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
    }
  }

  @Test
  void a_lateChildPastTheMemoryIsExportedUnderDefaultSampler() {
    parentThenLateChild(
        new OtelTraceEventListener(provider(false).get("t"), 1, Duration.ofHours(1)));
    assertChildExportedUnderParent();
  }

  @Test
  void b_lateChildPastTheMemoryIsExportedInMirroredMode() {
    parentThenLateChild(
        new OtelTraceEventListener(provider(true).get("t"), 1, Duration.ofHours(1)));
    assertChildExportedUnderParent();
  }

  @Test
  void c_controlChildWithinTheMemoryIsExported() {
    parentThenLateChild(
        new OtelTraceEventListener(provider(false).get("t"), 8, Duration.ofHours(1)));
    assertChildExportedUnderParent();
  }
}
