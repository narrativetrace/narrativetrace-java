# Help me debug and fix this bug

[English](help-me-debug-and-fix-this-bug.md) | [Español](../es/recetas/ayudame-a-depurar-y-arreglar-este-error.md) | [Português](../pt-BR/receitas/ajude-me-a-depurar-e-corrigir-este-bug.md) | [简体中文](../zh-CN/实用方案/帮我调试并修复这个缺陷.md)

## The problem

> Help me debug and fix this bug: a customer's card was declined and the
> keyboard never came back into stock.

The error the team has is `PaymentDeclinedException: card declined by the
issuer`, which is not the bug. The bug is what the checkout did *after* it.

## Minimal setup

Nothing beyond the [install](../installation-guide.md): the JUnit 5 extension
writes one trace per test with no configuration. The last step on this page —
pinning the fixed flow — turns on approval mode, which is one property:

```kotlin
// build.gradle.kts
narrativeTrace {
    approval.set(true)   // compare each passing test's structure with its committed baseline
}
```

See [Gradle Plugin Guide, `approval`](../gradle-plugin-guide.md#approval) and
[What to Commit](../what-to-commit.md#approval-mode-end-to-end).

## The scenario

Reproduce it in the smallest test that can fail for this reason — a declined
card must leave the stock as it found it:

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/DeclinedPaymentFixture.java -->
```java
// src/test/java/com/example/checkout/DeclinedPaymentFixture.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The reproduction documentation/recipes/help-me-debug-and-fix-this-bug.md is written around: a
 * declined card must leave the stock as it found it.
 *
 * <p>The second test is the same reproduction against the checkout as it stood before the fix
 * ({@link OrderServiceWithoutRelease}), so the page's "before" trace comes from a real failing run
 * rather than from prose. It FAILS by design, which is why this class is a fixture: {@code
 * tasks.test} excludes {@code *Fixture}, and {@code DebugRecipeTest} runs it through the JUnit
 * Platform launcher and asserts on its verdict.
 *
 * <p><b>@llmNote</b> Both tests adopt the same fixed traceparent, so the two artifacts a reader
 * compares differ only where the code differs.
 */
@ExtendWith(NarrativeTraceExtension.class)
class DeclinedPaymentFixture {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  @Test
  void the_stock_is_released_when_the_card_is_declined(NarrativeContext context) {
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

    assertThatThrownBy(
            () -> checkout.placeOrder("C-1234", "SKU-KB", 2, CardPaymentService.DECLINING_CARD))
        .isInstanceOf(PaymentDeclinedException.class);

    assertThat(repository.saved()).isEmpty();
    assertThat(stock.availableUnits("SKU-KB")).isEqualTo(5);
  }

  @Test
  void the_stock_is_released_when_the_card_is_declined_before_the_fix(NarrativeContext context) {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));

    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();

    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    var checkout =
        NarrativeTraceProxy.trace(
            new OrderServiceWithoutRelease(inventory, payments, orders),
            OrderService.class,
            context);

    assertThatThrownBy(
            () -> checkout.placeOrder("C-1234", "SKU-KB", 2, CardPaymentService.DECLINING_CARD))
        .isInstanceOf(PaymentDeclinedException.class);

    assertThat(repository.saved()).isEmpty();
    assertThat(stock.availableUnits("SKU-KB")).isEqualTo(5);
  }
}
```
<!-- /snippet -->

The second test runs the same reproduction against the checkout as it stood
before the fix, which is the run the first trace below comes from:

<!-- snippet: recipes/checkout/src/main/java/com/example/checkout/OrderServiceWithoutRelease.java -->
```java
// src/main/java/com/example/checkout/OrderServiceWithoutRelease.java
package com.example.checkout;

/**
 * The same checkout with no release path: a declined charge leaves the reservation behind.
 *
 * <p>INTENT: Two recipe pages need this code to RUN, not to be described — {@code
 * documentation/recipes/help-me-debug-and-fix-this-bug.md} (the bug, before its fix) and {@code
 * documentation/recipes/review-a-refactoring-did-behaviour-change.md} (the refactoring that drops
 * the release call while every test stays green). It is kept beside {@link DefaultOrderService}
 * rather than reconstructed by a flag, so each page embeds real source and each run is a real run.
 *
 * <p><b>@llmNote</b> Never the implementation to copy: {@link DefaultOrderService} is. A trace
 * names the interface, not the class, so both render as {@code OrderService.placeOrder} — which is
 * exactly what makes one approved baseline comparable across the two.
 */
public final class OrderServiceWithoutRelease implements OrderService {

  private static final int UNIT_PRICE_CENTS = 3999;

  private final InventoryService inventory;
  private final PaymentService payments;
  private final OrderRepository orders;

  public OrderServiceWithoutRelease(
      InventoryService inventory, PaymentService payments, OrderRepository orders) {
    this.inventory = inventory;
    this.payments = payments;
    this.orders = orders;
  }

  @Override
  public String placeOrder(String customerId, String sku, int quantity, String cardNumber) {
    inventory.reserve(sku, quantity);
    var amountCents = UNIT_PRICE_CENTS * quantity;
    payments.charge(customerId, amountCents, cardNumber);
    var orderId = "ORD-" + customerId + "-" + sku + "-" + quantity;
    orders.save(new Order(orderId, customerId, sku, quantity, amountCents));
    return orderId;
  }
}
```
<!-- /snippet -->

## The actual output

The failing run's own narrative, at
`build/narrativetrace/traces/DeclinedPaymentFixture/the_stock_is_released_when_the_card_is_declined_before_the_fix.md`:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/help-me-debug-and-fix-this-bug/traces/DeclinedPaymentFixture/the_stock_is_released_when_the_card_is_declined_before_the_fix.md mask=duration,traceName -->
```text
---
type: trace
run: elfin crest nods
scenario: The stock is released when the card is declined before the fix
entry_point: OrderService.placeOrder
duration_ms: 2.003
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 3
error_count: 2
---

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** The stock is released when the card is declined before the fix
**Duration:** 2.003ms | **Result:** FAILED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) — 2.003ms #1
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.381ms #1.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) #1.2

    > ❌ `PaymentDeclinedException`: card declined by the issuer — 0.321ms
  - ❌ `PaymentDeclinedException`: card declined by the issuer
```
<!-- /snippet -->

The fix is one `catch`: release what was reserved, then let the decline
propagate as before.

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

The same reproduction, now passing, against the fixed checkout:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/help-me-debug-and-fix-this-bug/traces/DeclinedPaymentFixture/the_stock_is_released_when_the_card_is_declined.md mask=duration,traceName -->
```text
---
type: trace
run: elfin crest nods
scenario: The stock is released when the card is declined
entry_point: OrderService.placeOrder
duration_ms: 3.03
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 2
---

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** The stock is released when the card is declined
**Duration:** 3.03ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) — 3.03ms #1
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.608ms #1.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) #1.2

    > ❌ `PaymentDeclinedException`: card declined by the issuer — 0.404ms
  - **InventoryService.release**(sku: `"SKU-KB"`, quantity: `2`) — 0.367ms #1.3
  - ❌ `PaymentDeclinedException`: card declined by the issuer
```
<!-- /snippet -->

With approval mode on, the first run of the fixed test writes its value-free
structure beside the test as `.received.nt`; `./gradlew approveNarratives`
promotes it to the committed baseline, which is now the shape of this flow:

<!-- snippet: recipes/checkout/src/test/narratives/DeclinedPaymentFixture/the_stock_is_released_when_the_card_is_declined.approved.nt -->
```text
scenario: The stock is released when the card is declined

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) !! PaymentDeclinedException
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) !! PaymentDeclinedException
  #1.3 - InventoryService.release(sku, quantity)
```
<!-- /snippet -->

## How to read it

In the failing trace:

- `#1 OrderService.placeOrder` ends with `❌ PaymentDeclinedException` and no
  return value: the call the customer made did not complete.
- `#1.1 InventoryService.reserve(sku: "SKU-KB", quantity: 2)` has no `❌` and
  no value — a `void` method that completed. Two units are now held.
- `#1.2 PaymentService.charge` threw. Its parameters say what was attempted:
  `amountCents: 7998` for the two units, `card: [REDACTED]`.
- **Nothing follows `#1.2`.** `#1` goes straight from the charge to its own
  failure, so the two units `#1.1` reserved are still held. That absence is
  the bug, and the exception message never mentions it — the trace is what
  makes an omission visible, because it shows the whole flow and not only the
  line that threw.

In the passing trace: `#1.3 InventoryService.release(sku: "SKU-KB", quantity: 2)`
now sits between the charge and the failure. `#1` still ends in the decline —
that is correct, the customer's card really was declined — and the stock is
back.

In the baseline: the approved `.nt` carries the same four calls with their
parameter *names* and no values at all, which is why it can be committed and
read in a review. `#1.3` is now part of the pinned shape: a later change that
drops the release makes a passing test fail with a diff naming
`InventoryService.release`, so this bug cannot silently return. That is
[Review a refactoring: did behaviour change](review-a-refactoring-did-behaviour-change.md).

## What is not captured

- **The trace shows calls that happened, not calls that should have.** The
  missing `release` is an absence a reader infers from the flow; nothing in
  the artifact says "a call is missing". What the baseline adds is the
  comparison that turns an absence into a build failure — once.
- **An exception swallowed inside unwrapped code is invisible.** Only the four
  wrapped interfaces appear. A `try`/`catch` inside `DefaultOrderService` that
  ate the decline and returned normally would show as a completed call.
- **The card number cannot be recovered** from the trace or the log. It is
  redacted at capture, by value here — the parameter is named `card`, so the
  name deny-list never saw it, and the number is Luhn-valid.
- **Timings are not evidence of a performance problem** in a test this small:
  the first wrapped call in a JVM carries the proxy's one-off setup.

**The skill for this:** `narrativetrace-debug` — it reproduces the symptom
with the smallest input, names the span where the value or the call diverges,
fixes it there, and pins the flow as a regression test behind your approval.
