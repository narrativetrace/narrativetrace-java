<!-- source: documentation/recipes/trace-in-production-without-the-cost.md blob fee0ebd589b5 | translated: 2026-10-17 | reviewed: - -->
# Traza en producción sin el coste

[English](../../recipes/trace-in-production-without-the-cost.md) | **Español** | [Português](../../pt-BR/receitas/trace-em-producao-sem-o-custo.md) | [简体中文](../../zh-CN/实用方案/在生产环境追踪而不付出代价.md)

## El problema

> Quiero esto en producción para las peticiones que salen mal. No voy a pagar
> una traza completa de cada petición que sale bien.

Eso es un único ajuste — `TracingLevel` — y esta página es la misma ejecución
vista a través de él dos veces: el checkout con la tarjeta rechazada en
`DETAIL`, donde se captura cada valor, y en `ERRORS`, donde solo sobreviven las
ejecuciones que fallaron.

## Configuración mínima

Un nivel, puesto donde se configure tu despliegue. Estas tres formas dicen lo
mismo:

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

El nivel es el dial propio de NarrativeTrace y va delante de todo: una llamada
que filtra no llega a ser nunca un evento, para ningún consumidor. El nivel de
tu logger es un segundo dial, independiente, que decide qué se *imprime* — las
[preguntas frecuentes, "Two dials, two paths"](../../faq.md) (en inglés) son la
versión larga, y la distinción importa aquí porque subir el nivel de un logger
te ahorra volumen de log sin cambiar nada de la captura.

## El escenario

La ejecución es la reproducción de la tarjeta rechazada de
[Ayúdame a depurar y arreglar este error](ayudame-a-depurar-y-arreglar-este-error.md)
— `reserve`, luego `charge` lanzando, luego `release` — reutilizada sin cambios,
porque el tema de esta página es el dial y no otro flujo. El driver de abajo la
ejecuta a los dos niveles, y además ejecuta un checkout *exitoso* en `ERRORS`:

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

## La salida real

**En `DETAIL`**, la ejecución rechazada, con cada valor capturado:

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

**En `ERRORS`**, la misma ejecución del mismo código:

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

**Y en `ERRORS`, un checkout que salió bien**: ningún artefacto. No hay nada
que incrustar, que es justo el punto — el escenario de arriba comprueba las dos
mitades de esa afirmación, la ausencia del archivo en `ERRORS` y su presencia en
`DETAIL`, para que el vacío sea obra del nivel y no de una ruta que nunca fue
correcta.

## Cómo leerlo

- **Qué conserva `ERRORS`.** La ejecución fallida, completa. `#1` lanzó, y el
  subárbol de un nodo con error se conserva intacto — así que `reserve` (`#1.1`),
  `charge` (`#1.2`) y `release` (`#1.3`) siguen ahí, incluidas las dos llamadas
  que volvieron con normalidad. Es deliberado: las llamadas alrededor de un fallo
  son lo que necesitas para diagnosticarlo, y descartar al hermano exitoso te
  dejaría con una excepción y ninguna historia. Una llamada que volvió *y* no
  lleva a ningún fallo por debajo se descarta, y una raíz sin ningún fallo en
  todo su subárbol se descarta por completo — que es por lo que el checkout
  exitoso no escribió nada.
- **Qué descarta `ERRORS`.** Cada valor de parámetro: `customerId: …`,
  `quantity: …`. El `…` es supresión en la *captura* — el valor nunca se
  renderizó, así que no se puede recuperar después a partir de estos eventos, a
  ningún nivel posterior. Lo que sobrevive es la forma, los resultados y la
  excepción, y `cardNumber: [REDACTED]` sigue oculto porque la ocultación es una
  decisión distinta de la supresión.
- **La poda ocurre después de la captura, no en lugar de ella.** Cada llamada
  se interceptó, cada evento se publicó; después `TraceTreeBuilder` construyó el
  árbol y quitó lo que el nivel no conserva. Esa es la forma honesta del coste:
  `ERRORS` es más barato que `DETAIL` porque no se renderizan los valores — la
  parte cara — no porque las llamadas queden sin interceptar.
- **Solo `OFF` se salta la interceptación.** En `OFF` la puerta `isActive()`
  del proxy vuelve antes de que haya ningún array de argumentos, ningún evento y
  ninguna publicación, y nada aguas abajo puede recuperar la llamada. `ERRORS`,
  `SUMMARY` y `NARRATIVE` siguen interceptando todas las llamadas y publicando
  una entrada y una salida por cada una. Si la pregunta es "qué cuesta el
  tracing en este bucle caliente", la respuesta es otro nivel para ese ámbito, o
  `OFF`.
- **Los números.** Las cifras medidas — el coste de la ruta inactiva, la
  asignación de memoria por llamada, el coste de una llamada trazada activa —
  están en la
  [sección de rendimiento del README](../../../LEAME.md#rendimiento), junto a
  las líneas base de JMH commiteadas de las que salen. No se repiten aquí,
  porque una sola copia que se mueve con las medidas vale más que dos que se
  contradicen.

## Qué no queda capturado

- **Las cifras publicadas son medidas, no una promesa.** Se tomaron con JMH en
  el harness y el hardware que nombra el README, con una operación de benchmark
  por llamada. Tus números dependen de tu JDK, tu hardware, la forma de tus
  parámetros (un grafo de objetos profundo tarda más en renderizarse que un
  `int`) y de cuánto de tu código está trazado. Mide el nivel que pretendes
  ejecutar.
- **Un nivel no es un control de privacidad.** `ERRORS` escribe menos valores,
  sí, porque los suprime, pero supresión y ocultación son mecanismos distintos
  con garantías distintas:
  [Mantén los secretos fuera de los logs](manten-los-secretos-fuera-de-los-logs.md)
  es el que se mantiene a cualquier nivel.
- **Nada de esto cambia cuánto escribe tu logger.** Cada evento capturado se
  sigue ofreciendo a la ruta sincrónica, así que el nivel del propio logger
  `narrativetrace` es lo que decide el volumen de log. Dos diales.
- **Un nivel no se puede subir con efecto retroactivo.** Los valores no
  capturados no están; reproducir la ejecución en `DETAIL` es el único camino de
  vuelta, que es lo que hace la receta de depuración.
- **Aquí no se muestran niveles por ámbito.** Esta página pone un nivel para
  toda una ejecución. Estrechar el ámbito trazado en su lugar — qué clases se
  envuelven siquiera — es la otra mitad de la pregunta del coste, y pertenece a
  [Eligiendo una integración](../eligiendo-una-integracion.md).

**La habilidad para esto:** `narrativetrace-doctor` — informa del nivel al que
un proyecto está corriendo de verdad, que es lo primero que hay que mirar
cuando una traza sale más fina de lo esperado.
