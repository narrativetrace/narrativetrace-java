# Send traces to the observability stack you already run

[English](send-traces-to-the-observability-stack-you-already-run.md) | [Español](../es/recetas/envia-las-trazas-al-stack-de-observabilidad-que-ya-tienes.md) | [Português](../pt-BR/receitas/envie-os-traces-para-o-stack-de-observabilidade-que-voce-ja-tem.md) | [简体中文](../zh-CN/实用方案/把追踪发到你已有的可观测性栈.md)

## The problem

> We already have tracing and dashboards. I am not adopting a second place to
> look — can this go into the stack we run?

Yes: one run, two consumers. The narrative lines keep going to your logger,
and the same events become OpenTelemetry spans on the way out. Below is one
checkout with both outputs side by side.

## Minimal setup

`narrativetrace-opentelemetry` plus the OTel API, which the module leaves to
you (`compileOnly`, so your SDK version wins):

```kotlin
// build.gradle.kts
dependencies {
    implementation("ai.narrativetrace:narrativetrace-opentelemetry:0.5.5")
    implementation("io.opentelemetry:opentelemetry-api:1.62.0")
}
```

There are two ways in, and the difference is *when* a span exists:

| Mode | Class | Use it when |
|---|---|---|
| Live, as the methods run | `OtelTraceEventListener` | You want spans in your collector even if the process dies mid-request — this page |
| Batch, after the fact | `TraceSpanExporter` | You want the spans of a finished trace tree, exported once, at the lowest execution cost: `exporter.export(context.captureTrace().roots())` |

In a deployment the live listener is usually not constructed in your code at
all: an implementation of `TraceEventListener` on the classpath is discovered
through `ServiceLoader` and placed beside retention automatically — presence
*is* the configuration ([Configuration Guide](../configuration-guide.md) §10
and §11). An in-memory exporter cannot be discovered that way, so the
scenario below composes the pipeline explicitly, which is also the clearest
picture of what the discovery would have built.

## The scenario

The pipeline is the only new idea: the narration listener you already have,
with the OTel listener composed onto the same durable path. The checkout
itself is wrapped exactly as on every other page:

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/ObservabilityRecipeTest.java -->
```java
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
```
<!-- /snippet -->

## The actual output

**The log lines**, unchanged by any of this — the same narration
[Add logging to this service](add-logging-to-this-service.md) produces:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/send-traces-to-the-observability-stack-you-already-run/checkout.log -->
```text
TRACE [solid wand romps] [narrativetrace] - → OrderService.placeOrder(customerId: "C-1234", sku: "SKU-KB", quantity: 2, cardNumber: [REDACTED])
TRACE [solid wand romps] [narrativetrace] - → InventoryService.reserve(sku: "SKU-KB", quantity: 2)
TRACE [solid wand romps] [narrativetrace] - ← completed
TRACE [solid wand romps] [narrativetrace] - → PaymentService.charge(customerId: "C-1234", amountCents: 7998, card: [REDACTED])
TRACE [solid wand romps] [narrativetrace] - ← returned: "PAY-C-1234-7998"
TRACE [solid wand romps] [narrativetrace] - → OrderRepository.save(order: Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998))
TRACE [solid wand romps] [narrativetrace] - ← completed
TRACE [solid wand romps] [narrativetrace] - ← returned: "ORD-C-1234-SKU-KB-2"
```
<!-- /snippet -->

**The spans that left the same run**, as the in-memory exporter received
them — name, parent and every attribute, sorted:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/send-traces-to-the-observability-stack-you-already-run/exported-spans.txt -->
```text
span: InventoryService.reserve
  parent: OrderService.placeOrder
    narrative.class: InventoryService
    narrative.method: reserve
    narrative.param.quantity: 2
    narrative.param.sku: SKU-KB
    narrative.trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
    narrative.trace_name: solid wand romps
    nt.entryType: entry
    nt.schemaVersion: 1.0
span: PaymentService.charge
  parent: OrderService.placeOrder
    narrative.class: PaymentService
    narrative.method: charge
    narrative.outcome: "PAY-C-1234-7998"
    narrative.param.amountCents: 7998
    narrative.param.customerId: C-1234
    narrative.trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
    narrative.trace_name: solid wand romps
    nt.entryType: entry
    nt.schemaVersion: 1.0
span: OrderRepository.save
  parent: OrderService.placeOrder
    narrative.class: OrderRepository
    narrative.method: save
    narrative.param.order.amountCents: 7998
    narrative.param.order.customerId: C-1234
    narrative.param.order.orderId: ORD-C-1234-SKU-KB-2
    narrative.param.order.quantity: 2
    narrative.param.order.sku: SKU-KB
    narrative.trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
    narrative.trace_name: solid wand romps
    nt.entryType: entry
    nt.schemaVersion: 1.0
span: OrderService.placeOrder
  parent: (remote — adopted from the inbound traceparent)
    narrative.class: OrderService
    narrative.method: placeOrder
    narrative.outcome: "ORD-C-1234-SKU-KB-2"
    narrative.param.customerId: C-1234
    narrative.param.quantity: 2
    narrative.param.sku: SKU-KB
    narrative.trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
    narrative.trace_name: solid wand romps
    nt.entryType: entry
    nt.schemaVersion: 1.0
```
<!-- /snippet -->

## How to read it

- **Four traced calls, four spans, one tree.** The spans arrive in the order
  they *ended* — innermost first, the root last, which is how any OTel
  pipeline sees a finished tree. Parentage is not inferred from that order or
  from a thread-local: the listener reads each event's explicit
  `parentSpanId`, which is why `InventoryService.reserve`,
  `PaymentService.charge` and `OrderRepository.save` all name
  `OrderService.placeOrder` as their parent even though they ended before it
  did. `OrderService.placeOrder`'s own parent reads `(remote — adopted from
  the inbound traceparent)`, not `(none — this is the root)`: this run adopted
  one before placing the order, so its parent is the caller's span, named by
  the header — a span this process never ran and so never exported.
- **What each span carries.** `narrative.class` and `narrative.method` are the
  call; `narrative.param.<name>` is one attribute per parameter, typed
  (`narrative.param.quantity: 2` is a long, not the string `"2"`);
  `narrative.outcome` is the returned value, and it is absent on the two
  `void` calls because there was nothing to return. `narrative.trace_id` and
  `narrative.trace_name` are NarrativeTrace's own identity for the trace,
  carried as attributes so a span in your backend can be matched to the log
  line and the trace file that describe the same call —
  [Follow one request across services](follow-one-request-across-services.md)
  is the same id doing the same job across a process boundary. `nt.entryType`
  and `nt.schemaVersion` mark the schema these attributes follow.
- **A record parameter arrives flattened.** `OrderRepository.save(order)` is
  one parameter, and it exported as `narrative.param.order.orderId`,
  `narrative.param.order.customerId`, `narrative.param.order.sku`,
  `narrative.param.order.quantity`, `narrative.param.order.amountCents` —
  component by component, because that is how the value was rendered: by
  reading its state, never by calling its `toString()`.
- **Redaction reaches this path too.** There is no `narrative.param.cardNumber`
  and no `narrative.param.card` attribute on any span above — not an attribute
  holding `[REDACTED]`, no attribute at all. A denied value is dropped at
  capture, before the event exists, so no consumer on either path can receive
  it; the scenario asserts that as well as the span tree
  ([Keep secrets out of the logs](keep-secrets-out-of-the-logs.md)).
- **Which setting moved an event to which consumer.** Three decisions, in
  this order:
  1. `TracingLevel` decides whether an event is created at all. At `SUMMARY`
     these same four spans would be exported *without* the
     `narrative.param.*` attributes, because parameter values are not
     rendered at that level; at `OFF` there is no event, and therefore no
     span ([Trace in production without the cost](trace-in-production-without-the-cost.md)).
  2. The pipeline's shape decides who sees the event. Here one durable
     listener — narration, then export — runs inline, and the best-effort slot
     keeps retention, which is what `captureTrace()` and the trace files read.
     Nothing was taken away from the logger to feed the exporter.
  3. Your logger's level decides what is *printed*. Raising the
     `narrativetrace` logger to `INFO` empties the log block above and
     changes nothing about the spans.
- **Why this is not a second place to look.** The spans are yours, in your
  collector, under your service name, beside the spans your framework already
  emits. What NarrativeTrace adds is a span per *traced method call* with the
  parameters and the outcome on it — one level below the HTTP span you
  already have.

## What is not captured

- **What your stack does with the spans is your stack's.** Sampling,
  retention, indexing and whether an attribute is searchable are the
  collector's and the backend's decisions. This page shows what leaves the
  process; no claim is made about any vendor's UI.
- **Span ids are never shared; the trace id is, once a header names one.**
  OpenTelemetry always picks its own span ids, and NarrativeTrace's identity
  rides along as `narrative.trace_id` / `narrative.trace_name` attributes for
  matching a span to a log line — a span's position in your backend's
  waterfall is a span id OTel chose, never one of NarrativeTrace's own. The
  trace id is different when a header was adopted, as above: the root parents
  on the remote context the header named, so OpenTelemetry's own trace id for
  the whole exported subtree becomes that header's trace id too — the same
  one `narrative.trace_id` already carried, and what connects this service's
  spans to the caller's trace in your backend, not merely alongside it.
- **Live and batch are not the same spans.** This page's listener creates a
  span as a method runs, so an orphan (an enter with no exit — a process
  killed mid-call) ends up ended with an error status by the listener's own
  TTL. The batch exporter only ever sees finished trees and adds
  `narrative.duration_ms`, which the live path does not.
- **The cost is a listener on the caller's thread.** Under the default
  topology a composed listener runs inline, so span creation is on the path of
  the traced call. That is the trade for durability; a queue or ring-buffer
  topology moves it off, and the listener contract is written so it keeps
  working when the topology changes.
- **Only what is traced is exported.** No span exists for a call no
  integration intercepted — the export path inherits the boundary of the
  capture path exactly.

**The skill for this:** `narrativetrace-doctor` — one wiring check per
framework integration, and it reports whether any sink actually receives what
is traced.
