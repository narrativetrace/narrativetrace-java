<!-- source: documentation/installation-guide.md blob cc4473269fe0 | translated: 2026-09-11 | reviewed: - -->
# Guía de instalación de NarrativeTrace Java

[English](../installation-guide.md) | **Español** | [简体中文](../zh-CN/安装指南.md)

Esta guía cubre la instalación y el cableado de NarrativeTrace Java en un proyecto JVM.

## Requisitos previos

- Java 17+
- Build con Gradle

## Compatibilidad

Versiones a fecha de 0.2.1. «Incluida» significa que el módulo depende de ella
y Gradle la resuelve por ti; «tuya» significa que el módulo compila contra ella
pero no depende de ella — ya la tienes, y NarrativeTrace usa la versión que
aportes.

| Requisito | Versión | Quién la aporta |
|---|---|---|
| Java | **17+** — compilado y probado en 17; JDK 21 se ejercita en un job de CI programado (los tests de hilos virtuales solo corren ahí) | tuya |
| Gradle (para el plugin) | **8.0+** — desarrollado y probado contra 8.14.2 | tuya |
| JUnit 5 | 5.11.4 (`narrativetrace-junit5` expone `junit-jupiter-api` como `api`) | incluida |
| JUnit 4 | 4.13.2 | incluida |
| Spring Framework | 6.2.3 — es decir, Spring Boot 3.x | incluida |
| Micronaut | 4.7.6 | incluida |
| Jakarta Servlet | 6.0 (`narrativetrace-servlet`) | tuya |
| SLF4J | 2.0.16 | incluida |
| Micrometer context-propagation | 1.1.2 (`narrativetrace-micrometer`; también lo necesita `ContextPropagatingTaskDecorator`) | incluida por el módulo micrometer, tuya si usas el decorador sin él |
| OpenTelemetry API | 1.46.0 (`narrativetrace-opentelemetry`) | tuya |
| ASM | 9.7.1 — empaquetado (shaded) dentro de `narrativetrace-agent`, nunca en tu classpath | incluida |

### Plataformas de ejecución

La tabla anterior trata de versiones; esta trata de *dónde se ejecuta la
biblioteca*.

| Plataforma | Soportada | Notas |
|---|---|---|
| JVM de servidor y escritorio (HotSpot, OpenJ9, GraalVM sobre la JVM) | **Sí** | El objetivo probado |
| Android | **No** | No probada, y no solo por falta de pruebas — ver abajo |
| iOS y otras plataformas Apple | **N/A** | Usa la edición Swift |
| Imagen nativa de GraalVM | **Sin probar** | Las rutas de proxy y agente dependen de reflexión; hoy no se publican metadatos de alcanzabilidad |

**Por qué Android es un "no" y no un "todavía no".** Tres mecanismos se
degradan *en silencio* bajo la minificación de R8/ProGuard: el descubrimiento
por SPI pierde sus entradas `META-INF/services`, así que las extensiones nunca
se cargan; el arranque del pipeline resuelve el listener de SLF4J por nombre,
así que la narración síncrona desaparece; y el renderizado de valores usa
reflexión sobre campos y getters, así que una narrativa se muestra como
`→ a.b(c: "x")` en vez de con nombres legibles. Ninguno falla de forma
ruidosa, lo que convierte "parecía funcionar en una build de depuración" en el
peor resultado posible. Un soporte honesto de Android necesita reglas de
conservación (keep rules) publicadas para el consumidor y una CI real de
Android, y hoy no existe ninguna de las dos. `narrativetrace-agent` nunca
podrá funcionar allí: Android no tiene `java.lang.instrument`.

La biblioteca ya no *falla* en un runtime sin `java.lang.ProcessHandle` (el id
de proceso simplemente se informa como ausente, lo que el esquema permite),
pero no fallar no es lo mismo que estar soportada.

El plugin exige las dos primeras filas al aplicarse: un Gradle no soportado o un
build que apunta a Java por debajo de 17 falla de inmediato, nombrando el
requisito, en vez de fallar más tarde dentro de la biblioteca.

Dos modos de fallo que conviene nombrar, porque ningún mensaje de error apunta
a la causa:

- **`arg0`, `arg1` en lugar de los nombres de parámetros** — falta el flag de
  compilación `-parameters`. Es un requisito duro, no un detalle; ver el paso 1.
- **`NoClassDefFoundError` en ejecución** — falta en el classpath de ejecución
  una de las filas «tuya». Los módulos compilan contra esas APIs a propósito,
  para que una aplicación sin servlets o sin OTel no cargue dependencias extra.

## Inicio rápido con el plugin de Gradle

El plugin de Gradle se encarga de todo el cableado automáticamente — dependencias, flags del compilador y configuración de la JVM de pruebas:

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.1"
}
```

Eso es todo — tampoco hace falta una dependencia de JUnit: con `testFramework = "junit5"` el plugin pone el motor de Jupiter en `testRuntimeOnly`, porque la JUnit Platform que configura se niega a arrancar sin uno. Ejecuta `./gradlew test` y la salida de trazas aparece en `build/narrativetrace/`.

Para exigir umbrales de calidad de los nombres:

```kotlin
narrativeTrace {
    clarity {
        minScore.set(0.80)
        maxHighIssues.set(0)
    }
}
```

Ahora `./gradlew check` falla si la puntuación de claridad de algún escenario cae por debajo de 0.80 o presenta algún problema de severidad HIGH.

Consulta la [Guía del plugin de Gradle](guia-del-plugin-de-gradle.md) para la referencia completa del DSL, los modos de interceptación, la activación por módulo y las recetas.

## Configuración manual

Las secciones siguientes cubren la instalación manual para proyectos que no usan el plugin de Gradle.

### Requisitos previos

- Metadatos de parámetros del compilador habilitados (`-parameters`)

## 1. Habilita la retención de nombres de parámetros

NarrativeTrace usa los nombres de los parámetros de los métodos en la salida de trazas. Sin `-parameters`, las trazas muestran `arg0`, `arg1`, etc.

```kotlin
// build.gradle.kts
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
```

## 2. Añade las dependencias

Empieza con el stack mínimo y luego añade solo las integraciones que necesites.

```kotlin
dependencies {
    // Mínimo
    implementation("ai.narrativetrace:narrativetrace-core:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.2.1")

    // Integraciones opcionales
    testImplementation("ai.narrativetrace:narrativetrace-junit5:0.2.1")
    testImplementation("ai.narrativetrace:narrativetrace-junit4:0.2.1")  // para JUnit 4
    implementation("ai.narrativetrace:narrativetrace-spring:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-slf4j:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-diagrams:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-clarity:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-opentelemetry:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-micrometer:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-agent:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-servlet:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-spring-web:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-micronaut:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.2.1")
}
```

## 3. Elige una vía de integración

### Opción A: proxy JDK (funciona en cualquier aplicación Java)

```java
var context = new ThreadLocalNarrativeContext();
var tracedOrderService = NarrativeTraceProxy.trace(orderService, OrderService.class, context);

tracedOrderService.placeOrder("C-1234", "SKU-KB", 2);
System.out.println(new IndentedTextRenderer().render(context.captureTrace()));
context.reset();
```

Usa esta opción cuando tus servicios estén basados en interfaces.

### Opción B: envoltura automática con Spring

```java
@Configuration
@EnableNarrativeTrace(basePackages = {"com.example.myapp"})
public class AppConfig {}
```

Usa esta opción cuando quieras que el post-procesamiento de beans envuelva automáticamente los beans elegibles.

### Opción B2: envoltura automática con Micronaut

```yaml
# src/main/resources/application.yml
narrativetrace:
  base-packages:
    - com.example.myapp
```

No se necesita ninguna anotación de habilitación — el módulo `narrativetrace-micronaut` se descubre automáticamente en el classpath. Todos los beans cuya clase e interfaces coinciden con los paquetes configurados se envuelven en proxies de tracing.

Consulta la [Guía de integración con Micronaut](guia-de-integracion-con-micronaut.md) para la configuración del filtro HTTP y los detalles de configuración.

### Opción C: contexto automático de JUnit 5 + salida de trazas

```java
@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void customerPlacesOrder(NarrativeContext context) {
        var orderService = NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);
        orderService.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

Características:
- `NarrativeContext` por prueba mediante inyección de parámetros
- Impresión automática en consola de la traza en caso de fallo
- Nombre del escenario derivado del nombre del método de prueba (`customerPlacesOrder` → "Customer places order")
- Escribe `.md`, `.json` y `.mmd` por prueba por defecto, además de un `clarity-report.md` a nivel de suite — establece `narrativetrace.output=false` para desactivarlo

### Opción D: contexto automático de JUnit 4 + salida de trazas

```java
public class OrderServiceTest {
    @Rule
    public NarrativeTraceRule narrativeTrace = new NarrativeTraceRule();

    @Test
    public void customerPlacesOrder() {
        NarrativeContext context = narrativeTrace.context();
        var orderService = NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);
        orderService.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

Características:
- `NarrativeContext` por prueba mediante `narrativeTrace.context()`
- Impresión automática en consola de la traza en caso de fallo
- Nombre del escenario derivado del nombre del método de prueba (`customerPlacesOrder` → "Customer places order")
- Escribe `.md`, `.json` y `.mmd` por prueba por defecto — establece `-Dnarrativetrace.output=false` para desactivarlo
- Añade `@ClassRule` con `NarrativeTraceClassRule` para obtener el `clarity-report.md` a nivel de suite y el resumen en consola

> **Ver la traza de fallo en tu terminal:** la "impresión de la traza de fallo en consola" anterior se escribe en la salida estándar del proceso de pruebas, que Gradle captura dentro del informe XML/HTML — una terminal normal no muestra nada. Para verla en vivo en la consola, habilita el registro de flujos estándar en la tarea `test`:
>
> ```kotlin
> tasks.test {
>     testLogging.showStandardStreams = true
> }
> ```
>
> Los archivos de traza en `build/narrativetrace/` se escriben de todas formas; esto solo afecta a lo que muestra la consola.

La configuración usa propiedades del sistema (JUnit 4 no tiene `junit-platform.properties`):
- `narrativetrace.output` — `true`/`false` (por defecto: `true`)
- `narrativetrace.outputDir` — ruta (por defecto: `build/narrativetrace`)
- `narrativetrace.format` — `markdown`/`text`/`mermaid`/`plantuml` (por defecto: `markdown`)

### Opción E: agente Java (sin cableado de proxies)

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.* -jar your-app.jar
```

Usa esta opción cuando quieras instrumentación de bytecode para las clases bajo los prefijos de paquete seleccionados. Cuando no se proporcionan argumentos de CLI, el agente recurre a `narrativetrace.properties` en el classpath.

Formato del argumento del agente: `packages=<pkg1>;<pkg2>;...`

Los patrones de paquete admiten comodines:

| Patrón | Coincide con |
|---|---|
| `com.example.*` | Todas las clases bajo `com.example` y sus subpaquetes |
| `com.example.**` | Igual que `.*` (ambos coinciden con todos los subpaquetes) |
| `com.example` | Igual que `com.example.*` (prefijo simple con verificación de límites) |

Varios paquetes:

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.app.*;com.example.shared.* -jar app.jar
```

Los separadores de paquetes son puntos y comas (`;`), no comas. Las claves desconocidas se ignoran; las claves duplicadas se rechazan.

#### La narración necesita un proveedor SLF4J — el agente nunca trae uno

El jar `-standalone` del agente incluye todo lo que necesita *excepto* un backend
de registro. Es deliberado: la pila de logging del host es tuya, y un agente que
colara un segundo proveedor pelearía con el que ya tienes.

Por eso un host mínimo sin proveedor en su classpath ve a SLF4J decirlo, una vez,
al arrancar:

```text
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
```

No hay nada roto — las trazas se siguen capturando y `captureTrace()` las sigue
devolviendo — pero no se escribe nada en el log. Dos arreglos de una línea, según
lo que quieras:

| Lo que quieres | Haz esto |
|---|---|
| Narración en tus logs | Pon un proveedor en el classpath (`logback-classic`, `slf4j-simple`, …), o pasa `loggingJars=/ruta/al/proveedor.jar` cuando el host no tenga un classpath alcanzable |
| Ninguna narración | Adjunta con `loggerName=` (vacío), o define `-Dnarrativetrace.narration=off` |

Con la narración desactivada el agente no toca SLF4J en absoluto: arranca en
silencio.


## 4. Configura la salida de trazas

La salida de trazas se escribe en `build/narrativetrace` por defecto — no
hay nada que activar. Las secciones siguientes son para cambiar el formato
o desactivarla.

### JUnit 5 (recomendado): `junit-platform.properties`

Añade `src/test/resources/junit-platform.properties`:

```properties
narrativetrace.format=markdown
```

Para desactivarla del todo, pon `narrativetrace.output=false` en el mismo
archivo. Ninguna de las dos cosas necesita cableado en Gradle. Este archivo
es solo de pruebas y nunca toca producción.

### Gradle: `gradle.properties` (alternativa)

Define la configuración de la salida de trazas en un solo lugar:

```properties
# gradle.properties
narrativetrace.format=markdown
```

Luego reenvíala a la JVM de pruebas en `build.gradle.kts`:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
    listOf("narrativetrace.output", "narrativetrace.outputDir", "narrativetrace.format")
        .forEach { key ->
            (findProperty(key) as? String)?.let { systemProperty(key, it) }
        }
}
```

### Anulación por CLI

Las propiedades del sistema anulan todas las demás fuentes:

```bash
./gradlew test -Dnarrativetrace.output=false
./gradlew test -Pnarrativetrace.format=text
```

### Java puro / agente: `narrativetrace.properties`

Añade un archivo al classpath (p. ej. `src/main/resources/narrativetrace.properties`):

```properties
narrativetrace.packages=com.example.app.*;com.example.shared.*
```

## 5. Valida la instalación

Ejecuta las pruebas:

```bash
./gradlew test
```

Los archivos de trazas se escriben automáticamente, a menos que `junit-platform.properties` tenga `narrativetrace.output=false`.

Estructura de salida esperada:

```
build/narrativetrace/
├── traces/
│   └── OrderServiceTest/
│       ├── customer_places_order.md
│       ├── customer_places_order.json
│       └── ...
├── diagrams/
│   └── OrderServiceTest/
│       ├── customer_places_order.mmd
│       └── ...
└── clarity-report.md
```

### Qué aparece en la salida generada

Cuando una traza tiene contexto de span, los archivos generados incluyen tanto el ID de traza en bruto
como un nombre de traza determinista y legible derivado de él.

El frontmatter de Markdown incluye:

```yaml
trace_id: 4bf92f3577b34da6a3ce929d0e0e4736
trace_name: bold elk soars
```

La exportación JSON incluye:

```json
{
  "trace": {
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
    "traceName": "bold elk soars"
  }
}
```

El `traceId` hexadecimal sigue siendo el identificador autoritativo. `traceName` es un alias legible para logs,
dashboards y conversaciones del equipo.

## Referencia de selección de módulos

| Módulo | Cuándo añadirlo |
|---|---|
| `narrativetrace-core` | Siempre requerido |
| `narrativetrace-proxy` | Tracing basado en interfaces mediante proxies JDK |
| `narrativetrace-junit5` | Extensión de JUnit 5 y emisión de archivos de trazas |
| `narrativetrace-junit4` | Regla y regla de clase de JUnit 4 para la salida de trazas |
| `narrativetrace-spring` | Envoltura automática de beans de Spring mediante `@EnableNarrativeTrace` |
| `narrativetrace-slf4j` | Emite eventos narrativos al logger de SLF4J |
| `narrativetrace-diagrams` | Renderizadores de Mermaid / PlantUML |
| `narrativetrace-clarity` | Análisis e informes de claridad de los nombres |
| `narrativetrace-opentelemetry` | Exporta árboles de trazas como spans de OpenTelemetry, o creación de spans en vivo mediante decorador |
| `narrativetrace-micrometer` | Propagación de trazas entre hilos mediante context-propagation de Micrometer |
| `narrativetrace-agent` | Instrumentación con agente Java |
| `narrativetrace-servlet` | Filtro de servlet para producción — ciclo de vida y exportación de trazas por petición (sin Spring) |
| `narrativetrace-spring-web` | `@Configuration` de Spring que cablea automáticamente el filtro de servlet con un exportador conectable |
| `narrativetrace-micronaut` | Envoltura automática de beans de Micronaut mediante `BeanCreatedEventListener` |
| `narrativetrace-micronaut-http` | Filtro HTTP reactivo de Micronaut para el ciclo de vida de trazas por petición |

## Véase también

- [Guía del plugin de Gradle](guia-del-plugin-de-gradle.md) — referencia completa del DSL, modos de interceptación, recetas, DSL de Groovy
- [Guía de configuración](guia-de-configuracion.md) — niveles de tracing, configuración de JUnit/Gradle/Spring/SLF4J
- [Guía de integración con Spring](guia-de-integracion-con-spring.md) — tracing de beans, filtro de servlet, propagación con `@Async`, pruebas
- [Guía de integración con Micronaut](guia-de-integracion-con-micronaut.md) — tracing de beans, filtro HTTP, propiedades de configuración
- [Guía de anotaciones](guia-de-anotaciones.md) — `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`
- [Guía de claridad](guia-de-claridad.md) — modelo de puntuación, componentes de NLP, integración con JUnit
