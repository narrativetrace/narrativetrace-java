<!-- source: documentation/sixty-seconds.md blob 8583f9a642a7 | translated: 2026-09-12 | reviewed: - -->
# Ve una traza en 60 segundos

[English](../sixty-seconds.md) | **Español** | [Português](../pt-BR/sessenta-segundos.md) | [简体中文](../zh-CN/60秒.md)

Sin sentencias de log, sin framework de pruebas, sin archivo que abrir
después: un `main` de Java sencillo, una llamada, y la traza aparece en tu
terminal. Todo lo de abajo se ejecutó de verdad contra los artefactos
publicados en Maven Central — la salida está pegada, no imaginada.

## 1. Proyecto nuevo, añade la dependencia

Java 17+. Dos artefactos — el núcleo del runtime y el proxy JDK que envuelve
una interfaz sencilla:

```kotlin
// build.gradle.kts
plugins {
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.narrativetrace:narrativetrace-core:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.2.1")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")   // sin esto, las trazas muestran arg0, arg1
}

application {
    mainClass.set("com.example.orders.Main")
}
```

## 2. El programa

Una interfaz y una implementación — el proxy envuelve la interfaz, así que
`OrderService` necesita una:

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

```java
// src/main/java/com/example/orders/Main.java
package com.example.orders;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.proxy.NarrativeTraceProxy;

public class Main {
  public static void main(String[] args) {
    var context = new ThreadLocalNarrativeContext();
    OrderService service =
        NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);

    service.placeOrder("C-1234", "SKU-KB", 2);

    System.out.println(new IndentedTextRenderer().render(context.captureTrace()));
    context.reset();
  }
}
```

`ThreadLocalNarrativeContext` es donde se acumula una traza.
`NarrativeTraceProxy.trace(...)` envuelve la implementación real detrás de la
interfaz, así que cada llamada a través de `service` queda capturada.
`captureTrace()` devuelve el árbol terminado; `IndentedTextRenderer` lo
convierte en texto.

## 3. Ejecútalo

Todavía no hay `./gradlew` en este directorio — genera el wrapper una vez
(necesitas Gradle instalado; ver [gradle.org/install](https://gradle.org/install/)):

```bash
gradle wrapper
```

```bash
./gradlew run
```

```text
OrderService.placeOrder(customerId: "C-1234", productId: "SKU-KB", quantity: 2) → "ORD-C-1234-SKU-KB-2" — 23ms
```

Esa es la salida real, sin editar, de la ejecución de arriba. La duración
(`23ms`) es lo único que variará en tu máquina y entre ejecuciones.

No escribiste ni una sola sentencia de log. La narrativa vino del nombre del
método (`placeOrder`), de los nombres de los parámetros (`customerId`,
`productId`, `quantity`) y del valor que el método devolvió — nada más.

## Qué acaba de pasar

- **`ThreadLocalNarrativeContext`** es el contexto de grabación — uno por
  hilo, que retiene lo que sea que se ejecute en él hasta que lo leas.
- **`NarrativeTraceProxy.trace(...)`** envuelve `OrderService` en un proxy
  dinámico de la JDK: cada llamada a través de la referencia envuelta queda
  capturada antes de llegar a la implementación real.
- **`context.captureTrace()` + un renderizador** convierten las llamadas
  capturadas en salida. `IndentedTextRenderer` es el renderizador de texto
  plano usado arriba; el mismo árbol también se renderiza como Markdown,
  JSON o un diagrama de secuencia Mermaid — consulta [Formato de traza
  estructural](../structural-trace-format.md) (en inglés) y la [Guía del
  plugin de Gradle](guia-del-plugin-de-gradle.md) para los demás formatos y
  cómo el plugin los escribe a disco por cada prueba.

## Envíalo a tu logger

Mismo proyecto, una dependencia y un archivo de configuración — el
`Main.java` de arriba no cambia. `narrativetrace-slf4j` se conecta solo al
pipeline en cuanto está en el classpath, así que este es todo el diff:

```diff
 dependencies {
     implementation("ai.narrativetrace:narrativetrace-core:0.2.1")
     implementation("ai.narrativetrace:narrativetrace-proxy:0.2.1")
+    runtimeOnly("ai.narrativetrace:narrativetrace-slf4j:0.2.1")
+    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")
 }
```

```xml
<!-- src/main/resources/logback.xml -->
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level [%logger] - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="narrativetrace" level="TRACE" />

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

```bash
./gradlew run
```

```text
22:24:53.632 TRACE [narrativetrace] - → OrderService.placeOrder(customerId: "C-1234", productId: "SKU-KB", quantity: 2)
22:24:53.637 TRACE [narrativetrace] - ← returned: "ORD-C-1234-SKU-KB-2"
OrderService.placeOrder(customerId: "C-1234", productId: "SKU-KB", quantity: 2) → "ORD-C-1234-SKU-KB-2" — 8ms
```

La duración varía como antes. La misma traza ahora llega a la biblioteca de
logging que ya tienes — Logback, elegido aquí por ser el backend SLF4J más
común — mientras la línea de consola sigue imprimiéndose exactamente igual.
Configuración completa (niveles de log, nombres de logger, campos MDC) en la
[Guía de Configuración, §7](guia-de-configuracion.md#7-configuración-de-slf4j).

## Siguiente

| Quieres | Ve a |
|---|---|
| Esto corriendo dentro de tu suite de pruebas, un archivo por escenario, automáticamente | [Guía de instalación](guia-de-instalacion.md) y la [Guía del plugin de Gradle](guia-del-plugin-de-gradle.md) |
| Mantener un valor — un token, una contraseña — fuera de la traza | [Privacidad y ocultación](privacidad-y-ocultacion.md) |
| Puntuar si tus nombres realmente se leen como una narrativa | [Guía de claridad](guia-de-claridad.md) |
| Cada perilla de configuración, JUnit/Gradle/Spring/Micronaut/SLF4J por igual | [Guía de configuración](guia-de-configuracion.md) |
| Una vía de integración distinta al proxy JDK de arriba (Spring, Micronaut, agente java, sin interfaz) | [Eligiendo una integración](eligiendo-una-integracion.md) |
| Algo de lo anterior no funcionó como se muestra | [Solución de problemas](solucion-de-problemas.md) |
