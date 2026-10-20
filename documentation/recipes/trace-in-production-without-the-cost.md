# Trace in production without the cost

[English](trace-in-production-without-the-cost.md) | [Español](../es/recetas/traza-en-produccion-sin-el-coste.md) | [Português](../pt-BR/receitas/trace-em-producao-sem-o-custo.md) | [简体中文](../zh-CN/实用方案/在生产环境追踪而不付出代价.md)

## The problem

> I want this in production for the requests that go wrong. I am not paying
> for a full trace of every request that goes right.

That is one setting — `TracingLevel` — and this page is the same run seen
through it twice: the declined-payment checkout at `DETAIL`, where every value
is captured, and at `ERRORS`, where only runs that failed survive at all.

## Minimal setup

One level, set wherever your deployment is configured. All three of these say
the same thing:

```kotlin
// build.gradle.kts — for test runs through the Gradle plugin
narrativeTrace {
    tracingLevel.set("ERRORS")   // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
}
```

```properties
# narrativetrace.properties — for a plain Java or java-agent deployment
narrativetrace.level=ERRORS
```

```bash
# or on the command line, which wins over the file
java -Dnarrativetrace.level=ERRORS -jar app.jar
```

The level is NarrativeTrace's own dial and it sits at the front: a call it
filters out never becomes an event, for any consumer. Your logger's level is a
second, independent dial that decides what is *printed* —
[FAQ, "Two dials, two paths"](../faq.md) is the long version, and the
distinction matters here because raising a logger level saves you log volume
while changing nothing about capture.

## The scenario

The run is the declined-payment reproduction from
[Help me debug and fix this bug](help-me-debug-and-fix-this-bug.md) —
`reserve`, then `charge` throwing, then `release` — reused unchanged, because
the subject of this page is the dial and not another flow. The driver below
runs it at both levels, and runs a *successful* checkout at `ERRORS` as well:

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/CostRecipeTest.java -->
```java
// src/test/java/com/example/checkout/CostRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;

/**
 * Proves documentation/recipes/trace-in-production-without-the-cost.md: the SAME declined-payment
 * run, captured once at {@code DETAIL} and once at {@code ERRORS}, writes the two artifacts that
 * page embeds — and what {@code ERRORS} drops is every call that returned normally (rule 8, docs as
 * tests).
 *
 * <p><b>@llmNote</b> The scenario is {@link DeclinedPaymentFixture}'s fixed reproduction, reused
 * rather than rewritten: this page's subject is the level dial, not another flow, and a reader
 * comparing the two levels should be comparing one run's renderings. Only the passing method is
 * selected — the fixture's other test fails by design for
 * documentation/recipes/help-me-debug-and-fix-this-bug.md.
 */
class CostRecipeTest {

  private static final Path PAGE_OUTPUT =
      Path.of("build/narrativetrace/recipes/trace-in-production-without-the-cost");

  private static final String SCENARIO = "the_stock_is_released_when_the_card_is_declined";

  @Test
  void errors_keeps_the_failed_run_whole_and_writes_nothing_for_a_successful_one()
      throws IOException {
    var errors = capture("errors", "ERRORS");

    assertThat(errors)
        .contains(
            "OrderService.placeOrder",
            "InventoryService.reserve",
            "PaymentService.charge",
            "InventoryService.release");
    // The control: the same path IS written when the level keeps successful runs, so the
    // absence below is the level's doing and not a path that was never right.
    assertThat(successfulRunAt(null)).exists();
    assertThat(successfulRunAt("ERRORS")).doesNotExist();
  }

  @Test
  void detail_carries_the_values_errors_suppresses() throws IOException {
    assertThat(capture("detail", null)).contains("customerId: `\"C-1234\"`");
    assertThat(capture("errors", "ERRORS")).contains("customerId: …").doesNotContain("\"C-1234\"");
  }

  @Test
  void both_levels_still_redact_the_card_number() throws IOException {
    assertThat(capture("detail", null)).contains("[REDACTED]").doesNotContain("4000000000000002");
    assertThat(capture("errors", "ERRORS"))
        .contains("[REDACTED]")
        .doesNotContain("4000000000000002");
  }

  /**
   * The successful checkout at [level]: at ERRORS nothing failed, so there is no trace to write.
   */
  private static Path successfulRunAt(String level) {
    var outputDir = PAGE_OUTPUT.resolve(level == null ? "detail-successful" : "errors-successful");
    var previous = System.getProperty("narrativetrace.level");
    setOrClear(level);
    try {
      var run =
          RecipeRuns.run(DiscoverySelectors.selectClass(CheckoutReviewFixture.class), outputDir);
      assertThat(run.getTestsFailedCount()).isZero();
    } finally {
      setOrClear(previous);
    }
    return outputDir.resolve("traces/CheckoutReviewFixture/a_customer_buys_two_keyboards.md");
  }

  /** Runs the reproduction into its own directory, at [level] (null leaves the default, DETAIL). */
  private static String capture(String directory, String level) throws IOException {
    var outputDir = PAGE_OUTPUT.resolve(directory);
    var previous = System.getProperty("narrativetrace.level");
    setOrClear(level);
    try {
      var run =
          RecipeRuns.run(
              DiscoverySelectors.selectMethod(
                  DeclinedPaymentFixture.class, SCENARIO, NarrativeContext.class.getName()),
              outputDir);
      assertThat(run.getTestsFailedCount()).isZero();
    } finally {
      setOrClear(previous);
    }
    return Files.readString(outputDir.resolve("traces/DeclinedPaymentFixture/" + SCENARIO + ".md"));
  }

  private static void setOrClear(String level) {
    if (level == null) {
      System.clearProperty("narrativetrace.level");
    } else {
      System.setProperty("narrativetrace.level", level);
    }
  }
}
```
<!-- /snippet -->

## The actual output

**At `DETAIL`**, the declined run, every value captured:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/trace-in-production-without-the-cost/detail/traces/DeclinedPaymentFixture/the_stock_is_released_when_the_card_is_declined.md mask=duration,traceName -->
```text
---
type: trace
run: big tray chirps
scenario: The stock is released when the card is declined
entry_point: OrderService.placeOrder
duration_ms: 1.424
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 2
---

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** The stock is released when the card is declined
**Duration:** 1.424ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) — 1.424ms #1
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.18ms #1.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) #1.2

    > ❌ `PaymentDeclinedException`: card declined by the issuer — 0.192ms
  - **InventoryService.release**(sku: `"SKU-KB"`, quantity: `2`) — 0.179ms #1.3
  - ❌ `PaymentDeclinedException`: card declined by the issuer
```
<!-- /snippet -->

**At `ERRORS`**, the same run of the same code:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/trace-in-production-without-the-cost/errors/traces/DeclinedPaymentFixture/the_stock_is_released_when_the_card_is_declined.md mask=duration,traceName -->
```text
---
type: trace
run: fast cleft grows
scenario: The stock is released when the card is declined
entry_point: OrderService.placeOrder
duration_ms: 2.282
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 2
---

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** The stock is released when the card is declined
**Duration:** 2.282ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: …, sku: …, quantity: …, cardNumber: `[REDACTED]`) — 2.282ms #1
  - **InventoryService.reserve**(sku: …, quantity: …) — 0.214ms #1.1
  - **PaymentService.charge**(customerId: …, amountCents: …, card: …) #1.2

    > ❌ `PaymentDeclinedException`: card declined by the issuer — 0.302ms
  - **InventoryService.release**(sku: …, quantity: …) — 0.44ms #1.3
  - ❌ `PaymentDeclinedException`: card declined by the issuer
```
<!-- /snippet -->

**And at `ERRORS`, a checkout that succeeded**: no artifact at all. There is
nothing to embed, which is the whole point — the scenario above asserts both
halves of that claim, the file's absence at `ERRORS` and its presence at
`DETAIL`, so the emptiness is the level's doing and not a path that was never
right.

## How to read it

- **What `ERRORS` keeps.** The failed run, whole. `#1` threw, and an error
  node's subtree is kept intact — so `reserve` (`#1.1`), `charge` (`#1.2`) and
  `release` (`#1.3`) are all still there, including the two calls that
  returned normally. That is deliberate: the calls around a failure are what
  you need to diagnose it, and dropping the successful sibling would leave you
  with an exception and no story. A call that returned *and* leads to no
  failure anywhere below it is dropped, and a root with no failure in its
  whole subtree is dropped entirely — which is why the successful checkout
  wrote nothing.
- **What `ERRORS` drops.** Every parameter value: `customerId: …`,
  `quantity: …`. The `…` is suppression at *capture* — the value was never
  rendered, so it cannot be recovered from these events afterwards, at any
  later level. What survives is the shape, the outcomes and the exception, and
  `cardNumber: [REDACTED]` is still redacted because redaction is a different
  decision from suppression.
- **The pruning happens after capture, not instead of it.** Every call was
  intercepted, every event was published; `TraceTreeBuilder` then built the
  tree and removed what the level does not keep. That is the honest shape of
  the cost: `ERRORS` is cheaper than `DETAIL` because values are not rendered
  — the expensive part — not because calls go unintercepted.
- **Only `OFF` skips interception.** At `OFF` the proxy's `isActive()` gate
  returns before any argument array, event or publish happens, and nothing
  downstream can bring the call back. `ERRORS`, `SUMMARY` and `NARRATIVE` all
  still intercept every call and publish an entry and an exit for it. If the
  question is "what does tracing cost in this hot loop", the answer is a
  different level for that scope, or `OFF`.
- **The numbers.** The measured figures — the inactive-path overhead, the
  allocation per call, the cost of an active traced call — are in the
  [README's Performance section](../../README.md#performance), beside the
  committed JMH baselines they come from. They are not repeated here, because
  one copy that moves with the measurements is worth more than two that
  disagree.

## What is not captured

- **The published figures are measurements, not a promise.** They were taken
  with JMH on the harness and hardware the README names, one benchmark
  operation per call. Your numbers depend on your JDK, your hardware, your
  parameter shapes (a deep object graph renders for longer than an `int`) and
  how much of your code is traced. Measure the level you intend to run.
- **A level is not a privacy control.** `ERRORS` happens to write fewer values
  because it suppresses them, but suppression and redaction are different
  mechanisms with different guarantees:
  [Keep secrets out of the logs](keep-secrets-out-of-the-logs.md) is the one
  that holds at every level.
- **Nothing here changes how much your logger writes.** Each captured event is
  still offered to the synchronous path, so the `narrativetrace` logger's own
  level is what decides log volume. Two dials.
- **A level cannot be raised retroactively.** Values not captured are gone;
  reproducing the run at `DETAIL` is the only way back, which is what the
  debugging recipe does.
- **Per-scope levels are not shown here.** This page sets one level for a
  whole run. Narrowing the traced scope instead — which classes are wrapped at
  all — is the other half of the cost question, and belongs to
  [Choosing an Integration](../choosing-an-integration.md).

**The skill for this:** `narrativetrace-doctor` — it reports the level a
project is actually running at, which is the first thing to check when a
trace is thinner than expected.
