/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineIntegrationTest {

  @Test
  void enterMethodPublishesToPipeline() {
    var published = new ArrayList<TraceEvent>();
    var pipeline = capturingPipeline(published);
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
    var signature =
        new MethodSignature("OrderService", "placeOrder", List.of(param("orderId", "\"42\"")));

    context.enterMethod(signature);

    assertThat(published).hasSize(1);
    assertThat(published.get(0)).isInstanceOf(TraceEvent.EnterEvent.class);
    var enter = (TraceEvent.EnterEvent) published.get(0);
    assertThat(enter.signature().className()).isEqualTo("OrderService");
  }

  @Test
  void exitMethodPublishesToPipeline() {
    var published = new ArrayList<TraceEvent>();
    var pipeline = capturingPipeline(published);
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
    context.enterMethod(new MethodSignature("Svc", "run", List.of()));

    context.exitMethodWithReturn("\"ok\"");

    assertThat(published).hasSize(2);
    assertThat(published.get(1)).isInstanceOf(TraceEvent.ExitEvent.class);
  }

  @Test
  void exceptionExitPublishesToPipeline() {
    var published = new ArrayList<TraceEvent>();
    var pipeline = capturingPipeline(published);
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
    context.enterMethod(new MethodSignature("Svc", "run", List.of()));

    context.exitMethodWithException(new RuntimeException("boom"), null);

    assertThat(published).hasSize(2);
    var exit = (TraceEvent.ExitEvent) published.get(1);
    assertThat(exit.outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void handleBasedExitPublishesToPipeline() {
    var published = new ArrayList<TraceEvent>();
    var pipeline = capturingPipeline(published);
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
    SpanId handle = context.enterMethod(new MethodSignature("Svc", "run", List.of()));
    context.detachFrame(handle);

    context.exitMethodWithReturn("\"ok\"", handle);

    assertThat(published).hasSize(2);
    assertThat(published.get(1)).isInstanceOf(TraceEvent.ExitEvent.class);
    var enter = (TraceEvent.EnterEvent) published.get(0);
    var exit = (TraceEvent.ExitEvent) published.get(1);
    assertThat(exit.spanContext().spanId()).isEqualTo(enter.spanContext().spanId());
  }

  @Test
  void onForkCreatedPublishesToPipeline() {
    var published = new ArrayList<TraceEvent>();
    var pipeline = capturingPipeline(published);
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);

    context.onForkCreated("fork-1");

    assertThat(published).hasSize(1);
    assertThat(published.get(0)).isInstanceOf(TraceEvent.ForkCreatedEvent.class);
    var event = (TraceEvent.ForkCreatedEvent) published.get(0);
    assertThat(event.groupId()).isEqualTo("fork-1");
  }

  @Test
  void onMergePublishesToPipeline() {
    var published = new ArrayList<TraceEvent>();
    var pipeline = capturingPipeline(published);
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);

    context.onMerge("fork-1", List.of());

    assertThat(published).hasSize(1);
    assertThat(published.get(0)).isInstanceOf(TraceEvent.MergeEvent.class);
  }

  @Test
  void onFireAndForgetPublishesToPipeline() {
    var published = new ArrayList<TraceEvent>();
    var pipeline = capturingPipeline(published);
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);

    context.onFireAndForgetLaunched("ff-1");

    assertThat(published).hasSize(1);
    assertThat(published.get(0)).isInstanceOf(TraceEvent.FireAndForgetEvent.class);
  }

  @Test
  void noPipelineDoesNotAffectTraceCapture() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Svc", "run", List.of()));
    context.exitMethodWithReturn("\"ok\"");

    var trace = context.captureTrace();

    assertThat(trace.roots()).hasSize(1);
    assertThat(trace.roots().get(0).signature().methodName()).isEqualTo("run");
  }

  private static ParameterCapture param(String name, String value) {
    return new ParameterCapture(name, value, false);
  }

  private static EventPipeline capturingPipeline(List<TraceEvent> sink) {
    return new EventPipeline() {
      @Override
      public void publish(TraceEvent event) {
        sink.add(event);
      }

      @Override
      public void close() {
        // no-op
      }
    };
  }
}
