<!-- source: README.md blob a4e519f1b340 | translated: 2026-09-07 | reviewed: - -->
# NarrativeTrace

[English](README.md) | **Español** | [Português](LEIAME.md) | [简体中文](自述文件.md)

> El código es el log.

NarrativeTrace™ convierte el código Java en ejecución en una narrativa legible,
construida a partir de los nombres de método, clase y parámetro que ya
escribiste. Sin líneas `logger.info(...)`. Si la traza es ilegible, tu código
necesita refactorización — no más sentencias de log.

Con prisa: [ejecuta la demo](#pruébalo-con-un-solo-comando) → [añádelo a una
sola prueba](#añádelo-a-una-sola-prueba) → [elige tu
integración](#elige-tu-integración).

## El problema

La mitad de este método es ruido de logging:

```java
public OrderResult placeOrder(String customerId, String productId, int quantity) {
    logger.info("Placing order for customer {} product {} quantity {}", customerId, productId, quantity);

    var inventory = inventoryService.reserve(productId, quantity);
    logger.debug("Reserved inventory: {}", inventory);

    var payment = paymentService.charge(customerId, inventory.total());
    logger.info("Payment processed: {}", payment.transactionId());

    var result = new OrderResult(payment.transactionId(), inventory.items());
    logger.info("Order placed successfully: {}", result);
    return result;
}
```

La lógica de negocio son tres líneas. El logging, otras cuatro. Cada
desarrollador escribe estos logs de forma distinta — mensajes distintos, niveles
distintos, valores incluidos distintos. El resultado es inconsistente, verboso y
enredado con el código que describe.

NarrativeTrace elimina todo esto por completo:

```java
public OrderResult placeOrder(String customerId, String productId, int quantity) {
    var inventory = inventoryService.reserve(productId, quantity);
    var payment = paymentService.charge(customerId, inventory.total());
    return new OrderResult(payment.transactionId(), inventory.items());
}
```

Lógica de negocio pura. La traza se genera a partir de los nombres de métodos,
los nombres de parámetros y los valores de retorno — la información que ya
estaba ahí.

## Cómo se ve la salida

Ejecuta tu código y obtén trazas de ejecución como esta:

```
OrderService.placeOrder(customerId: "C-1234", productId: "SKU-MECHANICAL-KB", quantity: 2)
  CustomerService.findCustomer(customerId: "C-1234") -> Customer[id=C-1234, name=Alice Johnson, tier=GOLD]
  ProductCatalogService.lookupPrice(productId: "SKU-MECHANICAL-KB") -> 89.99
  InventoryService.reserve(productId: "SKU-MECHANICAL-KB", quantity: 2) -> Reservation[productId=SKU-MECHANICAL-KB, quantity=2]
  PaymentService.charge(customerId: "C-1234", amount: 179.98) -> PaymentConfirmation[transactionId=TXN-00001, amount=179.98]
-> OrderResult[orderId=ORD-00001, transactionId=TXN-00001, totalCharged=179.98, itemCount=2]
```

**Cuando algo sale mal**, la traza hace visible el bug:

```
OrderService.placeOrder(customerId: "C-BROKE", productId: "SKU-MOUSE-PAD", quantity: 3)
  CustomerService.findCustomer(customerId: "C-BROKE") -> Customer[id=C-BROKE, name=Charlie Broke, tier=STANDARD]
  ProductCatalogService.lookupPrice(productId: "SKU-MOUSE-PAD") -> 24.99
  InventoryService.reserve(productId: "SKU-MOUSE-PAD", quantity: 3) -> Reservation[productId=SKU-MOUSE-PAD, quantity=3]
  PaymentService.charge(customerId: "C-BROKE", amount: 74.97) !! PaymentDeclinedException: Payment declined for customer C-BROKE
!! PaymentDeclinedException: Payment declined for customer C-BROKE
```

`InventoryService.reserve` fue invocado, pero `InventoryService.release` no
aparece en ninguna parte de la traza. El bug está a la vista.

**La traza vale tanto como tus nombres.** El mismo flujo de Minecraft «el
jugador se une al mundo», trazado dos veces — una con nombres de dominio, otra
con nombres genéricos.

**Nombres de dominio:**

```
WorldServer.playerJoined(playerName: "Steve")
  WorldGenerator.generateChunk(x: 0, z: 0) -> Chunk(x: 0, z: 0, biome: "plains")
  PlayerInventory.addItem(item: OAK_LOG, quantity: 4) -> true
  CraftingTable.craft(recipe: WOODEN_PICKAXE) -> WOODEN_PICKAXE
  CreatureSpawner.spawnHostile(type: ZOMBIE, x: 10, y: 64, z: 20) -> Creature(type: ZOMBIE, ...)
```

**Nombres genéricos:**

```
GameManager.handle(input: "Steve")
  DataProcessor.process(a: 0, b: 0) -> DataResult(a: 0, b: 0, tag: "plains")
  StateManager.update(type: 1, count: 4) -> true
  ThingFactory.create(type: 1) -> 1
  EntityHandler.execute(kind: 1, a: 10, b: 64, c: 20) -> Entity(kind: 1, ...)
```

El mismo grafo de llamadas. Los mismos valores de retorno. Solo cambian los
nombres. Si tu código no puede contar su propia historia, necesita
refactorización — y por eso NarrativeTrace también [puntúa tus
nombres](#más-allá-de-la-primera-traza).

### Por qué esto importa para el desarrollo asistido por IA

Cada línea `logger.info(...)` es una línea que las herramientas de IA para
programar — Claude Code, Copilot, Cursor — tienen que parsear, gastar tokens en
ella y sortear al razonar. En una clase de servicio típica, el logging es el
30–50 % de las líneas. Elimínalas y el mismo presupuesto de tokens cubre más de
tu código real, el modelo ve lo que hace el código en lugar de cómo loguea lo
que hace, y los pull requests muestran cambios de lógica de negocio en vez de
cambios mezclados de lógica y logging.

Hay una segunda mitad: cada test emite además una **traza estructural segura
para IA** (`structural/<Clase>/<escenario>.nt`) — la estructura de llamadas con
todos los valores de tiempo de ejecución eliminados, segura para entregarla a
una herramienta de IA o commitearla al repositorio. Consulta el [formato de
traza estructural](documentation/es/formato-de-traza-estructural.md).

### Comparación con otras opciones

| En lugar de | NarrativeTrace |
|---|---|
| **Logging estructurado** (SLF4J + MDC) — tú escribes las sentencias de log | Las genera desde la estructura del código; cuando el tracing de peticiones está activo, además rellena campos de correlación como `traceId` y un `traceName` legible como `bold elk soars`. *Usa* SLF4J en lugar de reemplazarlo |
| **Tracing distribuido** (OpenTelemetry, Jaeger) — spans entre servicios, sin valores de parámetros | Árboles de llamadas a nivel de método con valores de parámetros y retornos. `narrativetrace-opentelemetry` une ambos mundos: exporta los árboles de NarrativeTrace como spans de OTel con atributos `narrative.*` |
| **Logging AOP** (Spring AOP, AspectJ) — líneas planas y mecánicas de entrada/salida | Árboles de llamadas anidados, más una puntuación de claridad sobre los nombres que los produjeron |

NarrativeTrace no reemplaza tus alertas de producción ni tus mapas de topología
de servicios. Te da algo que ninguno de los dos ofrece: una narrativa de
ejecución legible por humanos que a la vez sirve de diagnóstico de calidad del
código.

### No reemplaza tu framework de logging

NarrativeTrace no toca tu framework de logging. No incluye ningún appender,
ningún encoder, ningún sink — tu configuración de Logback o Log4j, sus
formatos y sus destinos, siguen funcionando exactamente igual que hoy.

Lo que reemplaza es la narración que escribes a mano: las líneas
`log.info("Placing order {} for customer {}", ...)` que describen lo que hace
un método. Un método instrumentado produce esa narrativa automáticamente, a
partir de su propia firma y su valor de retorno.

Esto no es una metáfora. La ruta síncrona y durable de la tubería de eventos
*es* un listener de SLF4J (`narrativetrace-slf4j`) — la narrativa generada
llega a tus appenders a través de la misma llamada SLF4J que usarías con un
`log.info(...)` escrito a mano. Las sentencias de log manuales siguen
funcionando justo al lado: mismo logger, mismos flujos, antes, dentro o
después de un método instrumentado. Mézclalos libremente mientras migras —
consulta [Convivencia con el logging
tradicional](documentation/es/guia-de-configuracion.md#convivencia-con-el-logging-tradicional)
para ver un ejemplo completo.

## Pruébalo con un solo comando

Sin proyecto, sin cableado — el repositorio incluye un lanzador de demos que
ejecuta las aplicaciones de ejemplo y las narra en vivo (la primera ejecución
compila el ejemplo):

```bash
./demo.sh --list                                  # ecommerce, clarity, minecraft, library
./demo.sh --example ecommerce --no-pause          # el grafo de servicios Spring insignia
./demo.sh --example ecommerce --classic           # la misma ejecución como líneas de log clásicas
./demo.sh --example minecraft --no-pause          # la comparación de nombres de arriba, de verdad
./demo.sh --example ecommerce --lang es           # la misma ejecución re-renderizada con el glosario de dominio
```

Sin `--no-pause` la demo se detiene tras cada escenario — `[Enter]` continúa,
`q` sale — y cada escenario se abre con una nota sobre cómo está cableada *esa*
traza. Consulta los [ejemplos](narrativetrace-examples/LEAME.md) para saber qué
enseña cada uno.

## Añádelo a una sola prueba

El camino más corto desde «librería interesante» hasta «vi una traza útil de mi
propio código» es el plugin de Gradle más una prueba de JUnit 5. Java 17+
([matriz de compatibilidad
completa](documentation/es/guia-de-instalacion.md#compatibilidad)).

**1. Aplica el plugin.** Añade las dependencias, activa el flag de compilador
`-parameters` (sin él las trazas muestran `arg0`, `arg1`), configura la JVM de
pruebas y aporta el motor de Jupiter:

```kotlin
// build.gradle.kts
plugins {
    id("ai.narrativetrace") version "0.2.0"
}
```

**2. Traza un servicio en una prueba:**

```java
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

**3. Ejecuta la suite:**

```bash
./gradlew test
```

**4. Abre la narrativa** — el nombre del método de prueba se convirtió en el
nombre del escenario:

```text
build/narrativetrace/traces/OrderServiceTest/customer_places_order.md
```

Cada prueba de la suite escribe su propio conjunto de artefactos:

```text
build/narrativetrace/
├── traces/<ClaseDePrueba>/<escenario>.md        la narrativa para humanos
├── traces/<ClaseDePrueba>/<escenario>.json      la misma traza como JSON canónico
├── diagrams/<ClaseDePrueba>/<escenario>.mmd     diagrama de secuencia Mermaid
├── structural/<ClaseDePrueba>/<escenario>.nt    forma del comportamiento sin valores (segura para IA)
└── clarity-report.md                            feedback de nombres de toda la suite
```

Sin el plugin, la misma configuración son cuatro líneas de Gradle y dos
dependencias — consulta la [Guía de
instalación](documentation/es/guia-de-instalacion.md).

¿Quieres seguir desde aquí — renombrar el método y ver caer la puntuación de
claridad, añadir `@NotTraced` y ver un valor ocultado, activar el modo de
aprobación y ver un `.received.nt`? → [Primeros 10
minutos](documentation/first-10-minutes.md) (en inglés) recorre todo eso con
salida real, ejecutada de verdad.

### ¿Gradle o Maven?

Los jars del runtime son artefactos Maven corrientes.
`ai.narrativetrace:narrativetrace-core:0.2.0` y todos los módulos que lo
acompañan se resuelven y funcionan exactamente igual desde un build de Maven;
nada de la librería en sí es específico de Gradle. Lo que *sí* es específico de
Gradle es el plugin de arriba — una comodidad que cablea por ti el flag del
compilador, las dependencias y la JVM de pruebas.

Es decir: los mismos jars, con cualquier herramienta de build — pero la
experiencia de configuración documentada y de primera clase hoy es Gradle. Un
ejemplo Maven completo ya respalda esa afirmación:
[`narrativetrace-maven-example`](narrativetrace-maven-example) es un
`pom.xml` autónomo que consume artefactos instalados localmente, con Surefire
cableado para el directorio de salida y la extensión de JUnit 5 registrada a
la manera de Maven — la [Guía de Maven](documentation/maven-guide.md) (en
inglés) lo recorre de principio a fin, incluida la única diferencia que
importa: sin plugin no hay flag del compilador automático ni tarea
`approveNarratives`, y qué hacer en su lugar en cada caso. Ambos
resuelven los mismos artefactos publicados en Maven Central.

## Elige tu integración

Las pruebas son donde empieza casi todo el mundo. Esto es lo que viene después:

| Lo que quieres | Empieza con |
|---|---|
| Trazas en las pruebas, con el mínimo cableado | Plugin de Gradle + `narrativetrace-junit5` |
| Lo mismo, en JUnit 4 | `narrativetrace-junit4` |
| Elegir exactamente qué se envuelve, en Java puro | `narrativetrace-proxy` (proxy dinámico de la JDK) |
| Beans de Spring trazados automáticamente | `narrativetrace-spring` |
| Ciclo de vida de peticiones HTTP de Spring en producción | `narrativetrace-spring-web` (cablea `narrativetrace-servlet`) |
| Cualquier app servlet, sin Spring | `narrativetrace-servlet` |
| Beans y peticiones de Micronaut | `narrativetrace-micronaut` + `narrativetrace-micronaut-http` |
| **Cero cambios de código** — una app que no puedes o no quieres modificar | `narrativetrace-agent` (agente java) |
| Visibilidad de trabajo asíncrono entre hilos (`@Async`, Reactor, executors) | `narrativetrace-micrometer`, o `ContextSnapshot` a mano |
| Trazas en tu stream de logs de producción | `narrativetrace-slf4j` |
| Spans de OpenTelemetry | `narrativetrace-opentelemetry` |

**Cero cambios de código** merece detalle, porque no requiere ni siquiera tocar
el build — el agente java reescribe las clases al cargarlas:

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.* -jar your-app.jar
```

Cada método no privado de cada clase de los paquetes indicados recibe en su
cuerpo la captura de entrada y salida; nada fuera de ellos se toca. La
comparación de paquetes es solo de inclusión y respeta el delimitador, de modo
que `com.acme` nunca captura `com.acmeExtra`. En un servidor de aplicaciones sin
classpath alcanzable, usa el jar `-standalone` y `loggingJars=`; el agente
deliberadamente no trae ningún proveedor SLF4J propio. Las recetas de todas las
vías anteriores están en la [Guía de
instalación](documentation/es/guia-de-instalacion.md); un diagrama de decisión
para elegir una está en [Choosing an
Integration](documentation/choosing-an-integration.md) (en inglés).

## Módulos

| Módulo | Lo necesitas cuando... |
|--------|------------------------|
| `narrativetrace-api` | El contrato solo de compilación: anotaciones, modelo de eventos y SPI. Normalmente lo obtienes de forma transitiva con `core` — depende solo de él si eres autor de una librería que publica contra el contrato. |
| `narrativetrace-core` | Siempre requerido. Contexto de ejecución, pipeline, renderizadores, configuración y exportación. Cero dependencias en tiempo de ejecución. |
| `narrativetrace-proxy` | Usas tracing con proxy JDK (lo más común). |
| `narrativetrace-junit5` | Auto-tracing en pruebas JUnit 5. |
| `narrativetrace-junit4` | Auto-tracing en pruebas JUnit 4. |
| `narrativetrace-spring` | Auto-envoltura de beans de Spring. |
| `narrativetrace-micronaut` | Auto-envoltura de beans de Micronaut. |
| `narrativetrace-slf4j` | Encaminas las trazas a través de SLF4J/Logback. |
| `narrativetrace-clarity` | Analizas la calidad de los nombres de métodos/parámetros. |
| `narrativetrace-glossary` | Recolectas un glosario de dominio (lenguaje ubicuo) desde las trazas. |
| `narrativetrace-diagrams` | Generas diagramas de secuencia Mermaid/PlantUML. |
| `narrativetrace-opentelemetry` | Exportas árboles de trazas como spans de OpenTelemetry, o creas spans en vivo vía decorador. |
| `narrativetrace-micrometer` | Propagación de trazas entre hilos vía context-propagation de Micrometer. |
| `narrativetrace-agent` | Tracing a nivel de bytecode sin cablear proxies. |
| `narrativetrace-servlet` | Ciclo de vida de peticiones en producción en cualquier app servlet (no requiere Spring). |
| `narrativetrace-spring-web` | `@Configuration` de Spring para `narrativetrace-servlet` — auto-cablea el filtro con `ObjectProvider`. |
| `narrativetrace-micronaut-http` | Filtro HTTP reactivo de Micronaut para el ciclo de vida de trazas por petición. |
| `narrativetrace-examples` | Apps de referencia (solo código fuente): e-commerce, comparación de nombres en Minecraft, préstamo bibliotecario, demo de claridad de reservas de hotel, un WAR EJB 4 (Jakarta EE) de reclamaciones de seguros en WildFly trazado sin código por el agente java (`ejb4`, pruebas basadas en Docker), más utilidades compartidas en `common`. |

**Punto de partida típico:** el plugin de Gradle, que instala `core` + `proxy` +
`junit5` por ti.

## Más allá de la primera traza

Tres cosas que la suite te da una vez que las trazas están funcionando, cada una
con su guía detrás:

**Puntuación de claridad.** Si la traza *es* el código, la calidad de la traza
*es* la calidad del código. `clarity-report.md` puntúa cada nombre de método,
clase y parámetro que se ejecutó: los nombres genéricos como `processData` o
`handleRequest` puntúan bajo, los específicos del dominio como
`reserveInventory` o `customerId` puntúan alto, y `clarityCheck` puede hacer
fallar el build según el umbral que fijes. Aún es experimental.
→ [Guía de claridad](documentation/es/guia-de-claridad.md)

**Pruebas de aprobación narrativa.** Cada ejecución compara la *estructura* de
cada escenario con la última ejecución verde y cierra la suite con una línea:

```
NarrativeTrace — Suite complete
  5 scenarios recorded
  Clarity: 100% high | 0% moderate | 0% low
  Reports: build/narrativetrace
  Since last green: 4 scenarios unchanged · 1 changed: "Customer places order" (+1 call InventoryService.release)
```

Activa el modo de aprobación (`narrativetrace.approval=true`, o
`approval.set(true)` en el DSL del plugin) y esa estructura se convierte en un
contrato commiteado: un test que pasa pero cuya forma difiere de su línea base
`src/test/narratives/<Clase>/<escenario>.approved.nt` falla con un diff legible,
la nueva forma queda a su lado como `.received.nt`, y
`./gradlew approveNarratives` promueve lo que hayas revisado. Un cambio de
comportamiento — incluido uno que un agente de IA haya colado en una
refactorización — tiene que ser revisado y aprobado, no basta con que compile.
Las líneas base están libres de valores, así que son estables entre ejecuciones
y seguras de commitear.
→ [Formato de traza estructural](documentation/es/formato-de-traza-estructural.md)

**Concurrencia.** El paralelismo fork-join (`ForkGroup`), el trabajo
fire-and-forget (lanzar y olvidar, `FireAndForgetGroup`) y los retornos de
`CompletableFuture` son ciudadanos de primera clase del árbol de trazas, con
marcadores `⑂ fork` / `⑃ join`, nombres de hilos y análisis de tiempos de
espera. `captureTrace()` tiene ámbito de hilo deliberadamente — captura en el
hilo que registra, o injerta el trabajo en la traza padre con
`ContextSnapshot.wrap()`.
→ [Referencia completa: concurrencia](documentation/llms-full.md#concurrency)
(en inglés)

## Privacidad y seguridad

Esta librería se ejecuta dentro de tu proceso y escribe archivos que tu equipo
va a compartir. Lo que eso significa, en una sola pantalla:

| Garantía | Cómo se sostiene |
|---|---|
| **La ocultación es incondicional** | `@NotTraced` y la lista de denegación por nombre (`password`, `token`, `cvv`, `ssn`, …) se aplican a todas las salidas publicadas — trazas de pruebas, artefactos de CI, logs de contenedor, narración del agente. Ninguna etapa, flag o propiedad las desactiva (decisión del propietario, 2026-08-16). Sobrevive un contenedor de profundidad (`Optional`, `Future`, `AtomicReference`, `Map.Entry`); `@NotTraced` prevalece sobre un `toString()` propio, y una plantilla `{param.ruta}` que nombre un miembro oculto se resuelve como `[REDACTED]`. |
| **El artefacto seguro para IA no contiene valor alguno** | El archivo estructural `.nt` contiene solo nombres, jerarquía y tipos de resultado. Nada que ocultar, cero superficie de inyección de prompts — y eso es una prueba de propiedades, no una política. |
| **Un fallo del tracing no puede hacer fallar tu aplicación** | El registro está aislado frente a excepciones en todas las rutas, y ambos consumidores del pipeline se tragan sus propios errores. Un `toString()` que lanza, un búfer lleno o un appender roto nunca cambian lo que tu método devuelve o lanza. |
| **El uso de recursos está acotado** | La ruta de análisis con búfer es un anillo de tamaño fijo (65 536 ranuras por defecto, `narrativetrace.buffer.capacity`) que descarta en vez de bloquear — y lo dice: una captura que perdió eventos imprime el recuento en su propio pie. El renderizado de valores está acotado en longitud de cadena, tamaño de colección, anchura de objeto y profundidad de anidamiento. |
| **La salida no se puede falsificar** | Los valores renderizados se escapan para que no puedan inyectar líneas de log, romper Markdown ni corromper la sintaxis de los diagramas. |
| **Apilarse con otros envoltorios es seguro** | Proxies AOP, librerías de contratos, interceptores de contenedor y otros agentes pueden envolver el mismo método que NarrativeTrace. El orden de anidamiento puede cambiar cómo *se lee* la traza — nunca lo que *devuelve o lanza*: el registro está aislado frente a excepciones en todas las rutas y nunca reemplaza un resultado o excepción. El objetivo es un frame de traza por cruce de frontera de negocio; la maquinaria (métodos puente, stubs de vista del contenedor, código generado por otra librería) no es el objetivo. |

Dos límites honestos. Primero, la única forma de que un valor escape a la
ocultación es que el código de la aplicación construya su propio `ValueRenderer`
con `RedactionPolicy.DISABLED` — un acto deliberado y revisable en tu propio
código fuente, nunca un estado de configuración. Segundo, la captura lee
*campos* por reflexión y nunca llama a tus getters, pero sí invoca un
`toString()` propio, un método `@NarrativeSummary`, los accesores de componentes
de `record` y las rutas de propiedades nombradas en plantillas
`@Narrated`/`@OnError`; mantenlos puros, como lo harías para un depurador. Hoy
el alcance es solo de inclusión — `packages=` para el agente, paquetes base para
Spring y Micronaut — y todavía no hay lista de exclusión.

→ [Privacy and Redaction](documentation/privacy-and-redaction.md) (en inglés)
para el contrato de ocultación fila por fila, verificado contra el código,
[Guía del ciclo de vida](documentation/es/guia-del-ciclo-de-vida.md) para la
postura de privacidad en cada etapa, [Guía de
anotaciones](documentation/es/guia-de-anotaciones.md) para el contrato de pureza
completo, y [Eligiendo una integración § Apilarse con otros
envoltorios](documentation/es/eligiendo-una-integracion.md#apilarse-con-otros-envoltorios)
para el detalle por mecanismo de la convivencia con proxies AOP, librerías de
contratos y otros agentes.

## Rendimiento

Hemos puesto verdadero esfuerzo en las rutas calientes, y no vamos a proclamar
«sobrecoste cero» — trazar realiza trabajo, y el trabajo cuesta algo. Esto es lo
que medimos (JMH, JDK 17, `-prof gc`, una operación del benchmark = una
llamada):

- **Tracing desactivado o contexto inactivo:** el proxy JDK añade ~12–26 ns por
  llamada sobre una invocación directa (que en este banco de pruebas mide
  ~9–12 ns) y 24 B/op — un único `Object[]` para los argumentos. Una puerta
  `isActive()` omite todo el trabajo de captura, renderizado y reflexión cuando
  el tracing está deshabilitado.
- **La ruta inactiva del agente de bytecode no asigna nada.** `agent_OFF` mide
  **56 B/op** — lo mismo que una llamada directa — y ~37–44 ns por operación, lo
  que incluye el cambio de contexto que el benchmark realiza dentro de su propia
  medición. Un método instrumentado lee un `isActive()` estático antes de
  preparar nada: con el tracing apagado no se hace boxing de ningún argumento,
  no se construye ningún array, no se realiza ninguna llamada.
- **Tracing activo:** una llamada trazada por proxy con captura de parámetros y
  renderizado de valores mide **0,6–1,7 µs** y asigna **~1,0–1,5 kB/op**, según
  las anotaciones. Capturar una traza de un solo nodo y reiniciar el contexto
  cuesta 1,3–2,3 µs y 2,8–3,1 kB.

Estas cifras provienen de [benchmarks JMH](narrativetrace-benchmarks/) ejecutados
en un contenedor compartido, donde las de asignación se reproducen byte a byte y
las de nanosegundos se mueven con la carga de la máquina — por eso ambas se
guardan como *techos* en [`baseline.txt`](narrativetrace-benchmarks/baseline.txt)
y
[`allocation-baseline.txt`](narrativetrace-benchmarks/allocation-baseline.txt), y
las regresiones siguen siendo visibles entre commits. Son más altas que las que
llevaba este README hasta el 2026-08-31, medidas sobre un pipeline que hacía
menos: ahora cada entrada y cada salida publican un evento a través de un búfer
en anillo hacia el almacén retenido, y cada span lleva identidad de traza W3C. El
rendimiento es una preocupación continua, no un problema resuelto. Para bucles
extremadamente calientes, usa `TracingLevel.OFF` o reduce el alcance trazado.

## Qué es gratuito y qué es Pro

**Gratuito** es todo lo que hay en este repositorio — de código disponible bajo
BSL 1.1, gratuito en producción, y que pasa a Apache 2.0 cuatro años después de
cada publicación: el runtime completo, las trazas por prueba en todos los
formatos, el artefacto estructural `.nt` con el informe de deltas y el modo de
aprobación, la puntuación de claridad, el glosario de dominio y las vistas
traducidas de trazas, y todas las integraciones de la tabla de arriba. El jar de
contrato `narrativetrace-api` es Apache 2.0 sin más.

**Pro** es la inteligencia *entre* ejecuciones y repositorios: resúmenes de
flujos agregados y grafos de dependencias en tiempo de ejecución, diffs de
migración y semánticos, diagramas de secuencia agregados, bots de revisión en
los PR y analítica de deriva, tendencia histórica de claridad, herramientas y
servidor MCP para agentes de IA, y la suite de auditoría y cumplimiento
(`@AuditEvent`, motor de políticas, enmascaramiento de campos, trazabilidad de
controles). No todo está publicado hoy. La [Guía de
funcionalidades](documentation/es/guia-de-funcionalidades.md) es la tabla de
estado autoritativa: etiqueta cada funcionalidad como Open, Gratis, Pro, En
desarrollo o Planificada, y cita el código detrás de cada fila publicada.

## Documentación

**[documentation/](documentation/LEAME.md)** indexa todos los documentos de este
repositorio, tanto las guías en inglés como sus traducciones al español y al chino.
La documentación completa publicada — incluidas páginas sin equivalente aquí — está
en **[narrativetrace.ai/docs](https://narrativetrace.ai/docs.html)** (en inglés).
Para agentes de IA: [`documentation/llms.txt`](documentation/llms.txt) es el
índice legible por máquina, [`documentation/llms-full.md`](documentation/llms-full.md) la
referencia completa en un solo archivo, y cada módulo publicado incluye un jar de fuentes
con Javadoc rico ([javadoc.io](https://javadoc.io/doc/ai.narrativetrace)).

Empieza por aquí:

- [First 10 Minutes](documentation/first-10-minutes.md) (en inglés) — un servicio diminuto, una prueba JUnit, ocho pasos hasta una traza real, con salida real
- [Guía de instalación](documentation/es/guia-de-instalacion.md) — dependencias, todas las vías de integración, cómo funciona la captura, configuración de la salida de trazas
- [Choosing an Integration](documentation/choosing-an-integration.md) (en inglés) — qué módulo necesitas, como diagrama de decisión
- [Guía de configuración](documentation/es/guia-de-configuracion.md) — niveles de tracing, configuración de JUnit/Gradle/Spring/Micronaut/SLF4J
- [Guía del plugin de Gradle](documentation/es/guia-del-plugin-de-gradle.md) — referencia del DSL, puertas de calidad, recetas
- [Guía de anotaciones](documentation/es/guia-de-anotaciones.md) — `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`

Para profundizar:

- [Guía del ciclo de vida](documentation/es/guia-del-ciclo-de-vida.md) — dónde vive NarrativeTrace en tu proceso: desarrollo, CI/aceptación, producción
- [Privacy and Redaction](documentation/privacy-and-redaction.md) (en inglés) — el contrato de ocultación fila por fila, verificado contra el código
- [What to Commit](documentation/what-to-commit.md) (en inglés) — qué archivos generados son artefactos de CI y cuáles son líneas base revisadas
- [Troubleshooting](documentation/troubleshooting.md) (en inglés) — síntoma → causa → arreglo para los fallos más comunes
- [Guía de integración con Spring](documentation/es/guia-de-integracion-con-spring.md) — tracing de beans, filtro de servlet, propagación de `@Async`
- [Guía de integración con Micronaut](documentation/es/guia-de-integracion-con-micronaut.md) — tracing de beans, filtro HTTP, propiedades de configuración
- [Guía de claridad](documentation/es/guia-de-claridad.md) — modelo de puntuación, componentes NLP, integración con JUnit
- [Guía de funcionalidades](documentation/es/guia-de-funcionalidades.md) — catálogo canónico de cada funcionalidad en todas las plataformas, con nivel y estado
- [Formato de traza estructural](documentation/es/formato-de-traza-estructural.md) — el artefacto `.nt` libre de valores detrás del informe de deltas y las pruebas de aprobación
- [Referencia completa](documentation/llms-full.md) — API, interioridades de la captura, configuración, recetas de integración y resolución de problemas, en un solo archivo (en inglés)
- [Glosario de dominio](https://narrativetrace.ai/doc.html?p=docs/glossary.md) — glosario de lenguaje ubicuo recolectado desde las trazas, y vistas traducidas de las trazas (en inglés)

## Compilar desde el código fuente

```bash
./gradlew test                                     # ejecuta todas las pruebas
./gradlew check                                    # pruebas + PMD + cobertura JaCoCo + las demás puertas
./gradlew :narrativetrace-examples:runExamples     # todos los ejemplos en secuencia
./gradlew :narrativetrace-examples:traceExamples   # ejecuta pruebas → archivos Markdown de trazas
./gradlew :narrativetrace-examples:ejb4:dockerTest # WAR EJB 4 en WildFly trazado solo por el agente (requiere Docker)
```

## Licencia

La API y el formato de salida de NarrativeTrace son estándares abiertos
(Apache 2.0). Su runtime es gratuito y de código disponible (BSL 1.1, que
pasa a Apache 2.0 cuatro años después de cada publicación). Pro es
comercial.

| Artefacto | Licencia | Qué significa |
|---|---|---|
| `narrativetrace-api` | [Apache 2.0](LICENSE-APACHE) | Las anotaciones, el modelo de eventos y las SPI: todo aquello contra lo que compila tu código. Un estándar abierto, para que cualquier implementación pueda adoptarlo. |
| el resto de artefactos `ai.narrativetrace` | [BSL 1.1](LICENSE) | El runtime. Gratuito en producción, incluidos los productos y servicios que ofreces a tus propios clientes. La única exclusión es ofrecer NarrativeTrace en sí —o un producto o servicio cuyo valor derive sustancialmente de él— a terceros como producto o servicio de logging, tracing o narrativa de código. Cada versión publicada pasa a Apache 2.0 cuatro años después de publicarse. |
| la documentación (guías en prosa) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | Las guías y la documentación de referencia. La especificación del formato de salida y los esquemas JSON son Apache 2.0: son el estándar abierto. |

La categoría de cada módulo se declara en
[`licensing.properties`](licensing.properties), y la compilación rechaza un
grafo de dependencias que las licencias no puedan sostener.

El runtime **no** es código abierto, y este README no lo llamará así. Es
gratuito y de código disponible, con una promesa fechada de volverse abierto.

<!-- legal:trademark:begin -->
Ninguna de las licencias otorga derecho de marca alguno: NarrativeTrace es una
marca de Empower Agile, y el permiso para usar, copiar o modificar el código no
es permiso para usar el nombre en tu propia distribución o servicio.
<!-- legal:trademark:end -->

### La licencia, en palabras sencillas

El jar `narrativetrace-api`, la especificación del formato de salida y los
esquemas JSON son Apache 2.0 — código abierto sin reservas, sin más
restricciones que las de la propia Apache.

<!-- legal:plain-words:begin -->
**Gratis para ejecutar.** El runtime es de código disponible bajo la Business
Source License 1.1: puedes leerlo, auditarlo, modificarlo y usarlo en producción
sin coste — incluso dentro de los productos y servicios que vendes a tus propios
clientes.

**Una sola exclusión.** No puedes ofrecer NarrativeTrace en sí —o un producto o
servicio cuyo valor derive sustancialmente de él— a terceros como producto o
servicio de logging, tracing o narrativa de código.

**Se abre en una fecha.** Cada versión se convierte a Apache 2.0 cuatro años
después de publicarse; la fecha exacta se imprime en el LICENSE de esa versión.

*Este resumen es una cortesía, no una licencia. El archivo LICENSE es el único
texto vinculante; donde ambos difieran, prevalece el LICENSE.*
<!-- legal:plain-words:end -->
