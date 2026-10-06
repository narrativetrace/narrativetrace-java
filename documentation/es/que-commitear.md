<!-- source: documentation/what-to-commit.md blob 4f114a8f7fef | translated: 2026-10-04 | reviewed: - -->
# Qué commitear

[English](../what-to-commit.md) | **Español** | [Português](../pt-BR/o-que-commitar.md) | [简体中文](../zh-CN/应提交的内容.md)

NarrativeTrace escribe dos tipos de fichero: artefactos generados que
describen una ejecución, y baselines revisadas que describen un contrato
pretendido. Commitea el segundo tipo, no el primero.

Todo artefacto de tiempo de prueba se escribe por defecto en el directorio
efímero `build/narrativetrace` — sin necesidad de configuración,
`narrativetrace.output=false` lo desactiva (ver la
[Guía de Configuración](guia-de-configuracion.md)). Efímero es la clave: vive
bajo `build/`, así que nunca necesita la disciplina de la que trata esta
página — ya está excluido, se regenera en cada ejecución, y se puede borrar
en cualquier momento.

| Artefacto | ¿Commitear? | Por qué |
|---|---|---|
| `build/narrativetrace/traces/*.md` | No | Se regenera en cada ejecución; normalmente es un artefacto de CI, no fuente |
| `build/narrativetrace/traces/*.json` | No | La misma traza en JSON canónico — se regenera en cada ejecución |
| `build/narrativetrace/diagrams/*.mmd` | No | Se regenera en cada ejecución |
| `build/narrativetrace/manifest.json` | No | Se regenera en cada ejecución; su objeto `run` de nivel superior (`id`, `name` — la frase de tres palabras propia de la ejecución) nombra *esta ejecución*, no un escenario, así que cambia en cada ejecución incluso cuando nada más cambia |
| `build/narrativetrace/structural/*.nt` | No | La última baseline *local* en verde contra la que comparan el delta de consola y los informes de fallo — no es la baseline de aprobación (ver abajo) |
| `build/narrativetrace/clarity-report.md` | No | Un informe generado, no una decisión — `clarityCheck` lee `clarity-results.json`, que está justo al lado y también es generado |
| `src/test/narratives/<Class>/<scenario>.approved.nt` | **Sí** | La baseline de aprobación revisada (solo existe si el [modo aprobación](formato-de-traza-estructural.md) está activo). Es el único fichero de la lista que es una decisión deliberada, no una salida |
| `src/test/narratives/<Class>/<scenario>.received.nt` | No | Se escribe cuando hay un desajuste de aprobación, o cuando todavía no existe ninguna baseline. Revísalo, ejecuta `./gradlew approveNarratives` para promoverlo, y luego bórralo o deja que la tarea lo elimine — nunca commitees el propio fichero received |
| `src/test/narratives/<Class>/<scenario>.incomplete.nt` | No | Se escribe en lugar de `.received.nt` cuando la propia ejecución quedó incompleta (pérdida de mejor esfuerzo, o un ámbito asíncrono rechazado). `approveNarratives` lo ignora por nombre a propósito — consulta [Formato de traza estructural](formato-de-traza-estructural.md) |
| `glossary.json` / `glossary.md` | **Sí**, si se usa la recolección del glosario | Se commitea en la raíz del repositorio mediante `glossaryScan` / `glossary.set(true)`; el fichero commiteado es lo que leen de vuelta la puntuación de claridad y las comprobaciones de vocabulario. "Un fichero, un flujo de revisión" |
| `.claude/skills/**/SKILL.md`, `.agents/skills/**/SKILL.md`, la sección `<!-- narrativetrace:skills:* -->` de `AGENTS.md` | **Sí** | Salida de compilación del catálogo tipado de `narrativetrace-skills-catalogue`, no salida de una ejecución de pruebas — se commitea igual que `glossary.json`: se regenera, se revisa en los diffs, y se comprueba contra la deriva (`RenderDriftTest`, conectada a `./gradlew check`) en vez de editarse a mano |
| `skills-lock.json` | **Sí**, si tu equipo usa `npx skills add` | Se escribe en la raíz del proyecto mediante `npx skills add` — el propio registro de lo que ese registro instaló y de dónde; commitéalo igual que harías con cualquier otro fichero de bloqueo de dependencias del que dependa tu equipo |
| `.claude-plugin/marketplace.json` | **Sí** | El propio listado de este repositorio (Fase 4): salida de compilación del mismo catálogo tipado que la fila de arriba, renderizada y comprobada contra la misma prueba de deriva — nunca editada a mano |

En un proyecto que ejecutó [`narrativetraceInit`](guia-del-plugin-de-gradle.md#narrativetraceinit)
esas mismas tres rutas son las que hay que commitear, y cada `SKILL.md` instalado lleva una línea de
procedencia que nombra la versión desde la que se instaló — ese sello es como
`narrativetraceUninstall` sabe que una página es nuestra y como el doctor distingue una instalación
al día de una obsoleta, así que una página commiteada sin él se vuelve una página que nadie puede
eliminar ni refrescar con seguridad. La sección de `AGENTS.md` que un consumidor commitea vive entre
`<!-- narrativetrace:start ... -->` y `<!-- narrativetrace:end -->`: lo que escribas fuera de esos
marcadores es tuyo y sobrevive a cada nueva ejecución, y lo que quede dentro se reemplaza.

Una instalación personal del plugin (`/plugin install narrativetrace-java@narrativetrace-java` de
Claude Code) no escribe nada en absoluto dentro del proyecto — vive en tu propia caché de plugins,
así que no hay nada de ella que commitear.

Todo bajo `build/` ya está cubierto por el `.gitignore` que se distribuye
(`build/` es la primera línea). `src/test/narratives/` no lo está — los
ficheros `.approved.nt` de ahí están pensados para trackearse, pero un
`.received.nt` que se sienta al lado de uno no queda excluido
automáticamente. Si tu equipo no es disciplinado a la hora de borrar un
`.received.nt` ya revisado antes de commitear, añade una regla de
exclusión explícita para él:

```gitignore
src/test/narratives/**/*.received.nt
src/test/narratives/**/*.incomplete.nt
```

## La regla en una frase

Si un fichero solo existe porque corrió un test, es salida — no lo
commitees. Si un fichero existe porque un humano lo revisó y lo aceptó, es
una baseline — commitéalo, y espera que sus diffs se lean en code review de
la misma forma que se leería el diff de un snapshot test.

## Las baselines de aprobación son libres de valores por construcción

Un fichero `.approved.nt` nunca contiene valores de parámetros ni de
retorno (ADR-002) — solo estructura de llamadas, nombres y tipos de
resultado. Eso es lo que hace que sea seguro commitearlo y estable entre
ejecuciones: un valor de retorno que cambia sin cambio estructural nunca
toca la baseline, y revisar un diff nunca significa leer datos de runtime
en un pull request. Consulta
[Privacidad y ocultación](privacidad-y-ocultacion.md) para el resto de lo
que NarrativeTrace pone y no pone en un fichero que tu equipo va a
compartir.

## Modo aprobación, de principio a fin

```text
el test pasa
   |
   v
compara la estructura actual con la baseline aprobada
   |
   +-- igual      --> pasa, no se escribe nada
   +-- diferente  --> escribe .received.nt y falla
                      |
                      v
                 un humano revisa el diff
                      |
                      v
               ./gradlew approveNarratives
                      |
                      v
               .approved.nt actualizado, commitéalo
```

El estado de fallo es deliberado: un test que pasa pero cuya *forma*
cambió — incluido un cambio que un agente de IA coló en un refactor por lo
demás correcto — tiene que revisarse y aprobarse explícitamente, no solo
compilar. Nada se acepta en silencio, y nada se pierde en silencio: una
ejecución incompleta escribe `.incomplete.nt` en su lugar y se compara por
contención de subsecuencia en vez de por igualdad, así que una ejecución
corta nunca puede convertirse en la baseline commiteada.
