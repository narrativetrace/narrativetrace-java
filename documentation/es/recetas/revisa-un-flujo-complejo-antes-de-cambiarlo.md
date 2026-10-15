<!-- source: documentation/recipes/review-a-complex-flow-before-changing-it.md blob 4b69423b5f04 | translated: 2026-10-11 | reviewed: - -->
# Revisa un flujo complejo antes de cambiarlo

[English](../../recipes/review-a-complex-flow-before-changing-it.md) | **Español** | [Português](../../pt-BR/receitas/revise-um-fluxo-complexo-antes-de-altera-lo.md) | [简体中文](../../zh-CN/实用方案/修改复杂流程前先审查它.md)

## El problema

> Antes de cambiar el checkout, dime qué hace en realidad.

Leer el código fuente responde qué *puede* hacer. Una ejecución responde qué
*hace* con estas entradas — en qué orden, con qué valores y dónde el flujo se
sale del código que estás a punto de editar. Esta página no muestra la
implementación a propósito: de eso se trata leer una ejecución en lugar del
código.

## Configuración mínima

Nada más allá de la [instalación](../guia-de-instalacion.md) para los dos
artefactos de traza. Las líneas de log necesitan el módulo SLF4J y una
configuración de Logback, exactamente como en
[Añade registro a este servicio](anade-registro-a-este-servicio.md). El nivel de
captura es una propiedad, y `SUMMARY` es el que usa esta página junto al `DETAIL`
por defecto:

```kotlin
// build.gradle.kts
narrativeTrace {
    tracingLevel.set("SUMMARY")   // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
}
```

## El escenario

Una ejecución representativa del flujo — una prueba existente es la más barata de
tomar prestada, y esta es esa prueba:

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

## La salida real

**En `DETAIL`** (el valor por defecto), `build/narrativetrace/traces/CheckoutReviewFixture/a_customer_buys_two_keyboards.md`:

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

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** A customer buys two keyboards
**Duration:** 4.335ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 4.335ms #1
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.692ms #1.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) → `"PAY-C-1234-7998"` — 0.427ms #1.2
  - **OrderRepository.save**(order: `Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998)`) — 0.356ms #1.3
```

**En `SUMMARY`**, la misma ejecución de la misma prueba:

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

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** A customer buys two keyboards
**Duration:** 1.909ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: …, sku: …, quantity: …, cardNumber: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 1.909ms #1
  - **InventoryService.reserve**(sku: …, quantity: …) — 0.274ms #1.1
  - **PaymentService.charge**(customerId: …, amountCents: …, card: …) → `"PAY-C-1234-7998"` — 0.27ms #1.2
  - **OrderRepository.save**(order: …) — 0.233ms #1.3
```

**El log que escribió el mismo flujo** a través del propio logger del proyecto,
con valores y todo:

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

**La traza estructural de esa ejecución**, `build/narrativetrace/structural/CheckoutReviewFixture/a_customer_buys_two_keyboards.nt`:

```text
scenario: A customer buys two keyboards

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) → value
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) → value
  #1.3 - OrderRepository.save(order)
```

## Cómo leerlo

**El flujo, desde el artefacto `DETAIL`.** `#1` es la llamada que hizo la prueba
y la única raíz: todo lo indentado debajo ocurrió dentro, en el orden impreso.
`#1.1` reserva dos unidades antes de cobrar nada; `#1.2` cobra 7998 — dos
unidades a 3999, así que el precio se deriva de la cantidad y no se pasa como
parámetro; `#1.3` guarda un pedido que ya lleva el identificador que devuelve
`#1`. Leído como una frase: este checkout reserva primero, cobra después, guarda
al final, y el identificador del pedido se acuña antes de guardar. Un cambio que
cobre antes de reservar, o que guarde antes de cobrar, es un cambio a *esto*, y
ahora lo sabes sin fiarte de un comentario.

**Para qué sirve cada nivel.** `DETAIL` lleva los valores de los parámetros y es
el que lees mientras trabajas en el flujo. En `SUMMARY` cada valor de parámetro
es `…` — suprimido en la captura, no oculto al renderizar — y solo se conservan
las raíces y las llamadas hoja; este flujo tiene dos niveles de profundidad, así
que ningún marco es intermedio y aparecen las mismas cuatro llamadas. Un flujo
más profundo pierde su parte central. Los valores de retorno sobreviven en todos
los niveles activos: el retorno es la carga útil de una narrativa. Usa `SUMMARY`
donde la forma y los resultados sean lo que necesitas y los valores sean coste —
una ejecución de CI, una prueba de resistencia larga — y `OFF`/`ERRORS` en
producción hasta que necesites más.

**Qué salida lee una persona y qué salida lee un agente.** Las líneas de log son
para quien está de guardia: se intercalan con todo lo demás que registra el
servicio, llevan los valores y están indexadas por el nombre de la traza —
`[solid wand romps]` — así que un checkout se puede extraer con grep de un
archivo concurrido. El `.nt` es para el agente y para quien revisa: **libre de
valores por construcción** — compáralo con la línea de log de arriba y la
diferencia no es que los valores se hayan reemplazado, sino que nunca se
escribieron. `(customerId, sku, quantity, cardNumber)` son los *nombres* de los
parámetros; `→ value` dice que se devolvió un valor sin decir cuál. Eso es lo que
hace que sea seguro commitearlo, verlo en un diff en una revisión y entregárselo
a un modelo: no puede filtrar el identificador de un cliente, y no cambia cuando
cambian los datos — solo cuando cambian las *llamadas*. Fijarlo es
[Revisa una refactorización: ¿cambió el comportamiento?](revisa-una-refactorizacion-cambio-el-comportamiento.md).

## Qué no queda capturado

- **Las llamadas dentro de clases sin envolver.** Se envolvieron cuatro
  interfaces; una llamada entre dos clases que estén detrás de ellas, o un método
  privado, nunca aparece. Un flujo que parece plano puede ser profundo.
- **Lo que la integración elegida no intercepta.** El proxy JDK ve solo las
  llamadas hechas *a través* de la referencia envuelta: una llamada interna que
  una clase se hace a sí misma (`this.helper()`) le resulta invisible, mientras
  que el agente Java sí la vería.
  [Eligiendo una integración](../eligiendo-una-integracion.md) es el compromiso de
  cada camino, y esta es la parte que cambia lo que puedes leer aquí.
- **El trabajo que se fue del hilo.** Una llamada entregada a un executor sin el
  contexto no viaja más lejos; la traza termina donde terminó el hilo.
- **Los valores que atrapó la lista de denegación o la regla de forma del valor**,
  aquí `cardNumber` y `card`, son `[REDACTED]` tanto en el log como en el
  artefacto `DETAIL`, y están ausentes por construcción del `.nt`.
- **Una ejecución, no el contrato.** Otra entrada toma otro camino — un sku sin
  stock nunca llega a `#1.2`.

**La habilidad para esto:** `narrativetrace-verify` — escribe primero la
intención, ejecuta el camino real más pequeño y lee la traza libre de valores
contra esa intención antes de dar algo por terminado.
