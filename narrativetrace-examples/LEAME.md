<!-- source: narrativetrace-examples/README.md blob 67791bf552ec | translated: 2026-08-28 | reviewed: 2026-09-03 -->
# Ejemplos de NarrativeTrace

[English](README.md) | **Español** | [简体中文](自述文件.md)

Tutoriales ejecutables que muestran NarrativeTrace en la práctica. Este módulo **no se
publica** — existe para que un desarrollador (o un agente de IA) pueda ejecutar un
escenario realista, leer la traza resultante y conectarla con el código que la produjo.

Trata los subproyectos como tutoriales con un orden sugerido, no como una API
reutilizable. Cada paquete de ejemplo lleva un `package-info.java` con un orden de
lectura guiado; empieza por ahí cuando profundices en uno.

## Mapa de subproyectos

| Subproyecto | Lenguaje | Punto de entrada | Qué enseña |
|---|---|---|---|
| `ecommerce` | Java | `ECommerceExample` | El ejemplo insignia: trazar un grafo de servicios Spring realista — cableado de beans, auto-proxy, logging de eventos con SLF4J, propagación de contexto asíncrona con Micrometer, escenarios de éxito y de fallo. |
| `clarity` | Java | `ClarityDemoExample` | Cómo el subsistema de claridad puntúa la calidad de los nombres, usando un dominio de reservas de hotel con nombres deliberadamente excelentes, adecuados y pobres. |
| `minecraft` | Java | `MinecraftExample` | Cuánto cambia la calidad de la traza solo con los nombres: el mismo comportamiento trazado dos veces, una con nombres ricos en dominio y otra con nombres genéricos. |
| `library` | Kotlin | `LibraryExample` | Usar NarrativeTrace desde Kotlin: trazar servicios Kotlin mediante proxies dinámicos en un pequeño dominio de préstamo de libros. |
| `ejb4` | Java | `WildFlyAgentNarrationTest` *(Docker)* | Un WAR EJB 4 (Jakarta EE 10) sin modificar — servlet → EJB → servicios — trazado dentro de WildFly solo por el agente java, escrito deliberadamente en *estilo* de la era EJB 2.x; más el informe de claridad «renombra estos» sobre sus nombres de época. |
| `common` | Java | *(utilidad, sin tarea `run`)* | Herramientas compartidas de los ejemplos: `PlantUmlImageRenderer` convierte los diagramas de traza `.puml` generados en imágenes SVG; `AsciiSequenceDiagram` los dibuja como texto Unicode para la consola. |

## Ejecutar los ejemplos

Cada subproyecto de ejemplo registra una tarea `run` con su propia configuración de
logback:

```bash
./gradlew :narrativetrace-examples:ecommerce:run
./gradlew :narrativetrace-examples:clarity:run
./gradlew :narrativetrace-examples:minecraft:run
./gradlew :narrativetrace-examples:library:run

./gradlew :narrativetrace-examples:runExamples   # los cuatro en secuencia
```

`ejb4` no tiene tarea `run` — se ejecuta como WAR dentro de un contenedor WildFly,
impulsado por su prueba etiquetada `docker` (ver su sección más abajo):

```bash
./gradlew :narrativetrace-examples:ejb4:dockerTest   # requiere Docker
```

## Lanzador de demos

La raíz del repositorio incluye `./demo.sh`, la forma más rápida de ver los ejemplos:
un solo comando, sin ruido de Gradle, la narración en vivo coloreada e indentada por
profundidad de llamadas, y cada renderizado anunciado como su propia sección.
`./demo.sh` abre un selector interactivo; `--example <nombre>` ejecuta sin
interacción; `--list` enumera los ejemplos.

**Recorre, no se desplaza.** En una terminal la demo se detiene después de cada
escenario — `[Enter]` avanza, `q` sale — y cada escenario abre con una nota sobre *cómo
está configurada la traza de ese escenario*: aquí Spring AOP mediante
`@EnableNarrativeTrace`, allí un `NarrativeTraceProxy` simple, y cuál de `@Narrated` /
`@OnError` / `@NotTraced` produjo lo que estás a punto de leer. Las notas viven en
`demo/wiring.awk`, indexadas por título de escenario, y la tarea de build
`demoWiringCheck` falla si un escenario pierde su nota o si una nota sobrevive a su
escenario. Las ejecuciones pausadas se graban primero y se recorren después, de modo que
una parada nunca puede inflar las duraciones que informa el árbol de trazas;
`--no-pause` reproduce la ejecución de corrido, en vivo, y es lo que reciben las
tuberías, la CI y la grabación VHS.

**De dónde salen los renderizados** se responde una vez por ejecución, en la primera
sección de renderizado, porque es la siguiente pregunta de cualquiera que vea la demo.
No hay renderizador por defecto ni nada que configurar: la captura produce un
`TraceTree` y tú llamas al renderizador que quieras
(`new IndentedTextRenderer().render(trace)`), siendo `NarrativeRenderer` un único
método, así que el tuyo es una lambda. Las líneas en vivo `→ ← !!` no son un
renderizador — eso es `Slf4jTraceEventListener` en el `DualPathPipeline`, la única
vista que no cuesta código de renderizado. La configuración elige un renderizador en
exactamente un lugar, los archivos de traza escritos desde las pruebas:
`narrativetrace.output=true` más `narrativetrace.format=markdown|text|mermaid|plantuml`,
donde `markdown` es el valor por defecto y el `narrativeTrace { format = … }` del plugin
de Gradle fija la misma propiedad. Cada marcador de sección nombra el renderizador que
lo produjo.

**La salida de logs clásica es un modo de primera clase.** La narración es SLF4J
corriente sobre logback corriente, así que se renderiza en el formato tradicional que
ingiere cualquier herramienta de logs — timestamps completos
`yyyy-MM-dd HH:mm:ss.SSS`, nivel, hilo y nombre del logger (ecommerce lleva además el
`traceId` de MDC). Tres lugares donde verla:

- `./demo.sh --example <nombre> --classic` — la ejecución completa con la
  configuración de logback propia del ejemplo, sin adornos.
- La demo de ecommerce muestra un escenario ("Cliente desconocido") en formato
  clásico dentro de la propia ejecución — los mismos eventos que los escenarios
  estilizados, solo cambia la presentación.
- El `./gradlew :narrativetrace-examples:<nombre>:run` normal usa el mismo formato
  tradicional mediante los `logback-<nombre>.xml` incluidos.

**Las trazas traducidas también son un modo de primera clase.** Cada ejemplo versiona
un glosario de dominio (`<ejemplo>/glossary.json` — un contexto delimitado con
traducciones curadas al español y al chino), y `./demo.sh --example <nombre> --lang es`
(o `zh-CN`) re-renderiza la misma ejecución a través de él: los identificadores y las
plantillas `@Narrated` aparecen en el idioma elegido con los nombres originales entre
paréntesis, mientras que los valores de parámetros, los valores de retorno y los
mensajes de excepción permanecen byte a byte idénticos. El selector interactivo ofrece
exactamente los locales que porta el glosario del ejemplo. Las frases sin traducir se
recogen en un pie de "vacíos del glosario" — la cola de trabajo de curación — y los
escenarios mal nombrados (el mundo sin refactorizar de minecraft, el procesamiento
legacy de clarity) quedan sin traducir a propósito: los nombres que no cuentan ninguna
historia no pueden traducirse a una.

## Los ejemplos en detalle

### ecommerce — aplicación Spring de estilo productivo

El ejemplo más completo. `ECommerceExample` ejecuta seis escenarios:

1. **Pedido exitoso + notificación asíncrona** — el camino feliz que se ramifica en
   trabajo asíncrono, renderizado como texto indentado, prosa y un diagrama de secuencia
   Mermaid.
2. **Fallo de pago — bug de fuga de inventario** — la traza expone que se llamó a
   `InventoryService.reserve` pero nunca a `release`: una demostración de cómo las trazas
   sacan a la luz bugs reales.
3. **Servicio externo inestable** — un decorador (`FlakyNotificationService`) que envuelve
   un cliente HTTP real (`JsonPlaceholderNotificationService`) tiene éxito una vez y luego
   falla.
4. **Cliente desconocido** — rama de fallo por validación de entrada.
5. **Sin stock** — rama de fallo por regla de negocio, dibujada además como diagrama de
   secuencia ASCII en la consola mediante PlantUML.
6. **Captura asíncrona explícita** — capturas de traza separadas del hilo principal y del
   hilo trabajador mediante `CompletableFuture` sobre un `ThreadPoolTaskExecutor` de
   Spring.

Clases de apoyo clave: `ECommerceConfig` (cableado de Spring + NarrativeTrace +
Micrometer), `DefaultOrderService` (la orquestación que produce las trazas interesantes),
`ContextPropagatingTaskDecorator` (lleva el contexto de traza a través de los límites
entre hilos), más adaptadores simples en memoria para catálogo, inventario, pago,
cliente, descuento y envío para que la traza sea fácil de seguir.

La suite de pruebas también sirve de documentación: `SpringIntegrationTest` (cableado del
auto-proxy), `ConcurrencyScenarioTest` (captura en paralelo), `MarkdownDocumentTest`
(renderizar una traza como documento Markdown completo con frontmatter) y pruebas
unitarias por servicio usando la extensión de JUnit 5 `NarrativeTraceExtension`.
`NotificationServiceTest` está etiquetada como `network` porque realiza llamadas HTTP
reales — la etiqueta se excluye de todas las tareas de pruebas por defecto.

### clarity — qué premia y qué penaliza el analizador de claridad

Cuatro escenarios sobre un dominio de reservas de hotel, cada uno en un nivel distinto de
calidad de los nombres:

1. **Un huésped reserva una habitación** — nombres excelentes, específicos del dominio
   (`DefaultReservationService`).
2. **Reserva a través del manager** — nombres adecuados pero menos expresivos
   (`DefaultBookingManager`).
3. **Procesamiento de datos legado** — nombres intencionadamente débiles
   (`DefaultDataProcessor`) que el analizador debería penalizar.
4. **Operaciones del repositorio de huéspedes** — un escenario centrado en la cohesión.

Tras ejecutar todos los escenarios, alimenta las trazas capturadas al `ClarityAnalyzer` e
imprime la salida de `ClarityReportRenderer`, de modo que puedas conectar cada puntuación
con las decisiones de nomenclatura que la causaron.

### minecraft — calidad de los nombres, contrastada directamente

Dos paquetes realizan un trabajo comparable de "el jugador se une al mundo":

- `refactored/` — nombres ricos en dominio: `WorldGenerator`, `PlayerInventory`,
  `CraftingTable`, `CreatureSpawner`, `WorldServer`.
- `unrefactored/` — la misma intención escondida tras etiquetas genéricas:
  `DataProcessor`, `StateManager`, `ThingFactory`, `EntityHandler`, `GameManager`.

`MinecraftExample` ejecuta ambos uno tras otro para poder comparar las trazas lado a
lado. Es un material didáctico sobre nomenclatura y observabilidad, no un ejemplo de
juego. Las pruebas de `unrefactored/**` se excluyen de las tareas de salida de trazas de
abajo para que los documentos de traza generados solo muestren el buen vocabulario.

### library — consumidor Kotlin

Un pequeño dominio de préstamo de libros (`CatalogService`, `MemberService`,
`LendingService`) trazado mediante `NarrativeTraceProxy` desde Kotlin, con un préstamo
exitoso y un escenario de fallo con `BookUnavailableException`, renderizados como texto,
prosa, Mermaid y un diagrama de secuencia ASCII. El build establece
`javaParameters.set(true)` para que los nombres
reales de los parámetros sobrevivan en la traza — imprescindible para trazas Kotlin
legibles.

### ejb4 — WAR EJB 4 sin modificar bajo el agente java

Una aplicación EJB 4 de reclamaciones de seguros (servlet → EJB sin estado → servicios)
escrita en deliberado *estilo* de la era EJB 2.x —
`ClaimsProcessorBean`, `PolicyLookupEJB`, `FraudChkMgr`, `CoverageCalcEJB` — compilada
solo contra APIs provistas por el contenedor y empaquetada como un WAR con `WEB-INF/lib`
vacío: **no tiene ninguna dependencia de NarrativeTrace**. `WildFlyAgentNarrationTest`
(etiquetada `docker`, excluida de la tarea de pruebas por defecto) despliega ese WAR en
WildFly dentro de Docker con solo `-javaagent:narrativetrace-agent-<version>-standalone.jar`
adjunto, presenta una reclamación por HTTP y verifica desde el log del contenedor la
cadena narrada completa — servlet, proxy del contenedor EJB, beans y servicios con
parámetros y valores de retorno.

**Estilo frente a tecnología.** La tecnología es actual — Jakarta
Enterprise Beans 4.0 (`jakarta.*`, Jakarta EE 10) sobre WildFly, con el modelo de
programación por anotaciones de EJB 3.x (`@Stateless`, `@Singleton @Startup`, vistas sin
interfaz, inyección `@EJB`, sin `ejb-jar.xml`, sin interfaces home/remote). Solo la
*arquitectura y los nombres* imitan las bases de código de la era EJB 2.x; ese estilo de
época es de lo que se alimenta el informe de claridad. Aquí no hay soporte del espacio
de nombres `javax.*` ni de EJB 2.x, y los servidores Java 8 / EAP 6–7 quedan fuera de
alcance (la biblioteca núcleo es idiomática de Java 17; el registro de decisiones del
plan esboza un nivel grabador mínimo aparte si tales clientes llegaran a
materializarse). Lo que el ejemplo prueba: un contenedor de servidor de aplicaciones
real, proxies de contenedor reales, carga de clases JBoss-modules real — narrado con un
solo flag `-javaagent`.

Los nombres deliberadamente malos sirven además de enlace con claridad:
`Ejb4NamingClarityTest` (tarea `test` normal, sin Docker) ejecuta `ClarityScanner`
sobre las clases compiladas y escribe `build/narrativetrace/ejb4-clarity-report.md`,
el informe «renombra estos» («Spell out: chk → check, mgr → manager»). Las trampas de
WildFly las fija el propio arnés de Docker (el `JAVA_OPTS` completo, el
`jboss.modules.system.pkgs` exacto, esperar el registro del contexto de Undertow en vez
de la línea `Deployed`) — consulta `WildFlyAgentNarrationTest` para los ajustes exactos.

### common — utilidad de renderizado de diagramas

`PlantUmlImageRenderer` convierte los archivos `.puml` producidos por las tareas de traza
en imágenes `.svg` para que la salida de diagramas pueda verse fuera de herramientas de
solo texto. Sus recursos de prueba también muestran la salida de trazas controlada por
archivo de propiedades (`junit-platform.properties` con `narrativetrace.output=true`).

## Tareas de salida de trazas

Cada subproyecto de ejemplo recibe estas tareas (definidas en el `build.gradle.kts` de
este directorio). Ejecutan la suite de pruebas del ejemplo con la salida de trazas
activada y escriben un archivo por prueba en `<subproyecto>/build/narrativetrace/`:

```bash
./gradlew :narrativetrace-examples:ecommerce:traceTests      # documentos Markdown
./gradlew :narrativetrace-examples:ecommerce:traceTexts      # texto indentado
./gradlew :narrativetrace-examples:ecommerce:traceMermaid    # diagramas de secuencia Mermaid
./gradlew :narrativetrace-examples:ecommerce:tracePlantUml   # diagramas de secuencia PlantUML
./gradlew :narrativetrace-examples:ecommerce:renderDiagrams  # tracePlantUml + .puml → .svg

./gradlew :narrativetrace-examples:traceExamples             # trazas Markdown de los cuatro
```

Exclusiones que aplican a las tareas de traza: las pruebas etiquetadas `network`,
`MarkdownDocumentTest` (gestiona su propio renderizado) y las pruebas de
`unrefactored/**` de `minecraft`.

## Puertas de calidad

Los ejemplos están excluidos de la publicación y de las puertas de acoplamiento de
JDepend, pero **no** están exentos de calidad de código: PMD, Spotless
(google-java-format para Java, ktlint para Kotlin), la puerta de longitud de métodos de
20 NCSS y la cobertura de JaCoCo aplican todos. Solo los puntos de entrada no testeables
unitariamente (`*Example`, `PlantUmlImageRenderer`, `ContextPropagatingTaskDecorator`)
se excluyen del denominador de cobertura.

## Notas de mantenimiento

- `common/?/` (un directorio con un signo de interrogación literal) es una caché de
  fontconfig filtrada por ejecuciones de JVM en contenedores con `$HOME` sin definir.
  Está en el gitignore y es seguro borrarlo.
- Cada ejemplo lleva su propia configuración de logback (`logback-<nombre>.xml`) cableada
  mediante los argumentos de JVM de la tarea `run`, para que la salida de consola siga
  siendo legible por ejemplo.
