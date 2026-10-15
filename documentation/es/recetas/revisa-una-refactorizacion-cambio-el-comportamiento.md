<!-- source: documentation/recipes/review-a-refactoring-did-behaviour-change.md blob 5157fea6e605 | translated: 2026-10-11 | reviewed: - -->
# Revisa una refactorización: ¿cambió el comportamiento?

[English](../../recipes/review-a-refactoring-did-behaviour-change.md) | **Español** | [Português](../../pt-BR/receitas/revise-uma-refatoracao-o-comportamento-mudou.md) | [简体中文](../../zh-CN/实用方案/审查重构行为变了吗.md)

## El problema

> Refactoricé el checkout. Las pruebas están en verde. ¿Cambió el comportamiento?

Verde significa que ninguna aserción se dio cuenta. Una refactorización que
elimina una llamada sobre la que nadie asevera está en verde y está mal, y esta
es su forma habitual: la llamada compensatoria en el camino de error, al que no
llega ninguna prueba del camino feliz.

## Configuración mínima

Más allá de la [instalación](../guia-de-instalacion.md), el modo aprobación: una
propiedad y un directorio de baselines commiteadas.

```kotlin
// build.gradle.kts
narrativeTrace {
    approval.set(true)                              // comparar, no solo escribir
    approvedDir.set(layout.projectDirectory.dir("src/test/narratives"))   // el valor por defecto
}
```

La primera ejecución de cada prueba escribe
`src/test/narratives/<ClaseDePrueba>/<escenario>.received.nt` y falla; lo lees,
ejecutas `./gradlew approveNarratives` para promover cada archivo revisado a
`.approved.nt`, y commiteas esos. Véase
[Guía del plugin de Gradle, `approveNarratives`](../guia-del-plugin-de-gradle.md#approvenarratives)
y [Qué commitear](../que-commitear.md#modo-aprobación-de-principio-a-fin).

## El escenario

La prueba fija el camino de error que una refactorización tiene más
probabilidades de romper — una tarjeta rechazada no debe guardar ningún pedido —
y pasa contra las dos versiones del checkout, que es justo el problema:

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

## La salida real

La baseline commiteada, en
`src/test/narratives/RefactoredCheckoutFixture/a_declined_card_saves_no_order.approved.nt`:

```text
scenario: A declined card saves no order

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) !! PaymentDeclinedException
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) !! PaymentDeclinedException
  #1.3 - InventoryService.release(sku, quantity)
```

El veredicto de la ejecución refactorizada — las aserciones pasaron, la
estructura no:

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

Y el archivo que ofrece para revisión, el que promovería `approveNarratives`:

```text
scenario: A declined card saves no order

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) !! PaymentDeclinedException
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) !! PaymentDeclinedException
```

(La ruta de `Received:` es la de este repositorio: la ejecución que produce esta
página apunta `approvedDir` dentro de `build/`, porque la tarea que escribe una
página no puede escribir en el árbol de fuentes. En tu proyecto es
`src/test/narratives`, junto a la baseline.)

## Cómo leerlo

- **La primera línea nombra el cambio, no solo su existencia:**
  `-1 call InventoryService.release`. Una llamada menos, y cuál.
- **El cuerpo del diff es la forma fijada con la línea eliminada marcada.** `#1`,
  `#1.1` y `#1.2` están sin cambios y sin marcar;
  `-  #1.3 - InventoryService.release(sku, quantity)` es el comportamiento que se
  fue. Quien revise sin haber visto nunca este código puede decir qué hizo la
  refactorización a partir de cuatro líneas.
- **La prueba siguió pasando.** El fallo es de la aprobación, y eso es lo
  importante: la suite asevera que no se guarda ningún pedido, y la
  refactorización no rompió eso. Nada en la prueba miró nunca la liberación, y no
  tenía por qué.
- **Ahora decide.** Si eliminar la liberación es intencionado — porque quien llama
  se ha hecho cargo, digamos — ejecuta `./gradlew approveNarratives`, revisa el
  diff en el archivo promovido y commitéalo: la forma nueva pasa a ser la fijada.
  Si no es intencionado, acabas de atrapar el fallo que corrigió
  [Ayúdame a depurar y arreglar este error](ayudame-a-depurar-y-arreglar-este-error.md),
  justo cuando volvía. En cualquier caso la decisión queda registrada en el
  repositorio, por una persona, en un archivo que quien revise puede leer.
- **La baseline es libre de valores**, así que aprobarla no filtra nada y un
  cambio en los datos de prueba nunca la hace fallar:
  `(customerId, sku, quantity, cardNumber)` son nombres y
  `!! PaymentDeclinedException` es un resultado.

## Qué no queda capturado

- **Esto es una comparación estructural: las mismas llamadas, en el mismo orden,
  con los mismos resultados.** No es una prueba de que cada valor sea igual. Una
  refactorización que cobre 7998 donde cobraba 3999, con todas las llamadas en su
  sitio, pasa esta puerta — para eso están tus aserciones, y el artefacto `DETAIL`
  es donde lo verías.
- **Solo las llamadas que intercepta la integración.** Una llamada privada
  eliminada, o una entre dos clases sin envolver, le resulta invisible a la
  baseline igual que a la traza.
- **Una prueba que no se ejecuta no se compara.** La aprobación protege los
  escenarios que fijaste; un flujo sin prueba no tiene baseline, y borrar una
  prueba borra su puerta (el `.approved.nt` queda huérfano, algo que
  `narrativetrace-doctor` informa).
- **Los tiempos nunca forman parte de esto.** La baseline no lleva duraciones, así
  que no puede atrapar un cambio que solo hizo el flujo más lento.

**La habilidad para esto:** `narrativetrace-verify` — lee la traza estructural
contra la intención del cambio y fija el flujo como baseline con tu aprobación.
