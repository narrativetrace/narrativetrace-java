<!-- source: documentation/installation-guide.md blob 5722abf953f4 | translated: 2026-10-24 | reviewed: - -->
# Guía de instalación de NarrativeTrace Java

[English](../installation-guide.md) | **Español** | [简体中文](../zh-CN/安装指南.md)

Esta guía cubre la instalación y el cableado de NarrativeTrace Java en un proyecto JVM.

## Requisitos previos

- Java 17+
- Build con Gradle

## Compatibilidad

Estas son las versiones con las que se compila y prueba esta versión.
«Incluida» significa que el módulo depende de ella
y Gradle la resuelve por ti; «tuya» significa que el módulo compila contra ella
pero no depende de ella — ya la tienes, y NarrativeTrace usa la versión que
aportes.

| Requisito | Versión | Quién la aporta |
|---|---|---|
| Java | **17+** — compilado y probado en 17; JDK 21 se ejercita en un job de CI programado (los tests de hilos virtuales solo corren ahí) | tuya |
| Gradle (para el plugin) | **8.0+** — desarrollado y probado contra 8.14.2 | tuya |
| JUnit 5 | 5.11.4 (`narrativetrace-junit5` expone `junit-jupiter-api` como `api`) | incluida |
| JUnit 4 | 4.13.2 | incluida |
| TestNG | 7.10.2 | incluida |
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

El plugin de Gradle se encarga de todo el cableado automáticamente — dependencias, flags del compilador y configuración de la JVM de pruebas.

Dale a Maven Central la primera prioridad en `pluginManagement` — el plugin y su marcador se
publican en Maven Central con cada versión, mientras que la inclusión en el Gradle Plugin Portal
espera la aprobación de Gradle para un id de plugin nuevo, así que Central es lo que hace
resoluble una versión recién publicada el mismo día en que sale:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
plugins {
    id("ai.narrativetrace") version "0.5.5"
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
    implementation("ai.narrativetrace:narrativetrace-core:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.5.5")

    // Integraciones opcionales
    testImplementation("ai.narrativetrace:narrativetrace-junit5:0.5.5")
    testImplementation("ai.narrativetrace:narrativetrace-junit4:0.5.5")  // para JUnit 4
    testImplementation("ai.narrativetrace:narrativetrace-testng:0.5.5")  // para TestNG
    implementation("ai.narrativetrace:narrativetrace-spring:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-slf4j:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-diagrams:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-clarity:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-opentelemetry:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-micrometer:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-agent:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-servlet:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-spring-web:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-micronaut:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.5.5")
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

### Opción D2: contexto automático de TestNG + salida de trazas

Con el plugin de Gradle, `narrativeTrace { testFramework.set("testng") }` es toda la configuración: añade
`narrativetrace-testng` en `testImplementation`, cambia la tarea `test` a `useTestNG()` y pasa las propiedades
de sistema `narrativetrace.*`; el listener se engancha solo. Declara `org.testng:testng` tú mismo, como harías
con JUnit 4 (la dependencia `api` del módulo trae TestNG 7.10.2 si no lo haces). Sin el plugin:

Añade `narrativetrace-testng` en `testImplementation` y nada más: el service loader de TestNG
registra su listener (`NarrativeTraceListener`) en cada ejecución — `useTestNG()` de Gradle, Maven
surefire, el IDE. `@Listeners(NarrativeTraceListener.class)` en una clase de prueba también lo
registra explícitamente; con ambos a la vez, cada prueba se traza una sola vez.

```java
public class OrderServiceTest {
    private final OrderService orderService = NarrativeTraceProxy.trace(
        new DefaultOrderService(), OrderService.class, NarrativeTraceListener.context());

    @Test
    public void customerPlacesOrder() {
        orderService.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

Qué se escribe — los mismos archivos que con las integraciones de JUnit:
- Una traza por método de prueba en `build/narrativetrace/traces/<TestClass>/`: `.md`, `.json`, un diagrama de secuencia y el `.nt` estructural
- Una prueba que falla imprime su informe de fallo y, en la salida de error estándar, `narrativetrace: trace of <Class.method> → <ruta absoluta>`
- Al terminar la ejecución: `manifest.json`, `INDEX.md` (las pruebas que fallan primero), `clarity-report.md` y el pie de consola, que repite los punteros de las pruebas que fallan
- Una fila de un data provider, una repetición de `invocationCount` o una instancia de `@Factory` es una invocación con sus propios archivos — `<method>-001.md`, etiquetada `Class.method[#1]`
- Las trazas de aprobación (`narrativetrace.approval=true`) comparan cada prueba con su `.approved.nt` versionado, como en JUnit

La configuración usa las mismas propiedades del sistema que JUnit 4 (`narrativetrace.output`, `narrativetrace.outputDir`,
`narrativetrace.format`, `narrativetrace.level`, `narrativetrace.approval`, `narrativetrace.approvedDir`).

Límites:
- `NarrativeTraceListener.context()` es la forma en que una prueba obtiene el contexto — TestNG no puede inyectarlo como parámetro del método de prueba
- Los archivos de la ejecución se escriben cuando termina la ejecución de TestNG; TestNG no tiene un final por clase, así que no se escribe nada por clase
- Un solo pipeline sirve a toda la JVM de pruebas; cada prueba ve solo las llamadas hechas en su propio hilo mientras se ejecuta, incluido `parallel="methods"`
- Una prueba con `timeOut` ejecuta su cuerpo en un hilo aparte de TestNG, así que sus llamadas no están en su traza
- Una fila de un data provider se etiqueta por su número, nunca por sus argumentos: el renderizado nunca ejecuta el `toString` de tus objetos
- Sin cosecha del glosario (`narrativetrace.glossary`) — ese paso pertenece a la extensión de JUnit 5

### Opción E: agente Java (sin cableado de proxies)

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.* -jar your-app.jar
```

Usa esta opción cuando quieras instrumentación de bytecode para las clases bajo los prefijos de paquete seleccionados. Cuando no se proporcionan argumentos de CLI, el agente recurre a `narrativetrace.properties` en el classpath. Adjunto a una JVM de pruebas que también lleve la extensión de `narrativetrace-junit5` o las reglas de `narrativetrace-junit4`, las capturas del agente aterrizan en los mismos archivos por prueba que produce una prueba trazada por proxy — sin proxy ni anotación en la clase instrumentada (véase [Guía de configuración §3](guia-de-configuracion.md#3-configuración-para-java-puro--agente-narrativetraceproperties)).

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
silencio. **Sin un proveedor SLF4J en el classpath, el agente también está en
silencio** —— sigue capturando, pero la narración va a un logger NOP.

#### Las clases que no ven el runtime del agente se cargan sin instrumentar

Coincidir con el filtro de paquetes es necesario, pero no suficiente. El bytecode
instrumentado llama a `ai.narrativetrace.agent.AgentRuntime` por su nombre, así
que una clase se teje solo cuando el cargador de clases que la definió puede
resolver ese tipo. Dos clases de clase no pueden:

- **clases inyectadas en el bootstrap** —— el mock maker inline de mockito, por
  ejemplo, inyecta su despachador de métodos en el cargador de clases bootstrap
- **cargadores aislados** que no delegan al cargador por el que llegó el agente

Esas se cargan sin instrumentar, y el agente lo dice una vez por cargador:

```text
narrativetrace-agent: cannot instrument org/example/Injected (ai.narrativetrace.agent.AgentRuntime is not visible from the bootstrap class loader); classes from that loader load uninstrumented
```

Una vez por cargador, nunca una vez por clase: un cargador caliente no debe
inundar tu flujo de errores. Todo lo demás en la misma JVM se sigue trazando con
normalidad; si las clases que te importan están entre las no instrumentadas,
estrecha `packages=` para que el filtro deje de coincidir con ellas, o trázalas
con el proxy.

#### Las clases generadas o creadas por el compilador nunca se instrumentan

Algunas clases bajo un paquete coincidente nunca son un límite de negocio —
son bytecode que generó un generador o el compilador, no código que tú
escribiste. Se excluyen de forma incondicional, sin ningún interruptor para
reactivarlas:

- un proxy dinámico JDK (`$Proxy0`, `$Proxy17`, …)
- una clase cuyo nombre lleva la marca propia de un generador: `$$` (el
  `$$EnhancerByCGLIB$$` de CGLIB, el `$$SpringCGLIB$$` de Spring, la propia
  clase oculta de una lambda), `$ByteBuddy$` (el nombrado por defecto de Byte
  Buddy), o `$MockitoMock$` (el mock maker inline de Mockito)
- una clase oculta, por su sufijo `/0x…` asignado por la JVM
- un método puente o un método sintético — una duplicación generada por el
  compilador de un método real, o un accesor que nadie escribió

Esto es silencioso — sin línea en `stderr` — porque es el caso esperado y
estructural; la pertenencia nunca depende del nombre de paquete de un
proveedor, así que añadir una nueva librería de mocking o de proxies a tus
dependencias no necesita ningún cambio de configuración aquí.

#### Excluir paquetes: `exclude=<pkg1>;<pkg2>`

Los internos de librerías y frameworks — nunca tu propio límite de negocio,
pero nombrados por paquete en vez de por forma — se excluyen junto a
`packages=`:

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.*,exclude=org.mockito -jar your-app.jar
```

`exclude=` siempre gana: una clase que coincide tanto con `packages=` como
con `exclude=` no se instrumenta, incluso cuando el paquete excluido está
dentro de uno coincidente (`packages=com.example`,
`exclude=com.example.generated` — una clase bajo `com.example.generated` no
se instrumenta; todo lo demás bajo `com.example` sigue instrumentándose).
Lista predeterminada incluida, aplicada siempre que `exclude=` (y
`narrativetrace.exclude` en la ruta de respaldo por propiedades) esté
completamente ausente:

| Prefijo | Motivo |
|---|---|
| `org.mockito` | El despachador de la librería de mocking y las subclases de mock generadas |
| `net.bytebuddy` | El generador de código bajo Mockito, Hibernate y muchas herramientas de proxy |
| `org.junit` | Los propios ayudantes del test runner |
| `org.testng` | Los ayudantes del otro test runner habitual de la JVM |
| `net.sf.cglib` | Los propios internos del CGLIB clásico |
| `org.springframework.cglib` | El CGLIB reempaquetado por Spring |
| `org.aspectj` | El runtime de otro weaver |
| `datadog` | El runtime de otro agente APM — evita la instrumentación recursiva cuando se despliega junto a él |
| `io.opentelemetry.javaagent` | El otro agente APM con más probabilidad de desplegarse junto a este |

La presencia de la clave decide: la ausencia de `exclude=` significa que se
aplica esta lista predeterminada; la presencia de `exclude=` — incluso sin
nada detrás — expresa la lista completa de forma explícita, reemplazando la
predeterminada (vacía desactiva la lista 2 por completo). Para extender la
lista predeterminada en vez de
reemplazarla, copia estos nueve prefijos en tu propio valor de `exclude=` y
añade los tuyos junto a ellos. Sensible a los límites de delimitador igual
que `packages=` (`org.acme` nunca coincide con `org.acmeExtra`); una clave
`exclude=` duplicada se rechaza igual que una clave `packages=` duplicada.
Una clase coincidente y excluida se informa una vez por prefijo excluido,
nunca una vez por clase:

```text
narrativetrace-agent: cannot instrument org/mockito/internal/Foo (excluded package org/mockito/); classes under that package load uninstrumented
```

#### Trazas profundas (opt-in)

Las trazas profundas requieren el agente de Java (`mode = "agent"` en el plugin, o la opción `-javaagent`): la ruta de proxy no puede ver los métodos privados ni las llamadas de un objeto a sí mismo, así que con `mode = "proxy"` el ajuste no tiene efecto.

Por defecto el agente registra la frontera observable de cada clase. **No**
instrumenta métodos privados, constructores, inicializadores de clase, métodos
abstractos ni métodos puente/sintéticos (lo que incluye el cuerpo de cada lambda);
los métodos de paquete, protegidos, públicos y estáticos sí se instrumentan, y la
llamada de un método instrumentado a otro sobre `this` ya queda registrada, porque
el tejido está en el cuerpo de los métodos. Lo que una traza por defecto no puede
mostrar es un helper privado y el valor equivocado que devuelve.

Las trazas profundas registran también esos métodos privados, para las clases que
`packages=` ya selecciona (nunca una clase del JDK o de una biblioteca, y `exclude=`
sigue ganando). Es un ajuste verboso, solo para pruebas, y **nunca un ajuste de
producción**: está apagado por defecto en toda integración, y solo se alcanza con dos
opt-ins explícitos que deben darse ambos.

1. Arranca el agente de la JVM de pruebas con `depth=internal` (por defecto `public`;
   la alternativa por propiedad, que se usa cuando el agente no recibe argumentos, es
   `narrativetrace.agent.depth`). Esto teje los métodos privados cuando se carga su
   clase; por sí solo no registra nada. `maxInternalSpans=` acota los spans profundos
   de una prueba (por defecto 5000); más allá se descartan, se cuentan y se nombran en
   la cabecera del archivo de traza, y todas las llamadas públicas se conservan.
2. Pídelo por prueba con `@DeepTrace` (en una prueba, una clase o una clase envolvente),
   o para toda una ejecución con `narrativetrace.depth=internal`. Las integraciones de
   JUnit 5, JUnit 4 y TestNG abren el alcance profundo del agente exactamente para esa prueba.
   Una petición sin la opción del agente se ejecuta con la profundidad por defecto y lo
   dice una sola vez por la salida de error estándar.

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.*,depth=internal -jar your-app-tests.jar
```

```java
@Test
@DeepTrace
void pricesAreCalculated() { ... }
```

Con el plugin de Gradle en modo `agent`, la opción del agente es una línea del DSL
(`maxInternalSpans.set(...)` es opcional):

```kotlin
narrativeTrace {
    mode.set("agent")
    agent {
        depth.set("internal")   // añade depth=internal al argumento -javaagent
    }
}
```

Una llamada interna se anida bajo la llamada pública que la hizo, como cualquier otra,
y se marca con un `·` inicial en los renderizados de texto, Markdown y estructural
(`.nt`); la forma canónica y la de transporte no llevan la marca.

```text
#1 - Pricing.total() → value
  #1.1 - · Pricing.withTax(net) → value
```

Las líneas base de aprobación siguen el mismo opt-in. Un `*.approved.nt` cortado con
las trazas profundas activas declara `depth: internal` bajo su línea `scenario:` (sin
línea significa `public`, así que toda línea base anterior sigue siendo válida). Una
línea base y una ejecución de profundidades distintas nunca se comparan línea a línea
ni pasan en silencio: la ejecución falla con `Depth mismatch`, nombrando ambas
profundidades, y escribe su propio `*.received.nt` para revisión. Aprobar ese archivo
vuelve a fijar la línea base en la nueva profundidad.

Límites: los métodos privados se registran en el hilo propio de la prueba; un método
privado que corre en un hilo de trabajo que la prueba no inició no se registra. Los
cuerpos de lambda siguen fuera.


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

```text
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
| `narrativetrace-api` | Nunca — llega de forma transitiva con `narrativetrace-core`; depende de él directamente solo cuando publiques una librería que implemente una SPI sin necesitar el runtime |
| `narrativetrace-core` | Siempre requerido |
| `narrativetrace-proxy` | Tracing basado en interfaces mediante proxies JDK |
| `narrativetrace-junit5` | Extensión de JUnit 5 y emisión de archivos de trazas |
| `narrativetrace-junit4` | Regla y regla de clase de JUnit 4 para la salida de trazas |
| `narrativetrace-testng` | Listener de TestNG para la salida de trazas, registrado por el service loader de TestNG |
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
