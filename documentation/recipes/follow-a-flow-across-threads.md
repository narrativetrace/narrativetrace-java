# Follow a flow across threads

[English](follow-a-flow-across-threads.md) | [Español](../es/recetas/sigue-un-flujo-entre-hilos.md) | [Português](../pt-BR/receitas/acompanhe-um-fluxo-entre-threads.md) | [简体中文](../zh-CN/实用方案/跨线程追踪一个流程.md)

## The problem

> The checkout hands the slow parts to an executor. The trace stops at the
> submit and I cannot see what the background work did.

A thread knows nothing about the trace another thread is in, so handing work
to an executor hands over the work and nothing else. The trace has to be
handed over too — explicitly, at submission time. Below is one run that does,
and one that does not.

## Minimal setup

Nothing beyond the [install](../installation-guide.md): `ForkGroup` and
`FireAndForgetGroup` are in `narrativetrace-core`'s `context` package, beside
the context itself. Which one depends on what the caller does next:

| The launching call | The helper | The ending step |
|---|---|---|
| Waits for the background work (this page) | `ForkGroup` | `merge()`, after the tasks finish — it grafts what they recorded under the launching span |
| Returns without waiting | `FireAndForgetGroup` | None. The children are a separate record, read through `childRoots()` |

If your framework already propagates context — Spring Boot 3 with
Micrometer's `ThreadLocalAccessor`, Reactor, `@Async` — that handover is
covered by the integration, and you write neither of the above. The
[Feature Guide](../feature-guide.md) is the row-by-row version.

The log lines use the same configuration as
[Follow one request across services](follow-one-request-across-services.md),
with the thread and the raw trace id in the pattern — on this page the thread
column is half the story.

## The scenario

**The checkout, with the two slow steps off its own thread.** The handover is
`group.wrap(...)` around each task and `group.merge()` after both are done:

<!-- snippet: recipes/checkout/src/main/java/com/example/checkout/ParallelCheckout.java -->
```java
// src/main/java/com/example/checkout/ParallelCheckout.java
package com.example.checkout;

import ai.narrativetrace.core.context.ForkGroup;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * The same checkout with the two slow steps off the caller's thread: reserve the stock and run the
 * fraud check on an executor, wait for both, then charge and save.
 *
 * <p>INTENT: Shows the handover for documentation/recipes/follow-a-flow-across-threads.md. A task
 * handed to an executor runs on a thread that knows nothing about the caller's trace, so the trace
 * has to be handed over with it: {@link ForkGroup#wrap} takes a snapshot at submission time, the
 * worker activates it, and {@link ForkGroup#merge()} grafts what the worker recorded back under the
 * span that launched it.
 *
 * <p><b>@llmNote</b> Creating a group is not enough — every forked task must be wrapped and {@code
 * merge()} must run after they finish, or the children stay detached from the caller's trace. The
 * same applies to work that outlives the call, with {@code FireAndForgetGroup} instead.
 *
 * <p><b>@sideEffects</b> Submits to the executor it is given and blocks until both tasks are done;
 * a refused order releases the reservation before it throws, like {@link DefaultOrderService}.
 */
public final class ParallelCheckout implements OrderService {

  private static final int UNIT_PRICE_CENTS = 3999;

  private final NarrativeContext context;
  private final InventoryService inventory;
  private final FraudCheckService fraud;
  private final PaymentService payments;
  private final OrderRepository orders;
  private final ExecutorService workers;

  public ParallelCheckout(
      NarrativeContext context,
      InventoryService inventory,
      FraudCheckService fraud,
      PaymentService payments,
      OrderRepository orders,
      ExecutorService workers) {
    this.context = context;
    this.inventory = inventory;
    this.fraud = fraud;
    this.payments = payments;
    this.orders = orders;
    this.workers = workers;
  }

  @Override
  public String placeOrder(String customerId, String sku, int quantity, String cardNumber) {
    var amountCents = UNIT_PRICE_CENTS * quantity;
    Runnable reserving = () -> inventory.reserve(sku, quantity);
    Callable<Boolean> scoring = () -> fraud.approve(customerId, amountCents);

    var group = ForkGroup.create(context);
    var reserved = workers.submit(group.wrap(reserving));
    var approved = workers.submit(group.wrap(scoring));
    var allowed = awaitBoth(reserved, approved);
    group.merge();

    if (!allowed) {
      inventory.release(sku, quantity);
      throw new PaymentDeclinedException("the fraud check refused the order");
    }
    payments.charge(customerId, amountCents, cardNumber);
    var orderId = "ORD-" + customerId + "-" + sku + "-" + quantity;
    orders.save(new Order(orderId, customerId, sku, quantity, amountCents));
    return orderId;
  }

  /** Waits for the reservation, then for the decision — the merge may only run after both. */
  private static boolean awaitBoth(Future<?> reserved, Future<Boolean> approved) {
    try {
      reserved.get();
      return approved.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for the checkout's tasks", e);
    } catch (ExecutionException e) {
      throw asRuntime(e);
    }
  }

  /** A task's own failure belongs to the caller, not wrapped in an executor's exception. */
  private static RuntimeException asRuntime(ExecutionException failure) {
    var cause = failure.getCause();
    if (cause instanceof RuntimeException runtime) {
      return runtime;
    }
    return new IllegalStateException("a checkout task failed", cause);
  }
}
```
<!-- /snippet -->

**The run — and the same work without the handover.** The second test
submits a traced call to the same executor with no `ForkGroup` and no wrap,
which is what the code looked like before the handover was added:

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/AcrossThreadsRecipeTest.java -->
```java
// src/test/java/com/example/checkout/AcrossThreadsRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves documentation/recipes/follow-a-flow-across-threads.md: the two tasks the checkout hands to
 * an executor stay inside the caller's trace when they are wrapped, and a task submitted without
 * the handover starts a trace of its own (rule 8, docs as tests).
 *
 * <p><b>@llmNote</b> One worker thread, named: the page embeds a real run's log byte for byte, and
 * two threads logging at once interleave differently every time. The handover is identical either
 * way — what a second thread would add is nondeterministic output, not a second mechanism. The
 * Gradle task {@code recipeAcrossThreadsLog} names the first test METHOD, not this class: the
 * second one's trace id is freshly generated on every run, which is the finding it reports and the
 * one thing no embedded block can hold.
 */
@ExtendWith(NarrativeTraceExtension.class)
class AcrossThreadsRecipeTest {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  private final ExecutorService workers =
      Executors.newSingleThreadExecutor(AcrossThreadsRecipeTest::checkoutWorker);

  @AfterEach
  void stopTheWorker() {
    workers.shutdownNow();
  }

  @Test
  void the_background_tasks_stay_in_the_customers_trace(NarrativeContext context) {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));
    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();
    var checkout = parallelCheckout(context, stock, repository);

    var orderId = checkout.placeOrder("C-1234", "SKU-KB", 2, "4242424242424242");

    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");
    assertThat(stock.availableUnits("SKU-KB")).isEqualTo(3);
    assertThat(repository.saved()).hasSize(1);
    assertTheChildrenCameHome(context);
  }

  /** The merged children are under the span that launched them, in the caller's own trace. */
  private static void assertTheChildrenCameHome(NarrativeContext context) {
    var root = context.captureTrace().roots().get(0);
    assertThat(names(root.children()))
        .containsExactly(
            "InventoryService.reserve",
            "FraudCheckService.approve",
            "PaymentService.charge",
            "OrderRepository.save");
    var reserve = root.children().get(0);
    assertThat(reserve.spanContext().traceId()).isEqualTo(root.spanContext().traceId());
    assertThat(reserve.spanContext().parentSpanId()).isEqualTo(root.spanContext().spanId());
    assertThat(reserve.concurrency().threadName()).isEqualTo("checkout-worker-1");
  }

  @Test
  void a_task_submitted_without_the_handover_starts_a_new_trace(NarrativeContext context)
      throws InterruptedException, ExecutionException {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));
    var stock = new InMemoryInventory("SKU-KB", 5);
    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var onTheWorker = new AtomicReference<TraceId>();

    // No ForkGroup, no snapshot: the task is handed over, the trace is not.
    workers
        .submit(
            () -> {
              inventory.reserve("SKU-KB", 2);
              onTheWorker.set(context.traceId());
            })
        .get();

    assertThat(stock.availableUnits("SKU-KB")).isEqualTo(3);
    assertThat(onTheWorker.get()).isNotNull().isNotEqualTo(context.traceId());
    assertThat(names(context.captureTrace().roots())).doesNotContain("InventoryService.reserve");
  }

  private OrderService parallelCheckout(
      NarrativeContext context, InMemoryInventory stock, InMemoryOrderRepository repository) {
    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var fraud =
        NarrativeTraceProxy.trace(new RuleBasedFraudCheck(), FraudCheckService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    return NarrativeTraceProxy.trace(
        new ParallelCheckout(context, inventory, fraud, payments, orders, workers),
        OrderService.class,
        context);
  }

  private static List<String> names(List<TraceNode> nodes) {
    return nodes.stream()
        .map(node -> node.signature().className() + "." + node.signature().methodName())
        .toList();
  }

  /** A named daemon thread, so the page's embedded output names the same thread every run. */
  private static Thread checkoutWorker(Runnable runnable) {
    var thread = new Thread(runnable, "checkout-worker-1");
    thread.setDaemon(true);
    return thread;
  }
}
```
<!-- /snippet -->

## The actual output

**The log of the run that keeps the trace:**

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/follow-a-flow-across-threads/checkout.log -->
```text
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → OrderService.placeOrder(customerId: "C-1234", sku: "SKU-KB", quantity: 2, cardNumber: [REDACTED])
TRACE [Test worker] [narrativetrace] [] - ⑂ fork group created [groupId: fork-1]
TRACE [checkout-worker-1] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → InventoryService.reserve(sku: "SKU-KB", quantity: 2)
TRACE [checkout-worker-1] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← completed
TRACE [checkout-worker-1] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → FraudCheckService.approve(customerId: "C-1234", amountCents: 7998)
TRACE [checkout-worker-1] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← returned: true
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → InventoryService.reserve(sku: "SKU-KB", quantity: 2)
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← completed
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → FraudCheckService.approve(customerId: "C-1234", amountCents: 7998)
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← returned: true
TRACE [Test worker] [narrativetrace] [] - ⑃ fork joined [groupId: fork-1, members: 2]
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → PaymentService.charge(customerId: "C-1234", amountCents: 7998, card: [REDACTED])
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← returned: "PAY-C-1234-7998"
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → OrderRepository.save(order: Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998))
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← completed
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← returned: "ORD-C-1234-SKU-KB-2"
```
<!-- /snippet -->

**The structural trace of that run,**
`build/narrativetrace/structural/AcrossThreadsRecipeTest/the_background_tasks_stay_in_the_customers_trace.nt`:

<!-- snippet: recipes/checkout/build/narrativetrace/structural/AcrossThreadsRecipeTest/the_background_tasks_stay_in_the_customers_trace.nt -->
```text
scenario: The background tasks stay in the customers trace

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) → value
  ~ fork [2]
    #1.1 - FraudCheckService.approve(customerId, amountCents) → value
    #1.2 - InventoryService.reserve(sku, quantity)
  #1.3 - PaymentService.charge(customerId, amountCents, card) → value
  #1.4 - OrderRepository.save(order)
```
<!-- /snippet -->

The Markdown artifact of the same run carries three things more — the worker
thread's name against each child, the join's wall time, and a "sequential
async" hint where the tasks did not overlap — and is not embedded here
because every one of those numbers differs on every run.

## How to read it

- **The handover is visible, twice.**
  `⑂ fork group created [groupId: fork-1]` is the group being created, before
  either task is submitted; `⑃ fork joined [groupId: fork-1, members: 2]` is
  `merge()` reporting what came back. Between them is background work that
  belongs to this checkout. Those two lines carry an empty trace id — the last
  bracket is `[]` — because they are not span events: they are the group's own
  lifecycle, published by the context rather than by a traced call.
- **The children's lines carry the customer's trace id, on the worker's
  thread.** `InventoryService.reserve` and `FraudCheckService.approve` are
  written from `[checkout-worker-1]` with
  `[c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7]` — the same id as every `[Test worker]`
  line above and below them. That is the snapshot `group.wrap` took at
  submission time, activated on the worker.
- **Each child appears twice in the log, and once in the trace.** The first
  pair is the work happening, on `checkout-worker-1`. The second pair — the
  same two calls, same values, on `Test worker`, just before the join line —
  is `merge()` re-publishing them under the span that launched them, which is
  what puts them in the caller's tree. Read the log as a log: two writes, one
  event each. Read the `.nt` as the record: `#1.1` and `#1.2` under `#1`,
  inside `~ fork [2]`, exactly once.
- **The parent span id on each child is the launching span.** `#1.1` and
  `#1.2` name `#1` as their parent — not because they ran under it on the same
  thread (they did not) but because the group grafted them there. A log line
  cannot show this (a span id is generated per span and would differ on every
  run), so `assertTheChildrenCameHome` above asserts it: each child's
  `parentSpanId` is the root's `spanId`, each child's `traceId` is the root's,
  and the recorded thread is `checkout-worker-1`. That assertion is this
  page's proof.
- **How the second run differs.** Submit the same traced call with no wrap and
  the worker thread has no snapshot to activate, so its first traced call does
  what any first traced call does: it starts a trace. The second test asserts
  the two consequences — the trace id observed *on the worker* is not the
  caller's, and the caller's own captured trace contains no
  `InventoryService.reserve` at all. The work still happens, the stock is
  still reserved, the lines are still logged. They are simply somebody else's
  story, under an id nobody is searching for.
- **Why "awaited sequentially" is the right diagnosis here.** The executor in
  this scenario has one thread, so the two tasks really do run one after the
  other and the Markdown artifact says so. That is a property of this
  scenario, chosen so one run's log is byte-stable enough to embed — not of
  the handover, which is the same code for a pool of any size.

## What is not captured

- **Work started without the handover starts a new trace.** That is the whole
  second half of this page: no wrap, no snapshot, no shared trace id. Nothing
  warns you, because from the library's point of view a fresh thread with a
  traced call on it is an ordinary new trace.
- **A framework that propagates context itself is covered only where its
  integration says so.** Micrometer's `ThreadLocalAccessor` (Spring Boot 3,
  Reactor, `@Async`) is wired by `narrativetrace-micrometer`; outside what the
  [Feature Guide](../feature-guide.md) lists, assume the handover is yours to
  write.
- **`captureTrace()` is thread-scoped.** It returns what the *calling* thread
  can report. A capture taken on a worker thread sees that worker's spans, not
  the caller's — which is why the group, not the query, is what brings the two
  together.
- **A group without `merge()` collects nothing into the caller's trace.** The
  children are recorded, and then nothing grafts them: the log still shows
  them (with the right trace id), the caller's tree does not.
- **Work that outlives the call is a different helper.** `FireAndForgetGroup`
  has no merge step by design — the launching method has already returned, so
  there is nobody left to merge into.
- **Interleaving.** With a real pool the children's lines arrive in whatever
  order the threads get scheduled, which is why a log is read by trace id and
  not by position. The trace artifacts are unaffected: parentage comes from
  the snapshot, not from the order lines were written.

**The skill for this:** `narrativetrace-verify` — "runs async" and "crosses
threads" are two of the cases it exists for, and it reads the structural trace
against the intent before anything is called done.
