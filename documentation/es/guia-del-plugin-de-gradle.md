<!-- source: documentation/gradle-plugin-guide.md blob cf519cb92da0 | translated: 2026-09-09 | reviewed: - -->
# Guía del plugin de Gradle de NarrativeTrace

[English](../gradle-plugin-guide.md) | **Español** | [简体中文](../zh-CN/Gradle插件指南.md)

El plugin de Gradle `ai.narrativetrace` es la forma recomendada de usar NarrativeTrace en proyectos Gradle. Se encarga de la gestión de dependencias, los flags del compilador, la configuración de pruebas y las puertas de calidad — todo desde un único bloque DSL.

## Tabla de contenidos

- [Inicio rápido](#inicio-rápido)
- [Qué hace el plugin automáticamente](#qué-hace-el-plugin-automáticamente)
- [Propiedades](#propiedades) — [enabled](#enabled) | [mode](#mode) | [testFramework](#testframework) | [scope](#scope) | [format](#format) | [tracingLevel](#tracinglevel) | [outputDir](#outputdir) | [approval](#approval) | [approvedDir](#approveddir)
- [Bloque de módulos](#bloque-de-módulos)
- [Bloque del agente](#bloque-del-agente)
- [Bloque de claridad](#bloque-de-claridad)
- [Tareas](#tareas) — [clarityCheck](#claritycheck) | [clarityScan](#clarityscan) | [glossaryScan](#glossaryscan) | [approveNarratives](#approvenarratives)
- [Requisitos](#requisitos)
- [Resolución de versión](#resolución-de-versión)
- [Recetas comunes](#recetas-comunes)
- [DSL de Groovy](#dsl-de-groovy)
- [Validación](#validación)
- [Referencia completa del DSL](#referencia-completa-del-dsl)

## Inicio rápido

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.1"
}
```

Eso es todo. Ejecuta `./gradlew test` y la salida de trazas aparece en `build/narrativetrace/`.

No hace falta añadir ninguna dependencia de JUnit. Con el valor por defecto `testFramework = "junit5"`, el plugin cambia la tarea de test a la JUnit Platform *y* pone el motor de Jupiter (`org.junit.jupiter:junit-jupiter-engine`, fijado a la versión contra la que se prueba NarrativeTrace) en `testRuntimeOnly`, porque la plataforma se niega a arrancar sin uno. Declarar tu propia versión de JUnit sigue funcionando: la resolución de conflictos de Gradle escoge la mayor de las dos.

## Qué hace el plugin automáticamente

| Acción | Detalle |
|---|---|
| Añade el flag del compilador `-parameters` | En todas las tareas `JavaCompile`; se omite si ya está presente |
| Añade dependencias | Según `mode`, `modules` y `testFramework`; la versión se detecta automáticamente desde el JAR del plugin |
| Añade el motor de la JUnit Platform | Solo con `testFramework = "junit5"`: `testRuntimeOnly org.junit.jupiter:junit-jupiter-engine:5.11.4`, para que el primer `gradle test` se ejecute en vez de fallar con *«Cannot create Launcher without at least one TestEngine»*. JUnit 4 no recibe ninguno: no se cambia a la plataforma |
| Establece propiedades JVM de las pruebas | `narrativetrace.output=true`, `narrativetrace.outputDir` y, opcionalmente, `narrativetrace.format`, `narrativetrace.level`, el par de glosario (`glossary=true`) y el par de aprobación (`approval=true`) |
| Registra la tarea `clarityCheck` | Lee `clarity-results.json`, aplica los umbrales, conectada al ciclo de vida `check` |
| Registra la tarea `clarityScan` | Análisis de claridad independiente a partir de las clases compiladas (no requiere pruebas) |
| Registra la tarea `glossaryScan` | Recolección independiente del glosario a partir de las clases compiladas, incluidas las plantillas de anotaciones |
| Registra la tarea `approveNarratives` | Promueve las narrativas `*.received.nt` revisadas a líneas base `*.approved.nt` |
| Configura el argumento JVM del agente | Cuando `mode = "agent"`: resuelve el JAR del agente y añade `-javaagent` a las tareas Test |

## Propiedades

### `enabled`

Controla si el plugin hace algo. Cuando es `false`, no se registran tareas, no se añaden dependencias y no se establecen flags del compilador.

```kotlin
narrativeTrace {
    enabled.set(false)  // desactivar en este subproyecto
}
```

Por defecto: `true`

### `mode`

Selecciona la estrategia de interceptación. Esto determina qué dependencia de la biblioteca central añade el plugin.

| Modo | Dependencias añadidas (además de core + clarity + diagrams + framework de pruebas) |
|---|---|
| `proxy` | `narrativetrace-proxy` — proxy dinámico JDK, basado en interfaces |
| `agent` | `narrativetrace-agent` — instrumentación de bytecode, sin necesidad de interfaces |
| `spring` | `narrativetrace-spring` + `narrativetrace-proxy` — envoltura automática mediante el BeanPostProcessor de Spring |

Por defecto: `"proxy"`

**El modo proxy** es el más simple: envuelve los servicios con `NarrativeTraceProxy.trace()` en el código de pruebas.

**El modo agent** además crea una configuración `narrativeTraceAgent`, resuelve el JAR del agente y añade `-javaagent` a todas las tareas `Test`. Usa el bloque `agent { }` para especificar qué paquetes instrumentar.

**El modo spring** añade el BeanPostProcessor de Spring que envuelve automáticamente los beans elegibles. Usa `@EnableNarrativeTrace` en tu clase de configuración.

### `testFramework`

| Valor | Dependencia añadida |
|---|---|
| `"junit5"` | `narrativetrace-junit5` |
| `"junit4"` | `narrativetrace-junit4` |

Por defecto: `"junit5"`

### `scope`

Controla qué configuración de Gradle recibe las dependencias de las bibliotecas.

| Scope | Dependencias de biblioteca | Dependencia del framework de pruebas |
|---|---|---|
| `"test"` | `testImplementation` | `testImplementation` |
| `"production"` | `implementation` | `testImplementation` (siempre) |

Por defecto: `"test"`

Usa `"production"` cuando despliegues NarrativeTrace en una aplicación en ejecución (p. ej., con el filtro de servlet para tracing a nivel de petición). La dependencia del framework de pruebas siempre permanece en `testImplementation`, independientemente del scope.

### `format`

Formato de salida de los archivos de traza. Solo se reenvía a las tareas de prueba cuando se establece explícitamente — si se omite, la extensión de JUnit usa su propio valor por defecto (markdown).

| Valor | Descripción |
|---|---|
| `"markdown"` | Markdown legible para humanos con diagramas Mermaid |
| `"text"` | Texto plano con sangría |
| `"mermaid"` | Solo diagrama de secuencia Mermaid |
| `"plantuml"` | Solo diagrama de secuencia PlantUML |

Sin convención por defecto — omítelo para que decida la extensión de JUnit.

### `tracingLevel`

Controla cuánto detalle se captura en las trazas. Solo se reenvía a las tareas de prueba cuando se establece explícitamente.

| Valor | Comportamiento |
|---|---|
| `"OFF"` | No se captura ninguna traza |
| `"ERRORS"` | Solo se capturan las rutas de excepción |
| `"SUMMARY"` | Entrada raíz, hoja más profunda y cadenas de excepciones completas |
| `"NARRATIVE"` | Flujo de llamadas completo, con los valores de parámetros suprimidos |
| `"DETAIL"` | Flujo de llamadas completo con valores de parámetros y valores de retorno |

Sin convención por defecto — omítelo para que decida el runtime.

### `outputDir`

Directorio para los archivos de traza, los informes de claridad y los diagramas.

Por defecto: `layout.buildDirectory.dir("narrativetrace")` (es decir, `build/narrativetrace/`)

### `approval`

Modo de aprobación para las narrativas estructurales. Cuando es `true`, el plugin reenvía `narrativetrace.approval=true` y `narrativetrace.approvedDir` a las tareas de test: una prueba que **pasa** pero cuya estructura trazada difiere de su línea base confirmada `*.approved.nt` falla con un diff legible, y la estructura actual se escribe junto a la línea base como `*.received.nt` para su revisión. Acepta un cambio intencionado con la tarea [`approveNarratives`](#approvenarratives).

Por defecto: `false`

```kotlin
narrativeTrace {
    approval.set(true)
}
```

### `approvedDir`

Directorio de líneas base narrativas confirmadas, con la estructura `<dir>/<TestClassSimpleName>/<artifact_name>.approved.nt` — las mismas reglas de directorio por clase e identidad de artefacto que cualquier otro artefacto por test, así que un método que se ejecuta más de una vez (parametrizado, repetido) tiene una línea base por invocación.

Por defecto: `layout.projectDirectory.dir("src/test/narratives")`

## Bloque de módulos

Activación granular de módulos adicionales de NarrativeTrace. Todos son `false` por defecto.

```kotlin
narrativeTrace {
    modules {
        slf4j.set(true)        // narrativetrace-slf4j
        micrometer.set(true)   // narrativetrace-micrometer
        servlet.set(true)      // narrativetrace-servlet
        springWeb.set(true)    // narrativetrace-spring-web + narrativetrace-servlet
    }
}
```

| Flag | Artefacto | Notas |
|---|---|---|
| `slf4j` | `narrativetrace-slf4j` | Encamina los eventos de traza a través de SLF4J/logback |
| `micrometer` | `narrativetrace-micrometer` | Propagación de trazas entre hilos mediante context-propagation de Micrometer |
| `servlet` | `narrativetrace-servlet` | Filtro de servlet para el ciclo de vida de la traza por petición |
| `springWeb` | `narrativetrace-spring-web` + `narrativetrace-servlet` | Auto-configuración de Spring para el filtro de servlet; añade automáticamente el módulo servlet |

Establecer `springWeb` cuando `mode` no es `"spring"` produce una advertencia (pero no falla).

## Bloque del agente

Solo es relevante cuando `mode = "agent"`. Configura qué paquetes instrumenta el agente de bytecode.

```kotlin
narrativeTrace {
    mode.set("agent")
    agent {
        packages.set(listOf("com.example.app", "com.example.shared"))
    }
}
```

Cuando packages está vacío (el valor por defecto), el agente instrumenta todas las clases. Los paquetes se unen con `;` en el argumento `-javaagent`.

El JAR del agente se resuelve de forma diferida en el momento de ejecutar las pruebas, a partir de una configuración de Gradle dedicada `narrativeTraceAgent`.

## Bloque de claridad

Configura la puerta de calidad `clarityCheck`.

```kotlin
narrativeTrace {
    clarity {
        minScore.set(0.80)       // falla si algún escenario puntúa por debajo de 0.80
        maxHighIssues.set(0)     // falla si algún escenario tiene problemas HIGH
        maxSuiteIssues.set(0)    // falla ante cualquier incidencia a nivel de suite (p. ej. violación de vocabulario)
        warnOnly.set(true)       // registra advertencias en lugar de fallar
    }
}
```

### `minScore`

Puntuación mínima global de claridad (0.0–1.0). Cualquier escenario por debajo de este umbral hace fallar el build.

Por defecto: `0.0` (sin puerta)

### `maxHighIssues`

Número máximo de problemas de severidad HIGH por escenario. Superarlo hace fallar el build.

Por defecto: `Integer.MAX_VALUE` (sin puerta)

### `maxSuiteIssues`

Número máximo de incidencias a nivel de suite — incidencias que pertenecen a la ejecución completa y no a un escenario concreto, como las violaciones de vocabulario `non-canonical-term` de la recolección del glosario. Superarlo hace fallar el build; por debajo del umbral se registran como advertencias. Las violaciones de vocabulario solo se reportan cuando existe un `glossary.json` versionado antes de la ejecución.

Por defecto: `Integer.MAX_VALUE` (solo consultivo)

### `warnOnly`

Cuando es `true`, las violaciones de los umbrales producen advertencias en lugar de fallos del build.

Por defecto: `false`

## Tareas

### `clarityCheck`

Lee `build/narrativetrace/clarity-results.json` (generado por la extensión de JUnit durante `test`) y aplica los umbrales configurados.

- **Depende de**: `test`
- **Conectada a**: `check` (se ejecuta automáticamente con `./gradlew check`)
- **Se omite silenciosamente** cuando `clarity-results.json` no existe (p. ej., no se ejecutó ninguna prueba)

### `clarityScan`

Analiza la claridad de los nombres de las clases compiladas sin ejecutar pruebas. Usa reflexión para escanear los archivos de clase y producir un informe de claridad.

- **Depende de**: `classes`
- **Classpath**: `testRuntimeClasspath` (necesita el módulo clarity)
- **Argumentos**: `--classes-dir` y `--output-dir`, derivados de la configuración del plugin
- **Salida**: `clarity-scan-report.md` y `clarity-scan-results.json` en `outputDir` — deliberadamente distintos de los artefactos de la ejecución de pruebas (`clarity-report.md` / `clarity-results.json`), de modo que un escaneo nunca sobrescribe lo que lee la puerta `clarityCheck`
- **Alcance**: tipos públicos y de paquete; las clases anidadas privadas, anónimas, locales y lambda se omiten por ser detalles de implementación

Ejecución independiente:

```bash
./gradlew clarityScan
```

### `glossaryScan`

Recolecta el glosario de dominio a partir de las clases compiladas sin ejecutar pruebas.
Es el **único** modo que recolecta las plantillas `@Narrated` / `@OnError`:
una traza capturada lleva la narración con los valores de los parámetros ya
interpolados, así que recolectar allí escribiría datos de runtime en un
archivo bajo control de versiones.

- **Depende de**: `classes`
- **Classpath**: `testRuntimeClasspath` (necesita el módulo del glosario)
- **Argumentos**: `--classes-dir` (`build/classes/java/main`), `--glossary-dir`
  (la raíz del repositorio), `--output-dir` desde la configuración del plugin

```bash
./gradlew glossaryScan
```

Para recolectar durante la ejecución de las pruebas — desde trazas reales, sin
plantillas — habilítalo en la extensión:

```kotlin
narrativeTrace {
    glossary.set(true)
}
```

Esto establece `narrativetrace.glossary=true` y apunta
`narrativetrace.glossaryDir` a la raíz del repositorio. Está desactivado por
defecto porque escribe `glossary.json` / `glossary.md` fuera del directorio
de build.

Las vistas de trazas traducidas no son una tarea de build: adjunta un `TranslationSubscriber` del módulo de glosario a la tubería de eventos (locale + `glossary.json` versionado, localizado vía `narrativetrace.glossary.path` o el classpath) y cada ejecución — de pruebas o de producción — emite su flujo traducido en vivo, hacia el logger `narrativetrace.i18n.<locale>` o como archivos Markdown por traza. La antigua tarea `translateTraces` y la propiedad `translationLocales` se retiraron en favor de esta forma de tubería.

### `approveNarratives`

Acepta los cambios estructurales intencionados en el [modo de aprobación](#approval): promueve cada archivo `*.received.nt` revisado bajo `approvedDir` a su línea base `*.approved.nt`.

```bash
./gradlew approveNarratives
```

- **Grupo**: `verification`
- Siempre es seguro ejecutarla — imprime `Approved: <ruta>` por cada línea base promovida, o `No received narratives to approve.` cuando no hay nada que promover
- Nunca ejecuta pruebas: revisa primero los archivos received, aprueba y vuelve a ejecutar la suite en verde

## Requisitos

El plugin comprueba su entorno al aplicarse y falla con una sola línea nombrando
el requisito, en vez de dejar que el build llegue más tarde a un error confuso:

- **Gradle 8.0 o superior.** Desarrollado y probado contra 8.14.2.
- **Java 17 o superior** — el toolchain configurado si el build define uno, y si
  no la JVM que ejecuta Gradle. NarrativeTrace está escrito en Java 17 (records,
  interfaces selladas, switches con patrones), así que es un requisito del
  lenguaje, no una preferencia.

Ninguna de las dos comprobaciones se ejecuta con `enabled.set(false)`, y una
cadena de versión que el plugin no puede interpretar se considera aceptable: una
suposición nunca debe detener un build.

## Resolución de versión

El plugin detecta automáticamente su versión desde el JAR del plugin en tiempo de ejecución, y esa versión se usa para todas las dependencias de NarrativeTrace gestionadas.

Si la versión no puede detectarse (p. ej., al ejecutar desde el código fuente sin el archivo de propiedades), el plugin recurre a `0.0.0-unknown`.

Para fijar otra versión — por ejemplo, para hacer dogfooding de un `-SNAPSHOT` local cuyas bibliotecas van por delante de la versión embebida del plugin — usa `libraryVersion`:

```kotlin
narrativeTrace {
    libraryVersion.set("0.2.0-SNAPSHOT")
}
```

Cuando se define, todas las dependencias de NarrativeTrace gestionadas se resuelven en esa versión; cuando no se define (el valor por defecto), se usa la versión embebida y el comportamiento no cambia.

## Recetas comunes

### Tracing solo en pruebas (por defecto)

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.1"
}
```

Añade todas las dependencias en `testImplementation`. El código de producción no tiene ninguna clase de NarrativeTrace en el classpath.

### Tracing en producción con filtro de servlet

```kotlin
narrativeTrace {
    scope.set("production")
    mode.set("spring")
    modules {
        springWeb.set(true)
        slf4j.set(true)
    }
}
```

Añade las dependencias en `implementation` para que el filtro de servlet y la auto-configuración de Spring estén disponibles en tiempo de ejecución.

### Instrumentación basada en agente

```kotlin
narrativeTrace {
    mode.set("agent")
    agent {
        packages.set(listOf("com.example.app"))
    }
}
```

El plugin crea una configuración `narrativeTraceAgent`, resuelve el JAR del agente y añade `-javaagent:path/to/agent.jar=com.example.app` a todas las tareas Test.

### CI con puertas de claridad estrictas

```kotlin
narrativeTrace {
    tracingLevel.set("NARRATIVE")
    clarity {
        minScore.set(0.80)
        maxHighIssues.set(0)
    }
}
```

`./gradlew check` falla si algún escenario tiene una claridad por debajo de 0.80 o algún problema HIGH.

### Narrativas con pruebas de aprobación

```kotlin
narrativeTrace {
    approval.set(true)
}
```

La primera ejecución escribe la estructura libre de valores de cada escenario como `src/test/narratives/<TestClass>/<escenario>.received.nt` y falla; revisa, ejecuta `./gradlew approveNarratives` y commitea los archivos `*.approved.nt`. A partir de ahí, cualquier deriva estructural en una prueba que pasa — una llamada nueva, una llamada eliminada, un resultado distinto — hace fallar la build con un diff legible hasta que se aprueba explícitamente. Las líneas base están libres de valores, así que nunca filtran datos de tiempo de ejecución y sobreviven a los cambios que solo afectan a datos.

### Desactivar en un subproyecto

```kotlin
narrativeTrace {
    enabled.set(false)
}
```

No ocurre nada — sin dependencias, sin tareas, sin flags del compilador.

### Consumir un checkout local (composite build)

Antes de que los artefactos estén en un repositorio que puedas resolver — o cuando quieras probar un cambio en NarrativeTrace contra tu propio código — apunta tu proyecto a un checkout hermano. Esto necesita **las dos** mitades, y ninguna es opcional:

```kotlin
// settings.gradle.kts
pluginManagement {
    // 1. Resuelve el propio plugin: `id("ai.narrativetrace")` sin versión.
    includeBuild("../narrative-trace-java")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// 2. Sustituye las coordenadas de librería `ai.narrativetrace:*` que el plugin
//    añade por ti. Sin esto, la build falla resolviendo artefactos que están
//    justo ahí, en disco.
includeBuild("../narrative-trace-java")

rootProject.name = "my-app"
```

```kotlin
// build.gradle.kts
plugins {
    java
    id("ai.narrativetrace")  // sin versión — la build incluida la aporta
}

repositories { mavenCentral() }
```

Por qué las dos: `pluginManagement { includeBuild(...) }` y un `includeBuild(...)` de nivel superior son dos mecanismos distintos. El primero hace resoluble el *marker* del plugin; el segundo hace que Gradle sustituya por dependencias de proyecto las coordenadas de *librería* que añade `DependencyConfigurator` (`ai.narrativetrace:narrativetrace-core` y compañía). Con solo el primero, el plugin se aplica y la build falla después, al resolver dependencias.

Es comportamiento normal de Gradle, no algo que haga NarrativeTrace, pero es el plugin quien pone esas coordenadas de librería en tu build, así que es ahí donde te lo encuentras.

Dos cosas que conviene saber:

- **No hay que publicar nada.** No ejecutes `publishToMavenLocal`: la sustitución del composite reemplaza las coordenadas antes de resolver, así que una publicación local solo añadiría una copia obsoleta que puede tapar tus cambios.
- **La versión no tiene por qué coincidir.** La sustitución es por grupo y nombre de módulo, así que el `0.2.0-SNAPSHOT` de la build incluida satisface cualquier versión que pida el plugin. Si prefieres resolver artefactos reales y saltarte el composite, usa `libraryVersion` — consulta [Resolución de versiones](#resolución-de-versiones).

### Configuración multiproyecto

Aplica el plugin solo en los subproyectos que tienen pruebas:

```kotlin
// settings.gradle.kts
rootProject.name = "my-app"
include("core", "web", "shared")
```

```kotlin
// core/build.gradle.kts
plugins {
    java
    id("ai.narrativetrace")
}
```

Cada subproyecto obtiene sus propias tareas `clarityCheck` y `clarityScan`.

## DSL de Groovy

Todos los ejemplos anteriores usan el DSL de Kotlin. El equivalente en Groovy:

```groovy
plugins {
    id 'ai.narrativetrace' version '0.2.1'
}

narrativeTrace {
    mode = 'proxy'
    testFramework = 'junit5'
    scope = 'test'

    modules {
        slf4j = true
    }

    clarity {
        minScore = 0.80
        maxHighIssues = 0
    }
}
```

## Validación

El plugin valida todas las propiedades de tipo string en tiempo de configuración (dentro de `afterEvaluate`). Los valores inválidos producen un mensaje de error claro:

```
> Invalid narrativeTrace mode 'invalid'. Valid values: proxy, agent, spring
> Invalid narrativeTrace scope 'compile'. Valid values: test, production
> Invalid narrativeTrace format 'xml'. Valid values: markdown, text, mermaid, plantuml
> Invalid narrativeTrace tracingLevel 'VERBOSE'. Valid values: OFF, ERRORS, SUMMARY, NARRATIVE, DETAIL
```

## Referencia completa del DSL

Punto de partida para copiar y pegar con todas las propiedades mostradas:

```kotlin
narrativeTrace {
    enabled.set(true)                          // por defecto: true
    mode.set("proxy")                          // "proxy" (por defecto) | "agent" | "spring"
    testFramework.set("junit5")               // "junit5" (por defecto) | "junit4"
    scope.set("test")                          // "test" (por defecto) | "production"
    format.set("markdown")                     // "markdown" | "text" | "mermaid" | "plantuml"
    tracingLevel.set("DETAIL")                 // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
    outputDir.set(layout.buildDirectory.dir("narrativetrace"))
    glossary.set(false)                        // por defecto: false — recolecta glossary.json al final de la suite
    approval.set(false)                        // por defecto: false — verifica la estructura contra líneas base confirmadas
    approvedDir.set(layout.projectDirectory.dir("src/test/narratives"))
    // libraryVersion.set("0.2.0-SNAPSHOT")    // por defecto: versión embebida del plugin — anúlala para dogfooding de un snapshot

    modules {                                  // activación granular (todo false por defecto)
        slf4j.set(false)
        micrometer.set(false)
        servlet.set(false)
        springWeb.set(false)                   // implica servlet
    }

    agent {                                    // solo relevante cuando mode = "agent"
        packages.set(listOf("com.example.app"))
    }

    clarity {
        minScore.set(0.80)                     // por defecto: 0.0 (sin puerta)
        maxHighIssues.set(0)                   // por defecto: Integer.MAX_VALUE (sin puerta)
        maxSuiteIssues.set(0)                  // por defecto: Integer.MAX_VALUE (solo consultivo)
        warnOnly.set(false)                    // por defecto: false
    }
}
```

## Ver también

- [Guía de instalación](guia-de-instalacion.md) — configuración manual sin el plugin, rutas de integración
- [Guía de configuración](guia-de-configuracion.md) — niveles de tracing, configuración de JUnit/Spring/SLF4J
- [Guía de claridad](guia-de-claridad.md) — modelo de puntuación, componentes NLP, formato del informe de claridad
- [Guía de integración con Spring](guia-de-integracion-con-spring.md) — `@EnableNarrativeTrace`, propagación con `@Async`, filtro de servlet
- [Guía de anotaciones](guia-de-anotaciones.md) — `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`
