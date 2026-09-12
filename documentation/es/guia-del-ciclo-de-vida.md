<!-- source: documentation/lifecycle-guide.md blob 9919ee976581 | translated: 2026-09-12 | reviewed: - -->
# NarrativeTrace a lo largo del ciclo de desarrollo

[English](../lifecycle-guide.md) | **Español** | [简体中文](../zh-CN/生命周期指南.md)

NarrativeTrace no es una herramienta de tiempo de pruebas con un modo de
producción añadido, ni un trazador de producción que casualmente funciona
en las pruebas. Es **un único mecanismo de captura cuya configuración y
cuyos consumidores cambian a medida que el código avanza por el ciclo**:
la misma traza que documenta una prueba unitaria durante el desarrollo
actúa como puerta del build en CI, narra una ejecución de aceptación
contra un entorno desplegado y fluye por tu stack de logs en producción.
Esta guía recorre las tres etapas y las condensa en una matriz de
configuración y una postura de privacidad.

Las guías de mecanismo ([instalación](guia-de-instalacion.md),
[configuración](guia-de-configuracion.md),
[anotaciones](guia-de-anotaciones.md), las guías de integración)
explican cada pieza; esta guía explica **en qué momento de tu proceso
cada pieza aporta su valor**. Para una impresión práctica antes de
seguir leyendo, ejecuta `./demo.sh` en la raíz del repositorio.

---

## 1. Tiempo de desarrollo — el bucle interno

En tiempo de desarrollo el consumidor eres **tú, leyendo**. El trazado
viaja sobre las pruebas que ya escribes; no se instrumenta nada extra y
no existe infraestructura de larga vida.

- **Integración de pruebas sin configuración.** Con la extensión de
  JUnit 5 en el classpath (descubierta por ServiceLoader; las reglas de
  JUnit 4 se declaran explícitamente), cada prueba escribe su traza como
  artefacto revisable por defecto — sin necesidad de configurar nada,
  `narrativetrace.output=false` lo desactiva *(since 0.2.2, unreleased)*:
  `build/narrativetrace/traces/<ClaseDeTest>/<test>.md` más un `.json`
  canónico y un diagrama Mermaid por escenario. El [plugin de
  Gradle](guia-del-plugin-de-gradle.md) cablea las dependencias, el flag
  de compilación `-parameters` y las propiedades de la JVM de pruebas en
  un único bloque `narrativeTrace { }`.
- **Las pruebas que fallan cuentan su historia.** En una prueba roja, la
  traza capturada completa se imprime como narrativa del fallo — qué se
  llamó, con qué valores, dónde se detuvo — antes de recurrir al
  depurador.
- **Feedback de nombres cuando aún es barato.** La puntuación de
  claridad lee las trazas capturadas e informa de la calidad de los
  nombres de métodos/clases/parámetros por escenario
  (`clarity-report.md`, resumen en consola). En esta etapa es
  consultiva: un espejo, no una puerta.
- **El vocabulario crece desde el código.** Con
  `narrativetrace.glossary=true` la suite recolecta términos del dominio
  en el `glossary.json` versionado — la curación ocurre en la revisión
  de código, como cualquier otro artefacto.

El hábito que esta etapa construye es el objetivo: **la traza es lo
primero que lees**, antes que las sentencias de log, antes que el
depurador. Todo lo posterior en el ciclo reutiliza esa misma captura
legible.

## 2. Tiempo de pruebas — CI, integración, aceptación, smoke

En tiempo de pruebas los consumidores son **máquinas y revisores**: la
puerta del build, los archivos de artefactos, otras plataformas, las
herramientas de IA y los testers observando un entorno desplegado.

- **Trazas como artefactos del build.** Los archivos por prueba de la
  etapa 1 son artefactos de CI: archiva `build/narrativetrace/` y una
  pipeline fallida lleva consigo su propia narrativa. Los revisores leen
  lo que el código hizo, no lo que quien hizo el commit dice que hace.
- **Puertas de calidad.** `clarityCheck` corre dentro de `./gradlew
  check`: los umbrales (`clarity.minScore`, `clarity.maxHighIssues`,
  `clarity.maxSuiteIssues`) convierten el espejo de la etapa 1 en una
  puerta del build, con `warnOnly` como modo de despliegue suave.
  `clarityScan` puntúa clases compiladas sin ejecutar pruebas, para
  pipelines que separan ambas cosas.
- **Exportaciones legibles por máquinas.** Dos flags opt-in amplían la
  audiencia: `narrativetrace.canonicalJson=true` escribe por prueba los
  arrays de entradas del esquema 1.2 (el contrato que consumen las demás
  implementaciones y los fixtures de conformidad);
  `narrativetrace.structuralJson=true`
  escribe el artefacto estructural sin valores (ADR-002 Nivel 1) que
  puede entregarse a herramientas de revisión con IA sin exponer datos.
- **Las pruebas de aceptación y smoke observan un sistema desplegado.**
  Aquí el trazado se muda de la JVM de pruebas a la aplicación bajo
  prueba, usando las integraciones de producción de forma temprana: el
  filtro servlet, el cableado de Spring o Micronaut, o — para un sistema
  que no puedes modificar — el agente Java
  (`-javaagent:narrativetrace-agent-<versión>-standalone.jar`) adjunto
  al despliegue. Una prueba smoke entonces asevera sobre *comportamiento
  que puedes leer*: el flujo narrado de la petición en los logs del
  contenedor, correlacionado por el `traceId` del MDC. El ejemplo
  WildFly `narrativetrace-examples:ejb4` tiene exactamente esta forma.
- **El mismo esquema en todas partes.** Como los entornos de aceptación
  emiten el mismo flujo canónico que las pruebas unitarias, las
  herramientas escritas contra una etapa funcionan contra la otra — esa
  es la paridad que el esquema canónico existe para proteger.

## 3. Producción

En producción los consumidores son **operadores, herramientas de logs
y — tras un enrutamiento explícito — sistemas de IA**. Las restricciones
de diseño cambian: dominan el coste, la tolerancia a pérdidas y la
privacidad.

- **Alcance y coste.** `narrativeTrace { scope.set("production") }`
  mueve la biblioteca al classpath de runtime. El nivel de captura es el
  dial de coste — `OFF` (~1–2 ns por llamada) → `ERRORS` → `SUMMARY` →
  `NARRATIVE` → `DETAIL` — y es conmutable en runtime
  (`config.setLevel(...)`), de modo que "subir la narración durante el
  incidente y bajarla después" es una operación, no un deploy.
- **Dos compuertas independientes (ADR-008).** El nivel de captura
  decide qué se registra; tu configuración de logging decide qué se
  emite y adónde. Los eventos narrativos fluyen por el puente SLF4J bajo
  el logger `narrativetrace` (ENTRY/RETURN en TRACE, EXCEPTION en WARN
  por defecto), así que la configuración ordinaria de appenders — no la
  configuración de la biblioteca — los enruta, filtra y envía.
- **La identidad viaja en el MDC, no en el texto del mensaje.** La
  identidad de la traza (`traceId`, `nt.class`, `nt.method`,
  `nt.package`), la identidad del servicio (`service.*`,
  host/pid/runtime cuando `capture.resource` está activo) y el modelo de
  atributos de tres niveles (ADR-009) aparecen como claves MDC; tu
  patrón de log o encoder JSON decide la visibilidad.
- **OpenTelemetry.** Las trazas capturadas se exportan como spans OTel
  (por lotes o con listener en vivo) junto a tu tracing existente;
  NarrativeTrace nunca re-emite atributos de recurso que el SDK del
  cliente posee.
- **Las vistas derivadas en vivo son suscriptores.** La tubería de mejor
  esfuerzo (`BufferedEventConsumer`) expone una costura de publicador;
  todo lo derivado se adjunta ahí, fuera del hilo llamante: el flujo de
  narración traducida (loggers `narrativetrace.i18n.<locale>` o archivos
  Markdown por traza, guiados por el glosario) y el flujo estructural
  seguro para IA (`narrativetrace.ai.structural`, una línea JSON sin
  valores por evento). Esta ruta es **con pérdidas bajo carga por
  diseño** — descarta antes que bloquear la aplicación. La ruta síncrona
  del listener es el registro durable; las vistas derivadas son
  conveniencias por encima.
- **Quien arranca un hilo de drenaje es dueño de `close()`.** Todas las
  integraciones de aquí — Spring, el filtro de servlet, el agente, ambas
  extensiones de JUnit — retienen eventos mediante un consumidor creado
  con `startConsumer=false`: sin hilo de fondo, sin shutdown hook de la
  JVM, sin nada que lo enraíce. Un contexto que sale de ámbito se recolecta
  como cualquier otro objeto, y uno sin cerrar no filtra nada. Si en
  cambio construyes un `BufferedEventConsumer` con su propio hilo de
  drenaje (`new BufferedEventConsumer()`, o la forma `(int capacity)`), el
  shutdown hook que registra enraíza el consumidor, su anillo y todo lo que
  el anillo retiene hasta que la JVM termina. Esa es la única forma en la
  que `close()` es obligatorio — `try`-with-resources es la manera de
  escribirlo.
- **Los flags de captura se mantienen conservadores.** `capture.resource`
  está activo por defecto (desactívalo en despliegues sensibles al
  hostname); `capture.sourceLocation` y `capture.instanceIds` están
  desactivados por defecto. Los flags controlan la captura, nunca la
  forma del esquema — los campos ausentes permanecen ausentes y los
  consumidores nunca ramifican según la configuración.

## La matriz etapa × configuración

| | Desarrollo | Pruebas / CI / aceptación | Producción |
|---|---|---|---|
| **Consumidor principal** | El desarrollador, leyendo | Puertas, artefactos, máquinas, testers | Operadores, stack de logs, flujos de IA enrutados |
| **Integración** | JUnit 5/4 vía el plugin de Gradle | Igual, más filtros/agente en entornos desplegados | Proxy / Spring / Micronaut / servlet / agente; `scope = "production"` |
| **Nivel de captura** | `DETAIL` | `DETAIL` | Base `SUMMARY` o `NARRATIVE`; `DETAIL` bajo demanda; `OFF`/`ERRORS` en rutas calientes |
| **Salidas** | `.md` + `.json` + diagramas por prueba, narrativas de fallo | Las mismas como artefactos de CI; `canonicalJson` / `structuralJson`; logs de contenedor en aceptación | Flujo SLF4J + MDC; spans OTel; flujos de suscriptores (i18n, estructural) |
| **Puertas** | Ninguna — la claridad es consultiva | Umbrales de `clarityCheck`, tus propias aserciones sobre trazas | Ninguna — observabilidad, no imposición |
| **Modelo de pérdidas** | Completo (captura síncrona en la prueba) | Completo en la prueba; desplegado = modelo de producción | Ruta síncrona durable; ruta de suscriptores de mejor esfuerzo, descarta bajo carga |
| **Ocultación** | **Siempre activa** | **Siempre activa** | **Siempre activa** |

## Postura de privacidad a lo largo del ciclo

La última fila de la matriz es deliberada y merece enunciarse como regla:

**La ocultación es incondicional. No existe etapa, flag ni propiedad que
desactive `@NotTraced` o las reglas de ocultación basadas en nombres —
por decisión, no por omisión** (decisión del propietario, 2026-08-16).
El razonamiento:

- El valor de "las salidas son seguras" está en que es un *invariante*,
  no un estado de configuración. Todo artefacto que esta biblioteca
  escribe — trazas de pruebas, archivos de CI, logs de contenedor,
  flujos de IA — puede compartirse sin auditar antes qué flags estaban
  activos cuando se produjo.
- "Los datos de prueba son sintéticos" es exactamente falso en los
  entornos donde un interruptor de apagado sería más tentador: los
  sistemas de aceptación y staging se pueblan rutinariamente con datos
  con forma de producción.
- Los artefactos de prueba viajan — al control de versiones, a los
  tickets y a las herramientas de IA. Esas son las salidas que la
  ocultación existe para proteger.

Cuando un valor ocultado bloquea la depuración, las respuestas
soportadas son, en orden: aseverar sobre el comportamiento y no sobre el
valor secreto; usar los **tipos declarados** capturados (esquema 1.2)
para diagnosticar la forma sin revelar nada; y construir fixtures
obviamente falsos cuyos *nombres* lleven la información
(`"card-token-for-decline-path"` se oculta, pero el nombre del parámetro
y su tipo siguen narrando). Una **vía de escape por prueba, anotada
explícitamente** (visible en la revisión de código, cableada solo a
través de la extensión de JUnit — nunca por la cadena de configuración
de producción — y que estampa un aviso llamativo en cualquier archivo
que toque) es una dirección de diseño registrada, condicionada a la
demanda: se construirá cuando un usuario real choque con el muro, y un
interruptor global no se construirá jamás.

Nótese lo que la ocultación no tiene que cargar sola: los niveles
`SUMMARY` y `NARRATIVE` suprimen todos los valores de parámetros, el
artefacto estructural elide arquitectónicamente todo campo de valor, y
los valores renderizados no pueden falsificar líneas de log ni romper la
sintaxis de Markdown o de los diagramas (blindaje contra inyección). La
ocultación es una capa de una postura, y cada etapa del ciclo elige las
capas que necesita.

## Adónde ir después

- [Guía de instalación](guia-de-instalacion.md) — pon en marcha la etapa 1 en minutos
- [Guía de configuración](guia-de-configuracion.md) — todas las claves citadas arriba
- [Guía del plugin de Gradle](guia-del-plugin-de-gradle.md) — el DSL `narrativeTrace { }` y las puertas
- [Guía de claridad](guia-de-claridad.md) — el modelo de puntuación detrás de la puerta
- [Guía de funcionalidades](guia-de-funcionalidades.md) — el catálogo completo, con tier y estado por funcionalidad
