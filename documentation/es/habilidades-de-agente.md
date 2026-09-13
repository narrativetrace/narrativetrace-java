<!-- source: documentation/agent-skills.md blob dd3b3ab7e75b | translated: 2026-09-13 | reviewed: - -->
# Habilidades de agente

*(since 0.2.2, unreleased)*

NarrativeTrace incluye **habilidades** (*skills*): procedimientos cargables por un agente que
ejecutan comandos probados y condicionan su finalización a un paso `verify`, en lugar de una
documentación que un agente podría leer o no. Una habilidad es deliberadamente delgada — la lógica
de comprobación, diagnóstico o generación vive en código de biblioteca probado; el trabajo propio
de la habilidad es saber cuándo actuar, invocar ese código probado e interpretar el resultado en
contexto.

## Dos habilidades: configuración y diagnóstico

- **`add-narrative-tracing`** — instala NarrativeTrace en un proyecto y lo lleva a su primera
  traza: instala con el toolchain real, envuelve una clase con `NarrativeTraceProxy.trace`,
  renderiza y ejecuta la primera traza, y luego conecta un logger real (`narrativetrace-slf4j` más
  Logback). Termina ejecutando `narrativetrace-doctor` y cediéndole el testigo — la costura entre
  ambas habilidades.
- **`narrativetrace-doctor`** — solo diagnóstico, y de **solo lectura**: nunca edita, genera ni
  borra un fichero. Ejecuta la CLI probada, lee su informe, y recorre las partes que la salida
  simple de una CLI no puede cubrir por sí sola: demostrar la ocultación en una prueba, leer una
  traza renderizada antes de hacer aserciones sobre ella, y el flujo de trazas de aprobación
  (marcado como no estudiado todavía — su propia celda de evaluación sigue pendiente).

Se combinan: un proyecto totalmente nuevo empieza con `add-narrative-tracing`; un proyecto que ya
tiene NarrativeTrace instalado, donde algo no funciona, empieza con `narrativetrace-doctor`.
Ambos caminos terminan en el doctor — a partir de ahí, el diagnóstico es suyo. Una habilidad futura
se encargará de la generación (escribir la prueba que demuestra la ocultación, que hoy el doctor
solo puede pedirte que añadas).

## Instalarlas

- **Claude Code**: los ficheros `SKILL.md` renderizados viven en
  [`.claude/skills/doctor/`](../../.claude/skills/doctor/SKILL.md) y
  [`.claude/skills/add-narrative-tracing/`](../../.claude/skills/add-narrative-tracing/SKILL.md)
  en este repositorio. Copia cualquiera de los dos directorios en el `.claude/skills/<nombre>/` de
  tu propio proyecto y Claude la reconoce por sí solo, invocable por su nombre
  (`narrativetrace-doctor` / `add-narrative-tracing`) directamente.
- **Cualquier agente, cualquier plataforma**: todo agente que lea `AGENTS.md` ve el aviso siempre
  activo que el propio `AGENTS.md` de este repositorio lleva entre sus marcadores
  `<!-- narrativetrace:skills:start -->` — el nombre y la descripción de ambas habilidades, de modo
  que un agente que nunca pensó en buscarlas igualmente sepa que existen.
- **Codex y Gemini** se ejecutan contra el mismo catálogo con una cadencia esporádica y limitada
  por cuota (ver [Tier B — pruebas con LLM](../../narrativetrace-skills/evals/README.md)) en lugar
  de en cada commit; un instalador empaquetado para cualquiera de las dos plataformas está en la
  hoja de ruta pero aún no se ha construido — hoy, copiar los ficheros renderizados es el camino.

## Cómo se construyen

Ninguna de las dos habilidades se edita nunca a mano.
`narrativetrace-skills/src/main/java/ai/narrativetrace/skills/catalogue/AddNarrativeTracingSkill.java`
y `.../NarrativeTraceDoctorSkill.java` son las dos fuentes de la verdad; sus pasos tipados
renderizan `.claude/skills/add-narrative-tracing/SKILL.md`, `.claude/skills/doctor/SKILL.md`, y la
sección propia de `AGENTS.md` de este repositorio — una prueba de deriva (`RenderDriftTest`,
conectada a `./gradlew check`) hace fallar la compilación en cuanto cualquiera de las tres se
desvía de la fuente tipada. Una segunda suite (`SkillReplayer`, Tier A2) reproduce mecánicamente
cada comando y cada `verify` comprobable por máquina que un paso nombra contra el fixture
`sixty-seconds`, hoy mismo, de forma determinista, sin LLM — que esté en verde significa que las
instrucciones son literalmente ejecutables ahora mismo, no solo prosa plausible. Un lint de Nivel A
mantiene fuera de ambas páginas las citas a notas de planificación privadas, al hermano Pro de este
repositorio, a nombres de ficheros de CI y a hashes de git: las frases de razonamiento se publican,
la cita que nombra la fuente no.

## Ver también

- [`narrativetrace-cli`](../../narrativetrace-cli/) — el verbo `doctor` que ejecuta `narrativetrace-doctor`
- [Ve una traza en 60 segundos](sesenta-segundos.md) — el recorrido de instalación y primera traza del que se extraen los pasos de `add-narrative-tracing`
- [Qué commitear](que-commitear.md) — el estado de trazas de aprobación que comprueba el cuarto paso del doctor
- [Tier B — pruebas con LLM](../../narrativetrace-skills/evals/README.md) — la disposición de casos neutral respecto al motor, la política esporádica de Codex/Gemini, y la matriz de promoción
