<!-- source: documentation/recipes/help-me-debug-and-fix-this-bug.md blob 10506fc14292 | translated: 2026-10-11 | reviewed: - -->
# Ayúdame a depurar y arreglar este error

[English](../../recipes/help-me-debug-and-fix-this-bug.md) | **Español** | [Português](../../pt-BR/receitas/ajude-me-a-depurar-e-corrigir-este-bug.md) | [简体中文](../../zh-CN/实用方案/帮我调试并修复这个缺陷.md)

## El problema

> Ayúdame a depurar y arreglar este error: a un cliente le rechazaron la tarjeta
> y el teclado nunca volvió al stock.

El error que tiene el equipo es `PaymentDeclinedException: card declined by the
issuer`, que no es el fallo. El fallo es lo que hizo el checkout *después*.

## Configuración mínima

Nada más allá de la [instalación](../guia-de-instalacion.md): la extensión de
JUnit 5 escribe una traza por prueba sin configuración alguna. El último paso de
esta página — fijar el flujo corregido — activa el modo aprobación, que es una
sola propiedad:

```kotlin
// build.gradle.kts
narrativeTrace {
    approval.set(true)   // compara la estructura de cada prueba que pasa con su baseline
}
```

Véase [Guía del plugin de Gradle, `approval`](../guia-del-plugin-de-gradle.md#approval)
y [Qué commitear](../que-commitear.md#modo-aprobación-de-principio-a-fin).

## El escenario

Reprodúcelo en la prueba más pequeña que pueda fallar por este motivo — una
tarjeta rechazada debe dejar el stock como lo encontró:

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

La segunda prueba ejecuta la misma reproducción contra el checkout tal como
estaba antes de la corrección, que es la ejecución de la que sale la primera
traza de abajo:

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

## La salida real

La narrativa propia de la ejecución que falló, en
`build/narrativetrace/traces/DeclinedPaymentFixture/the_stock_is_released_when_the_card_is_declined_before_the_fix.md`:

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

La corrección es un `catch`: libera lo que se había reservado y deja que el
rechazo se propague como antes.

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

La misma reproducción, ahora en verde, contra el checkout corregido:

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

Con el modo aprobación activo, la primera ejecución de la prueba corregida
escribe su estructura libre de valores junto a la prueba como `.received.nt`;
`./gradlew approveNarratives` la promueve a baseline commiteada, que ahora es la
forma de este flujo:

```text
scenario: The stock is released when the card is declined

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) !! PaymentDeclinedException
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) !! PaymentDeclinedException
  #1.3 - InventoryService.release(sku, quantity)
```

## Cómo leerlo

En la traza que falla:

- `#1 OrderService.placeOrder` termina con `❌ PaymentDeclinedException` y sin
  valor de retorno: la llamada que hizo el cliente no se completó.
- `#1.1 InventoryService.reserve(sku: "SKU-KB", quantity: 2)` no tiene `❌` ni
  valor — un método `void` que se completó. Ahora hay dos unidades retenidas.
- `#1.2 PaymentService.charge` lanzó. Sus parámetros dicen qué se intentó:
  `amountCents: 7998` por las dos unidades, `card: [REDACTED]`.
- **Después de `#1.2` no hay nada.** `#1` pasa directamente del cobro a su propio
  fallo, así que las dos unidades que reservó `#1.1` siguen retenidas. Esa
  ausencia es el fallo, y el mensaje de la excepción nunca la menciona — la traza
  es lo que hace visible una omisión, porque muestra el flujo completo y no solo
  la línea que lanzó.

En la traza que pasa:
`#1.3 InventoryService.release(sku: "SKU-KB", quantity: 2)` se sitúa ahora entre
el cobro y el fallo. `#1` sigue terminando en el rechazo — y es correcto, la
tarjeta del cliente se rechazó de verdad — y el stock ha vuelto.

En la baseline: el `.nt` aprobado lleva las mismas cuatro llamadas con los
*nombres* de sus parámetros y ningún valor, que es lo que permite commitearlo y
leerlo en una revisión. `#1.3` forma parte ya de la forma fijada: un cambio
posterior que elimine la liberación hace fallar a una prueba que pasa, con un
diff que nombra `InventoryService.release`, de modo que este fallo no puede
volver en silencio. Eso es
[Revisa una refactorización: ¿cambió el comportamiento?](revisa-una-refactorizacion-cambio-el-comportamiento.md).

## Qué no queda capturado

- **La traza muestra las llamadas que ocurrieron, no las que debían ocurrir.** La
  llamada `release` ausente es una ausencia que el lector infiere del flujo; nada
  en el artefacto dice "falta una llamada". Lo que añade la baseline es la
  comparación que convierte una ausencia en un fallo de compilación — una vez.
- **Una excepción tragada dentro de código sin envolver es invisible.** Solo
  aparecen las cuatro interfaces envueltas. Un `try`/`catch` dentro de
  `DefaultOrderService` que se comiera el rechazo y retornara con normalidad se
  vería como una llamada completada.
- **El número de tarjeta no se puede recuperar** de la traza ni del log. Se oculta
  en el momento de la captura, aquí por su valor: el parámetro se llama `card`,
  así que la lista de denegación por nombre nunca lo vio, y el número es válido
  según Luhn.
- **Los tiempos no son prueba de un problema de rendimiento** en una prueba tan
  pequeña: la primera llamada envuelta de una JVM carga con la preparación única
  del proxy.

**La habilidad para esto:** `narrativetrace-debug` — reproduce el síntoma con la
entrada más pequeña, nombra el span donde el valor o la llamada divergen, lo
corrige ahí y fija el flujo como prueba de regresión con tu aprobación.
