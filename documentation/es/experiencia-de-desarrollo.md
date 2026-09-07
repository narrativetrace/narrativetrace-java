<!-- source: documentation/developer-experience.md blob 34a39401d4b9 | translated: 2026-09-07 | reviewed: - -->
# Experiencia de desarrollo

[English](../developer-experience.md) | **Español** | [简体中文](../zh-CN/开发者体验.md)

Lo que hace falta para pasar de un proyecto vacío a leer tu primera
narrativa, y dónde vive cada pieza de esa experiencia. Esta página describe
lo que ya está disponible hoy; las mejoras planificadas se registran en un
backlog privado.

## Configuración en una línea

Aplica el plugin de Gradle y escribe una prueba. El plugin añade los
artefactos de NarrativeTrace, configura la JUnit Platform y aporta el motor
de pruebas Jupiter — la configuración mínima documentada ejecuta una prueba
real en verde sin más dependencias. Consulta la guía de instalación para el
fragmento exacto según tu estilo de build.

## Narrativas por prueba

El módulo `narrativetrace-junit5` escribe una narrativa para cada prueba a
medida que se ejecuta. Los artefactos aparecen junto a la salida del build
en los formatos canónicos (texto de narrativa `.nt`, JSON estructural y
canónico, documentos de capítulo) — los mismos formatos que emite cada
implementación de NarrativeTrace, validados por los esquemas incluidos en
este repositorio.

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
una colección, ni a través de una plantilla de narración que nombre su
ruta. Si una narrativa necesita un valor, el acto deliberado y revisable es
quitar la anotación, nunca esquivarla.
