<!-- source: documentation/recipes/follow-a-flow-across-threads.md blob bb0939c33604 | translated: 2026-10-17 | reviewed: - -->
# Sigue un flujo entre hilos

[English](../../recipes/follow-a-flow-across-threads.md) | **Español** | [Português](../../pt-BR/receitas/acompanhe-um-fluxo-entre-threads.md) | [简体中文](../../zh-CN/实用方案/跨线程追踪一个流程.md)

## El problema

> El checkout entrega las partes lentas a un executor. La traza se corta en el
> submit y no puedo ver qué hizo el trabajo en segundo plano.

Un hilo no sabe nada de la traza en la que está otro hilo, así que entregar
trabajo a un executor entrega el trabajo y nada más. La traza también hay que
entregarla — explícitamente, en el momento del submit. Abajo hay una ejecución
que lo hace y otra que no.

## Configuración mínima

Nada más allá de la [instalación](../guia-de-instalacion.md): `ForkGroup` y
`FireAndForgetGroup` están en el paquete `context` de `narrativetrace-core`,
junto al propio contexto. Cuál usar depende de qué hace quien lanza después:

| La llamada que lanza | El ayudante | El paso final |
|---|---|---|
| Espera al trabajo en segundo plano (esta página) | `ForkGroup` | `merge()`, después de que las tareas terminen — injerta lo que registraron bajo el span que las lanzó |
| Vuelve sin esperar | `FireAndForgetGroup` | Ninguno. Los hijos son un registro aparte, que se lee con `childRoots()` |

Si tu framework ya propaga el contexto — Spring Boot 3 con el
`ThreadLocalAccessor` de Micrometer, Reactor, `@Async` — esa entrega la cubre
la integración y no escribes ninguno de los dos. La
[Guía de funcionalidades](../guia-de-funcionalidades.md) es la versión fila a
fila.

Las líneas de log usan la misma configuración que
[Sigue una petición entre servicios](sigue-una-peticion-entre-servicios.md),
con el hilo y el identificador de traza en crudo en el patrón — en esta página
la columna del hilo es la mitad de la historia.

## El escenario

**El checkout, con los dos pasos lentos fuera de su propio hilo.** La entrega
es `group.wrap(...)` alrededor de cada tarea y `group.merge()` cuando las dos
han terminado:

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

**La ejecución — y el mismo trabajo sin la entrega.** La segunda prueba envía
una llamada trazada al mismo executor sin `ForkGroup` y sin wrap, que es como
estaba el código antes de añadir la entrega:

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

## La salida real

**El log de la ejecución que conserva la traza:**

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

**La traza estructural de esa ejecución,**
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

El artefacto Markdown de la misma ejecución lleva tres cosas más — el nombre
del hilo trabajador junto a cada hijo, el tiempo de pared del join y una pista
de "sequential async" cuando las tareas no se solaparon — y no se incrusta aquí
porque cada uno de esos números cambia en cada ejecución.

## Cómo leerlo

- **La entrega se ve, dos veces.**
  `⑂ fork group created [groupId: fork-1]` es el grupo creándose, antes de que
  se envíe cualquiera de las tareas; `⑃ fork joined [groupId: fork-1, members: 2]`
  es `merge()` informando de lo que volvió. Entre ambas está el trabajo en
  segundo plano que pertenece a este checkout. Esas dos líneas llevan el
  identificador de traza vacío — el último corchete es `[]` — porque no son
  eventos de span: son el ciclo de vida del propio grupo, publicado por el
  contexto y no por una llamada trazada.
- **Las líneas de los hijos llevan el identificador de traza del cliente, en el
  hilo del trabajador.** `InventoryService.reserve` y `FraudCheckService.approve`
  se escriben desde `[checkout-worker-1]` con
  `[c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7]` — el mismo identificador que todas las
  líneas de `[Test worker]` de arriba y de abajo. Ese es el snapshot que
  `group.wrap` tomó en el momento del submit, activado en el trabajador.
- **Cada hijo aparece dos veces en el log y una vez en la traza.** El primer
  par es el trabajo ocurriendo, en `checkout-worker-1`. El segundo par — las
  mismas dos llamadas, los mismos valores, en `Test worker`, justo antes de la
  línea del join — es `merge()` republicándolas bajo el span que las lanzó, que
  es lo que las mete en el árbol de quien llama. Lee el log como un log: dos
  escrituras, un evento cada una. Lee el `.nt` como el registro: `#1.1` y `#1.2`
  bajo `#1`, dentro de `~ fork [2]`, exactamente una vez.
- **El identificador del span padre en cada hijo es el span que lanzó.** `#1.1`
  y `#1.2` nombran a `#1` como su padre — no porque se ejecutaran bajo él en el
  mismo hilo (no fue así) sino porque el grupo los injertó ahí. Una línea de log
  no puede mostrar esto (un id de span se genera por span y cambiaría en cada
  ejecución), así que `assertTheChildrenCameHome` de arriba lo comprueba: el
  `parentSpanId` de cada hijo es el `spanId` de la raíz, el `traceId` de cada
  hijo es el de la raíz, y el hilo registrado es `checkout-worker-1`. Esa
  aserción es la prueba de esta página.
- **En qué difiere la segunda ejecución.** Envía la misma llamada trazada sin
  wrap y el hilo trabajador no tiene ningún snapshot que activar, así que su
  primera llamada trazada hace lo que hace cualquier primera llamada trazada:
  empieza una traza. La segunda prueba comprueba las dos consecuencias — el
  identificador de traza observado *en el trabajador* no es el de quien llama, y
  la traza capturada por quien llama no contiene ningún
  `InventoryService.reserve`. El trabajo sigue ocurriendo, el stock sigue
  reservándose, las líneas se siguen logueando. Simplemente son la historia de
  otro, bajo un identificador que nadie está buscando.
- **Por qué "awaited sequentially" es el diagnóstico correcto aquí.** El
  executor de este escenario tiene un solo hilo, así que las dos tareas se
  ejecutan de verdad una después de otra y el artefacto Markdown lo dice. Eso es
  una propiedad de este escenario, elegida para que el log de una ejecución sea
  lo bastante estable byte a byte como para incrustarlo — no de la entrega, que
  es el mismo código para un pool de cualquier tamaño.

## Qué no queda capturado

- **El trabajo lanzado sin la entrega empieza una traza nueva.** Eso es toda la
  segunda mitad de esta página: sin wrap, sin snapshot, sin identificador de
  traza compartido. Nada te avisa, porque desde el punto de vista de la librería
  un hilo nuevo con una llamada trazada es una traza nueva corriente.
- **Un framework que propaga el contexto él mismo está cubierto solo donde lo
  diga su integración.** El `ThreadLocalAccessor` de Micrometer (Spring Boot 3,
  Reactor, `@Async`) lo conecta `narrativetrace-micrometer`; fuera de lo que
  enumera la [Guía de funcionalidades](../guia-de-funcionalidades.md), da por
  hecho que la entrega te toca escribirla a ti.
- **`captureTrace()` está limitado al hilo.** Devuelve lo que puede reportar el
  hilo *que llama*. Una captura tomada en un hilo trabajador ve los spans de ese
  trabajador, no los de quien llama — que es justo por qué lo que une a los dos
  es el grupo y no la consulta.
- **Un grupo sin `merge()` no recoge nada en la traza de quien llama.** Los
  hijos quedan registrados y luego nada los injerta: el log sigue mostrándolos
  (con el identificador de traza correcto), el árbol de quien llama no.
- **El trabajo que sobrevive a la llamada es otro ayudante.**
  `FireAndForgetGroup` no tiene paso de merge por diseño — el método que lanzó
  ya ha vuelto, así que no queda nadie en quien hacer merge.
- **El entrelazado.** Con un pool real las líneas de los hijos llegan en el
  orden en que los hilos sean planificados, que es por lo que un log se lee por
  identificador de traza y no por posición. Los artefactos de traza no se ven
  afectados: la paternidad viene del snapshot, no del orden en que se
  escribieron las líneas.

**La habilidad para esto:** `narrativetrace-verify` — "se ejecuta async" y
"cruza hilos" son dos de los casos para los que existe, y lee la traza
estructural contra la intención antes de dar algo por terminado.
