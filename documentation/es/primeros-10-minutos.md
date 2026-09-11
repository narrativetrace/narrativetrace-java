<!-- source: documentation/first-10-minutes.md blob e11cf346a7c3 | translated: 2026-09-03 | reviewed: 2026-09-03 -->
# Primeros 10 minutos

[English](../first-10-minutes.md) | **Español** | [Português](../pt-BR/primeiros-10-minutos.md) | [简体中文](../zh-CN/前10分钟.md)

Un servicio minúsculo, un test JUnit, ocho pasos. Cada comando de abajo se
ejecutó de verdad contra esta versión del repositorio — las rutas de
fichero, las puntuaciones de claridad, el mensaje de la aserción y el
marcador `[REDACTED]` son salida real, no ilustraciones. Lo único que
variará en tu máquina es la duración (`ms`) y el `trace_name` de dos
palabras, ambos generados de nuevo en cada ejecución.

Java 17+, el plugin de Gradle, JUnit 5. Si aún no has ejecutado la demo,
`./demo.sh --example ecommerce --no-pause` desde la raíz del repositorio es
todavía más rápido — esta página es para cuando quieres verlo funcionar
contra *tu propio* código.

## 1. Añade el plugin

```kotlin
// build.gradle.kts
plugins {
    id("ai.narrativetrace") version "0.2.1"
}
```

Eso es toda la configuración de dependencias: el plugin añade
`narrativetrace-core`, `-proxy`, `-clarity`, `-diagrams` y `-junit5`, el flag
del compilador `-parameters`, y el motor de la JUnit Platform.

## 2. Añade una interfaz de servicio y su implementación

```java
// src/main/java/com/example/orders/OrderService.java
package com.example.orders;

public interface OrderService {
    String placeOrder(String customerId, String productId, int quantity);
}
```

```java
// src/main/java/com/example/orders/DefaultOrderService.java
package com.example.orders;

public class DefaultOrderService implements OrderService {
    @Override
    public String placeOrder(String customerId, String productId, int quantity) {
        return "ORD-" + customerId + "-" + productId + "-" + quantity;
    }
}
```

Una interfaz, porque el proxy JDK que usa este tutorial envuelve interfaces.
La [matriz de integración](eligiendo-una-integracion.md) tiene la vía para
servicios que no tienen una.

## 3. Añade un test JUnit

```java
// src/test/java/com/example/orders/OrderServiceTest.java
package com.example.orders;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void customerPlacesOrder(NarrativeContext context) {
        OrderService service = NarrativeTraceProxy.trace(
            new DefaultOrderService(), OrderService.class, context);

        service.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

`NarrativeTraceExtension` inyecta `context`; `NarrativeTraceProxy.trace(...)`
envuelve la implementación real detrás de la interfaz, así que cada llamada
a `service` queda capturada.

## 4. Ejecuta la suite

```bash
./gradlew test
```

La salida cae en `build/narrativetrace/` — exactamente la forma que promete
el plugin y nada más, un fichero por escenario y por formato:

```text
build/narrativetrace/
   |
   +-- traces/OrderServiceTest/customer_places_order.md    narrativa legible
   +-- traces/OrderServiceTest/customer_places_order.json  misma traza, JSON canónico
   +-- diagrams/OrderServiceTest/customer_places_order.mmd diagrama de secuencia Mermaid
   +-- structural/OrderServiceTest/customer_places_order.nt forma libre de valores (última baseline en verde)
   +-- clarity-report.md                                   feedback de nombres para toda la suite
   +-- clarity-results.json                                mismas puntuaciones, para clarityCheck
```

`OrderServiceTest.customerPlacesOrder` se convirtió en
`customer_places_order` — el nombre del método de test, humanizado y
convertido en slug. Nada más que configurar.

> NarrativeTrace también imprime un resumen en vivo en la salida estándar
> del proceso de test, que Gradle por defecto enruta solo hacia el informe
> XML/HTML — un `./gradlew test` a secas no muestra nada en tu terminal
> aunque los ficheros de arriba se escriban correctamente. Para verlo en
> vivo, consulta
> [Solución de problemas](solucion-de-problemas.md#no-veo-nada-en-mi-terminal).

## 5. Abre la narrativa

`build/narrativetrace/traces/OrderServiceTest/customer_places_order.md`:

```markdown
---
type: trace
scenario: Customer places order
entry_point: OrderService.placeOrder
duration_ms: 3.973
trace_id: be4e2e8ea4dfcb18ccc43fbb59223bcc
trace_name: rural ivory heaps
method_count: 1
error_count: 0
---

## Trace: OrderService.placeOrder

**Scenario:** Customer places order
**Duration:** 3.973ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, productId: `"SKU-KB"`, quantity: `2`) → `"ORD-C-1234-SKU-KB-2"` — 3.973ms
```

Cada valor del flujo de llamadas — los valores de los parámetros, el valor
de retorno — vino de la llamada que hiciste de verdad. Nada se escribió a
mano.

## 6. Renombra `placeOrder` a `process` y observa cómo cae la claridad

La calidad de los nombres se mide, no se asume. Antes del renombrado,
`clarity-report.md` decía:

```markdown
| Scenario | Score |
|----------|-------|
| Customer places order | 0.89 |

| Element | Score | Note |
|---------|-------|------|
| `OrderService.placeOrder` | 0.81 | Standard verb 'place' + broad noun 'order' |
```

Renombra el método (interfaz, implementación y el punto de llamada) a
`process` y ejecuta `./gradlew test` de nuevo:

```markdown
| Scenario | Score |
|----------|-------|
| Customer places order | 0.68 |

| Category | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Method Names | 0.10 | 0.30 | 0.03 |
| Class Names | 0.91 | 0.20 | 0.18 |
| Parameter Names | 0.92 | 0.25 | 0.23 |
| Structural | 1.00 | 0.15 | 0.15 |
| Cohesion | 0.90 | 0.10 | 0.09 |
| **Overall** | **0.68** | | |

| Severity | Category | Element | Suggestion |
|----------|----------|---------|------------|
| HIGH | method-name | `OrderService.process` | Use a domain-specific verb+noun (e.g., calculateTotal, reserveInventory) |
```

Misma llamada, mismos valores, todo igual salvo el nombre — la puntuación
global cayó de 0.89 a 0.68, la dimensión del nombre del método por sí sola
cayó de 0.81 a 0.10, y apareció una incidencia de severidad HIGH. Este es el
mecanismo detrás de `clarityCheck`: fija un umbral en CI y un nombre
genérico hace fallar la build en lugar de colarse en silencio. Consulta la
[Guía de claridad](guia-de-claridad.md) para el modelo de puntuación
completo. Vuelve a llamarlo `placeOrder` (o algo todavía más específico)
antes de continuar.

## 7. Añade `@NotTraced` y observa la ocultación

```java
public interface OrderService {
    String placeOrder(
        String customerId, String productId, int quantity, @NotTraced String paymentToken);
}
```

Como el `scope` por defecto del plugin es `"test"`, los jars de
NarrativeTrace — incluido `narrativetrace-api`, donde vive `@NotTraced` —
se sitúan solo en `testImplementation`, no en el classpath de compilación
principal. Referenciar la anotación desde una interfaz de `src/main/java`
hace que `./gradlew test` falle al *compilar* con "package
ai.narrativetrace.api.annotation does not exist" antes de que llegue a
ejecutar ningún test. Añade el jar de la API a `compileOnly` para
arreglarlo (no acarrea dependencias de runtime propias):

```kotlin
dependencies {
    compileOnly("ai.narrativetrace:narrativetrace-api:0.2.1")
}
```

Pasa un token en el test (`service.placeOrder("C-1234", "SKU-KB", 2,
"tok_live_51H8x9J")`) y ejecútalo de nuevo. La traza:

```text
- **OrderService.placeOrder**(customerId: `"C-1234"`, productId: `"SKU-KB"`, quantity: `2`, paymentToken: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 9.428ms
```

El nombre del parámetro sigue apareciendo — puedes ver que *se pasó* un
token — pero su valor nunca llega a disco. Consulta
[Privacidad y ocultación](privacidad-y-ocultacion.md) para saber qué más
cubre la ocultación y las dos formas de desactivarla.

## 8. Activa el modo aprobación y observa `.received.nt`

```kotlin
narrativeTrace {
    approval.set(true)
}
```

Ejecuta `./gradlew test` sin ninguna baseline aprobada todavía, y el test
que pasaba falla de todos modos:

```text
java.lang.AssertionError: No approved narrative for scenario "Customer places order".
Received: src/test/narratives/OrderServiceTest/customer_places_order.received.nt
Review it and approve via the approveNarratives task (or rename it to customer_places_order.approved.nt).
```

`customer_places_order.received.nt` contiene la forma libre de valores de
la llamada:

```text
scenario: Customer places order

- OrderService.placeOrder(customerId, productId, quantity, paymentToken) → value
```

Revísala y luego promociónala:

```bash
./gradlew approveNarratives
# Approved: src/test/narratives/OrderServiceTest/customer_places_order.approved.nt
```

`./gradlew test` ahora pasa. A partir de aquí, cualquier cambio estructural
en este escenario — una llamada nueva, una llamada eliminada, un tipo de
resultado cambiado — hace fallar la build con un diff legible hasta que
alguien lo revise y lo vuelva a aprobar. El detalle completo, incluido qué
cuenta como "cambio estructural", está en
[Formato de traza estructural](formato-de-traza-estructural.md).

## A dónde ir a continuación

| Quieres | Ve a |
|---|---|
| Una vía de integración distinta al proxy JDK de arriba | [Eligiendo una integración](eligiendo-una-integracion.md) |
| El contrato de privacidad fila por fila | [Privacidad y ocultación](privacidad-y-ocultacion.md) |
| Qué ficheros generados hacer commit | [Qué commitear](que-commitear.md) |
| Algo de lo anterior no funcionó como se mostraba | [Solución de problemas](solucion-de-problemas.md) |
| Cada opción de configuración | [Guía de configuración](guia-de-configuracion.md) |
