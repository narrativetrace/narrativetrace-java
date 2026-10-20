/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/ObservabilityRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import ai.narrativetrace.opentelemetry.OtelTraceEventListener;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ai.narrativetrace.slf4j.Slf4jTraceEventListener;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves documentation/recipes/send-traces-to-the-observability-stack-you-already-run.md: ONE run
 * of the checkout writes the log lines it always wrote AND an OpenTelemetry span per traced call,
 * with the parentage and the attributes the page shows — and without the redacted values (rule 8,
 * docs as tests).
 *
 * <p><b>@llmNote</b> No JUnit extension here, deliberately: this page's subject is the pipeline a
 * deployment composes, so the context is built with an explicit one — narration on the durable
 * path, the OTel listener beside it. In production the listener is usually discovered off the
 * classpath instead (configuration-guide §11), which needs no code at all; an in-memory exporter
 * cannot be discovered, so the page shows the explicit composition the same guide's §10 documents.
 *
 * <p>The exported spans are written out by {@link #writeExportedSpans}, naming and sorting
 * everything it prints, so one run's dump is byte-identical to the next — a raw span dump carries
 * timestamps and OTel's own generated ids and could never be embedded.
 */
class ObservabilityRecipeTest {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  private static final Path PAGE_OUTPUT =
      Path.of(
          "build/narrativetrace/recipes/send-traces-to-the-observability-stack-you-already-run");

  private InMemorySpanExporter spans;
  private SdkTracerProvider tracerProvider;
  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void wireTheStack() {
    spans = InMemorySpanExporter.create();
    tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build();

    // The pipeline this page is about: the narration you already have, and the OTel listener
    // beside it on the same durable path. Retention — what captureTrace() reads — is untouched.
    var narration = new Slf4jTraceEventListener();
    var export = new OtelTraceEventListener(tracerProvider.get("narrativetrace"));
    context =
        new ThreadLocalNarrativeContext(
            new NarrativeTraceConfig(), new DualPathPipeline(narration.andThen(export)));
  }

  @AfterEach
  void tearDown() {
    context.reset();
    tracerProvider.close();
  }

  @Test
  void one_run_narrates_and_exports() throws IOException {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));
    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();
    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    var checkout =
        NarrativeTraceProxy.trace(
            new DefaultOrderService(inventory, payments, orders), OrderService.class, context);

    var orderId = checkout.placeOrder("C-1234", "SKU-KB", 2, "4242424242424242");

    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");
    assertOneSpanPerTracedCall();
    assertNoRedactedValueWasExported();
    writeExportedSpans();
  }

  /** Four traced calls, four spans, ended innermost first — and the tree is reconstructed. */
  private void assertOneSpanPerTracedCall() {
    var exported = spans.getFinishedSpanItems();
    assertThat(exported.stream().map(SpanData::getName))
        .containsExactly(
            "InventoryService.reserve",
            "PaymentService.charge",
            "OrderRepository.save",
            "OrderService.placeOrder");
    var byId =
        exported.stream().collect(Collectors.toMap(SpanData::getSpanId, Function.identity()));
    var root = named(exported, "OrderService.placeOrder");
    assertThat(byId.get(named(exported, "PaymentService.charge").getParentSpanId()))
        .isEqualTo(root);
    assertThat(byId).doesNotContainKey(root.getParentSpanId());
    assertRootParentsOnTheAdoptedHeader(root);
  }

  /** The adopted header named this trace's remote parent; it must reach the exported root too. */
  private void assertRootParentsOnTheAdoptedHeader(SpanData root) {
    var header = Traceparent.parse(DEMO_TRACEPARENT);
    assertThat(root.getParentSpanContext().isRemote())
        .as("the root adopted a traceparent, so its parent is the header's span, not local")
        .isTrue();
    assertThat(root.getTraceId())
        .as("adoption connects this service's OTel trace to the caller's trace, not a fresh one")
        .isEqualTo(header.traceId().toString());
  }

  /** What the deny-list caught never reaches the exporter either — there is no attribute for it. */
  private void assertNoRedactedValueWasExported() {
    var attributes =
        spans.getFinishedSpanItems().stream()
            .flatMap(span -> span.getAttributes().asMap().entrySet().stream())
            .collect(
                Collectors.toMap(
                    e -> e.getKey().getKey(), e -> String.valueOf(e.getValue()), (a, b) -> a));
    assertThat(attributes).doesNotContainKeys("narrative.param.cardNumber", "narrative.param.card");
    assertThat(attributes.values()).noneMatch(value -> value.contains("4242424242424242"));
  }

  private static SpanData named(List<SpanData> exported, String name) {
    return exported.stream().filter(span -> span.getName().equals(name)).findFirst().orElseThrow();
  }

  /** The dump the page embeds: span name, parent, and every attribute, sorted by key. */
  private void writeExportedSpans() throws IOException {
    var exported = spans.getFinishedSpanItems();
    var names =
        exported.stream()
            .collect(Collectors.toMap(SpanData::getSpanId, SpanData::getName, (a, b) -> a));
    var text = new StringBuilder();
    for (var span : exported) {
      text.append("span: ").append(span.getName()).append('\n');
      text.append("  parent: ").append(parentLabel(span, names)).append('\n');
      appendAttributes(span, text);
    }
    Files.createDirectories(PAGE_OUTPUT);
    Files.writeString(PAGE_OUTPUT.resolve("exported-spans.txt"), text.toString());
  }

  /**
   * A root under an adopted {@code traceparent} has a parent OTel never created locally — never
   * mislabel that "no parent" the way an absent local name would otherwise read.
   */
  private static String parentLabel(SpanData span, Map<String, String> names) {
    if (!span.getParentSpanContext().isValid()) {
      return "(none — this is the root)";
    }
    var local = names.get(span.getParentSpanId());
    return local != null ? local : "(remote — adopted from the inbound traceparent)";
  }

  private static void appendAttributes(SpanData span, StringBuilder text) {
    span.getAttributes().asMap().entrySet().stream()
        .collect(
            Collectors.toMap(
                e -> e.getKey().getKey(), Map.Entry::getValue, (a, b) -> a, java.util.TreeMap::new))
        .forEach(
            (key, value) ->
                text.append("    ").append(key).append(": ").append(value).append('\n'));
  }
}
