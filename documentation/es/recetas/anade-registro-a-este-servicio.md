<!-- source: documentation/recipes/add-logging-to-this-service.md blob 2e1d62cc2264 | translated: 2026-10-11 | reviewed: - -->
# Añade registro a este servicio

[English](../../recipes/add-logging-to-this-service.md) | **Español** | [Português](../../pt-BR/receitas/adicione-logs-a-este-servico.md) | [简体中文](../../zh-CN/实用方案/给这个服务加日志.md)

## El problema

> Añade registro a este servicio.

El servicio es un checkout. Reserva existencias, cobra una tarjeta y guarda un
pedido, y no contiene ni una sola sentencia de log:

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

Lo que de verdad quieres es aquello para lo que el registro es un medio: qué
hizo esta llamada, con qué valores lo hizo, en el log que tu equipo ya lee. Así
que la respuesta de abajo tampoco escribe ninguna sentencia de log.

## Configuración mínima

Más allá de la [instalación](../guia-de-instalacion.md) — `narrativetrace-core`,
el proxy JDK y `-parameters` para que los nombres de los parámetros sobrevivan a
la compilación — esta página necesita dos dependencias de ejecución y un archivo
de configuración:

```kotlin
// build.gradle.kts
dependencies {
    runtimeOnly("ai.narrativetrace:narrativetrace-slf4j:0.4.0")   // se engancha a la canalización
    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")          // el backend SLF4J que necesita
}
```

`narrativetrace-slf4j` se engancha solo en cuanto está en el classpath; nada del
código de abajo lo menciona. Logback decide a dónde van sus eventos:

```xml
<!-- src/main/resources/logback.xml -->
<configuration>
    <!-- The pattern deliberately carries no %d{HH:mm:ss.SSS}: every recipe page embeds a real
         run's log output byte-for-byte, and a clock in it would differ on every run. Put the
         timestamp back in your own project — nothing else here changes. -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%-5level [%X{traceName}] [%logger] - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="narrativetrace" level="TRACE" />

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

## El escenario

Cada colaborador se envuelve una vez, donde se construye — el checkout al final,
para que las llamadas que pasen por él se capturen antes de llegar a la
implementación real. No cambia nada más:

```java
// src/test/java/com/example/checkout/AddLoggingRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves documentation/recipes/add-logging-to-this-service.md: the checkout, with no log statement
 * anywhere in it, logs every call through the project's own SLF4J logger once its collaborators are
 * wrapped (rule 8, docs as tests).
 *
 * <p><b>@llmNote</b> The page's output block is the log file Logback writes, not a rendering this
 * test produces — that is the whole claim of the page, so the evidence has to come from the logger.
 * The Gradle task {@code recipeAddLoggingLog} runs this class alone and names that file.
 */
@ExtendWith(NarrativeTraceExtension.class)
class AddLoggingRecipeTest {

  /**
   * A fixed W3C traceparent, adopted so this page's embedded output names the same trace every run
   * — the same mechanism a servlet filter uses for an inbound request header. A real run generates
   * a random trace id every time, and the name in the log lines is derived from it.
   */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  @Test
  void a_customer_checks_out(NarrativeContext context) {
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

Es una prueba JUnit 5 porque ahí es donde un flujo así ya se ejercita; las mismas
cuatro líneas de cableado funcionan en un `main`, en una factoría de beans de
Spring o en un servlet. `NarrativeTraceExtension` es lo que escribe los artefactos
de traza por prueba que leen las demás recetas.

## La salida real

El log que escribió la ejecución de arriba, a través del propio logger del
proyecto:

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

## Cómo leerlo

- `→` abre una llamada y `←` cierra la abierta más recientemente, así que la
  indentación es innecesaria: el primer `→ OrderService.placeOrder` y el último
  `← returned: "ORD-C-1234-SKU-KB-2"` son la misma llamada, y todo lo que hay
  entre ellos ocurrió dentro. En los artefactos de traza esas mismas cuatro
  llamadas llevan los identificadores de span `#1`, `#1.1`, `#1.2` y `#1.3` —
  [Revisa un flujo complejo antes de cambiarlo](revisa-un-flujo-complejo-antes-de-cambiarlo.md)
  las lee de esa forma.
- Cada línea lleva los *nombres* de los parámetros que declara el método, que es
  lo que la convierte en una frase y no en una tupla:
  `reserve(sku: "SKU-KB", quantity: 2)` no necesita leyenda.
- `← completed` es un método `void` que retorna; `← returned: …` lleva el valor.
  Que `charge` devuelva `"PAY-C-1234-7998"` es el identificador del propio
  gateway, y `amountCents: 7998` es de dónde sale el precio de dos teclados.
- Dos valores han desaparecido. `cardNumber: [REDACTED]` se ocultó por su
  *nombre* — la lista de denegación siempre activa, sin anotación y sin
  configuración. `card: [REDACTED]`, una línea más abajo, se ocultó por su
  *valor*: el parámetro se llama `card`, que la lista no conoce, y el número que
  contenía es válido según Luhn, así que la regla de forma del valor lo atrapó
  igualmente. Véase [Privacidad y ocultación](../privacidad-y-ocultacion.md).
- `[solid wand romps]` es el nombre propio de la traza en el MDC, así que cada
  línea de un checkout se puede extraer con grep de un log concurrido.

La conclusión que esto respalda: la narrativa salió de los nombres de los
métodos, de los nombres de los parámetros y de los valores, y no se escribió ni
una sentencia de log para obtenerla.

## Qué no queda capturado

- **Solo se registra el código envuelto.** Arriba se envolvieron cuatro
  interfaces, así que aparecen cuatro llamadas. Una llamada entre dos clases sin
  envolver — o un método privado dentro de `DefaultOrderService` — es invisible
  aquí. Si envolver cada colaborador a mano no es lo que quieres, el agente Java
  o la integración con Spring se enganchan sin tocar el código de construcción:
  [Eligiendo una integración](../eligiendo-una-integracion.md).
- **Un valor que encaje con las reglas de ocultación se reemplaza antes de
  escribirse,** también en esta línea de log. Eso no es configurable a la baja, y
  significa que un parámetro oculto no se puede recuperar del log.
- **El nivel del propio logger sigue aplicándose.** Estas líneas son `TRACE` en
  el logger `narrativetrace`; en `INFO` el archivo está vacío y nada te avisa.
- **Los tiempos no están en las líneas de log.** La duración de cada llamada
  está en los artefactos de traza, no aquí.

**La habilidad para esto:** `add-narrative-tracing` — instala las dos
dependencias, envuelve la primera clase y comprueba el cableado con
`narrativetrace-doctor`.
