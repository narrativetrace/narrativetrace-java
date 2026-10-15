# Add logging to this service

[English](add-logging-to-this-service.md) | [Español](../es/recetas/anade-registro-a-este-servicio.md) | [Português](../pt-BR/receitas/adicione-logs-a-este-servico.md) | [简体中文](../zh-CN/实用方案/给这个服务加日志.md)

## The problem

> Add logging to this service.

The service is a checkout. It reserves stock, charges a card and saves an
order, and it contains no log statement at all:

<!-- snippet: recipes/checkout/src/main/java/com/example/checkout/DefaultOrderService.java -->
```java
// src/main/java/com/example/checkout/DefaultOrderService.java
package com.example.checkout;

/** The checkout: reserve the stock, charge the card, save the order — and release on a decline. */
public final class DefaultOrderService implements OrderService {

  private static final int UNIT_PRICE_CENTS = 3999;

  private final InventoryService inventory;
  private final PaymentService payments;
  private final OrderRepository orders;

  public DefaultOrderService(
      InventoryService inventory, PaymentService payments, OrderRepository orders) {
    this.inventory = inventory;
    this.payments = payments;
    this.orders = orders;
  }

  @Override
  public String placeOrder(String customerId, String sku, int quantity, String cardNumber) {
    inventory.reserve(sku, quantity);
    var amountCents = UNIT_PRICE_CENTS * quantity;
    try {
      payments.charge(customerId, amountCents, cardNumber);
    } catch (PaymentDeclinedException declined) {
      inventory.release(sku, quantity);
      throw declined;
    }
    var orderId = "ORD-" + customerId + "-" + sku + "-" + quantity;
    orders.save(new Order(orderId, customerId, sku, quantity, amountCents));
    return orderId;
  }
}
```
<!-- /snippet -->

What you actually want is the thing logging is a means to: what this call
did, with the values it did it with, in the log your team already reads. So
the answer below writes no log statement either.

## Minimal setup

Beyond the [install](../installation-guide.md) — `narrativetrace-core`, the
JDK proxy, and `-parameters` so parameter names survive compilation — this
page needs two runtime dependencies and one configuration file:

```kotlin
// build.gradle.kts
dependencies {
    runtimeOnly("ai.narrativetrace:narrativetrace-slf4j:0.4.0")   // attaches to the pipeline
    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")          // the SLF4J backend it needs
}
```

`narrativetrace-slf4j` attaches itself the moment it is on the classpath;
nothing in the code below references it. Logback decides where its events go:

<!-- snippet: recipes/checkout/src/main/resources/logback.xml -->
```xml
<!-- src/main/resources/logback.xml -->
<configuration>
    <!-- The pattern deliberately carries no %d{HH:mm:ss.SSS}: every recipe page embeds a real
         run's log output byte-for-byte, and a clock in it would differ on every run. Put the
         timestamp back in your own project — nothing else here changes. -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%-5level [%X{traceName}] [%logger] - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="narrativetrace" level="TRACE" />

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```
<!-- /snippet -->

## The scenario

Each collaborator is wrapped once, where it is constructed — the checkout
itself last, so calls through it are captured before they reach the real
implementation. Nothing else changes:

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/AddLoggingRecipeTest.java -->
```java
// src/test/java/com/example/checkout/AddLoggingRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves documentation/recipes/add-logging-to-this-service.md: the checkout, with no log statement
 * anywhere in it, logs every call through the project's own SLF4J logger once its collaborators are
 * wrapped (rule 8, docs as tests).
 *
 * <p><b>@llmNote</b> The page's output block is the log file Logback writes, not a rendering this
 * test produces — that is the whole claim of the page, so the evidence has to come from the logger.
 * The Gradle task {@code recipeAddLoggingLog} runs this class alone and names that file.
 */
@ExtendWith(NarrativeTraceExtension.class)
class AddLoggingRecipeTest {

  /**
   * A fixed W3C traceparent, adopted so this page's embedded output names the same trace every run
   * — the same mechanism a servlet filter uses for an inbound request header. A real run generates
   * a random trace id every time, and the name in the log lines is derived from it.
   */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  @Test
  void a_customer_checks_out(NarrativeContext context) {
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
    assertThat(stock.availableUnits("SKU-KB")).isEqualTo(3);
    assertThat(repository.saved()).hasSize(1);
  }
}
```
<!-- /snippet -->

The test is a JUnit 5 test because that is where a flow like this is already
exercised; the same four lines of wiring work in a `main`, a Spring bean
factory or a servlet. `NarrativeTraceExtension` is what writes the per-test
trace artifacts the other recipes read.

## The actual output

The log the run above wrote, through the project's own logger:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/add-logging-to-this-service/checkout.log -->
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

## How to read it

- `→` opens a call, `←` closes the one most recently opened, so indentation
  is unnecessary: the first `→ OrderService.placeOrder` and the last
  `← returned: "ORD-C-1234-SKU-KB-2"` are the same call, and everything
  between them happened inside it. In the trace artifacts the same four calls
  carry the span ids `#1`, `#1.1`, `#1.2` and `#1.3` —
  [Review a complex flow before changing it](review-a-complex-flow-before-changing-it.md)
  reads them that way.
- Every line carries the parameter *names* the method declares, which is what
  makes it a sentence rather than a tuple: `reserve(sku: "SKU-KB", quantity: 2)`
  needs no legend.
- `← completed` is a `void` method returning; `← returned: …` carries the
  value. `charge` returning `"PAY-C-1234-7998"` is the gateway's own id, and
  `amountCents: 7998` is where the price of two keyboards comes from.
- Two values are gone. `cardNumber: [REDACTED]` was hidden by its *name* —
  the always-on deny-list, no annotation, no configuration. `card: [REDACTED]`
  one line down was hidden by its *value*: the parameter is named `card`,
  which the deny-list does not know, and the number it held is Luhn-valid, so
  the value-shape rule caught it anyway. See
  [Privacy and Redaction](../privacy-and-redaction.md).
- `[solid wand romps]` is the trace's own name in the MDC, so every line of
  one checkout can be grepped out of a busy log.

The conclusion this supports: the narrative came from the method names, the
parameter names and the values, and not one log statement was written to get
it.

## What is not captured

- **Only wrapped code is logged.** Four interfaces were wrapped above, so
  four calls appear. A call between two unwrapped classes — or a private
  method inside `DefaultOrderService` — is invisible here. If wrapping every
  collaborator by hand is not what you want, the java agent or the Spring
  integration attaches without touching construction code:
  [Choosing an Integration](../choosing-an-integration.md).
- **A value matching the redaction rules is replaced before it is written,**
  including in this log line. That is not configurable downward, and it means
  a redacted parameter cannot be recovered from the log.
- **The logger's own level still applies.** These lines are `TRACE` on the
  `narrativetrace` logger; at `INFO` the file is empty and nothing warns you.
- **Timings are not in the log lines.** Each call's duration is in the trace
  artifacts, not here.

**The skill for this:** `add-narrative-tracing` — it installs the two
dependencies, wraps the first class, and checks the wiring with
`narrativetrace-doctor`.
