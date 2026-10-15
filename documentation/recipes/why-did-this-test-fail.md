# Why did this test fail

[English](why-did-this-test-fail.md) | [Español](../es/recetas/por-que-fallo-esta-prueba.md) | [Português](../pt-BR/receitas/por-que-este-teste-falhou.md) | [简体中文](../zh-CN/实用方案/这个测试为什么失败了.md)

## The problem

> Why did this test fail?

One checkout test is red. The assertion says what did not match; it does not
say what the code did on the way there, which is the part you need in order
to know whether the code or the expectation is wrong.

## Minimal setup

Nothing beyond the [install](../installation-guide.md). The JUnit 5 extension
writes one narrative per test method with no configuration — for a failing
test too, which is the case this page is about. The files land in
`build/narrativetrace/traces/<TestClass>/<scenario>.md`, and
`build/narrativetrace/manifest.json` lists every artifact of every scenario
when you know the test's name but not its file's.

## The scenario

The failing test:

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/StaleExpectationFixture.java -->
```java
// src/test/java/com/example/checkout/StaleExpectationFixture.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The failing checkout test documentation/recipes/why-did-this-test-fail.md is read through. It
 * fails on an assertion, by design, which is why it is a fixture: {@code tasks.test} excludes
 * {@code *Fixture}, and {@code TestFailureRecipeTest} runs it through the JUnit Platform launcher
 * and writes down what the run reported.
 *
 * <p><b>@llmNote</b> Nothing here says which side is wrong — that is the reader's exercise, and the
 * narrative the run writes is what settles it. Do not add a comment that gives it away.
 */
@ExtendWith(NarrativeTraceExtension.class)
class StaleExpectationFixture {

  /** A fixed W3C traceparent, so the page's embedded artifact names the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  @Test
  void the_customer_gets_an_order_id_for_their_keyboards(NarrativeContext context) {
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

    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-1");
  }
}
```
<!-- /snippet -->

## The actual output

What the runner reported:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/why-did-this-test-fail/assertion-failure.txt -->
```text
expected: "ORD-C-1234-SKU-KB-1"
 but was: "ORD-C-1234-SKU-KB-2"
```
<!-- /snippet -->

And what the same run wrote beside it, at
`build/narrativetrace/traces/StaleExpectationFixture/the_customer_gets_an_order_id_for_their_keyboards.md`:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/why-did-this-test-fail/traces/StaleExpectationFixture/the_customer_gets_an_order_id_for_their_keyboards.md mask=duration,traceName -->
```text
---
type: trace
run: mini wedge soars
scenario: The customer gets an order id for their keyboards
entry_point: OrderService.placeOrder
duration_ms: 2.435
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 0
---

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** The customer gets an order id for their keyboards
**Duration:** 2.435ms | **Result:** FAILED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 2.435ms #1
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.389ms #1.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) → `"PAY-C-1234-7998"` — 0.243ms #1.2
  - **OrderRepository.save**(order: `Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998)`) — 0.323ms #1.3
```
<!-- /snippet -->

## How to read it

Read the narrative top-down against the assertion, one span at a time:

- `#1 OrderService.placeOrder(... quantity: 2 ...) → "ORD-C-1234-SKU-KB-2"`.
  The value the assertion rejected is right there, and so is the input that
  produced it: the call asked for **two** keyboards.
- `#1.1 InventoryService.reserve(sku: "SKU-KB", quantity: 2)` — two units
  reserved, consistent with the call.
- `#1.2 PaymentService.charge(..., amountCents: 7998, ...)` — 7998 is two
  units at 3999, so the price agrees with the quantity too.
- `#1.3 OrderRepository.save(order: Order(... quantity: 2 ...))` — what was
  stored agrees with what was asked for.
- `**Result:** FAILED` in the header is the *test's* verdict, not the code's:
  `error_count: 0` and every span completed. Nothing threw.

Every span agrees with every other span and with the call that started them.
The expectation — `ORD-C-1234-SKU-KB-1` — is the only thing in the run that
says one keyboard, so it is the thing that is out of date; the fixture it was
written against has since changed to order two. The fix is in the test.

The opposite reading is the other recipe: when a span *disagrees* with the
intent — a quantity that changes between two calls, a call that should have
followed and did not — the code is wrong, and
[Help me debug and fix this bug](help-me-debug-and-fix-this-bug.md) is that
walk-through.

## What is not captured

- **Only that test's run.** The narrative is written per test method, per run.
  A failure that happens once in twenty runs needs one of those runs to be
  the one that wrote the file; a passing run overwrites nothing (each run
  writes its own file, and the value-free `.nt` of a failing run is not
  advanced at all).
- **The assertion is not in the trace.** The two outputs above come from two
  places — the runner reported the mismatch, the extension wrote the flow —
  and nothing correlates them automatically beyond the scenario name.
- **A value a test computed but never passed to a traced call is invisible.**
  The trace carries parameters and return values of wrapped calls, not local
  variables.
- **Timings in a failing run mean nothing.** The run stopped being
  representative the moment the assertion threw.

**The skill for this:** `narrativetrace-debug` — it starts from the symptom,
reads the first span where a value diverges, and names it by span id.
