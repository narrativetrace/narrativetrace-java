<!-- source: documentation/recipes/why-did-this-test-fail.md blob 3e9b39b9dd80 | translated: 2026-10-11 | reviewed: - -->
# ¿Por qué falló esta prueba?

[English](../../recipes/why-did-this-test-fail.md) | **Español** | [Português](../../pt-BR/receitas/por-que-este-teste-falhou.md) | [简体中文](../../zh-CN/实用方案/这个测试为什么失败了.md)

## El problema

> ¿Por qué falló esta prueba?

Una prueba del checkout está en rojo. La aserción dice qué no coincidió; no dice
qué hizo el código camino de allí, que es la parte que necesitas para saber si lo
que está mal es el código o la expectativa.

## Configuración mínima

Nada más allá de la [instalación](../guia-de-instalacion.md). La extensión de
JUnit 5 escribe una narrativa por método de prueba sin configuración alguna —
también para una prueba que falla, que es el caso del que trata esta página. Los
archivos aparecen en `build/narrativetrace/traces/<ClaseDePrueba>/<escenario>.md`,
y `build/narrativetrace/manifest.json` lista todos los artefactos de cada
escenario cuando conoces el nombre de la prueba pero no el de su archivo.

## El escenario

La prueba que falla:

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

## La salida real

Lo que informó el runner:

```text
expected: "ORD-C-1234-SKU-KB-1"
 but was: "ORD-C-1234-SKU-KB-2"
```

Y lo que la misma ejecución escribió al lado, en
`build/narrativetrace/traces/StaleExpectationFixture/the_customer_gets_an_order_id_for_their_keyboards.md`:

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

## Cómo leerlo

Lee la narrativa de arriba abajo contra la aserción, un span a la vez:

- `#1 OrderService.placeOrder(... quantity: 2 ...) → "ORD-C-1234-SKU-KB-2"`. El
  valor que rechazó la aserción está ahí mismo, y también la entrada que lo
  produjo: la llamada pidió **dos** teclados.
- `#1.1 InventoryService.reserve(sku: "SKU-KB", quantity: 2)` — dos unidades
  reservadas, coherente con la llamada.
- `#1.2 PaymentService.charge(..., amountCents: 7998, ...)` — 7998 son dos
  unidades a 3999, así que el precio también concuerda con la cantidad.
- `#1.3 OrderRepository.save(order: Order(... quantity: 2 ...))` — lo que se
  guardó concuerda con lo que se pidió.
- `**Result:** FAILED` en la cabecera es el veredicto de la *prueba*, no del
  código: `error_count: 0` y todos los spans se completaron. Nada lanzó.

Cada span concuerda con los demás y con la llamada que los inició. La expectativa
— `ORD-C-1234-SKU-KB-1` — es lo único de la ejecución que dice un teclado, así
que es lo que está desactualizado; el fixture contra el que se escribió cambió
desde entonces para pedir dos. La corrección está en la prueba.

La lectura opuesta es la otra receta: cuando un span *no* concuerda con la
intención — una cantidad que cambia entre dos llamadas, una llamada que debería
haber seguido y no está — el código está mal, y
[Ayúdame a depurar y arreglar este error](ayudame-a-depurar-y-arreglar-este-error.md)
es ese recorrido.

## Qué no queda capturado

- **Solo esa ejecución de esa prueba.** La narrativa se escribe por método de
  prueba y por ejecución. Un fallo que ocurre una vez cada veinte ejecuciones
  necesita que una de ellas sea la que escribió el archivo; una ejecución en
  verde no sobrescribe nada (cada ejecución escribe su propio archivo, y el `.nt`
  libre de valores de una ejecución fallida no avanza en absoluto).
- **La aserción no está en la traza.** Las dos salidas de arriba vienen de dos
  sitios — el runner informó del desajuste, la extensión escribió el flujo — y
  nada las correlaciona automáticamente más allá del nombre del escenario.
- **Un valor que la prueba calculó pero nunca pasó a una llamada trazada es
  invisible.** La traza lleva parámetros y valores de retorno de llamadas
  envueltas, no variables locales.
- **Los tiempos de una ejecución fallida no significan nada.** La ejecución dejó
  de ser representativa en el momento en que la aserción lanzó.

**La habilidad para esto:** `narrativetrace-debug` — parte del síntoma, lee el
primer span donde un valor diverge y lo nombra por su identificador de span.
