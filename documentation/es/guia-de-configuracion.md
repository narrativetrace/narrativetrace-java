<!-- source: documentation/configuration-guide.md blob eda4f553fb10 | translated: 2026-09-07 | reviewed: - -->
# Guía de configuración de NarrativeTrace Java

[English](../configuration-guide.md) | **Español** | [简体中文](../zh-CN/配置指南.md)

Esta guía documenta la configuración de runtime y de pruebas de NarrativeTrace Java.
Para saber qué configuración corresponde a cada etapa de tu proceso —
desarrollo, CI/aceptación, producción — consulta la
[Guía del ciclo de vida](guia-del-ciclo-de-vida.md).

## Superficie de configuración

NarrativeTrace ofrece seis rutas de configuración:

| Ruta | Mecanismo | Ideal para |
|---|---|---|
| Plugin de Gradle | DSL `narrativeTrace { }` en `build.gradle.kts` | Proyectos Gradle (recomendado) |
| JUnit 5 | `junit-platform.properties` | Salida de trazas en tiempo de pruebas |
| Java puro / Agente | `narrativetrace.properties` en el classpath | Apps independientes, agente |
| Gradle (manual) | `gradle.properties` + reenvío en el script de build | Proyectos Gradle sin el plugin |
| Spring | Anotación `@EnableNarrativeTrace` | Apps Spring |
| Micronaut | `application.yml` vía `@ConfigurationProperties` | Apps Micronaut |

Todas las rutas admiten overrides mediante propiedades del sistema (flags `-D`) como la fuente de mayor prioridad.

## DSL del plugin de Gradle

El plugin de Gradle (`ai.narrativetrace`) lo configura todo automáticamente. Aplícalo y personalízalo si lo necesitas:

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.0"
}

// La configuración cero funciona — valores predeterminados sensatos para todo:
narrativeTrace { }

// Superficie completa:
narrativeTrace {
    enabled.set(true)                          // predeterminado: true
    mode.set("proxy")                          // "proxy" (predeterminado) | "agent" | "spring"
    testFramework.set("junit5")               // "junit5" (predeterminado) | "junit4"
    scope.set("test")                          // "test" (predeterminado) | "production"
    format.set("markdown")                     // "markdown" | "text" | "mermaid" | "plantuml"
    tracingLevel.set("DETAIL")                 // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
    outputDir.set(layout.buildDirectory.dir("narrativetrace"))
    glossary.set(false)                        // predeterminado: false — cosecha glossary.json al final de la suite
    approval.set(false)                        // predeterminado: false — verifica la estructura contra líneas base confirmadas
    approvedDir.set(layout.projectDirectory.dir("src/test/narratives"))

    modules {                                  // activación granular (todo con false predeterminado)
        slf4j.set(false)
        micrometer.set(false)
        servlet.set(false)
        springWeb.set(false)                   // implica servlet
    }

    agent {                                    // solo relevante cuando mode = "agent"
        packages.set(listOf("com.example.app"))
    }

    clarity {
        minScore.set(0.80)                     // predeterminado: 0.0 (sin gate)
        maxHighIssues.set(0)                   // predeterminado: Integer.MAX_VALUE (sin gate)
        maxSuiteIssues.set(0)                  // predeterminado: Integer.MAX_VALUE (solo advertencias)
        warnOnly.set(false)                    // predeterminado: false
    }
}
```

### Qué hace el plugin

| Acción | Detalle |
|---|---|
| Añade el flag del compilador `-parameters` | En todas las tareas `JavaCompile`; se omite si ya está presente |
| Añade dependencias | Según `mode`, `modules` y `testFramework`; la versión se autodetecta desde el JAR del plugin |
| Establece propiedades JVM de pruebas | `narrativetrace.output=true`, `narrativetrace.outputDir` y, opcionalmente, `narrativetrace.format`, `narrativetrace.level`, el par de glosario (`glossary=true`) y el par de aprobación (`approval=true`) |
| Registra la tarea `clarityCheck` | Lee `clarity-results.json`, aplica los umbrales, integrada en el ciclo de vida `check` |
| Registra la tarea `clarityScan` | Análisis de claridad independiente a partir de clases compiladas (no requiere tests) |
| Registra la tarea `glossaryScan` | Cosecha independiente del glosario a partir de clases compiladas, incluidas las plantillas de anotaciones |
| Registra la tarea `approveNarratives` | Promueve las narrativas `*.received.nt` revisadas a líneas base `*.approved.nt` (modo de aprobación) |
| Configura el argumento JVM del agente | Cuando `mode = "agent"`: resuelve el JAR del agente y añade `-javaagent` a las tareas Test |

### Modos de interceptación

| Modo | Dependencias añadidas (además de core + clarity + diagrams + framework de pruebas) |
|---|---|
| `proxy` (predeterminado) | `narrativetrace-proxy` |
| `agent` | `narrativetrace-agent` (configuración separada para la resolución del JAR) |
| `spring` | `narrativetrace-spring` + `narrativetrace-proxy` |

### Activación de módulos

| Flag | Artefacto | Notas |
|---|---|---|
| `modules.slf4j` | `narrativetrace-slf4j` | Puente SLF4J |
| `modules.micrometer` | `narrativetrace-micrometer` | Propagación entre hilos |
| `modules.servlet` | `narrativetrace-servlet` | Filtro de servlet |
| `modules.springWeb` | `narrativetrace-spring-web` + `narrativetrace-servlet` | Añade servlet automáticamente |

### Alcance de las dependencias

| Alcance | Dependencias de la librería | Dependencia del framework de pruebas |
|---|---|---|
| `test` (predeterminado) | `testImplementation` | `testImplementation` |
| `production` | `implementation` | `testImplementation` (siempre) |

### Desactivar el plugin

Para desactivar el plugin por completo (p. ej., en un subproyecto), establece `enabled.set(false)`. No se registran tareas, no se añaden dependencias y no se establecen flags del compilador.

```kotlin
narrativeTrace {
    enabled.set(false)
}
```

### Umbrales de claridad

La tarea `clarityCheck` lee `build/narrativetrace/clarity-results.json` (generado por la extensión de JUnit durante `test`) y aplica los umbrales configurados. Se ejecuta automáticamente como parte de `./gradlew check`.

- **`minScore`** — puntuación de claridad global mínima (0.0–1.0). Cualquier escenario por debajo de este umbral hace fallar la build.
- **`maxHighIssues`** — número máximo de issues de severidad HIGH por escenario. Superarlo hace fallar la build.
- **`warnOnly`** — cuando es `true`, las violaciones de umbral producen advertencias en lugar de fallos de build.

Si no existe `clarity-results.json` (p. ej., no se ejecutó ningún test), la tarea pasa silenciosamente.

### Resolución de versiones

El plugin autodetecta su versión desde el JAR del plugin (no hay propiedad DSL). Se usa la misma versión para todas las dependencias gestionadas. No existe una propiedad `manageDependencies` — si `enabled=true`, el plugin gestiona las dependencias según mode/modules/scope. Quienes quieren control manual total establecen `enabled.set(false)` y cablean todo por su cuenta.

Para la referencia completa del plugin, incluidas recetas, DSL en Groovy y detalles de validación, consulta la [Guía del plugin de Gradle](guia-del-plugin-de-gradle.md).

## 1. Niveles de tracing (`NarrativeTraceConfig`)

`ThreadLocalNarrativeContext` usa `NarrativeTraceConfig`, cuyo valor predeterminado es `DETAIL`.

```java
var config = new NarrativeTraceConfig(TracingLevel.NARRATIVE);
var context = new ThreadLocalNarrativeContext(config);
```

Niveles disponibles:

| Nivel | Comportamiento |
|---|---|
| `OFF` | No se captura ninguna traza |
| `ERRORS` | Solo se capturan las rutas con excepciones |
| `SUMMARY` | Captura la entrada raíz, la hoja más profunda y las cadenas de excepciones completas |
| `NARRATIVE` | Captura el flujo de llamadas completo, suprime los valores de parámetros; los valores de retorno se renderizan en todos los niveles activos (una promesa documentada — el retorno es la carga útil de una narrativa) |
| `DETAIL` | Captura el flujo de llamadas completo con valores de parámetros y valores de retorno |

Se admite cambiar el nivel en runtime:

```java
config.setLevel(TracingLevel.ERRORS);
```

## 2. Configuración de JUnit 5 (`junit-platform.properties`)

La extensión de JUnit usa `ExtensionContext.getConfigurationParameter()`, que resuelve los valores en este orden:

1. Propiedades del sistema (máxima prioridad — los flags `-D` de la CLI siguen funcionando)
2. `junit-platform.properties` en el classpath de pruebas
3. Valores predeterminados codificados (mínima prioridad)

### Propiedades

| Propiedad | Valores | Predeterminado |
|---|---|---|
| `narrativetrace.output` | `true` / `false` | `false` |
| `narrativetrace.outputDir` | Cualquier ruta con permiso de escritura | `build/narrativetrace` |
| `narrativetrace.format` | `markdown`, `text`, `mermaid`, `plantuml` | `markdown` |
| `narrativetrace.glossary` | `true` / `false` | `false` |
| `narrativetrace.glossaryDir` | Cualquier ruta con permiso de escritura | directorio de trabajo |
| `narrativetrace.canonicalJson` | `true` / `false` | `false` |
| `narrativetrace.structuralJson` | `true` / `false` | `false` |
| `narrativetrace.approval` | `true` / `false` | `false` |
| `narrativetrace.approvedDir` | Directorio de líneas base confirmadas | `src/test/narratives` |
| `narrativetrace.bufferCapacity` | Ranuras del anillo de eventos de un contexto de prueba | `8192` |

`narrativetrace.bufferCapacity` dimensiona el anillo de eventos del contexto
que la extensión construye **por método de prueba**. Su valor predeterminado
es de 8.192 ranuras en lugar de las 65.536 del runtime porque una prueba
traza decenas de llamadas, no decenas de miles, y el anillo se asigna de
forma anticipada en la construcción: el valor del runtime costaría 1,75 MB y
alrededor de 1,5 ms por método de prueba para ranuras que ninguna prueba
alcanza. Con 8.192 son 224 kB.

Es la grafía JUnit 5 de la misma perilla; la clave del runtime
`narrativetrace.buffer.capacity` (propiedad del sistema o entrada de
`narrativetrace.properties`) tiene prioridad, porque el ajuste de un
despliegue manda sobre el predeterminado de una integración. Nada en el
runtime detecta JUnit — la extensión pasa su elección de forma explícita, en
su propio código.

Una suite que sí supere los 8.192 eventos en una prueba descarta el exceso, y
lo dice: cada narrativa que escribe lleva una línea de pie con el recuento y
la propiedad que hay que subir. Sube esta para un arreglo solo de pruebas, o
la clave del runtime para cambiarlo en todas partes.

La recolección del glosario está desactivada de forma predeterminada porque
reescribe `glossary.json` y `glossary.md` **fuera** del directorio de build —
un artefacto commiteado y revisado, no un producto de la build.
`narrativetrace.glossaryDir` nombra el directorio que contiene esos dos
archivos; el plugin de Gradle lo establece en la raíz del repositorio, lo cual
importa en builds multi-módulo donde el directorio de trabajo de una tarea de
test es el subproyecto y el glosario es un archivo por repositorio.

`narrativetrace.glossaryDir` se lee incluso con la recolección desactivada:
un `glossary.json` commiteado es el vocabulario del proyecto con el que se
puntúa la claridad (véase la [Guía de claridad](guia-de-claridad.md)). Leer
no cambia nada en disco, así que no necesita opt-in; un repositorio sin ese
archivo se puntúa solo con los diccionarios integrados.

`narrativetrace.canonicalJson` escribe además un archivo
`<test>.canonical.json` (array de entradas del esquema 1.1) junto a cada
archivo de traza — un artefacto de máquina para consumidores del esquema
canónico, como las demás implementaciones de NarrativeTrace y los fixtures
de conformidad.

`narrativetrace.structuralJson` escribe además un
`<test>.structural.json` junto a cada archivo de traza: el mismo array
de entradas del esquema 1.1 con todos los campos de valores de tiempo
de ejecución elididos (los parámetros llevan `[ELIDED]`, los valores de
retorno y los mensajes de excepción son null) — el artefacto
estructural seguro para IA del Nivel 1 del ADR-002. Ambos flags son
independientes y pueden combinarse en una misma ejecución.

`narrativetrace.approval` activa el modo de aprobación: tras un test que
**pasa**, la estructura libre de valores del escenario (el mismo render
que el artefacto `.nt`) se verifica contra la línea base confirmada
`<approvedDir>/<TestClassSimpleName>/<test_method_slug>.approved.nt`.
Una línea base ausente o una diferencia estructural hace fallar el test
con un diff legible y escribe la estructura actual junto a la línea base
como `*.received.nt`; revísala y acéptala con la tarea de Gradle
`approveNarratives` (o renómbrala manualmente). Los tests que fallan
nunca se verifican — su estructura está a medio vuelo y no debe agitar
los archivos received. El plugin de Gradle establece ambas propiedades
desde su DSL `approval` / `approvedDir`.

### Configuración basada en archivo (recomendada)

Coloca un archivo en `src/test/resources/junit-platform.properties`:

```properties
narrativetrace.output=true
narrativetrace.format=markdown
```

No hace falta cablear `systemProperty()` en Gradle. El archivo es solo de pruebas y nunca toca producción.

### Overrides desde la CLI

Las propiedades del sistema siguen funcionando como overrides:

```bash
./gradlew test -Dnarrativetrace.output=true
./gradlew test -Dnarrativetrace.format=text
./gradlew test -Dnarrativetrace.outputDir=out/narrative
```

### Reenvío desde la CLI de Gradle (opcional)

Solo es necesario si quieres pasar flags `-D` de la CLI a través de Gradle hasta la JVM de pruebas (fork):

```kotlin
tasks.withType<Test> {
    System.getProperty("narrativetrace.output")?.let { systemProperty("narrativetrace.output", it) }
    System.getProperty("narrativetrace.outputDir")?.let { systemProperty("narrativetrace.outputDir", it) }
    System.getProperty("narrativetrace.format")?.let { systemProperty("narrativetrace.format", it) }
}
```

### Nombres de escenario

La extensión deriva de cada test un nombre de escenario legible:

- `customerPlacesOrder()` → "Customer places order"
- `customer_places_order()` → "Customer places order"
- `@DisplayName("customer places order")` → "customer places order" (se pasa tal cual)

Los sufijos de tipo de parámetro de JUnit (p. ej. `(NarrativeContext)`) se eliminan automáticamente.

### Estructura de archivos

Estructura base:

- `<outputDir>/traces/<TestClassSimpleName>/<test_method_slug>.<ext>`

Cuando `format=markdown`, la extensión también escribe por cada test:

- Diagrama Mermaid: `<outputDir>/diagrams/<TestClassSimpleName>/<test_method_slug>.mmd`
- Exportación JSON: `<outputDir>/traces/<TestClassSimpleName>/<test_method_slug>.json`
- Artefacto estructural: `<outputDir>/structural/<TestClassSimpleName>/<test_method_slug>.nt`
  — la estructura de llamadas libre de valores (especificación del formato:
  [structural-trace-format.md](../structural-trace-format.md)). El archivo
  en disco es la **línea base del último verde**: una ejecución verde la
  avanza, una ejecución fallida compara contra ella pero nunca la
  sobrescribe, de modo que cada delta se lee como "qué cambió desde la
  última vez que este escenario pasó"

Cuando terminan todos los tests de una clase, la extensión escribe:

- Informe de claridad: `<outputDir>/clarity-report.md`
- Resumen en consola (impreso en stdout), que termina con el delta
  estructural de una línea contra la última ejecución verde:
  ```
  NarrativeTrace — Suite complete
    2 scenarios recorded
    Clarity: 100% high | 0% moderate | 0% low
    Reports: build/narrativetrace
    Since last green: 1 scenario unchanged · 1 changed: "Customer places order" (+1 call InventoryService.release)
  ```

El informe en consola de un test que falla imprime el delta estructural
contra el último artefacto verde — resumen más diff legible — en lugar de
la traza completa, y enlaza el archivo de traza como un URI `file://`
clicable.

## 3. Configuración para Java puro / agente (`narrativetrace.properties`)

Para apps Java independientes y el agente de bytecode, `ConfigResolver` carga la configuración desde el classpath.

Orden de resolución:

1. Propiedades del sistema (máxima prioridad)
2. `narrativetrace.properties` en el classpath
3. Valores predeterminados codificados (mínima prioridad)

### Propiedades

| Propiedad | Valores | Predeterminado |
|---|---|---|
| `narrativetrace.level` | `OFF`, `ERRORS`, `SUMMARY`, `NARRATIVE`, `DETAIL` | `DETAIL` |
| `narrativetrace.packages` | Prefijos de paquete separados por punto y coma | (vacío) |
| `narrativetrace.loggerName` | Nombre del logger SLF4J | `narrativetrace` |
| `narrativetrace.loggingJars` | Jars o directorios separados por punto y coma | (vacío) |
| `narrativetrace.capture.resource` | `true` / `false` | `true` |
| `narrativetrace.capture.sourceLocation` | `true` / `false` | `false` |
| `narrativetrace.capture.instanceIds` | `true` / `false` | `false` |
| `narrativetrace.narration` | `off` para suprimir el listener SLF4J | (activado) |
| `narrativetrace.pipeline` | Nombre de una topología de pipeline registrada | (ruta dual) |
| `narrativetrace.pipeline.<nombre>.*` | Ajustes de esa topología | (según topología) |
| `narrativetrace.buffer.capacity` | Ranuras del anillo de la topología por defecto, redondeadas a la siguiente potencia de dos | `65536` |
| `narrativetrace.discovery` | `off` para desactivar el descubrimiento de extensiones | (activado) |
| `narrativetrace.discovery.disabled` | Nombres de clases proveedoras separados por comas | (vacío) |

### Flags de captura

Los flags `narrativetrace.capture.*` amplían qué identidad se captura
por evento. Controlan **solo la captura, nunca la forma del esquema**:
cada campo condicionado sigue siendo anulable en el esquema canónico y
simplemente está ausente cuando el flag está apagado, de modo que los
consumidores y los fixtures multiplataforma nunca ramifican según la
configuración.

- **`narrativetrace.capture.resource`** (activado por defecto) —
  estampa la identidad de proceso autodetectada en cada span:
  `host.name` (de `HOSTNAME`/`COMPUTERNAME`, con lookup inverso como
  respaldo), `process.pid` y `process.runtime.version`. La detección se
  ejecuta una vez por proceso. Los despliegues sensibles al hostname
  pueden desactivarlo. Estos campos nunca se reemiten sobre spans de
  OpenTelemetry — los detectores de recursos del SDK de OTel son dueños
  de esa vía; esto cubre las salidas propias de la biblioteca (JSON
  canónico, MDC).
- **`narrativetrace.capture.sourceLocation`** (desactivado por
  defecto) — registra `code.filepath`/`code.lineno` en las entradas de
  entrada. Las dos vías de captura son deliberadamente asimétricas: el
  agente incrusta el archivo fuente y la primera línea del propio
  método instrumentado en tiempo de instrumentación (gratis), mientras
  que el proxy paga un recorrido de pila por llamada y registra el
  frame del *llamador* (las interfaces con proxy no llevan información
  de líneas) — por eso el flag está desactivado por defecto.
- **`narrativetrace.capture.instanceIds`** (desactivado por defecto) —
  registra el hash de identidad del objeto receptor (hex en minúsculas)
  como `nt.instanceId` en las entradas de entrada; útil para distinguir
  instancias de una misma clase.

La colección automática se mantiene con forma de identidad por diseño.
El contexto con forma de contenido (totales de pedidos, feature flags,
estado de negocio) nunca entra en la captura automática — el
enriquecimiento de atributos/MDC de tres niveles es el canal del
cliente para contexto imprevisto, y fluye por la redacción y la elisión
como cualquier otro valor.

`narrativetrace.loggingJars` da soporte al attach autónomo del agente en
hosts cuyos class loaders no ven un proveedor SLF4J (servidores de
aplicaciones): cada jar listado — los directorios se expanden a los jars
que contienen — se añade a la búsqueda del class loader del sistema
antes de iniciar el trazado. Una ruta inexistente falla de inmediato en
el momento del attach. También disponible como argumento del agente
`loggingJars=`.

### Dimensionar el buffer de eventos

La ruta de mejor esfuerzo retiene los eventos en un **buffer en anillo de
tamaño fijo**. Nunca crece: la capacidad se elige una sola vez, el anillo
completo se reserva en la construcción y un productor que adelanta al
drenaje sobrescribe la ranura más antigua en lugar de expandirse. No hay
capacidad inicial, ni factor de crecimiento, ni redimensionado — lo que un
proceso trazado gasta en retención se decide al arrancar y queda decidido.

`narrativetrace.buffer.capacity` fija ese tamaño para la topología de ruta
dual por defecto, redondeado a la siguiente potencia de dos (el anillo
enmascara en lugar de dividir). El valor por defecto es de **65.536
ranuras**. Un valor que el anillo no puede honrar — no numérico, cero,
negativo o por encima de 2^30 — cae de vuelta al valor por defecto en vez
de impedir el arranque: un buffer mal dimensionado cuesta historial de
análisis, que esta ruta tiene permitido perder, mientras que un arranque
rechazado cuesta la aplicación.

**La regla de dimensionado:**

```
capacidad  ≈  pico de eventos/s  ×  peor pausa de drenaje tolerable
memoria en saturación  ≈  capacidad  ×  ~300 B/evento
```

Ejemplo trabajado. 1.000 peticiones/s × 50 llamadas trazadas por petición
× 2 eventos por llamada (entrada y salida) son 100.000 eventos/s.
Presupuesta una pausa de drenaje de 500 ms en el peor caso — una pausa
larga de GC, un hilo hambriento — y quedan 50.000 eventos pendientes en el
pico. Eso cabe en las 65.536 ranuras por defecto y cuesta unos 20 MB en
saturación. Con el doble de tráfico, o con una pausa mayor que estés
dispuesto a sobrevivir, lo subes deliberadamente:

```properties
narrativetrace.buffer.capacity=131072
```

Por encima del 70% de ocupación el buffer descarta en lugar de encolar, y
cada evento descartado se cuenta (`EventPipeline.droppedEventCount()`) — un
anillo demasiado pequeño para su tráfico aparece como un número, no como
silencio. Se cuentan los tres modos de pérdida: el anillo sobrescribiendo
una ranura que el consumidor no había alcanzado, el drenaje adaptativo
descartando un lote por encima del umbral de descarte, y un suscriptor que no
pudo seguir el ritmo.

El recuento no solo está disponible: se **anuncia**. Una captura que perdió
eventos lo lleva en el árbol (`TraceTree.loss()`), y todo formato renderizado
con ranura de pie de página imprime una línea — el recuento y la propiedad
que hay que subir:

```
⚠ Incomplete narrative: 1204 events shed under load (buffer full) — raise narrativetrace.buffer.capacity.
```

Texto, prosa y Markdown lo llevan como pie de página (Markdown como
blockquote, además de `incomplete: true` y `dropped_events:` en el
frontmatter del documento); Mermaid y PlantUML lo llevan como comentario del
diagrama, de modo que el dibujo en sí no cambia. El artefacto estructural
`.nt` deliberadamente no lo lleva: es la línea base de aprobación y el
formato de los fixtures de conformidad, y debe permanecer idéntico byte a
byte para un mismo comportamiento. Una captura que no perdió nada no imprime
nada.

**Por qué hay un hilo de drenaje, y cuándo lo hay.** El contexto por
defecto no arranca ninguno: construye su consumidor con
`startConsumer=false` y drena bajo demanda, de modo que `captureTrace()`
vacía el anillo antes de leerlo. Un consumidor que arrancas tú posee un
hilo por la cola — los últimos eventos publicados antes de que pare el
tráfico ya están en el anillo, y no viene nada nuevo que los saque de ahí.
Drenar solo como efecto secundario de publicar los dejaría varados hasta
una siguiente publicación que quizá no llegue nunca, así que el hilo se
aparca y vuelve a comprobar en vez de terminar, y sigue drenando hasta
vaciar el anillo. `close()` drena lo que quede, y por eso cerrar ese
consumidor es obligatorio — véase la
[guía del ciclo de vida](guia-del-ciclo-de-vida.md).

### Configuración basada en archivo

Coloca un archivo en el classpath (p. ej. `src/main/resources/narrativetrace.properties`):

```properties
narrativetrace.level=DETAIL
narrativetrace.packages=com.example.app.*;com.example.shared.*
narrativetrace.loggerName=myapp.traces
```

### Uso programático

```java
var resolver = new ConfigResolver();
var level = resolver.resolve("narrativetrace.level", "DETAIL");
```

### Detección de archivos duplicados

Si se encuentran varios archivos `narrativetrace.properties` en el classpath (p. ej., uno en el JAR de la app y otro en una dependencia), `ConfigResolver` lanza `DuplicateConfigurationException` listando todas las ubicaciones. Esto evita bugs de sombreado silencioso.

### Fallback del agente

Cuando el agente no recibe argumentos por CLI, recurre a `ConfigResolver`:

```bash
# Argumentos CLI explícitos (máxima prioridad)
java -javaagent:narrativetrace-agent.jar=packages=com.example.app -jar app.jar

# Con nombre de logger personalizado
java -javaagent:narrativetrace-agent.jar=packages=com.example.app,loggerName=myapp.traces -jar app.jar

# Recurre a narrativetrace.properties en el classpath
java -javaagent:narrativetrace-agent.jar -jar app.jar
```

## 4. Configuración de Gradle (`gradle.properties`)

Para proyectos Gradle, `gradle.properties` ofrece un único lugar donde definir la configuración de salida de pruebas de NarrativeTrace. Las propiedades definidas aquí quedan disponibles como propiedades del proyecto Gradle y pueden reenviarse a la JVM de pruebas (fork).

### Definir propiedades

Añade a `gradle.properties` en la raíz del proyecto:

```properties
narrativetrace.output=true
narrativetrace.format=markdown
```

### Reenviar a la JVM de pruebas

Las propiedades del proyecto Gradle no fluyen automáticamente hacia las JVM de pruebas (fork). Añade el reenvío en `build.gradle.kts`:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
    listOf("narrativetrace.output", "narrativetrace.outputDir", "narrativetrace.format")
        .forEach { key ->
            (findProperty(key) as? String)?.let { systemProperty(key, it) }
        }
}
```

Esto lee cada propiedad de `gradle.properties` (o de flags `-P` de la CLI) y la pasa como propiedad del sistema a la JVM de pruebas. Las propiedades del sistema tienen la máxima prioridad en la resolución de `getConfigurationParameter()` de JUnit.

### Overrides por CLI con `-P`

Las propiedades del proyecto Gradle pueden sobrescribirse desde la línea de comandos con `-P`:

```bash
./gradlew test -Pnarrativetrace.format=text
./gradlew test -Pnarrativetrace.output=false
```

### DSL específico de JUnit

Gradle también ofrece una vía específica de JUnit para pasar parámetros de configuración directamente:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform {
        configurationParameter("narrativetrace.output", "true")
        configurationParameter("narrativetrace.format", "markdown")
    }
}
```

Esto solo alimenta el `getConfigurationParameter()` de JUnit — no afecta a `ConfigResolver` ni al agente. Usa `gradle.properties` con reenvío cuando necesites una única fuente de configuración para todas las integraciones.

## 5. Configuración de Spring

Usa filtros de paquete para controlar qué beans se consideran para el envoltorio con proxy:

```java
@Configuration
@EnableNarrativeTrace
public class AppConfig { }
```

Cuando se omite `basePackages`, el valor predeterminado es el paquete de la clase anotada — igual que `@ComponentScan`. Para acotar el alcance explícitamente:

```java
@EnableNarrativeTrace(basePackages = {"com.example.order", "com.example.payment"})
```

### Nombre del logger

Cuando `narrativetrace-slf4j` está en el classpath, el bean `NarrativeContext` autocreado narra a través de SLF4J automáticamente (`Slf4jTraceEventListener` en la ruta síncrona de la tubería). Configura el nombre del logger SLF4J mediante la anotación:

```java
@EnableNarrativeTrace(loggerName = "myapp.traces")
```

El nombre de logger predeterminado es `"narrativetrace"`. Establece una cadena vacía para desactivar el envoltorio SLF4J automático:

```java
@EnableNarrativeTrace(loggerName = "")
```

El nombre del logger también se propaga a `Slf4jTraceExporter` en el módulo spring-web, que deriva el logger de exportación como `<loggerName>.export`.

Las apps Spring usan sus propias convenciones de configuración. La anotación `@EnableNarrativeTrace` es el enfoque recomendado — no se necesitan archivos de propiedades.

Comportamiento:

- Los beans fuera de `basePackages` (o del paquete predeterminado) se omiten.
- Los beans sin interfaces se omiten (limitación del proxy dinámico JDK).
- Solo se trazan las interfaces de los paquetes configurados; las interfaces del framework Spring se ignoran.
- Se proporciona automáticamente un bean `NarrativeContext`. Cuando `narrativetrace-slf4j` está en el classpath y `loggerName` no está vacío, narra a través de SLF4J bajo ese logger.
- Definir tu propio bean `narrativeContext` sobrescribe el autocreado.

Para la propagación entre hilos con `@Async`, la configuración del filtro de servlet y los patrones de despliegue en producción, consulta la [Guía de integración con Spring](guia-de-integracion-con-spring.md).

## 6. Configuración de Micronaut

La integración con Micronaut se autodescubre en el classpath — no hace falta ninguna anotación de activación. La configuración usa `application.yml`:

```yaml
# src/main/resources/application.yml
narrativetrace:
  base-packages:
    - com.example.order
    - com.example.payment
  logger-name: myapp.traces
  service-name: order-service
  service-version: "2.0"
  environment: production
```

### Propiedades

| Propiedad | Tipo | Predeterminado | Propósito |
|---|---|---|---|
| `narrativetrace.base-packages` | `List<String>` | `[]` (vacío — no envuelve nada) | Prefijos de paquete para el envoltorio de beans |
| `narrativetrace.logger-name` | `String` | `"narrativetrace"` | Nombre del logger SLF4J para los eventos de traza |
| `narrativetrace.service-name` | `String` | `""` | Metadatos de identidad del servicio |
| `narrativetrace.service-version` | `String` | `""` | Metadatos de identidad del servicio |
| `narrativetrace.environment` | `String` | `""` | Metadatos de identidad del servicio |

### Qué ocurre

Un `BeanCreatedEventListener<Any>` envuelve los beans elegibles en proxies dinámicos JDK. Aplican las mismas reglas de elegibilidad que en Spring:
- La clase del bean debe estar en un paquete base configurado
- El bean debe implementar al menos una interfaz de un paquete configurado
- Los beans sin interfaces coincidentes se dejan intactos

Se proporciona automáticamente un bean `NarrativeContext` (marcado `@Secondary`). Cuando `narrativetrace-slf4j` está en el classpath y `loggerName` no está vacío, el contexto se cablea con logging de eventos SLF4J. Define tu propio `@Bean NarrativeContext` para sobrescribir el predeterminado.

### Filtro HTTP

Añade `narrativetrace-micronaut-http` para el ciclo de vida de traza por petición:

```kotlin
implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.2.0")
```

El filtro HTTP reactivo (`HttpServerFilter`) se autorregistra al estar en el classpath. Ciclo de vida: reset → estampar metadatos HTTP → continuar → capturar → exportar → reset.

Se proporciona un `Slf4jTraceExporter` predeterminado (marcado `@Secondary`). Proporciona tu propio `@Bean TraceExporter` para sobrescribirlo.

Para la guía de integración completa, consulta la [Guía de integración con Micronaut](guia-de-integracion-con-micronaut.md).

## 7. Configuración de SLF4J

**NarrativeTrace no es un framework de logging.** Todo lo que sigue se conecta *a* tu configuración existente de SLF4J/Logback/Log4j — cambia qué se narra (generado en vez de escrito a mano), nunca cómo, dónde ni a través de qué se envían tus logs. Consulta [No reemplaza tu framework de logging](../../LEAME.md#no-reemplaza-tu-framework-de-logging) para la versión breve.

La narración de trazas a través de tu framework de logging existente es automática: cuando `narrativetrace-slf4j` está en el classpath, `PipelineBootstrap` (la raíz de composición detrás de cada contexto construido por defecto) adjunta `Slf4jTraceEventListener` a la ruta síncrona de la tubería de eventos. Sin clase envoltorio, sin cableado:

```java
var context = new ThreadLocalNarrativeContext(); // narra vía SLF4J cuando el módulo está presente
```

Veta la narración sin quitar el módulo con `narrativetrace.narration=off`.

### Nombre del logger

De forma predeterminada, los eventos de traza se registran bajo el logger SLF4J `narrativetrace`. Encamina los eventos a un logger distinto con la propiedad `narrativetrace.loggerName` (cadena ConfigResolver: propiedad del sistema o `narrativetrace.properties`), o de forma programática:

```java
var context = new ThreadLocalNarrativeContext(
    new NarrativeTraceConfig(), PipelineBootstrap.createDefault("myapp.traces"));
```

Esto es útil cuando distintas aplicaciones o módulos necesitan un enrutamiento de logs separado. El nombre del logger se propaga a otros componentes:

- **Exportador de Spring web** — cuando el contexto usa un nombre de logger personalizado, `Slf4jTraceExporter` deriva automáticamente `<loggerName>.export` (p. ej., `myapp.traces.export`)
- **Anotación de Spring** — `@EnableNarrativeTrace(loggerName = "myapp.traces")` configura el nombre para el contexto autocreado
- **Agente** — `loggerName=myapp.traces` en los argumentos del agente o `narrativetrace.loggerName=myapp.traces` en propiedades

### Niveles de log

Los eventos se registran bajo el logger configurado en estos niveles predeterminados:

| Tipo de evento | Nivel predeterminado |
|---|---|
| Entrada de método | `TRACE` |
| Retorno de método | `TRACE` |
| Excepción de método | `WARN` |

### Niveles de log personalizados

Sobrescribe los predeterminados construyendo tú mismo el listener y entregando al contexto una tubería montada a su alrededor:

```java
var listener = new Slf4jTraceEventListener("myapp.traces", Map.of(
    Slf4jTraceEventListener.EventType.ENTRY, Level.DEBUG,
    Slf4jTraceEventListener.EventType.RETURN, Level.DEBUG,
    Slf4jTraceEventListener.EventType.EXCEPTION, Level.ERROR
));
var context = new ThreadLocalNarrativeContext(
    new NarrativeTraceConfig(), new DualPathPipeline(listener));
```

### Campos MDC

`Slf4jTraceEventListener` establece campos MDC en cada evento de traza. Los filtros de petición de los
módulos servlet y Micronaut HTTP también rellenan campos MDC persistentes con alcance de petición antes de que se ejecuten los métodos trazados.

| Clave MDC | Valor |
|---|---|
| `traceId` | ID de traza W3C en bruto de 32 caracteres |
| `traceName` | Nombre legible determinista de tres palabras derivado de `traceId` |
| `spanId` | ID del span actual |
| `parentSpanId` | ID del span padre cuando existe |
| `service.name` | Nombre de servicio configurado cuando existe |
| `service.version` | Versión de servicio configurada cuando existe |
| `service.environment` | Entorno configurado cuando existe |
| `host.name` | Nombre de host autodetectado (`narrativetrace.capture.resource`, activado por defecto) |
| `process.pid` | Id del proceso (`narrativetrace.capture.resource`) |
| `process.runtime.version` | Versión del runtime de Java (`narrativetrace.capture.resource`) |
| `nt.class` | Nombre de la clase del servicio trazado |
| `nt.method` | Nombre del método |
| `nt.package` | Paquete declarante del servicio trazado, cuando se captura |
| `nt.depth` | Profundidad de llamada (1 para eventos de entrada de nivel superior) |
| `nt.threadVirtual` | Si la entrada se ejecutó en un hilo virtual (nombre/id del hilo son integrados de `%thread`) |

`traceName` es determinista pero no se garantiza que sea único. Usa `traceId` para la correlación exacta y
`traceName` para la legibilidad.

Úsalos en patrones de logback para obtener salida de log estructurada.

**La apariencia es configuración de logging; la captura es
configuración de NarrativeTrace.** El texto del mensaje narrativo se
mantiene limpio por diseño — todo lo demás (identidad de la traza,
identidad del servicio, clase, método, profundidad) se publica como
claves MDC, y tu patrón de logging decide qué aparece: un patrón sin
`%X{...}` muestra la narrativa pura, `%X{nt.class}` hace visible una
clave, y un encoder JSON las emite todas para los agregadores de logs.
La configuración de NarrativeTrace controla solo qué se *captura* — y
por tanto qué *puede* aparecer — nunca cómo se compone la línea de log.

### Convivencia con el logging tradicional

El código bien estructurado — métodos pequeños con nombres claros, valores calculados que se devuelven en lugar de loguearse — no necesita ninguna llamada SLF4J. NarrativeTrace lo captura todo a partir de las firmas de los métodos y los valores de retorno.

En código que aún no está completamente estructurado así, puedes intercalar llamadas SLF4J tradicionales para cosas como cálculos intermedios o puntos de decisión que no afloran en los límites de los métodos. Ambos se entrelazan con naturalidad:

```java
public class DefaultOrderService implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(DefaultOrderService.class);

    @Override
    public OrderResult placeOrder(String customerId, String productId, int quantity) {
        log.info("Placing order: customer={}, product={}, qty={}", customerId, productId, quantity);

        var customer = customers.findCustomer(customerId);
        log.debug("Resolved customer {} (tier: {})", customer.name(), customer.tier());

        double unitPrice = catalog.lookupPrice(productId);
        double total = unitPrice * quantity;
        log.debug("Calculated total: {} x {} = {}", unitPrice, quantity, total);

        inventory.reserve(productId, quantity);
        var payment = payments.charge(customerId, total, "tok_" + customer.id());
        log.info("Payment {} confirmed for ${}", payment.transactionId(), payment.amount());

        var orderId = "ORD-%05d".formatted(orderCounter.getAndIncrement());
        return new OrderResult(orderId, payment.transactionId(), total, quantity);
    }
}
```

```
TRACE [narrativetrace]        → OrderService.placeOrder(customerId: C-1234, productId: SKU-MECHANICAL-KB, quantity: 2)
INFO  [DefaultOrderService]   Placing order: customer=C-1234, product=SKU-MECHANICAL-KB, qty=2
TRACE [narrativetrace]        → CustomerService.findCustomer(customerId: C-1234)
TRACE [narrativetrace]        ← returned: Customer[id=C-1234, name=Alice Johnson, tier=GOLD]
DEBUG [DefaultOrderService]   Resolved customer Alice Johnson (tier: GOLD)
TRACE [narrativetrace]        → ProductCatalogService.lookupPrice(productId: SKU-MECHANICAL-KB)
TRACE [narrativetrace]        ← returned: 89.99
DEBUG [DefaultOrderService]   Calculated total: 89.99 x 2 = 179.98
...
INFO  [DefaultOrderService]   Payment TXN-00001 confirmed for $179.98
TRACE [narrativetrace]        ← returned: OrderResult[orderId=ORD-00001, ...]
```

Esto hace que NarrativeTrace sea fácil de adoptar incrementalmente — añádelo junto al logging existente y luego elimina las llamadas de log manuales a medida que refactorizas hacia límites de método más limpios.

### Frameworks de logging legados

Apps que usan `java.util.logging` o Log4j 1.x: añade el puente SLF4J apropiado ([jul-to-slf4j](https://www.slf4j.org/legacy.html#jul-to-slf4j) o [log4j-over-slf4j](https://www.slf4j.org/legacy.html#log4j-over-slf4j)) y la salida de NarrativeTrace fluye hacia tu infraestructura de logging existente sin cambios.

## 8. Interacción entre TracingLevel y SLF4J

TracingLevel (sección 1) y los niveles de log SLF4J (sección 7) son dos capas de filtrado independientes. Ambas deben permitir un evento para que aparezca en la salida de log.

### Flujo de datos

```
llamada al método → filtro TracingLevel → tubería de eventos
                                            ↓
                                  Slf4jTraceEventListener
                                            ↓
                                  filtro de nivel del logger SLF4J → salida de log
```

**TracingLevel** controla qué se **graba** en el árbol de trazas. Si una llamada se filtra aquí, nunca llega al contexto, a los renderers ni a SLF4J — simplemente no existe.

**El nivel de log SLF4J** controla qué se **imprime** en los logs. Los eventos ya están capturados; esto solo afecta a si las sentencias de log de `Slf4jTraceEventListener` atraviesan logback/log4j.

### Ejemplos de combinaciones

| TracingLevel | Nivel de logback en `narrativetrace` | Resultado |
|---|---|---|
| `DETAIL` | `INFO` | Árbol de trazas completo (con valores de parámetros) en archivos y renderers, pero las líneas de log de entrada/retorno se suprimen (se loguean a TRACE). Solo las rutas de excepción (WARN) aparecen en los logs. |
| `ERRORS` | `TRACE` | Solo las rutas de excepción se graban en el árbol de trazas. Esas excepciones se loguean (WARN supera el umbral TRACE). Las llamadas normales no producen nada en ninguna parte. |
| `NARRATIVE` | `TRACE` | Flujo de llamadas completo grabado y logueado, pero los valores de parámetros aparecen como cadenas vacías (NARRATIVE suprime los valores). |
| `DETAIL` | `TRACE` | Todo grabado y todo logueado — máxima verbosidad. |
| `OFF` | `TRACE` | Nada grabado, nada logueado. La puerta de TracingLevel bloquea todos los eventos antes de que lleguen a SLF4J. |

### Qué ajuste para qué objetivo

| Objetivo | Ajusta | Por qué |
|---|---|---|
| Reducir el ruido de logging | Sube el nivel SLF4J del logger `narrativetrace` | El árbol de trazas se sigue capturando para la salida a archivos y los renderers; solo disminuye el volumen en consola/archivo de log. |
| Reducir el tamaño de los archivos de traza | Baja el TracingLevel (p. ej. `NARRATIVE` → `SUMMARY`) | Entran menos eventos en el árbol de trazas, produciendo una salida renderizada más pequeña. |
| Reducir la sobrecarga de CPU/memoria | Baja el TracingLevel | El nivel SLF4J no tiene efecto en la sobrecarga de captura — el proxy sigue interceptando, serializando y grabando cada llamada permitida. Solo TracingLevel evita ese trabajo. |

## 9. Valores predeterminados recomendados por entorno

| Entorno | Nivel sugerido | Salida sugerida |
|---|---|---|
| Trabajo local en features | `DETAIL` | `narrativetrace.output=true`, `format=markdown` |
| Ejecuciones de test en CI | `NARRATIVE` o `SUMMARY` | `output=true`, `format=markdown` |
| Producción sensible al rendimiento | `ERRORS` (u `OFF`) | sin salida de archivos de test |

## 10. Configuración de OpenTelemetry

El módulo `narrativetrace-opentelemetry` ofrece dos modos de integración. Ambos requieren `opentelemetry-api` en el classpath (es `compileOnly` en el módulo — lo proporcionas tú).

### Exportación por lotes (post-hoc)

Exporta un árbol de trazas capturado a spans de OTel:

```java
var tracer = openTelemetry.getTracer("narrativetrace");
var exporter = new TraceSpanExporter(tracer);
exporter.export(context.captureTrace().roots());
```

Úsalo para salida de pruebas o exportación posterior a la petición. Baja sobrecarga durante la ejecución.

### Decorador de spans en vivo (tiempo real)

Crea spans de OTel a partir de los eventos del pipeline:

```java
var tracer = openTelemetry.getTracer("narrativetrace");
var listener = new OtelTraceEventListener(tracer);
// Conéctalo a DualPathPipeline como listener síncrono
```

`OtelTraceEventListener` es un `Consumer<TraceEvent>` que crea spans a partir de pares `EnterEvent`/`ExitEvent` con timestamps explícitos y enlazado explícito de `parentSpanId`.

### Atributos de span

Ambos modos establecen el mismo esquema de atributos:

| Atributo | Origen |
|---|---|
| `narrative.class` | `MethodSignature.className()` |
| `narrative.method` | `MethodSignature.methodName()` |
| `narrative.trace_id` | `SpanContext.traceId()` |
| `narrative.trace_name` | `TraceNamer.name(SpanContext.traceId().value())` |
| `narrative.param.<name>` | Valor renderizado de cada parámetro |
| `narrative.outcome` | Valor de retorno renderizado |
| `narrative.duration_ms` | Duración del nodo (solo por lotes) |
| `narrative.concurrency.groupId` | ID del grupo fork-join o fire-and-forget (lanzar y olvidar) |
| `narrative.concurrency.kind` | `FORK_JOIN` o `FIRE_AND_FORGET` |
| `narrative.concurrency.threadId` | ID del hilo |
| `narrative.concurrency.threadName` | Nombre del hilo |
| `narrative.concurrency.virtual` | Si el hilo es virtual |

## 11. Puntos de extensión

Los módulos presentes en el classpath pueden extender NarrativeTrace mediante
`java.util.ServiceLoader`, declarándose en `META-INF/services`. Existen dos tipos y
se activan de forma distinta.

Las **extensiones aditivas** son observadores. Pueden coexistir muchas, el orden
entre ellas no está definido y lo que las activa es su presencia en el classpath:
añadir el jar es toda la instalación.

| Punto de extensión | Recibe | Se invoca en |
|---|---|---|
| `ai.narrativetrace.api.spi.TraceEventListener` | cada evento publicado | el hilo que publica (según la topología) |
| `ai.narrativetrace.api.spi.ReportContributor` | todas las trazas acumuladas en una ejecución | una vez al final de la ejecución de pruebas |

Un `TraceEventListener` debe ser seguro para hilos; dónde se conecta lo decide la
topología, así que el mismo listener sigue funcionando sin cambios si se reconfigura
el pipeline. Ambos tipos están aislados: el que lance una excepción se informa una vez
y se omite, sin afectar a las demás extensiones ni a la aplicación.

Dos vías de escape controlan el descubrimiento, para despliegues que no quieren que
el classpath decida:

```properties
# Desactivar por completo el descubrimiento de extensiones
narrativetrace.discovery=off

# O desactivar solo proveedores concretos
narrativetrace.discovery.disabled=com.example.NoisyListener,com.example.SlowContributor
```

Las **extensiones de reemplazo** aportan otra topología de pipeline mediante
`ai.narrativetrace.core.pipeline.EventPipelineFactory`. Estas *nunca* se activan por
la presencia en el classpath: una topología decide la durabilidad, así que solo cambia
cuando la configuración la nombra:

```properties
narrativetrace.pipeline=<nombre>
narrativetrace.pipeline.<nombre>.<ajuste>=<valor>
```

Sin `narrativetrace.pipeline` se construye la topología de ruta dual por defecto y
nunca se busca ninguna factoría. Con ella, debe haber en el classpath una factoría que
responda a ese nombre: si no se encuentra ninguna, la inicialización falla en lugar de
recurrir en silencio a una topología con garantías de durabilidad distintas.

De forma predeterminada, el listener SLF4J se compone automáticamente en la ruta
duradera siempre que el módulo `narrativetrace-slf4j` esté presente. Usa
`narrativetrace.narration=off` para mantener la captura sin narración.

## 12. Ocultación

La introspección reflexiva trata los datos como sensibles de forma
predeterminada: sin un `toString()` cuidado, un DTO lleva el valor de cada
campo a las trazas, los registros y las exportaciones. NarrativeTrace oculta
valores en dos ejes independientes, ambos activos de forma predeterminada.

### Eje 1 — el nombre del campo

Una coincidencia con un vocabulario incorporado, insensible a mayúsculas y a
acentos. El vocabulario es **multilingüe y siempre activo**: no hay una
configuración regional que elegir ni nada a lo que adherirse, porque una lista
de denegación solo en inglés no ofrece una garantía más débil, sino una
distribuida de otro modo: protege a quien nombra sus campos en el idioma en que
se escribió la lista.

| Idioma | Palabras |
|---|---|
| Inglés | `password`, `passwd`, `secret`, `token`, `apikey`, `api_key`, `cvv`, `ssn`, `authorization`, `credential`, `privatekey`, `private_key`, `cardnumber`, `card_number`, `jwt`, `cookie`, `setcookie`, `set_cookie`, `sessionid`, `session_id`, `accountnumber`, `account_number`, `routingnumber`, `routing_number`, `pan`, `iban` |
| Español | `contraseña`, `tarjeta`, `cédula`, `rut`, `cuit`, `dni`, `claveAcceso`, `claveSecreta` |
| Portugués | `senha`, `cpf`, `cnpj`, `cartão` |
| Francés | `motDePasse`, `mot_de_passe`, `nir`, `carteBancaire`, `numeroCarte` |
| Chino | `密码`, `身份证`, y el pinyin `mima`, `shenfenzheng` |

Los acentos se normalizan en ambos lados, de modo que `contraseña`,
`contrasena` y `CONTRASEÑA` son un único patrón y no tres, incluida la grafía
descompuesta que devuelve un sistema de archivos de macOS.

La mayoría de las palabras coincide como **subcadena**, así que `userPassword`
y `numeroTarjeta` quedan cubiertos. Las cortas coinciden, en cambio, en los
**límites de token del identificador**:

`pan` · `iban` · `rut` · `cuit` · `dni` · `senha` · `cpf` · `cnpj` · `nir` ·
`mima`

Cada una de ellas aparece dentro de una palabra de negocio corriente — `cuit`
en `circuitBreaker`, `rut` en `truthValue`, `dni` en `midnightCutoff`, `senha`
en la unión de `chosenHash`— y un valor predeterminado que las vacía es uno
que los equipos desactivan por completo, lo que filtra todos los campos en
lugar de uno. `rutCliente`, `cuit_empresa` y `DNI` siguen coincidiendo;
`circuitBreaker` no.

**Ante la duda, se estrecha.** `clave` y `carte`, sueltas, eran palabras de
coincidencia por token en esa lista, con el mismo razonamiento que `rut`/`cuit`
arriba — hasta que una revisión de hablante nativo encontró que la
coincidencia por token solo protege una palabra corta de un compuesto *ajeno*,
nunca de uno propio del código base: `clavePrimaria`/`claveForanea` (español,
"primary key"/"foreign key" — el código de bases de datos en español también
escribe `llavePrimaria`) y `carteGraphique`/`carteRoutiere` (francés, "tarjeta
gráfica"/"mapa de carreteras") son ellas mismas un token de identificador
completo, así que la regla anterior también las vaciaba. Ambas palabras se
retiraron y se sustituyeron por los compuestos específicos que sí son
credenciales — `claveAcceso`/`clave_acceso`, `claveSecreta`/`clave_secreta`,
`carteBancaire`/`carte_bancaire`, `numeroCarte`/`numero_carte`—, lo bastante
largos para ser seguros como subcadena simple, comparados con la grafía
camelCase y snake_case del mismo modo que ya lo son `motDePasse`/
`mot_de_passe`.

### Eje 2 — la forma del propio valor

Un token portador llega como `value`, `header`, `data` o el tercer elemento de
una lista sin nombre alguno, así que el segundo eje pregunta qué dicen los
bytes. Cada comparador es estructural: no hay heurística de entropía ni regla
de longitud.

| Forma | Reconocida por |
|---|---|
| JWT | el prefijo `eyJ` y tres segmentos base64url |
| Número de tarjeta (PAN) | 13–19 dígitos, válido según Luhn |
| `Set-Cookie` | `name=value` más un atributo de RFC 6265 |
| RUT chileno | verificador módulo 11; los puntos son opcionales, **el separador `-` del verificador es obligatorio** |
| CPF brasileño | 11 dígitos, ambos dígitos verificadores |
| CNPJ brasileño | 14 dígitos, ambos dígitos verificadores |
| DNI / NIE español | la letra de control módulo 23 |
| NIR francés | la clave módulo 97, incluidos `2A`/`2B` de Córcega |
| Cédula de identidad china | el carácter de control ISO 7064 *y* una fecha de nacimiento plausible |

Las formas de documento nacional son neutrales respecto al idioma: un CPF es un
CPF se llame como se llame el campo que lo contiene, y por eso el eje del valor
es el adecuado para un documento cuyo nombre de campo suele estar en un idioma
en el que la lista de denegación se lee, pero no se escribió.

Un parecido que falla su suma de verificación permanece **visible**: un número
de pedido, un número de factura, una fecha. Por esa razón una secuencia de
nueve dígitos sin más no se trata como un RUT: el módulo 11 por sí solo
ocultaría aproximadamente uno de cada once identificadores de nueve dígitos de
tu sistema.

### Ampliar los valores predeterminados

Dos mandos, y hacen cosas distintas.

```properties
# AÑADE al vocabulario incorporado (propiedad del sistema, o la variable de
# entorno NARRATIVETRACE_REDACTION_ADDITIONALPATTERNS)
narrativetrace.redaction.additionalPatterns=betalingskort,kontonummer
```

```java
// REEMPLAZA por completo el vocabulario incorporado
new ValueRenderer(RedactionPolicy.ofPatterns(Set.of("ssn", "internalRef")));
```

`additionalPatterns` es para quien *despliega* el artefacto: no requiere
recompilar y se lee primero de la propiedad y después de la variable de
entorno, no de `narrativetrace.properties`. `ofPatterns` es para quien *escribe*
la aplicación. Se componen: las adiciones siguen aplicándose a una política
construida con `ofPatterns`, porque reemplazar el vocabulario es la opinión de
una aplicación sobre cuáles de sus propios campos son sensibles, no un permiso
para deshacer la ampliación de un despliegue.

> **Las adiciones son patrones de subcadena**, con la misma coincidencia que el
> vocabulario de subcadena incorporado. Una corta acarrea la misma trampa que
> evitan las palabras coincidentes por token: añadir `id` vacía todos los
> identificadores de la traza.

### Desactivarla

```java
new ValueRenderer(RedactionPolicy.DISABLED);
```

`DISABLED` desactiva ambos ejes y las adiciones. `@NotTraced` se sigue
respetando: es una instrucción explícita, no un valor predeterminado. Los
valores ocultos se renderizan como el literal `[REDACTED]`, nunca como
silencio, de modo que quien lee puede distinguir «oculto» de «nunca
capturado».

## Ver también

- [Guía del plugin de Gradle](guia-del-plugin-de-gradle.md) — referencia completa del DSL, modos de interceptación, recetas, DSL en Groovy
- [Guía de instalación](guia-de-instalacion.md) — dependencias, rutas de integración, configuración del agente Java
- [Guía de integración con Spring](guia-de-integracion-con-spring.md) — trazado de beans, filtro de servlet, propagación con `@Async`, pruebas
- [Guía de integración con Micronaut](guia-de-integracion-con-micronaut.md) — trazado de beans, filtro HTTP, propiedades de configuración
- [Guía de anotaciones](guia-de-anotaciones.md) — `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`
- [Guía de claridad](guia-de-claridad.md) — modelo de puntuación, componentes NLP, integración con JUnit
