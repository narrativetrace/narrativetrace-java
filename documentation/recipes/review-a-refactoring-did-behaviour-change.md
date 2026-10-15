# Review a refactoring: did behaviour change

[English](review-a-refactoring-did-behaviour-change.md) | [Español](../es/recetas/revisa-una-refactorizacion-cambio-el-comportamiento.md) | [Português](../pt-BR/receitas/revise-uma-refatoracao-o-comportamento-mudou.md) | [简体中文](../zh-CN/实用方案/审查重构行为变了吗.md)

## The problem

> I refactored the checkout. The tests are green. Did the behaviour change?

Green means no assertion noticed. A refactoring that drops a call nobody
asserts on is green and wrong, and this is the common shape of it: the
compensating call on an error path, which no happy-path test reaches.

## Minimal setup

Beyond the [install](../installation-guide.md), approval mode — one property,
and a directory of committed baselines:

```kotlin
// build.gradle.kts
narrativeTrace {
    approval.set(true)                              // compare, don't just write
    approvedDir.set(layout.projectDirectory.dir("src/test/narratives"))   // the default
}
```

The first run of each test writes `src/test/narratives/<TestClass>/<scenario>.received.nt`
and fails; you read it, run `./gradlew approveNarratives` to promote every
reviewed file to `.approved.nt`, and commit those. See
[Gradle Plugin Guide, `approveNarratives`](../gradle-plugin-guide.md#approvenarratives)
and [What to Commit](../what-to-commit.md#approval-mode-end-to-end).

## The scenario

The test pins the error path a refactoring is most likely to break — a
declined card must save no order — and it passes against both versions of the
checkout, which is the whole problem:

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/RefactoredCheckoutFixture.java -->
```java
// src/test/java/com/example/checkout/RefactoredCheckoutFixture.java
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
 * The test documentation/recipes/review-a-refactoring-did-behaviour-change.md pins: a declined card
 * must save no order. It passes before and after the refactoring — which is the whole point of the
 * page — so only the approved structure can object.
 *
 * <p>A fixture because {@code RefactoringRecipeTest} runs it twice under approval mode, once per
 * version of the checkout, and reads the verdicts.
 */
@ExtendWith(NarrativeTraceExtension.class)
class RefactoredCheckoutFixture {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  /**
   * Which version of the checkout this run wires: the one that releases the reservation when the
   * charge is declined, or the refactoring that dropped that call. A field, because one build has
   * to run both — in your project the refactoring is an edit to one file and this switch does not
   * exist.
   */
  static volatile boolean refactoringDroppedTheRelease;

  @Test
  void a_declined_card_saves_no_order(NarrativeContext context) {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));

    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();

    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    var checkout =
        NarrativeTraceProxy.trace(
            refactoringDroppedTheRelease
                ? new OrderServiceWithoutRelease(inventory, payments, orders)
                : new DefaultOrderService(inventory, payments, orders),
            OrderService.class,
            context);

    assertThatThrownBy(
            () -> checkout.placeOrder("C-1234", "SKU-KB", 2, CardPaymentService.DECLINING_CARD))
        .isInstanceOf(PaymentDeclinedException.class);

    assertThat(repository.saved()).isEmpty();
  }
}
```
<!-- /snippet -->

## The actual output

The committed baseline, at
`src/test/narratives/RefactoredCheckoutFixture/a_declined_card_saves_no_order.approved.nt`:

<!-- snippet: recipes/checkout/src/test/narratives/RefactoredCheckoutFixture/a_declined_card_saves_no_order.approved.nt -->
```text
scenario: A declined card saves no order

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) !! PaymentDeclinedException
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) !! PaymentDeclinedException
  #1.3 - InventoryService.release(sku, quantity)
```
<!-- /snippet -->

The refactored run's verdict — the assertions passed, the structure did not:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/review-a-refactoring-did-behaviour-change/approval-rejection.txt -->
```text
Narrative changed against the approved baseline (-1 call InventoryService.release):
 scenario: A declined card saves no order
 
 #1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) !! PaymentDeclinedException
   #1.1 - InventoryService.reserve(sku, quantity)
   #1.2 - PaymentService.charge(customerId, amountCents, card) !! PaymentDeclinedException
-  #1.3 - InventoryService.release(sku, quantity)
Received: build/narrativetrace/recipes/review-a-refactoring-did-behaviour-change/approved-after-the-refactoring/RefactoredCheckoutFixture/a_declined_card_saves_no_order.received.nt
If this change is intended, approve it via the approveNarratives task. A stale .received.nt left after you resolve this is exactly what the approval-traces check catches → narrativetrace-doctor skill
```
<!-- /snippet -->

And the file it offers for review, which `approveNarratives` would promote:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/review-a-refactoring-did-behaviour-change/approved-after-the-refactoring/RefactoredCheckoutFixture/a_declined_card_saves_no_order.received.nt -->
```text
scenario: A declined card saves no order

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) !! PaymentDeclinedException
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) !! PaymentDeclinedException
```
<!-- /snippet -->

(The path in `Received:` is this repository's own: the run that produces this
page points `approvedDir` under `build/`, because the task that writes a page
may not write into the source tree. In your project it is `src/test/narratives`,
beside the baseline.)

## How to read it

- **The first line names the change, not just the fact of it:**
  `-1 call InventoryService.release`. One call fewer, and which one.
- **The diff body is the pinned shape with the dropped line marked.** `#1`,
  `#1.1` and `#1.2` are unchanged and unmarked; `-  #1.3 - InventoryService.release(sku, quantity)`
  is the behaviour that left. A reviewer who has never seen this code can tell
  what the refactoring did from four lines.
- **The test still passed.** The failure is the approval's, which is the point:
  the suite asserts that no order is saved, and the refactoring did not break
  that. Nothing in the test ever looked at the release, and nothing had to.
- **Now decide.** If dropping the release is intended — the caller took it
  over, say — run `./gradlew approveNarratives`, review the diff in the
  promoted file, and commit it: the new shape becomes the pinned one. If it is
  not intended, you have just caught the bug that
  [Help me debug and fix this bug](help-me-debug-and-fix-this-bug.md) fixed,
  on its way back in. Either way the decision is recorded in the repository,
  by a human, in a file a reviewer can read.
- **The baseline is value-free**, so approving it leaks nothing and a change
  in test data never makes it fail: `(customerId, sku, quantity, cardNumber)`
  are names, `!! PaymentDeclinedException` is an outcome.

## What is not captured

- **This is a structural comparison: the same calls, in the same order, with
  the same outcomes.** It is not a proof that every value is equal. A
  refactoring that charges 7998 where it charged 3999, with every call in
  place, passes this gate — that is what your assertions are for, and the
  `DETAIL` artifact is where you would see it.
- **Only the calls the integration intercepts.** A dropped private call, or
  one between two unwrapped classes, is invisible to the baseline exactly as
  it is to the trace.
- **A test that does not run is not compared.** Approval protects the
  scenarios you pinned; a flow with no test has no baseline, and deleting a
  test deletes its gate (the `.approved.nt` is then an orphan, which
  `narrativetrace-doctor` reports).
- **Timings are never part of it.** The baseline carries no durations, so it
  cannot catch a change that only made the flow slower.

**The skill for this:** `narrativetrace-verify` — it reads the structural
trace against the intent of the change and pins the flow as a baseline behind
your yes.
