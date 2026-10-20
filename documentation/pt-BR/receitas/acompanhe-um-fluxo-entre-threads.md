<!-- source: documentation/recipes/follow-a-flow-across-threads.md blob bb0939c33604 | translated: 2026-10-17 | reviewed: - -->
# Acompanhe um fluxo entre threads

[English](../../recipes/follow-a-flow-across-threads.md) | [Español](../../es/recetas/sigue-un-flujo-entre-hilos.md) | **Português** | [简体中文](../../zh-CN/实用方案/跨线程追踪一个流程.md)

## O problema

> O checkout entrega as partes lentas a um executor. O trace para no submit e
> eu não consigo ver o que o trabalho em segundo plano fez.

Uma thread não sabe nada do trace em que outra thread está, então entregar
trabalho a um executor entrega o trabalho e mais nada. O trace também precisa
ser entregue — explicitamente, no momento do submit. Abaixo há uma execução que
entrega e uma que não.

## Configuração mínima

Nada além da [instalação](../guia-de-instalacao.md): `ForkGroup` e
`FireAndForgetGroup` estão no pacote `context` do `narrativetrace-core`, ao
lado do próprio contexto. Qual deles depende do que quem lança faz em seguida:

| A chamada que lança | O ajudante | O passo final |
|---|---|---|
| Espera pelo trabalho em segundo plano (esta página) | `ForkGroup` | `merge()`, depois que as tarefas terminam — ele enxerta o que elas registraram sob o span que as lançou |
| Retorna sem esperar | `FireAndForgetGroup` | Nenhum. Os filhos são um registro separado, lido por `childRoots()` |

Se o seu framework já propaga contexto — Spring Boot 3 com o
`ThreadLocalAccessor` do Micrometer, Reactor, `@Async` — essa entrega é coberta
pela integração e você não escreve nenhum dos dois. O
[Guia de funcionalidades](../guia-de-funcionalidades.md) é a versão linha por
linha.

As linhas de log usam a mesma configuração de
[Acompanhe uma requisição entre serviços](acompanhe-uma-requisicao-entre-servicos.md),
com a thread e o id do trace em cru no pattern — nesta página a coluna da
thread é metade da história.

## O cenário

**O checkout, com os dois passos lentos fora da própria thread.** A entrega é
`group.wrap(...)` em volta de cada tarefa e `group.merge()` depois que as duas
terminam:

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

**A execução — e o mesmo trabalho sem a entrega.** O segundo teste envia uma
chamada traçada ao mesmo executor sem `ForkGroup` e sem wrap, que é como o
código era antes de a entrega ser adicionada:

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

## A saída real

**O log da execução que mantém o trace:**

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

**O trace estrutural daquela execução,**
`build/narrativetrace/structural/AcrossThreadsRecipeTest/the_background_tasks_stay_in_the_customers_trace.nt`:

```text
scenario: The background tasks stay in the customers trace

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) → value
  ~ fork [2]
    #1.1 - FraudCheckService.approve(customerId, amountCents) → value
    #1.2 - InventoryService.reserve(sku, quantity)
  #1.3 - PaymentService.charge(customerId, amountCents, card) → value
  #1.4 - OrderRepository.save(order)
```

O artefato Markdown da mesma execução carrega três coisas a mais — o nome da
thread trabalhadora ao lado de cada filho, o tempo de parede do join e uma
dica de "sequential async" quando as tarefas não se sobrepuseram — e não é
embutido aqui porque cada um desses números muda a cada execução.

## Como ler isso

- **A entrega aparece, duas vezes.**
  `⑂ fork group created [groupId: fork-1]` é o grupo sendo criado, antes de
  qualquer tarefa ser enviada; `⑃ fork joined [groupId: fork-1, members: 2]` é
  o `merge()` relatando o que voltou. Entre as duas está o trabalho em segundo
  plano que pertence a este checkout. Essas duas linhas carregam o id de trace
  vazio — o último colchete é `[]` — porque elas não são eventos de span: são o
  ciclo de vida do próprio grupo, publicado pelo contexto e não por uma chamada
  traçada.
- **As linhas dos filhos carregam o id de trace do cliente, na thread do
  trabalhador.** `InventoryService.reserve` e `FraudCheckService.approve` são
  escritas de `[checkout-worker-1]` com
  `[c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7]` — o mesmo id de todas as linhas de
  `[Test worker]` acima e abaixo delas. Esse é o snapshot que `group.wrap`
  tirou no momento do submit, ativado no trabalhador.
- **Cada filho aparece duas vezes no log e uma vez no trace.** O primeiro par é
  o trabalho acontecendo, em `checkout-worker-1`. O segundo par — as mesmas duas
  chamadas, os mesmos valores, em `Test worker`, logo antes da linha do join — é
  o `merge()` republicando-as sob o span que as lançou, que é o que as coloca na
  árvore de quem chamou. Leia o log como log: duas escritas, um evento cada.
  Leia o `.nt` como o registro: `#1.1` e `#1.2` sob `#1`, dentro de
  `~ fork [2]`, exatamente uma vez.
- **O id do span pai em cada filho é o span que lançou.** `#1.1` e `#1.2`
  nomeiam `#1` como pai — não porque rodaram sob ele na mesma thread (não
  rodaram) mas porque o grupo os enxertou ali. Uma linha de log não consegue
  mostrar isso (um id de span é gerado por span e mudaria a cada execução), então
  o `assertTheChildrenCameHome` acima afirma: o `parentSpanId` de cada filho é o
  `spanId` da raiz, o `traceId` de cada filho é o da raiz, e a thread registrada
  é `checkout-worker-1`. Aquela asserção é a prova desta página.
- **Como a segunda execução difere.** Envie a mesma chamada traçada sem wrap e a
  thread trabalhadora não tem snapshot nenhum para ativar, então a primeira
  chamada traçada dela faz o que qualquer primeira chamada traçada faz: começa um
  trace. O segundo teste afirma as duas consequências — o id de trace observado
  *no trabalhador* não é o de quem chamou, e o trace capturado por quem chamou
  não contém `InventoryService.reserve` nenhum. O trabalho continua acontecendo,
  o estoque continua reservado, as linhas continuam logadas. Elas simplesmente
  são a história de outra pessoa, sob um id que ninguém está procurando.
- **Por que "awaited sequentially" é o diagnóstico certo aqui.** O executor
  deste cenário tem uma thread, então as duas tarefas realmente rodam uma depois
  da outra e o artefato Markdown diz isso. Isso é uma propriedade deste cenário,
  escolhida para que o log de uma execução seja estável byte a byte o suficiente
  para ser embutido — não da entrega, que é o mesmo código para um pool de
  qualquer tamanho.

## O que não é capturado

- **Trabalho lançado sem a entrega começa um trace novo.** Essa é toda a
  segunda metade desta página: sem wrap, sem snapshot, sem id de trace
  compartilhado. Nada te avisa, porque do ponto de vista da biblioteca uma
  thread nova com uma chamada traçada nela é um trace novo comum.
- **Um framework que propaga contexto por conta própria só está coberto onde a
  integração dele diz.** O `ThreadLocalAccessor` do Micrometer (Spring Boot 3,
  Reactor, `@Async`) é ligado pelo `narrativetrace-micrometer`; fora do que o
  [Guia de funcionalidades](../guia-de-funcionalidades.md) lista, assuma que a
  entrega é sua para escrever.
- **`captureTrace()` é por thread.** Ele devolve o que a thread *chamadora*
  pode relatar. Uma captura feita em uma thread trabalhadora vê os spans
  daquele trabalhador, não os de quem chamou — que é por isso que o que junta os
  dois é o grupo, não a consulta.
- **Um grupo sem `merge()` não recolhe nada para o trace de quem chamou.** Os
  filhos são registrados e então nada os enxerta: o log continua mostrando-os
  (com o id de trace certo), a árvore de quem chamou não.
- **Trabalho que sobrevive à chamada é outro ajudante.** O
  `FireAndForgetGroup` não tem passo de merge por design — o método que lançou
  já retornou, então não sobrou ninguém em quem fazer merge.
- **Entrelaçamento.** Com um pool real as linhas dos filhos chegam na ordem em
  que as threads forem escalonadas, que é por isso que um log se lê por id de
  trace e não por posição. Os artefatos de trace não são afetados: a paternidade
  vem do snapshot, não da ordem em que as linhas foram escritas.

**A skill para isso:** `narrativetrace-verify` — "roda async" e "cruza
threads" são dois dos casos para os quais ela existe, e ela lê o trace
estrutural contra a intenção antes de considerar qualquer coisa pronta.
