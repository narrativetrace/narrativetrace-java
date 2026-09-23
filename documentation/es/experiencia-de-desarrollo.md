<!-- source: documentation/developer-experience.md blob 42c7f40d6c46 | translated: 2026-09-23 | reviewed: - -->
# Experiencia de desarrollo

[English](../developer-experience.md) | **Español** | [简体中文](../zh-CN/开发者体验.md)

Lo que hace falta para pasar de un proyecto vacío a leer tu primera
narrativa, y dónde vive cada pieza de esa experiencia. Esta página describe
lo que ya está disponible hoy; las mejoras planificadas se registran en un
backlog privado.

## Configuración en una línea

Aplica el plugin de Gradle y escribe una prueba. El plugin añade los
artefactos de NarrativeTrace, configura la JUnit Platform y aporta el motor
de pruebas Jupiter para el modo predeterminado JUnit 5 — la configuración mínima documentada ejecuta una prueba
real en verde sin más dependencias. Consulta la guía de instalación para el
fragmento exacto según tu estilo de build. Para producir narrativas, registra
`NarrativeTraceExtension` en la prueba y ejecuta llamadas trazadas, por ejemplo
mediante `NarrativeTraceProxy.trace`. El plugin aporta las dependencias y la
configuración de pruebas; no registra la extensión.

Para una suite JUnit 4 existente, conserva ese framework y configura
`narrativeTrace { testFramework.set("junit4") }`. Vincula una
`@ClassRule NarrativeTraceClassRule` pública y estática a una `@Rule` pública
creada con `classRule.testRule()`, y traza mediante el contexto de la regla por
prueba. El plugin aporta la integración JUnit 4, pero no añade esas reglas a las pruebas.

## Narrativas por prueba

Con la extensión registrada y la salida habilitada (el valor predeterminado),
el módulo `narrativetrace-junit5` escribe narrativas para las pruebas que capturan
trazas. Los artefactos aparecen junto a la salida del build
en los formatos canónicos (texto de narrativa `.nt`, JSON estructural y
canónico, documentos de capítulo) — los mismos formatos que emite cada
implementación de NarrativeTrace, validados por los esquemas incluidos en
este repositorio.

La `NarrativeTraceRule` de JUnit 4 ofrece un contexto y una salida equivalentes
por prueba. Lee propiedades del sistema de la JVM de pruebas en lugar de
`junit-platform.properties`.

## Comentarios sobre nombres y habilidades de agente

En un proyecto consumidor con el plugin de Gradle aplicado, `./gradlew clean clarityScan`
puntúa las clases de producción compiladas sin ejecutar pruebas. Lee los archivos
recién generados `clarity-scan-report.md` y `clarity-scan-results.json` del directorio
de salida configurado (predeterminado: `build/narrativetrace`) para ver las
puntuaciones y las notas por elemento.

Para analizar las llamadas ejecutadas, la extensión de JUnit registrada analiza
las trazas de prueba no vacías y escribe `clarity-report.md` y `clarity-results.json`
al finalizar la suite. En JUnit 4, la regla de clase vinculada escribe los mismos
informes después de cada clase, combinando las clases finalizadas en esa JVM de
pruebas. Una regla por prueba independiente no produce informes agregados de Clarity.
No existe una opción separada para habilitar Clarity;
`narrativetrace.output=false` deshabilita estos informes y los demás artefactos de traza.

La validación opcional en CI utiliza `./gradlew clean clarityCheck`. Su entrada
predeterminada es el JSON generado por las pruebas. `minScore` compara el
`overallScore` de cada escenario; los límites de cantidad de incidencias son
independientes. Si falta el JSON, la comprobación se omite sin fallar: verifica
que existan informes recientes y no vacíos antes de confiar en un build verde.
Los resultados del escaneo estático por sí solos no demuestran que esta validación pasó.

La guía de [habilidades de agente](habilidades-de-agente.md) ofrece tres puntos de entrada:

- `add-narrative-tracing` instala el trazado y produce la primera traza.
- `narrativetrace-doctor` diagnostica una configuración existente sin modificarla.
- `add-narrativetrace-clarity` produce y explica un primer informe de nombres,
  verifica los artefactos y añade una validación de calidad cuando se solicita.

Consulta la [guía de claridad](guia-de-claridad.md) para las opciones de puntuación y validación.

## Probarlo sin un proyecto

`./demo.sh` lanza las aplicaciones de ejemplo (e-commerce y compañía) y
narra escenarios reales en la consola — la forma más rápida de ver cómo se
lee la salida antes de cablear nada.

## Consumir un build local

Evaluar cambios aún no publicados, o construir una integración contra este
repositorio, usa los builds compuestos de Gradle — y necesita **ambos**
roles de inclusión: un `pluginManagement { includeBuild(...) }` para que el
plugin se resuelva, y un `includeBuild(...)` de nivel superior para que las
coordenadas de biblioteca que añade el plugin se sustituyan por tu checkout
local. La documentación del plugin trae la receta completa de
`settings.gradle.kts`.

## Seguridad mientras desarrollas

La ocultación es parte de la experiencia de desarrollo, no un añadido de
última hora: un componente `@NotTraced` nunca aparece en ninguna salida
renderizada — ni a través del `toString()` de un envoltorio, ni a través de
una colección, ni a través del `toString()` propio, escrito a mano, de una
clase que lo contenga, ni a través de una plantilla de narración que nombre
su ruta. Si una narrativa necesita un valor, el acto deliberado y revisable es
quitar la anotación, nunca esquivarla.
