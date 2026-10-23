# Review a complex flow before changing it

[English](review-a-complex-flow-before-changing-it.md) | [Español](../es/recetas/revisa-un-flujo-complejo-antes-de-cambiarlo.md) | [Português](../pt-BR/receitas/revise-um-fluxo-complexo-antes-de-altera-lo.md) | [简体中文](../zh-CN/实用方案/修改复杂流程前先审查它.md)

## The problem

> Before you change the checkout, tell me what it actually does.

Reading the source answers what it *can* do. One run answers what it *does*
with these inputs — in what order, with which values, and where the flow
leaves the code you are about to edit. This page deliberately never shows the
implementation: that is the point of reading a run instead.

## Minimal setup

Nothing beyond the [install](../installation-guide.md) for the two trace
artifacts. The log lines need the SLF4J module and a Logback configuration,
exactly as in [Add logging to this service](add-logging-to-this-service.md).
The capture level is one property, and `SUMMARY` is the one this page uses
beside the default `DETAIL`:

```kotlin
// build.gradle.kts
narrativeTrace {
    tracingLevel.set("SUMMARY")   // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
}
```

## The scenario

One representative run of the flow — an existing test is the cheapest one to
borrow, and this is that test:

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/CheckoutReviewFixture.java -->
```java
// src/test/java/com/example/checkout/CheckoutReviewFixture.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * One successful checkout, the flow
 * documentation/recipes/review-a-complex-flow-before-changing-it.md is read before changing
 * anything.
 *
 * <p>A fixture because two readers need the same run under different settings: {@code
 * ReviewFlowRecipeTest} runs it through the JUnit Platform launcher once at DETAIL and once at
 * SUMMARY, and the Gradle task {@code recipeReviewFlowLog} runs it alone so one scenario's log
 * lines land in one file. It passes either way — nothing here fails by design.
 */
@ExtendWith(NarrativeTraceExtension.class)
class CheckoutReviewFixture {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  @Test
  void a_customer_buys_two_keyboards(NarrativeContext context) {
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

## The actual output

**At `DETAIL`** (the default), `build/narrativetrace/traces/CheckoutReviewFixture/a_customer_buys_two_keyboards.md`:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/review-a-complex-flow-before-changing-it/detail/traces/CheckoutReviewFixture/a_customer_buys_two_keyboards.md mask=duration,traceName -->
```text
---
type: trace
run: curly fly romps
scenario: A customer buys two keyboards
entry_point: OrderService.placeOrder
duration_ms: 4.335
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 0
---
<!--
NarrativeTrace trace file
What this is: the recorded call tree of one test run: CheckoutReviewFixture.a_customer_buys_two_keyboards, passed, 1.996 ms.
How to read it: indentation is call nesting; each line is one call with its parameters and its return value or thrown exception, as rendered values. [REDACTED] marks a redacted value.
Failing call: none, no call threw in this run.
Run index: ../../INDEX.md. Artifact list: ../../manifest.json (both are written when the run ends).
-->

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** A customer buys two keyboards
**Duration:** 4.335ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 4.335ms #1
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.692ms #1.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) → `"PAY-C-1234-7998"` — 0.427ms #1.2
  - **OrderRepository.save**(order: `Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998)`) — 0.356ms #1.3
```
<!-- /snippet -->

**At `SUMMARY`**, the same run of the same test:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/review-a-complex-flow-before-changing-it/summary/traces/CheckoutReviewFixture/a_customer_buys_two_keyboards.md mask=duration,traceName -->
```text
---
type: trace
run: sweet hare notes
scenario: A customer buys two keyboards
entry_point: OrderService.placeOrder
duration_ms: 1.909
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 0
---
<!--
NarrativeTrace trace file
What this is: the recorded call tree of one test run: CheckoutReviewFixture.a_customer_buys_two_keyboards, passed, 1.334 ms.
How to read it: indentation is call nesting; each line is one call with its parameters and its return value or thrown exception, as rendered values. [REDACTED] marks a redacted value.
Failing call: none, no call threw in this run.
Run index: ../../INDEX.md. Artifact list: ../../manifest.json (both are written when the run ends).
-->

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** A customer buys two keyboards
**Duration:** 1.909ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: …, sku: …, quantity: …, cardNumber: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 1.909ms #1
  - **InventoryService.reserve**(sku: …, quantity: …) — 0.274ms #1.1
  - **PaymentService.charge**(customerId: …, amountCents: …, card: …) → `"PAY-C-1234-7998"` — 0.27ms #1.2
  - **OrderRepository.save**(order: …) — 0.233ms #1.3
```
<!-- /snippet -->

**The log the same flow wrote** through the project's own logger, values and
all:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/review-a-complex-flow-before-changing-it/checkout.log -->
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

**The structural trace of that run**, `build/narrativetrace/structural/CheckoutReviewFixture/a_customer_buys_two_keyboards.nt`:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/review-a-complex-flow-before-changing-it/detail/structural/CheckoutReviewFixture/a_customer_buys_two_keyboards.nt -->
```text
scenario: A customer buys two keyboards

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) → value
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) → value
  #1.3 - OrderRepository.save(order)
```
<!-- /snippet -->

## How to read it

**The flow, from the `DETAIL` artifact.** `#1` is the call the test made and
the only root: everything indented under it happened inside it, in the order
printed. `#1.1` reserves two units before anything is charged; `#1.2` charges
7998 — two units at 3999, so the price is derived from the quantity and not
passed in; `#1.3` saves an order that already carries the id `#1` returns.
Read as a sentence: this checkout reserves first, charges second, saves third,
and the order id is minted before the save. A change that charges before it
reserves, or saves before it charges, is a change to *this*, and now you know
it without trusting a comment.

**What each level is for.** `DETAIL` carries the parameter values, and it is
what you read while working on the flow. At `SUMMARY` every parameter value
is `…` — suppressed at capture, not hidden at render — and only roots and
leaf calls are kept; this flow is two levels deep, so no frame is
intermediate and the same four calls appear. A deeper flow loses its middle.
Return values survive at every active level: the return is the payload of a
narrative. Use `SUMMARY` where the shape and the outcomes are what you need
and the values are cost — a CI run, a long soak — and `OFF`/`ERRORS` in
production until you need more.

**Which output a human reads, and which an agent reads.** The log lines are
for the human on call: they interleave with everything else the service logs,
carry the values, and are keyed by the trace's name — `[solid wand romps]` —
so one checkout can be grepped out of a busy file. The `.nt` is for the agent
and the reviewer: **value-free by construction** — compare it with the log
line above and the difference is not that the values were replaced but that
they were never written. `(customerId, sku, quantity, cardNumber)` is the
parameter *names*; `→ value` says a value was returned without saying which.
That is what makes it safe to commit, to diff in a review, and to hand to a
model: it cannot leak a customer id, and it does not change when the data
changes — only when the *calls* change. Pinning it is
[Review a refactoring: did behaviour change](review-a-refactoring-did-behaviour-change.md).

## What is not captured

- **Calls inside unwrapped classes.** Four interfaces were wrapped; a call
  between two classes behind them, or a private method, never appears. A flow
  that looks shallow may be deep.
- **What the chosen integration does not intercept.** The JDK proxy sees
  calls made *through* the wrapped reference only: an inner call that a class
  makes on itself (`this.helper()`) is invisible to it, where the java agent
  would see it. [Choosing an Integration](../choosing-an-integration.md) is
  the trade-off per path, and this is the part of it that changes what you can
  read here.
- **Work that left the thread.** A call handed to an executor without the
  context travels no further; the trace ends where the thread ended.
- **Values the deny-list or the value-shape rule caught**, here `cardNumber`
  and `card`, are `[REDACTED]` in both the log and the `DETAIL` artifact, and
  absent by construction from the `.nt`.
- **One run, not the contract.** Another input takes another path — an
  out-of-stock sku never reaches `#1.2` at all.

**The skill for this:** `narrativetrace-verify` — it writes the intent down
first, runs the smallest real path, and reads the value-free trace against
that intent before anything is called done.
