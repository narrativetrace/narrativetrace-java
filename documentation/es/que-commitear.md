<!-- source: documentation/what-to-commit.md blob 0224575ae05b | translated: 2026-09-03 | reviewed: 2026-09-03 -->
# Qué commitear

[English](../what-to-commit.md) | **Español** | [Português](../pt-BR/o-que-commitar.md) | [简体中文](../zh-CN/应提交的内容.md)

NarrativeTrace escribe dos tipos de fichero: artefactos generados que
describen una ejecución, y baselines revisadas que describen un contrato
pretendido. Commitea el segundo tipo, no el primero.

| Artefacto | ¿Commitear? | Por qué |
|---|---|---|
| `build/narrativetrace/traces/*.md` | No | Se regenera en cada ejecución; normalmente es un artefacto de CI, no fuente |
| `build/narrativetrace/traces/*.json` | No | La misma traza en JSON canónico — se regenera en cada ejecución |
| `build/narrativetrace/diagrams/*.mmd` | No | Se regenera en cada ejecución |
| `build/narrativetrace/structural/*.nt` | No | La última baseline *local* en verde contra la que comparan el delta de consola y los informes de fallo — no es la baseline de aprobación (ver abajo) |
| `build/narrativetrace/clarity-report.md` | No | Un informe generado, no una decisión — `clarityCheck` lee `clarity-results.json`, que está justo al lado y también es generado |
| `src/test/narratives/<Class>/<scenario>.approved.nt` | **Sí** | La baseline de aprobación revisada (solo existe si el [modo aprobación](formato-de-traza-estructural.md) está activo). Es el único fichero de la lista que es una decisión deliberada, no una salida |
| `src/test/narratives/<Class>/<scenario>.received.nt` | No | Se escribe cuando hay un desajuste de aprobación, o cuando todavía no existe ninguna baseline. Revísalo, ejecuta `./gradlew approveNarratives` para promoverlo, y luego bórralo o deja que la tarea lo elimine — nunca commitees el propio fichero received |
| `src/test/narratives/<Class>/<scenario>.incomplete.nt` | No | Se escribe en lugar de `.received.nt` cuando la propia ejecución quedó incompleta (pérdida de mejor esfuerzo, o un ámbito asíncrono rechazado). `approveNarratives` lo ignora por nombre a propósito — consulta [Formato de traza estructural](formato-de-traza-estructural.md) |
| `glossary.json` / `glossary.md` | **Sí**, si se usa la recolección del glosario | Se commitea en la raíz del repositorio mediante `glossaryScan` / `glossary.set(true)`; el fichero commiteado es lo que leen de vuelta la puntuación de claridad y las comprobaciones de vocabulario. "Un fichero, un flujo de revisión" |

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
