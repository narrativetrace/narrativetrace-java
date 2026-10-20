# Follow one request across services (distributed tracing)

[English](follow-one-request-across-services.md) | [Español](../es/recetas/sigue-una-peticion-entre-servicios.md) | [Português](../pt-BR/receitas/acompanhe-uma-requisicao-entre-servicos.md) | [简体中文](../zh-CN/实用方案/跨服务追踪一次请求.md)

## The problem

> The checkout calls the payment service over HTTP. When an order goes wrong,
> I want one request across both — not two logs I have to line up by hand.

Two services, two log files, two sets of ids. What makes them one story is a
single value travelling with the request: the W3C
[`traceparent`](https://www.w3.org/TR/trace-context/#traceparent-header)
header. The caller sends the trace it is in and the span it is calling from;
the callee adopts both instead of starting a trace of its own. Below is one
run of exactly that, with both services' lines in one file.

## Minimal setup

Beyond the [install](../installation-guide.md), the caller needs nothing new:
`outboundTraceparent()` is on the context it already has. The callee needs the
inbound half — and which code that is depends on how it is built:

| The callee is | The inbound half is |
|---|---|
| A servlet application (Spring MVC, Jakarta EE) | `narrativetrace-servlet`'s `NarrativeTraceFilter`, which adopts the header for you |
| A Micronaut application | `narrativetrace-micronaut-http`'s filter, the same way |
| Anything else — a plain `HttpServer`, a handler framework | Three lines in whatever runs first per request: reset, `adoptTraceparent(Traceparent.parse(header))`, serve |

This page's payment service is the third row: the checkout project runs
without a framework, so the handler reads the header itself. That is also the
honest example of what the filters do, since it is the same three lines.

The log lines come from the SLF4J module and a Logback configuration, as in
[Add logging to this service](add-logging-to-this-service.md). This page uses
one with the raw trace id and the thread in the pattern, because the trace id
is the thing being followed:

<!-- snippet: recipes/checkout/src/test/resources/logback-correlation.xml -->
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- The configuration the two correlation recipes run under: src/main/resources/logback.xml
         with the thread name and the raw trace id added to the pattern, because those two pages
         are about which trace a line belongs to and which thread wrote it. A Gradle task selects
         this file with -Dlogback.configurationFile=logback-correlation.xml and names the FILE
         appender's target with narrativetrace.recipes.log, so one scenario's lines land in one
         file a page can embed. Deliberately no %d{HH:mm:ss.SSS}: a clock would differ on every
         run and the embedded block is a real run's bytes. -->
    <property name="LOG_FILE"
              value="${narrativetrace.recipes.log:-build/narrativetrace/recipes/suite.log}" />

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%-5level [%thread] [%logger] [%X{traceId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>${LOG_FILE}</file>
        <append>false</append>
        <encoder>
            <pattern>%-5level [%thread] [%logger] [%X{traceId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="narrativetrace" level="TRACE" />

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="FILE" />
    </root>
</configuration>
```
<!-- /snippet -->

## The scenario

**The caller's side: one header.** The checkout's `PaymentService` is now an
HTTP client. It asks the context for the trace context to send and sets one
header — everything else is an ordinary request:

<!-- snippet: recipes/checkout/src/main/java/com/example/checkout/HttpPaymentService.java -->
```java
// src/main/java/com/example/checkout/HttpPaymentService.java
package com.example.checkout;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * The checkout's payment collaborator when the payment service is a different process.
 *
 * <p>INTENT: The outbound half of cross-process correlation, in the shape any HTTP client copies:
 * ask the context for {@link NarrativeContext#outboundTraceparent()} and, when it is non-null, set
 * one header. The callee then continues this trace instead of starting its own.
 *
 * <p><b>@llmNote</b> The request body is a comma-separated line rather than JSON on purpose: this
 * class exists to show the header, and a parser would be the only thing a reader had to skip. A
 * real client sends whatever the payment service's API says.
 *
 * <p><b>@edgeCase</b> A null {@code outboundTraceparent()} — no span open, nothing adopted — sends
 * no header at all, which is the honest outcome: a header without a real parent id would point the
 * callee at a span that never existed.
 */
public final class HttpPaymentService implements PaymentService {

  private static final int DECLINED_STATUS = 402;

  private final NarrativeContext context;
  private final URI endpoint;
  private final HttpClient client = HttpClient.newHttpClient();

  public HttpPaymentService(NarrativeContext context, URI endpoint) {
    if (context == null || endpoint == null) {
      throw new IllegalArgumentException("context and endpoint must not be null");
    }
    this.context = context;
    this.endpoint = endpoint;
  }

  @Override
  public String charge(String customerId, int amountCents, String card) {
    var body = customerId + "," + amountCents + "," + card;
    var request =
        withTraceparent(
            HttpRequest.newBuilder()
                .uri(endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    var response = send(request.build());
    if (response.statusCode() == DECLINED_STATUS) {
      throw new PaymentDeclinedException(response.body());
    }
    return response.body();
  }

  /** Sets the W3C header when this call has a span to name as the callee's parent. */
  private HttpRequest.Builder withTraceparent(HttpRequest.Builder builder) {
    var traceparent = context.outboundTraceparent();
    if (traceparent == null) {
      return builder;
    }
    return builder.header(Traceparent.HEADER_NAME, traceparent.format());
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("the payment service could not be reached", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while charging", e);
    }
  }
}
```
<!-- /snippet -->

**The callee's side: its own context.** The payment service is a second
service, which means a second narrative context — created with its own logger
name, so one log file can tell the two apart
(`src/test/java/com/example/checkout/PaymentServiceProcess.java`):

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/PaymentServiceProcess.java region=own-context -->
```java
  private final NarrativeContext context =
      new ThreadLocalNarrativeContext(
          new NarrativeTraceConfig(), PipelineBootstrap.createDefault(LOGGER_NAME));
  private final PaymentService payments =
      NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
```
<!-- /snippet -->

**The callee's side: adopt, serve, capture, reset.** This is the whole
inbound half, in the same file — and the same sequence
`NarrativeTraceFilter` performs for a servlet application:

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/PaymentServiceProcess.java region=inbound -->
```java
  private void handle(HttpExchange exchange) throws IOException {
    var header = exchange.getRequestHeaders().getFirst(Traceparent.HEADER_NAME);
    inboundHeader.set(header);
    context.reset();
    context.adoptTraceparent(Traceparent.parse(header));
    try {
      var fields = body(exchange).split(",", 3);
      respond(exchange, OK, payments.charge(fields[0], Integer.parseInt(fields[1]), fields[2]));
    } catch (PaymentDeclinedException declined) {
      respond(exchange, PAYMENT_REQUIRED, declined.getMessage());
    } finally {
      lastTrace.set(context.captureTrace());
      context.reset();
    }
  }

```
<!-- /snippet -->

**The run.** The checkout, wrapped as always, with the HTTP client where the
local payment service used to be. The server is a loopback `HttpServer` in the
same JVM: the request really crosses a socket and the header really arrives as
a header, which is what this page is about — a second process would add a
second classpath and prove nothing further:

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/AcrossServicesRecipeTest.java -->
```java
// src/test/java/com/example/checkout/AcrossServicesRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves documentation/recipes/follow-one-request-across-services.md: the checkout charges a card
 * through a second service over HTTP, the W3C {@code traceparent} goes with the request, and the
 * callee's own trace carries the caller's trace id and names the caller's span as its parent (rule
 * 8, docs as tests).
 *
 * <p><b>@llmNote</b> The callee's narrative is rendered here, not by the JUnit extension: the
 * extension writes the trace of the context it manages, and the point of this page is that the
 * second service has a context — and a narrative — of its own. The Gradle task {@code
 * recipeAcrossServicesLog} runs this class alone under logback-correlation.xml, so the page's log
 * block holds both services' lines with their trace ids.
 */
@ExtendWith(NarrativeTraceExtension.class)
class AcrossServicesRecipeTest {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  private static final Path PAGE_OUTPUT =
      Path.of("build/narrativetrace/recipes/follow-one-request-across-services");

  @Test
  void a_customer_checks_out_against_the_payment_service(NarrativeContext context)
      throws IOException {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));
    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();

    try (var paymentService = new PaymentServiceProcess()) {
      var checkout = checkoutCalling(paymentService, context, stock, repository);

      var orderId = checkout.placeOrder("C-1234", "SKU-KB", 2, "4242424242424242");

      assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");
      assertThat(repository.saved()).hasSize(1);
      writeCalleeNarrative(paymentService);
      assertTheTraceCrossedTheWire(paymentService);
    }
  }

  /** The checkout, with the HTTP payment client where the local one used to be. */
  private static OrderService checkoutCalling(
      PaymentServiceProcess paymentService,
      NarrativeContext context,
      InMemoryInventory stock,
      InMemoryOrderRepository repository) {
    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(
            new HttpPaymentService(context, paymentService.endpoint()),
            PaymentService.class,
            context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    return NarrativeTraceProxy.trace(
        new DefaultOrderService(inventory, payments, orders), OrderService.class, context);
  }

  /**
   * One trace, two services: the header named the caller's own span, and the callee's root adopted
   * it as its parent instead of starting a trace of its own.
   */
  private static void assertTheTraceCrossedTheWire(PaymentServiceProcess paymentService) {
    var sent = Traceparent.parse(paymentService.inboundTraceparent());
    assertThat(sent).isNotNull();
    assertThat(sent.traceId()).isEqualTo(Traceparent.parse(DEMO_TRACEPARENT).traceId());

    var calleeRoot = paymentService.lastTrace().roots().get(0);
    assertThat(calleeRoot.signature().methodName()).isEqualTo("charge");
    assertThat(calleeRoot.spanContext().traceId()).isEqualTo(sent.traceId());
    assertThat(calleeRoot.spanContext().parentSpanId()).isEqualTo(sent.parentSpanId());
  }

  /** The callee's own narrative, into the directory the page embeds from. */
  private static void writeCalleeNarrative(PaymentServiceProcess paymentService)
      throws IOException {
    Files.createDirectories(PAGE_OUTPUT);
    Files.writeString(
        PAGE_OUTPUT.resolve("payment-service.md"),
        new MarkdownRenderer().render(paymentService.lastTrace()));
  }
}
```
<!-- /snippet -->

## The actual output

**Both services' lines, one file** — the caller's under `narrativetrace`, the
payment service's under `narrativetrace.payments`:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/follow-one-request-across-services/checkout.log -->
```text
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → OrderService.placeOrder(customerId: "C-1234", sku: "SKU-KB", quantity: 2, cardNumber: [REDACTED])
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → InventoryService.reserve(sku: "SKU-KB", quantity: 2)
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← completed
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → PaymentService.charge(customerId: "C-1234", amountCents: 7998, card: [REDACTED])
TRACE [payment-service-1] [narrativetrace.payments] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → PaymentService.charge(customerId: "C-1234", amountCents: 7998, card: [REDACTED])
TRACE [payment-service-1] [narrativetrace.payments] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← returned: "PAY-C-1234-7998"
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← returned: "PAY-C-1234-7998"
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → OrderRepository.save(order: Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998))
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← completed
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← returned: "ORD-C-1234-SKU-KB-2"
```
<!-- /snippet -->

**The caller's own trace,** the value-free structural artifact of that run at
`build/narrativetrace/structural/AcrossServicesRecipeTest/a_customer_checks_out_against_the_payment_service.nt`
— the span ids below are the ones this page reads by:

<!-- snippet: recipes/checkout/build/narrativetrace/structural/AcrossServicesRecipeTest/a_customer_checks_out_against_the_payment_service.nt -->
```text
scenario: A customer checks out against the payment service

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) → value
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) → value
  #1.3 - OrderRepository.save(order)
```
<!-- /snippet -->

**The payment service's own narrative,** captured by its own context at the
end of the request and rendered beside the caller's:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/follow-one-request-across-services/payment-service.md mask=duration -->
```text
- **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) → `"PAY-C-1234-7998"` — 9.621ms #1
```
<!-- /snippet -->

## How to read it

- **One trace id, in both services' lines.**
  `c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7` is the third bracket of every line
  above, the caller's and the callee's alike. The caller's is the trace this
  run is in; the callee's is that same id because it *adopted* it. Nothing
  correlates them after the fact — they were never apart.
- **The two services are told apart by the logger, not by the id.**
  `[narrativetrace]` is the checkout, `[narrativetrace.payments]` is the
  payment service, because its context was built with that logger name. The
  thread column says the same thing from the other side: `[Test worker]` is
  the caller's thread, `[payment-service-1]` is the server's.
- **The caller's `#1.2` and the callee's `#1` are the same work, seen from
  both ends.** In the caller's narrative, `#1.2 PaymentService.charge` is the
  *client* span: it starts when the checkout calls the client and ends when
  the response comes back, so it includes the whole round trip. In the payment
  service's narrative, `#1 PaymentService.charge` is the *server* span: the
  work it actually did. Span ids are per-trace-tree and each service numbers
  its own from `#1` — the join is the trace id, not the number.
- **The parent span id on the callee's root is the caller's span.** The header
  the request carried was
  `00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-<the caller's #1.2 span id>-01`, and
  the callee's root span records that id as its parent. That is not visible in
  a log line — a span id is generated per span, so it differs on every run and
  nothing byte-stable could be printed here — so it is asserted instead, in
  `assertTheTraceCrossedTheWire` above: the callee's root carries the sent
  trace id *and* the sent parent span id. Parentage across the wire is this
  page's claim, and that assertion is its proof.
- **How to join them in a log search.** The trace id is in the MDC under
  `traceId` on every line NarrativeTrace writes, in either service. Put
  `%X{traceId}` in your pattern (as above) or a JSON encoder in front of it,
  and one request across both services is
  `grep c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7` — or, in whatever aggregator you
  run, `traceId:"c4e1…"`. The trace *name* that
  [Add logging to this service](add-logging-to-this-service.md) greps by is
  derived from the same id, so it works as a search key too, and reads better
  for a human.
- **The two narratives stay two.** The payment service's spans are in the
  payment service's trace, not in the caller's: `captureTrace()` on either
  side returns that side's work. One trace id, two trees — which is exactly
  what you have in production, where the other service is a different process
  on a different host.

## What is not captured

- **A hop through a service that does not forward the header breaks the
  chain.** Correlation is cooperative: the chain holds for as long as every
  service in it *passes `traceparent` on*. A proxy, a queue consumer or a
  third-party service that drops the header leaves the next NarrativeTrace
  service with nothing to adopt, and it starts a fresh trace — correctly, since
  it has no honest parent to name. A service with no NarrativeTrace at all is
  fine *if* it forwards the header it received.
- **No header is sent when there is nothing to name.** With no span open and
  nothing adopted, `outboundTraceparent()` returns `null` and the client sends
  nothing rather than a `traceparent` pointing at a span that never existed.
  An untraced call out of the checkout therefore cannot be joined, and that is
  by design.
- **`traceparent` only.** The client above sends no `tracestate` and no
  baggage: nothing in this path reads or writes vendor state, and no
  application value crosses the wire because of tracing.
- **Nothing comes back.** The response carries no trace header, so the
  caller's narrative says what it sent and what it got, never what the callee
  did inside. The callee's own narrative is the record of that, and the trace
  id is what finds it.
- **Two services, one JVM, one socket.** What this run does not exercise is a
  second process, a second classpath and a network that can fail. The header
  mechanism is unchanged by any of those; the failure modes of the call itself
  are your HTTP client's.

**The skill for this:** `narrativetrace-verify` — a change that crosses
services is exactly the case it exists for: write the intent down, run the
smallest real path, and read the trace against it before calling it done.
