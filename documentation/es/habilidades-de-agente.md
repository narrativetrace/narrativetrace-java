<!-- source: documentation/agent-skills.md blob 88c83f42536c | translated: 2026-09-14 | reviewed: - -->
# Habilidades de agente

*(since 0.2.3)*

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
  [`.claude/skills/narrativetrace-doctor/`](../../.claude/skills/narrativetrace-doctor/SKILL.md) y
  [`.claude/skills/add-narrative-tracing/`](../../.claude/skills/add-narrative-tracing/SKILL.md)
  en este repositorio. Copia cualquiera de los dos directorios en el `.claude/skills/<nombre>/` de
  tu propio proyecto y Claude la reconoce por sí solo, invocable por su nombre
  (`narrativetrace-doctor` / `add-narrative-tracing`) directamente.
- **Codex**: el mismo catálogo también renderiza
  [`.agents/skills/narrativetrace-doctor/`](../../.agents/skills/narrativetrace-doctor/SKILL.md) y
  [`.agents/skills/add-narrative-tracing/`](../../.agents/skills/add-narrative-tracing/SKILL.md) —
  la disposición que Codex CLI documenta para las habilidades propias de un proyecto. Su
  frontmatter lleva solo `name` y `description` (Codex no documenta claves `when_to_use` ni
  `allowed-tools`); el cuerpo de la página es idéntico. Copia cualquiera de los dos directorios en
  el `.agents/skills/<nombre>/` de tu propio proyecto y Codex la reconoce del mismo modo.
- **Cualquier agente, cualquier plataforma**: todo agente que lea `AGENTS.md` ve el aviso siempre
  activo que el propio `AGENTS.md` de este repositorio lleva entre sus marcadores
  `<!-- narrativetrace:skills:start -->` — el nombre y la descripción de ambas habilidades, de modo
  que un agente que nunca pensó en buscarlas igualmente sepa que existen.
- **Gemini** se ejecuta contra el mismo catálogo con una cadencia esporádica y limitada por cuota
  (ver [Tier B — pruebas con LLM](../../narrativetrace-skills/evals/README.md)) en lugar de en cada
  commit; todavía no tiene una disposición propia renderizada — copiar los ficheros de Claude o
  Codex de arriba es, por ahora, el camino más cercano.

## Cómo se construyen

Ninguna de las dos habilidades se edita nunca a mano.
`narrativetrace-skills/src/main/java/ai/narrativetrace/skills/catalogue/AddNarrativeTracingSkill.java`
y `.../NarrativeTraceDoctorSkill.java` son las dos fuentes de la verdad; sus pasos tipados
renderizan `.claude/skills/add-narrative-tracing/SKILL.md`,
`.claude/skills/narrativetrace-doctor/SKILL.md`, sus espejos de Codex en
`.agents/skills/add-narrative-tracing/SKILL.md` y
`.agents/skills/narrativetrace-doctor/SKILL.md`, y la sección propia de `AGENTS.md` de este
repositorio — una prueba de deriva (`RenderDriftTest`, conectada a `./gradlew check`) hace fallar
la compilación en cuanto cualquiera de las cinco se desvía de la fuente tipada. El `name:`
renderizado de una habilidad es siempre su nombre canónico, nunca un "segmento de claude"
abreviado — un directorio `.claude/skills/` o `.agents/skills/` incluido en el propio repositorio
es un espacio de nombres plano, sin prefijo de plugin tras el que esconderse, así que el nombre
debe autoidentificarse globalmente por sí solo. Una segunda suite (`SkillReplayer`, Tier A2)
reproduce mecánicamente cada comando y cada `verify` comprobable por máquina que un paso nombra
contra el fixture `sixty-seconds`, hoy mismo, de forma determinista, sin LLM — que esté en verde
significa que las instrucciones son literalmente ejecutables ahora mismo, no solo prosa plausible.
Un lint de Nivel A mantiene fuera de ambas páginas las citas a notas de planificación privadas, al
hermano Pro de este repositorio, a nombres de ficheros de CI y a hashes de git: las frases de
razonamiento se publican, la cita que nombra la fuente no.

## Ver también

- [`narrativetrace-cli`](../../narrativetrace-cli/) — el verbo `doctor` que ejecuta `narrativetrace-doctor`
- [Ve una traza en 60 segundos](sesenta-segundos.md) — el recorrido de instalación y primera traza del que se extraen los pasos de `add-narrative-tracing`
- [Qué commitear](que-commitear.md) — el estado de trazas de aprobación que comprueba el cuarto paso del doctor
- [Tier B — pruebas con LLM](../../narrativetrace-skills/evals/README.md) — la disposición de casos neutral respecto al motor, la política esporádica de Codex/Gemini, y la matriz de promoción
