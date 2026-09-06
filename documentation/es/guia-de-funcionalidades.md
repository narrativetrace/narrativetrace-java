<!-- source: documentation/feature-guide.md blob f65d7cf53d54 | translated: 2026-09-03 | reviewed: 2026-09-03 -->

# Guía de funcionalidades de NarrativeTrace

[English](../feature-guide.md) | **Español** | [简体中文](../zh-CN/功能指南.md)

**Alcance: Producto.** Esta guía es el catálogo canónico de las
funcionalidades de NarrativeTrace para todas las plataformas (Java,
TypeScript, Python, .NET). El flujo de la verdad es:

> **Código Java** (implementación de referencia — la fuente de
> referencia o *golden source*) → **esta guía** (catálogo canónico:
> qué es el producto, con estado y tier) → **ports de plataforma**
> (construidos a partir de esta guía más el código Java referenciado).

El único hogar de este archivo es el repositorio Java; los ports de
plataforma no deben bifurcarlo. Los ports mantienen solo notas sobre
los mecanismos propios de cada plataforma en sus propios repositorios:
una decisión que se sostiene en todas las plataformas se registra una
sola vez, aquí; un mecanismo específico de la implementación de una
plataforma queda en las notas propias de ese port. Cada fila
publicada cita su implementación Java concreta (`module: main classes`)
para que quien porta vaya directo de la fila al código de referencia.
Una fila describe *qué* es la funcionalidad; el código Java citado es
la referencia de *cómo* se comporta — la paridad a nivel de
comportamiento pertenece a los fixtures de conformidad sobre el
esquema JSON canónico, no a la prosa de este documento.

Organizada por lo que quieres lograr, no por módulo.

**Etiquetas de estado:**

- **Abierta** — publicada, Apache 2.0, en `narrativetrace-api`: las anotaciones,
  el modelo de eventos, la especificación del formato de salida y las SPI. Un
  estándar abierto, para que cualquier implementación pueda adoptarlo.
- **Gratis** — publicada, de código disponible (BSL 1.1, pasa a Apache 2.0 tras
  cuatro años), disponible en este repositorio. Gratuita en producción.
- **Gratis/cerrada** — publicada sin coste, propietaria, construida fuera de
  este repositorio.
- **Pro** — publicada en NarrativeTrace Pro (tier comercial).
- **En desarrollo** — en construcción activa; el diseño está decidido.
- **Planificada** — especificada, aún sin empezar; puede cambiar.

La categoría de cada módulo de este repositorio se declara en
`licensing.properties`, y la compilación rechaza un grafo de dependencias que
las licencias no puedan sostener.

Última auditoría completa de esta guía contra el código Java: **2026-08-18**.

---

## Captura la historia de tu código (tracing esencial)

| Funcionalidad | Estado | Referencia Java | Notas |
|---|---|---|---|
| Captura automática de narrativa — nombres de método, clase y parámetros, valores de retorno, tiempos, errores; cero sentencias de log | Gratis | `proxy: NarrativeTraceProxy` · `agent: NarrativeClassFileTransformer` · `core: NarrativeContext, TraceEvent` | Tier 1: sin anotaciones, sin configuración. Los nombres de parámetros requieren `-parameters` (el plugin de Gradle lo añade) |
| Anotaciones de enriquecimiento — `@Narrated`, `@OnError`/`@OnErrors` con plantillas `{param}`, `@NarrativeSummary` | Gratis | `core: Narrated, OnError, NarrativeSummary, TemplateParser` | Los placeholders sin resolver se reportan en tiempo de pruebas (`TemplateWarningCollector`); la narración se renderiza en cada llamada trazada, incluidas las llamadas hoja. [annotations-guide.md](guia-de-anotaciones.md) |
| Ocultación de datos sensibles — `@NotTraced` (todas las salidas, siempre) + reglas de ocultación basadas en nombres | Gratis | `core: NotTraced, RedactionPolicy` | Lista de denegación por subcadenas, sin distinguir mayúsculas, sobre nombres de campos; valores por defecto integrados (password, token, ssn, …) + conjuntos personalizados. Las claves de los `Map` se renderizan por la misma ruta protegida (ocultación + límites), nunca con el `toString()` crudo. Los envoltorios de un solo contenido (`Optional`, los opcionales primitivos, `Future`, `AtomicReference`) y los contenedores con forma de lista o de par (`AtomicReferenceArray`, un `Map.Entry` suelto) se abren y su contenido se renderiza con las mismas reglas, de modo que la ocultación no se pierde a un contenedor de profundidad |
| Contrato de finalización void — los métodos void no llevan valor renderizado (`null`, nunca la cadena "null") | Gratis | captura en `proxy` + `agent`; respetado por todos los renderers/exportadores | Markdown/texto no renderizan nada, el JSON omite `returnValue`, los diagramas renderizan ✓, SLF4J registra “← completed”; un `"null"` renderizado siempre significa un retorno null real |
| Blindaje contra inyección en la salida — los valores renderizados no pueden falsificar líneas de log ni romper la sintaxis de Markdown o de los diagramas | Gratis | `core: MarkdownEscape, ControlEscape` · `diagrams: DiagramText` | Saneamiento de caracteres de control, escapado de HTML, ensanchamiento dinámico de los delimitadores de código (code fences) |
| Cinco niveles de captura (OFF → ERRORS → SUMMARY → NARRATIVE → DETAIL), modificables en runtime | Gratis | `core: TracingLevel, NarrativeTraceConfig` | OFF cuesta ~1–2 ns; SUMMARY/NARRATIVE suprimen los valores de parámetros |
| Arquitectura de niveles con dos compuertas — el nivel de captura y el nivel de log son independientes | Gratis | `core: NarrativeTraceConfig` · `slf4j: Slf4jTraceEventListener` | ADR-008. [configuration-guide.md](guia-de-configuracion.md) |
| Resolución de configuración — propiedad de sistema → `narrativetrace.properties`, fallo inmediato ante configuración duplicada | Gratis | `core: ConfigResolver, DuplicateConfigurationException` | Dos archivos de configuración en el classpath son un error duro, no una precedencia silenciosa |
| Captura de concurrencia — grupos fork-join y fire-and-forget (lanzar y olvidar), injerto entre hilos, hilos virtuales, diagnósticos de tiempos de espera y de asincronía secuencial | Gratis | `core: ForkGroup, FireAndForgetGroup, ContextSnapshot` · `render: SequentialAsyncDetector` | Verificado con jcstress; el análisis de tiempos de espera y de asincronía secuencial ocurre al renderizar (salida Markdown). `captureTrace()` está acotado al hilo — captura en el hilo que registra o injerta vía `ContextSnapshot.wrap()` |
| Identidad de la traza — traceId, nombres de traza legibles por humanos, derivación de storyId/chapterId | Gratis | `core: TraceId, SpanContext` · `export: CanonicalEntryMapper` | Alineada con el esquema canónico |
| Escalado de trazas para bucles de alto fan-out — muestreo, límites de anchura/profundidad, modo streaming | Planificada (Gratis) | — | Mientras tanto: exclusión con `TracingLevel.OFF` en bucles calientes |

## Conéctalo a tu stack (integraciones)

| Funcionalidad | Estado | Referencia Java | Notas |
|---|---|---|---|
| Envoltura con proxy dinámico JDK | Gratis | `proxy: NarrativeTraceProxy` | `NarrativeTraceProxy.trace(...)`, sobrecargas para una o varias interfaces |
| Agente Java — instrumentación de bytecode, cero cambios de código, filtrado por paquetes | Gratis | `agent: NarrativeTraceAgent, AgentConfig` | Filtra mediante argumentos del agente o `narrativetrace.properties`. El jar con clasificador `-standalone` empaqueta el núcleo + el puente SLF4J para enganchar `-javaagent` en hosts sin herramienta de build (servidores de aplicaciones); el argumento del agente `loggingJars=` inyecta un proveedor SLF4J vía `appendToSystemClassLoaderSearch` |
| Spring — `@EnableNarrativeTrace`, envoltura automática de beans | Gratis | `spring: EnableNarrativeTrace, NarrativeTraceBeanPostProcessor` | [spring-integration-guide.md](guia-de-integracion-con-spring.md) |
| Micronaut — envoltura de beans + filtro HTTP reactivo | Gratis | `micronaut: NarrativeTraceBeanListener` · `micronaut-http: NarrativeTraceHttpFilter` | Módulos Kotlin-first. [micronaut-integration-guide.md](guia-de-integracion-con-micronaut.md) |
| Filtro de ciclo de vida de peticiones servlet (no requiere Spring) + cableado `@Configuration` de Spring Web | Gratis | `servlet: NarrativeTraceFilter` · `spring-web: NarrativeTraceWebConfiguration` | `@Configuration` basada en import, no auto-configuración de Boot |
| Continuidad de traza entre procesos — `traceparent` del W3C leído en las peticiones entrantes y escrito en las llamadas salientes | Gratis | `core: Traceparent, NarrativeContext.adoptTraceparent/outboundTraceparent` · `servlet: NarrativeTraceFilter` · `micronaut-http: NarrativeTraceHttpFilter` | Una sola historia a través de las fronteras entre servicios. Los filtros adoptan la cabecera entrante; `outboundTraceparent()` da a cualquier cliente HTTP el valor que debe enviar. Peldaño 1 del ADR-014: el span adoptado solo es padre del span raíz, y una cabecera mal formada se ignora en lugar de hacer fallar la petición |
| Extensión de JUnit 5 / reglas de JUnit 4 — escenarios por prueba | Gratis | `junit5: NarrativeTraceExtension` · `junit4: NarrativeTraceClassRule, NarrativeTraceRule` | La autodetección por ServiceLoader es solo de JUnit 5; las reglas de JUnit 4 se declaran explícitamente |
| Plugin de Gradle — cableado de dependencias, `-parameters`, tareas de claridad | Gratis | `gradle-plugin: NarrativeTracePlugin, ClarityCheckTask` | [gradle-plugin-guide.md](guia-del-plugin-de-gradle.md) |
| Propagación de contexto entre hilos vía Micrometer (Spring Boot 3 / Reactor / `@Async`) | Gratis | `micrometer: NarrativeTraceThreadLocalAccessor` | Un único `ThreadLocalAccessor` bajo la clave `"narrativetrace"` |
| Apps de referencia ejecutables — e-commerce, comparación de nombres en Minecraft, préstamo bibliotecario (Kotlin), demo de claridad | Gratis | `examples: ECommerceExample, MinecraftExample, LibraryExample, ClarityDemoExample` | Módulo solo de código fuente con ~100 pruebas de ejemplo |
| Servidores de aplicaciones Jakarta EE / EJB — tracing sin código de un WAR sin modificar vía el agente (verificado con WildFly) | Gratis | `agent: NarrativeTraceAgent` · `examples: ejb4` | WAR EJB 4 libre de dependencias trazado únicamente con `-javaagent` y el jar `-standalone`; receta ejecutable y trampas de WildFly en `narrativetrace-examples/ejb4` |
| Puente de interceptor EJB `@AroundInvoke` (alternativa sin agente) | Planificada (Gratis) | — | En el backlog, sujeta a demanda — la vía del agente de arriba es la forma publicada |
| Opción de contexto con `ScopedValue` de Java 21+ | Planificada (Gratis) | — | En backlog; `ThreadLocalNarrativeContext` funciona hoy con hilos virtuales |

## Lee la historia (salidas)

| Funcionalidad | Estado | Referencia Java | Notas |
|---|---|---|---|
| Renderers de texto indentado y Markdown (conectados a la salida de pruebas); renderer de prosa (API de biblioteca) | Gratis | `core: IndentedTextRenderer, MarkdownRenderer, ProseRenderer` | La prosa aún no es una opción de `narrativetrace.format` — solo API + ejemplos. Markdown renderiza el retorno del padre en línea en la entrada (sin repetición de cierre) |
| Referencias de valores en la traza — deduplicación por contenido de valores capturados repetidos con etiquetas legibles (`‹Hotel›=completo` en la primera emisión, `‹Hotel›` después) | Gratis | `core: ValueReferenceIndex` (vía `MarkdownRenderer`) | Etiquetas desde el campo de identidad del valor estructurado (name/id/description/…), nunca desde un campo ocultado; la igualdad de bytes certifica la mismidad — cualquier diferencia se renderiza completa; la contención dentro de otros valores capturados cuenta y se reemplaza |
| Deltas de valores dentro de la traza — una recaptura de la misma entidad, ya cambiada, se renderiza como una diferencia respecto de la referencia (`‹Dinner›′{amount: 100.0→92.0, currency: "USD"→"EUR"}`) | Gratis | `core: ValueDelta` (vía `ValueReferenceIndex`, `MarkdownRenderer`) | «Misma entidad» es el mismo nombre de tipo estructurado más un campo de identidad igual — la misma escalera que da nombre a la etiqueta; bytes renderizados distintos significan que cambió. La diferencia se calcula a partir de los dos valores estructurados y nombra solo los campos escalares que cambiaron (cadena, entero, decimal, booleano, instante, nulo), sin reconstruir nunca un render plano a partir de uno estructurado. Todo lo que no puede expresar — un objeto o lista anidada que cambió, un conjunto de campos distinto, un valor sin campo de identidad — se renderiza completo exactamente como antes, sin estampar una etiqueta en una definición a la que nada vuelve a referirse. Una variante cambiada que a su vez se repite se define COMO la diferencia (`‹Dinner·2›=‹Dinner›′{…}`), así que sigue ganando una etiqueta reutilizable, y una iteración de bucle plegada se nombra por su diferencia en la línea `×k more`. Los decimales viajan como `double`: un `BigDecimal` capturado como `100.00` se imprime `100.0` en la diferencia, mientras que la línea de referencia sigue mostrando el texto original. A diferencia de las diferencias entre ejecuciones, la línea base está dentro del mismo documento, así que el artefacto sigue siendo autocontenido. Solo presentación y solo Markdown |
| Plegado de bucles — condensa subárboles hermanos repetidos con la misma forma en Markdown | Gratis | `core: LoopFold, StructuralTraceRenderer#subtreeKey` (vía `MarkdownRenderer`) | Una serie máxima de ≥2 hermanos secuenciales estructuralmente idénticos consecutivos renderiza la primera iteración completa y luego una sola línea `×k more: ‹Dinner›, ‹Taxi› — same flow (validate ✓ → record ✓) — 12ms total, 2–5ms each`. «Misma forma» es la proyección estructural sin valores (se reutiliza el oráculo del ítem 8): firmas, forma de los hijos y tipos de resultado iguales — una llamada divergente, una única excepción o un tipo de resultado distinto se renderiza completo fuera del plegado (la anomalía es la señal); una serie de iteraciones que lanzan la misma excepción sí se pliega. Cada iteración plegada se nombra por su primer argumento distintivo — como una diferencia `‹ref›′{…}` cuando el documento ya define esa entidad, y si no mediante la escalera de identidad (`ValueReferenceIndex`, coherente con usos posteriores de `‹ref›`) — posicional `#n` cuando no aplica ningún campo de identidad; las duraciones se agregan (total + rango, nunca por iteración); las etiquetas se limitan a 6 y la cola de flujo a 8 con `…`. Solo presentación y solo Markdown — JSON, `.nt`, diagramas y vistas traducidas quedan sin cambios; la concurrencia (grupos fork, fire-and-forget) nunca se pliega |
| Archivos de traza por prueba — Markdown a todo detalle con frontmatter YAML + JSON canónico + diagrama Mermaid por escenario | Gratis | `core: TraceTestSupport, TraceFileWriter, FrontmatterBuilder` | `build/narrativetrace/traces/<Class>/<test>.md` + `.json` + `diagrams/` |
| Archivos de traza estructurales seguros para IA — artefacto separado sin valores por prueba, más un flujo estructural en vivo | Gratis | `core: StructuralTraceRenderer, StructuralProjection, TraceTestSupport` · `slf4j: StructuralSubscriber` | `structural/<Clase>/<escenario>.nt` por escenario — solo nombres, jerarquía y tipos de resultado; determinista (bytes idénticos para comportamiento idéntico); especificación del formato: [structural-trace-format.md](../structural-trace-format.md). `narrativetrace.structuralJson=true` emite además `<test>.structural.json` — entradas schema 1.2 con todos los campos de valores de runtime elididos (ADR-002 Nivel 1; las adiciones de 1.2 tienen forma de identidad y sobreviven la proyección); `StructuralSubscriber` en la costura de la tubería emite la misma proyección en vivo como líneas JSON en el logger `narrativetrace.ai.structural` |
| Exportación de JSON canónico (esquema de árbol de capítulos) | Gratis | `core: JsonExporter, ChapterExporter, CanonicalEntry, CanonicalEntryMapper` | El `.json` por prueba está conectado y validado contra `chapter-tree.schema.json` por una puerta de conformidad que ejerce el escritor real; `narrativetrace.canonicalJson=true` escribe además un `.canonical.json` por prueba (entradas canónicas planas — el formato para ports y fixtures de conformidad). `nt.schemaVersion` es global entre las entradas y el objeto de capítulo — 1.2 en todas partes, estampado desde `CanonicalEntry.SCHEMA_VERSION` (= 1.1 + campos de identidad de amplitud de captura). La identidad del capítulo es inmediata: `trace_id`, `nt.storyId` y `nt.chapterId` se escriben siempre — el trace id se adopta, se hereda o se genera (nunca una constante compartida), la historia se deriva de la primera llamada de nivel raíz y el capítulo es igual a ella — y se resuelve una sola vez por árbol, de modo que un capítulo y sus propias entradas nombran siempre la misma traza. El exportador de capítulos es API de biblioteca aún sin un llamador en producción |
| Diagramas de secuencia por escenario — Mermaid + PlantUML | Gratis | `diagrams: MermaidSequenceDiagramRenderer, PlantUmlSequenceDiagramRenderer` | Mermaid se emite automáticamente por escenario; PlantUML vía `narrativetrace.format=plantuml` (reemplaza la traza Markdown) |
| Resúmenes de pruebas en consola con puntuaciones de claridad + narrativas de fallo | Gratis | `core: ConsoleSummaryReporter` · `output: TraceTestSupport` | Ante un fallo, el informe localiza el cambio: el delta estructural contra el último verde cuando existe una línea base (resumen + diff legible), la traza completa en caso contrario; las rutas de traza se imprimen como enlaces `file://` clicables |
| Delta narrativo en el bucle de pruebas — delta estructural de una línea tras cada ejecución + modo de aprobación con líneas base versionadas | Gratis | `core: StructuralDelta, ScenarioDelta, NarrativeApproval` · `junit5/junit4` · `gradle-plugin: approveNarratives` | El `.nt` en disco es la línea base del último verde — una ejecución fallida compara contra ella pero nunca la sobrescribe. El pie de suite termina con `Since last green: 4 scenarios unchanged · 1 new · 1 changed: "…" (+4 calls X.y)`. Modo de aprobación (`narrativetrace.approval=true`; DSL del plugin `approval.set(true)`, líneas base por defecto en `src/test/narratives/<Clase>/<escenario>.approved.nt`): una prueba que pasa pero cuya estructura difiere de su línea base versionada falla con un diff legible, la estructura actual queda al lado como `.received.nt`, y `approveNarratives` promueve los archivos revisados. El mecanismo es Gratis por la decisión de niveles del 2026-08-23; el diff semántico, la revisión por PR-bot y la analítica de deriva son Pro |
| Resúmenes de flujo — rutas agregadas + frecuencias por punto de entrada | Pro | Repositorio Pro | Frontera del núcleo Gratis exigida con ArchUnit (`ArchitectureTest`) |
| Diffs de migración — comparación de comportamiento antes/después | Pro | Repositorio Pro | |
| Grafos de dependencias en runtime (siempre llamadas vs condicionales) | Pro | Repositorio Pro | |
| Diagramas de secuencia agregados — todas las ramas observadas en un solo diagrama, `alt/else` + conteos de frecuencia | En desarrollo (Pro) | Repositorio Pro | |
| Diagramas de actividad agregados | Planificada (Pro) | — | |
| Salidas Pro sin configuración — añade los jars Pro y los informes/diagramas agregados aparecen en la siguiente ejecución de pruebas, sin cableado | En desarrollo (Pro) | Repositorio Pro | Vía las costuras ServiceLoader del núcleo (ADR-010) |

## Conserva tu stack de logging (logging + observabilidad)

| Funcionalidad | Estado | Referencia Java | Notas |
|---|---|---|---|
| Puente SLF4J — eventos de narrativa a través de tus appenders existentes, niveles de log por tipo de evento, flujo síncrono a prueba de caídas | Gratis | `slf4j: Slf4jTraceEventListener` · `core: DualPathPipeline` | Logger `narrativetrace`; por defecto ENTRY/RETURN=TRACE, EXCEPTION=WARN |
| Enriquecimiento de MDC — modelo de atributos de tres niveles (recurso / traza / span), campos persistentes con alcance de petición | Gratis | `core: AttributeTier, SpanContext` · `servlet: NarrativeTraceFilter` | ADR-009 |
| Coexistencia con logs escritos a mano | Gratis | — | Elimínalos a tu propio ritmo |
| Descarte ruidoso — una captura que perdió eventos lo dice en la narrativa que escribe | Gratis | `api: TraceLoss, TraceTree.loss()` · `core: LossFooter, BoundedEventBuffer, BufferedEventConsumer` | La ruta con búfer es best-effort por diseño, así que nunca guarda silencio: los eventos descartados se cuentan (sobrescrituras del anillo, descartes del drenaje adaptativo y caídas de suscriptor por igual) y todo formato con ranura de pie de página lleva una línea con el recuento y `narrativetrace.buffer.capacity`. Texto, prosa, Markdown (blockquote + `incomplete: true` en el frontmatter), Mermaid y PlantUML (sintaxis de comentario). El artefacto estructural `.nt` deliberadamente **no** lo lleva: es la línea base de aprobación y debe permanecer idéntica byte a byte para un mismo comportamiento. Una captura limpia no dice nada |
| Exportación de spans a OpenTelemetry — por lotes tras la captura y listener en vivo, atributos tipados, eventos de negocio en los spans padre | Gratis | `opentelemetry: TraceSpanExporter, OtelTraceEventListener, SpanContextAttributeMapper` | El listener en vivo acota los spans activos con TTL |
| MDC consciente de PII — campos de identidad convertidos mediante hash en tokens buscables (`@MdcField(pii = true)`) | Planificada (Pro) | — | |
| Identidad de infraestructura — contexto de k8s / nube / contenedor autodetectado como atributos de recurso | Planificada (Pro) | — | |
| Claves OTel semánticas — `@SpanAttribute("order.total_usd")`, `@SpanEvent("order.shipped")` | Planificada (Pro) | — | |
| Opciones de pipeline de alto rendimiento — LMAX Disruptor; modo durable/WAL con Chronicle Queue | Planificada (Pro) | — | Ver ADR-006 |

## Mejora el código (diagnósticos de claridad)

| Funcionalidad | Estado | Referencia Java | Notas |
|---|---|---|---|
| Puntuación de claridad — calidad de los nombres de métodos / clases / parámetros a partir de la ejecución real | Gratis | `clarity: ClarityAnalyzer` + `MethodNameScorer, ClassNameScorer, ParameterNameScorer, CohesionScorer` | [clarity-guide.md](guia-de-claridad.md); experimental |
| Informe de claridad a nivel de suite con objetivos de renombrado + notas por elemento + resultados legibles por máquina | Gratis | `clarity: ClarityReportRenderer, ClarityJsonExporter, ElementNoteComposer` | `clarity-report.md` + `clarity-results.json` (el contrato que consumen `clarityCheck` y las herramientas de CI). Una tabla **Elements** / array `elements` da una nota didáctica por elemento en cada puntuación (la claridad es una maestra, no una jueza), separada de las incidencias limitadas por umbral. El schema 1.2 de resultados añade `elements` por escenario (el 1.1 añadió `suiteIssues`); los consumidores deben seguir aceptando archivos 1.0/1.1 |
| Escáner independiente — puntúa clases compiladas sin ejecutar pruebas (`clarityScan`, CLI) | Gratis | `clarity: ClarityScanner, ClarityScannerMain` | CLI `--format markdown\|json\|both` |
| Vocabulario del proyecto en la puntuación — el glosario commiteado extiende los diccionarios integrados | Gratis | `clarity: DomainVocabulary, ProjectVocabularySource` · `glossary: GlossaryVocabulary, GlossaryAwareClarityScannerMain` · `junit5/junit4/gradle-plugin` | Un fichero, un flujo de revisión: los verbos del `glossary.json` commiteado puntúan como verbos del dominio, sus sustantivos como tokens del dominio, y su sección raíz `abbreviations` (esquema 2) declara la abreviatura aceptada — que un token aparezca dentro de una frase commiteada no basta. Una abreviatura listada queda aceptada *y* se desarrolla a partir de la expansión declarada. La lectura es incondicional (a diferencia de la recolección); solo cuenta el fichero *commiteado*, así que una ejecución no puede expandir su propio vocabulario. Los niveles integrados conservan su autoridad — verbos genéricos, prefijos booleanos, marcadores sin significado, sinónimos obsoletos y términos `stale` nunca se promocionan |
| Compuerta de CI (`clarityCheck`) | Gratis | `gradle-plugin: ClarityCheckTask` | Se une a `check` y hace fallar el build por defecto; configura `warnOnly` para modo consultivo |
| Tendencias históricas de claridad | Planificada (Pro) | — | |
| Renombrado asistido por IA con evidencia de trazas | Planificada (Pro) | — | |
| Detección de código muerto y ramas no probadas a partir de trazas | Planificada (Pro) | — | |
| Análisis de redundancia de logs — qué logs escritos a mano vuelve innecesarios la narrativa, con ahorro de tokens | Planificada (Pro) | — | |

## Habla el lenguaje del dominio (glosario y traducción)

| Funcionalidad | Estado | Referencia Java | Notas |
|---|---|---|---|
| Glosario de dominio — un único archivo de lenguaje ubicuo por repositorio (JSON canónico + vista Markdown), recolectado aditivamente desde las trazas en tiempo de pruebas | Gratis | `glossary: Glossary, GlossaryJsonWriter, GlossaryJsonReader, GlossaryMarkdownRenderer, GlossaryHarvester, GlossaryMerger, GlossarySuiteHarvest` · `junit5: NarrativeTraceExtension` | Opt-in: `narrativetrace.glossary=true` (`narrativeTrace { glossary.set(true) }`), porque escribe fuera del directorio de build |
| Recolección del glosario desde clases compiladas — `glossaryScan`, el único modo que recolecta las plantillas `@Narrated`/`@OnError` | Gratis | `glossary: GlossaryStaticScanner, GlossaryScannerMain` · `gradle-plugin: NarrativeTracePlugin` | Estático por diseño: una traza capturada lleva la narración con los valores de runtime ya interpolados |
| Términos canónicos + sinónimos obsoletos — el uso no canónico se marca en la salida de la ejecución y se suprime de la recolección | Gratis | `glossary: AliasIndex, VocabularyViolations, RenameSuggester, VocabularySummaryFormatter, NonCanonicalTermIssues, GlossaryUsageReport` | Un término por concepto. Las violaciones llegan a la consola, a `glossary-usage.json` y a `clarity-report.md` ("Suite Issues") / `clarity-results.json` (`suiteIssues`, schema 1.1); la puerta `clarityCheck` es de solo advertencia por defecto y falla en firme vía `clarity.maxSuiteIssues`. La comprobación de vocabulario solo se activa cuando ya existe un `glossary.json` commiteado antes de la ejecución |
| Contextos delimitados — vocabulario acotado por contextos mapeados a paquetes | Gratis | `glossary: BoundedContext, ContextResolver, TermNormalizer` | El mismo término puede diferir por contexto; mapeo de paquetes por prefijo más largo consciente de delimitadores, con `_unassigned` como respaldo. Las reglas de `TermNormalizer` (lista de conservación de singulares terminados en s, regla de raíz estable, idempotencia) son la identidad de los términos en los glosarios persistidos — cada port debe adoptarlas al pie de la letra |
| Vistas de traducción de trazas — archivos Markdown por traza renderizados en vivo desde la tubería de eventos + glosario | Gratis | `glossary: TraceTranslationView, TranslationSubscriber, GlossaryTranslator, GlossaryLoader` | Los valores nunca se traducen; un `<traceId>.md` por traza, el pie lista los vacíos del glosario |
| Flujo traducido en vivo — loggers SLF4J con sufijo de locale, enrutables por appender al mismo destino o a uno separado | Gratis | `glossary: TranslationSubscriber` | Ruta de mejor esfuerzo; el flujo canónico queda intacto |
| Traducción del glosario y generación de definiciones asistidas por IA | Planificada (Pro) | — | Solo términos del glosario; opt-in explícito |
| Diagnóstico de filtración de términos entre contextos | Planificada (Pro) | — | |

## Deja que los agentes de IA vean la verdad del runtime (integración con IA)

| Funcionalidad | Estado | Referencia Java | Notas |
|---|---|---|---|
| Trazas estructurales seguras para IA — sin valores de runtime, cero superficie de inyección | Gratis | `core: StructuralTraceRenderer, StructuralProjection` | Ya disponible: el artefacto `.nt` distinto (ADR-002 completo) más la proyección `.structural.json` tras el flag, con su seguridad fijada por un test de propiedades; los niveles SUMMARY/NARRATIVE suprimen además los valores en la salida orientada a personas |
| Documentación orientada a LLM (`llms.txt`, `llms-full.md`) | Gratis | `documentation/llms.txt, llms-full.md` | |
| Herramientas de análisis MCP — trazas de ejecución, grafo de dependencias, ramificación, sugerencias de renombrado, informe de claridad, nivel de captura, comparación de trazas | Pro | Repositorio Pro | Disponible hoy como biblioteca |
| Servidor MCP (transporte stdio — conecta Claude Code / Cursor directamente) | En desarrollo (Pro) | Repositorio Pro | |
| Herramientas de inteligencia de runtime — perfil de rendimiento, salud de la arquitectura, análisis de brechas de pruebas, contratos de comportamiento, impacto de refactorizaciones | Planificada (Pro) | — | |
| Salida IA seudonimizada (Nivel 2) — tokens sintéticos, flujo de datos preservado, valores reales destruidos | Planificada (Pro) | — | |
| Salida IA selectiva / a todo detalle (Niveles 3–4) con saneamiento `@UntrustedInput` | Planificada (Pro) | — | |

## Demuestra lo que ocurrió (auditoría y cumplimiento — Pro)

| Funcionalidad | Estado | Referencia Java | Notas |
|---|---|---|---|
| Anotaciones de auditoría y SecOps — `@AuditEvent`, `@SecurityEvent`, `@AuditActor`, `@AuditEntityId`, `@AuditField` | Pro | Repositorio Pro | |
| Inferencia determinista — resolución de acción, actor, entidad y resultado | Pro | Repositorio Pro | |
| Motor de políticas — `AUDIT_RELAXED` / `AUDIT_STRICT` / `SECOPS_STRICT`, filtrado por clasificación | Pro | Repositorio Pro | |
| Enmascaramiento de campos — `LAST4`, `REDACT`, `HASH` | Pro | Repositorio Pro | |
| Verificador de gobernanza — validación de cumplimiento de anotaciones en tiempo de build | Pro | Repositorio Pro | |
| Eventos JSON estructurados con versión de esquema + correlación de trazas | Pro | Repositorio Pro | |
| Entrega durable — sink síncrono en la ruta de logging, enrutamiento de audit/secops | En desarrollo (Pro) | Repositorio Pro | Ver ADR-005 para el porqué de esta ruta |
| Intercepción de Spring para anotaciones de auditoría | En desarrollo (Pro) | Repositorio Pro | |
| Tarea Gradle de gobernanza + acción de CI | En desarrollo (Pro) | Repositorio Pro | |
| Trazabilidad de controles — `controls={"AU-05"}`, registro de controles, mapeo entre marcos (PCI-DSS / SOC 2 / NIST / ISO), informes de cobertura | En desarrollo (Pro) | Repositorio Pro | |
| Artefactos de cumplimiento — exportación del informe de gobernanza, esquema de eventos de auditoría, evidencia por versión publicada | En desarrollo (Pro) | Repositorio Pro | |
| Fábricas de políticas para estándares de cumplimiento, enmascaramiento de PAN (`FIRST6_LAST4`), IP de origen | Planificada (Pro) | — | |
| Adaptadores de sink — Pangea, WorkOS, SIEM/HEC | Planificada (Pro) | — | |
| Encadenamiento de hashes con evidencia de manipulación + verificador | Planificada (Pro) | — | |
| Seudonimización de actores para GDPR (derecho de supresión) | Planificada (Pro) | — | |
| Exportación OCSF para interoperabilidad con SIEM | Planificada (Pro) | — | |

---

## Mantener esta guía honesta

Esta guía existe para que ninguna funcionalidad se pierda entre el
código, los planes y los documentos de visión — y para que nada se lea
como publicado cuando no lo está. Reglas:

1. Cada funcionalidad visible para el usuario aparece aquí, exactamente
   una vez, con un estado.
2. Una funcionalidad pasa a **Gratis**/**Pro** solo cuando está
   fusionada, probada y documentada. "En desarrollo" significa que el
   diseño está decidido y el trabajo está programado; "Planificada"
   significa solo especificada.
3. Los cambios que añaden o promueven una funcionalidad deben actualizar
   este archivo en el mismo commit.
4. **El código Java es la fuente de referencia (golden source).** Cada
   fila Gratis publicada cita su implementación de referencia
   (`module: main classes`). Cuando la guía y el código discrepan, el
   código tiene razón y la guía es el bug — corrige la fila y registra
   la fecha de auditoría en el encabezado.
5. **Los ports derivan, nunca bifurcan.** Los ports de plataforma
   (TypeScript, Python, .NET) implementan a partir de esta guía más el
   código Java referenciado, y registran solo sus mecanismos por
   plataforma en sus propios repositorios. El estado publicado/pendiente
   por plataforma pertenece a la matriz de paridad —
   `feature-parity.md`, un registro de trabajo privado — no a copias de
   este catálogo.
