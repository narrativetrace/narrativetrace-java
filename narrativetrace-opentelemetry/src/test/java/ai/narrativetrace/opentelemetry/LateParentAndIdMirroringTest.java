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
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** B-161: late async children, the marked synthetic parent, and NarrativeTrace id mirroring. */
class LateParentAndIdMirroringTest {

  private final InMemorySpanExporter output = InMemorySpanExporter.create();
  private SdkTracerProvider provider;

  @AfterEach
  void closeProvider() {
    if (provider != null) {
      provider.close();
    }
  }

  private SdkTracerProvider plainProvider() {
    provider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(output)).build();
    return provider;
  }

  private SdkTracerProvider mirroredProvider() {
    provider =
        SdkTracerProvider.builder()
            .setIdGenerator(new NarrativeTraceIdGenerator())
            .addSpanProcessor(SimpleSpanProcessor.create(output))
            .build();
    return provider;
  }

  /**
   * Parent ends, an unrelated span pushes it out of a one-slot memory, then the late child runs.
   */
  private void parentForgottenThenLateChild(OtelTraceEventListener listener) {
    try (var pipeline = DualPathPipeline.narrationOnly(listener)) {
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

  private SpanData span(String name) {
    return output.getFinishedSpanItems().stream()
        .filter(s -> s.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aChildLaterThanTheMemoryGetsALocalParentMarkedSynthetic() {
    parentForgottenThenLateChild(
        new OtelTraceEventListener(plainProvider().get("t"), 1, Duration.ofHours(1)));

    var child = span("Worker.child");
    var parentNtId = child.getAttributes().get(OtelTraceEventListener.PARENT_SPAN_ID);
    assertThat(child.getAttributes().get(OtelTraceEventListener.PARENT_SYNTHETIC)).isTrue();
    assertThat(parentNtId).isNotNull();
    assertThat(child.getParentSpanId()).isEqualTo(parentNtId);
    assertThat(child.getParentSpanContext().isRemote())
        .as("a reconstructed parent is never presented as a backend-known remote one")
        .isFalse();
  }

  @Test
  void aChildWithinTheMemoryParentsOnItsRealParentUnmarked() {
    parentForgottenThenLateChild(
        new OtelTraceEventListener(plainProvider().get("t"), 8, Duration.ofHours(1)));

    var child = span("Worker.child");
    assertThat(child.getParentSpanId()).isEqualTo(span("Service.parent").getSpanId());
    assertThat(child.getAttributes().get(OtelTraceEventListener.PARENT_SYNTHETIC)).isNull();
  }

  @Test
  void mirroredIdsMakeTheReconstructedParentExactAndUnmarked() {
    parentForgottenThenLateChild(
        new OtelTraceEventListener(mirroredProvider().get("t"), 1, Duration.ofHours(1)));

    var parent = span("Service.parent");
    var child = span("Worker.child");
    assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
    assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
    assertThat(child.getAttributes().get(OtelTraceEventListener.PARENT_SYNTHETIC)).isNull();
  }

  @Test
  void mirroredSpansCarryTheirNarrativeTraceIds() {
    var listener = new OtelTraceEventListener(mirroredProvider().get("t"));
    var sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    var sig = new MethodSignature("Service", "work", List.of());

    listener.accept(new TraceEvent.EnterEvent(sc, 1_000, sig));
    listener.accept(new TraceEvent.ExitEvent(sc, 2_000, new TraceOutcome.Returned("1"), null));

    var span = output.getFinishedSpanItems().get(0);
    assertThat(span.getSpanId()).isEqualTo(sc.spanId().toString());
    assertThat(span.getTraceId()).isEqualTo(sc.traceId().toString());
  }

  @Test
  void adoptedRootStillParentsOnTheRemoteHeaderUnmarked() {
    var listener = new OtelTraceEventListener(plainProvider().get("t"));
    var remote = SpanId.of("00f067aa0ba902b7");
    var sc =
        SpanContext.builder(
                TraceId.of("4bf92f3577b34da6a3ce929d0e0e4736"), SpanIdGenerator.spanId())
            .parentSpanId(remote)
            .traceFlags(1)
            .build();
    var sig = new MethodSignature("Api", "handle", List.of());

    listener.accept(new TraceEvent.EnterEvent(sc, 1_000, sig));
    listener.accept(new TraceEvent.ExitEvent(sc, 2_000, new TraceOutcome.Returned("1"), null));

    var span = output.getFinishedSpanItems().get(0);
    assertThat(span.getParentSpanContext().isRemote()).isTrue();
    assertThat(span.getParentSpanId()).isEqualTo(remote.toString());
    assertThat(span.getAttributes().get(OtelTraceEventListener.PARENT_SYNTHETIC)).isNull();
  }

  @Test
  void theApplicationsOwnSpansKeepTheDelegatesIds() {
    var delegate = new FixedIds("aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    provider =
        SdkTracerProvider.builder()
            .setIdGenerator(new NarrativeTraceIdGenerator(delegate))
            .addSpanProcessor(SimpleSpanProcessor.create(output))
            .build();

    provider.get("app").spanBuilder("own").startSpan().end();

    var span = output.getFinishedSpanItems().get(0);
    assertThat(span.getSpanId()).isEqualTo("aaaaaaaaaaaaaaaa");
    assertThat(span.getTraceId()).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
  }

  @Test
  void liveAndBatchExportOfOneSpanNeverShareAnIdAndWarnOnce() {
    var generator = new NarrativeTraceIdGenerator();
    provider =
        SdkTracerProvider.builder()
            .setIdGenerator(generator)
            .addSpanProcessor(SimpleSpanProcessor.create(output))
            .build();
    var tracer = provider.get("t");
    var listener = new OtelTraceEventListener(tracer);
    var sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    var sig = new MethodSignature("Service", "work", List.of());
    listener.accept(new TraceEvent.EnterEvent(sc, 1_000, sig));
    listener.accept(new TraceEvent.ExitEvent(sc, 2_000, new TraceOutcome.Returned("1"), null));
    var node =
        new TraceNode(sig, List.of(), new TraceOutcome.Returned("1"), 1_000, 1_000, null, sc);

    var err = exportTwiceCapturingStderr(tracer, node);

    var ids = new HashSet<String>();
    output.getFinishedSpanItems().forEach(s -> ids.add(s.getSpanId()));
    assertThat(ids).as("three exports, three distinct OTel span ids").hasSize(3);
    assertThat(generator.doubleExportWarned()).isTrue();
    assertThat(err.split("NarrativeTrace: ", -1)).hasSize(2);
  }

  private static String exportTwiceCapturingStderr(
      io.opentelemetry.api.trace.Tracer tracer, TraceNode node) {
    var err = new ByteArrayOutputStream();
    var original = System.err;
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    try {
      new TraceSpanExporter(tracer).export(List.of(node));
      new TraceSpanExporter(tracer).export(List.of(node));
    } finally {
      System.setErr(original);
    }
    return err.toString(StandardCharsets.UTF_8);
  }

  @Test
  void anIdOfferedOutsideStartSpanIsNeverTaken() {
    var generator = new NarrativeTraceIdGenerator(new FixedIds("cccccccccccccccc", "d".repeat(32)));
    var sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    var offer = IdHandoff.offer(sc, IdHandoff.Source.LIVE);
    try {
      assertThat(generator.generateSpanId()).isEqualTo(sc.spanId().toString());
      assertThat(generator.generateSpanId())
          .as("taken once: a nested span gets ordinary ids")
          .isEqualTo("cccccccccccccccc");
    } finally {
      IdHandoff.withdraw(offer);
    }
    assertThat(generator.generateSpanId()).isEqualTo("cccccccccccccccc");
    assertThat(IdHandoff.current()).isNull();
  }

  @Test
  void installOnWrapsTheBuildersGeneratorAndIsIdempotent() {
    var own = new FixedIds("eeeeeeeeeeeeeeee", "f".repeat(32));
    var builder = SdkTracerProvider.builder().setIdGenerator(own);

    NarrativeTraceIdGenerator.installOn(builder);
    var installed = NarrativeTraceIdGenerator.currentGenerator(builder);
    NarrativeTraceIdGenerator.installOn(builder);

    assertThat(installed).isInstanceOf(NarrativeTraceIdGenerator.class);
    assertThat(NarrativeTraceIdGenerator.currentGenerator(builder)).isSameAs(installed);
    assertThat(installed.generateSpanId())
        .as("the application's own generator still serves its spans")
        .isEqualTo("eeeeeeeeeeeeeeee");
  }

  @Test
  void theBuildersGeneratorIsReadableOnThisSdkVersion() {
    assertThat(NarrativeTraceIdGenerator.currentGenerator(SdkTracerProvider.builder()))
        .as("installOn depends on reading the builder's generator; a silent null disables it")
        .isNotNull();
  }

  @Test
  void autoconfigureInstallsMirroringByDefault() {
    try (var sdk = autoconfigured(Map.of())) {
      assertThat(spanIdOfNarrativeTraceSpan(sdk)).isTrue();
    }
  }

  @Test
  void autoconfigureHonoursTheOptOut() {
    try (var sdk = autoconfigured(Map.of("narrativetrace.otel.mirror.ids", "false"))) {
      assertThat(spanIdOfNarrativeTraceSpan(sdk)).isFalse();
    }
  }

  @Test
  void systemPropertyAndEnvironmentOptOutsAreRead() {
    assertThat(NarrativeTraceAutoConfiguration.mirrorIdsEnabled(null, k -> null, k -> null))
        .isTrue();
    assertThat(
            NarrativeTraceAutoConfiguration.mirrorIdsEnabled(
                null, k -> k.equals("narrativetrace.otel.mirrorIds") ? "false" : null, k -> null))
        .isFalse();
    assertThat(
            NarrativeTraceAutoConfiguration.mirrorIdsEnabled(
                null, k -> null, k -> k.equals("NARRATIVETRACE_OTEL_MIRROR_IDS") ? "FALSE" : null))
        .isFalse();
  }

  private static io.opentelemetry.sdk.OpenTelemetrySdk autoconfigured(Map<String, String> extra) {
    var props = new java.util.HashMap<String, String>();
    props.put("otel.traces.exporter", "none");
    props.put("otel.metrics.exporter", "none");
    props.put("otel.logs.exporter", "none");
    props.putAll(extra);
    return AutoConfiguredOpenTelemetrySdk.builder()
        .addPropertiesSupplier(() -> props)
        .build()
        .getOpenTelemetrySdk();
  }

  private static boolean spanIdOfNarrativeTraceSpan(io.opentelemetry.sdk.OpenTelemetrySdk sdk) {
    var sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    var offer = IdHandoff.offer(sc, IdHandoff.Source.LIVE);
    Span span;
    try {
      span = sdk.getTracer("t").spanBuilder("s").startSpan();
    } finally {
      IdHandoff.withdraw(offer);
    }
    span.end();
    return span.getSpanContext().getSpanId().equals(sc.spanId().toString());
  }

  private record FixedIds(String spanId, String traceId) implements IdGenerator {
    @Override
    public String generateSpanId() {
      return spanId;
    }

    @Override
    public String generateTraceId() {
      return traceId;
    }
  }
}
