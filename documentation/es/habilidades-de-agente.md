<!-- source: documentation/agent-skills.md blob 097f4e3c98ef | translated: 2026-09-22 | reviewed: - -->
# Habilidades de agente

*(since 0.2.3)*

NarrativeTrace incluye **habilidades** (*skills*): procedimientos cargables por un agente que
ejecutan comandos probados y condicionan su finalización a un paso `verify`, en lugar de una
documentación que un agente podría leer o no. Una habilidad es deliberadamente delgada — la lógica
de comprobación, diagnóstico o generación vive en código de biblioteca probado; el trabajo propio
de la habilidad es saber cuándo actuar, invocar ese código probado e interpretar el resultado en
contexto.

## Tres habilidades: configuración, diagnóstico y claridad

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
- **`add-narrativetrace-clarity`** — ejecuta un primer escaneo estático de nombres, comprueba
  sus artefactos recién generados, explica las incidencias ordenadas y las notas por elemento,
  y añade una puerta de calidad explícita solo cuando se solicita.

Se combinan: un proyecto totalmente nuevo empieza con `add-narrative-tracing`; un proyecto que ya
tiene NarrativeTrace instalado, donde algo no funciona, empieza con `narrativetrace-doctor`.
Ambos caminos terminan en el doctor — a partir de ahí, el diagnóstico es suyo.
`add-narrativetrace-clarity` se encarga de los primeros informes estáticos de nombres y de la
aplicación opcional de umbrales de claridad; no instala trazas ni cosecha un glosario.

## Instalarlas

- **Claude Code**: los ficheros `SKILL.md` renderizados viven en
  [`.claude/skills/narrativetrace-doctor/`](../../.claude/skills/narrativetrace-doctor/SKILL.md) y
  [`.claude/skills/add-narrative-tracing/`](../../.claude/skills/add-narrative-tracing/SKILL.md) y
  [`.claude/skills/add-narrativetrace-clarity/`](../../.claude/skills/add-narrativetrace-clarity/SKILL.md)
  en este repositorio. Copia el directorio pertinente en el `.claude/skills/<nombre>/` de tu
  proyecto y Claude lo reconoce automáticamente; puedes invocar la habilidad por su nombre.
- **Codex**: el mismo catálogo también renderiza
  [`.agents/skills/narrativetrace-doctor/`](../../.agents/skills/narrativetrace-doctor/SKILL.md) y
  [`.agents/skills/add-narrative-tracing/`](../../.agents/skills/add-narrative-tracing/SKILL.md) y
  [`.agents/skills/add-narrativetrace-clarity/`](../../.agents/skills/add-narrativetrace-clarity/SKILL.md) —
  la disposición que Codex CLI documenta para las habilidades propias de un proyecto. Su
  frontmatter lleva solo `name` y `description` (Codex no documenta claves `when_to_use` ni
  `allowed-tools`); el cuerpo de la página es idéntico. Copia el directorio pertinente en
  el `.agents/skills/<nombre>/` de tu propio proyecto y Codex la reconoce del mismo modo.
- **Cualquier agente, cualquier plataforma**: todo agente que lea `AGENTS.md` ve el aviso siempre
  activo que el propio `AGENTS.md` de este repositorio lleva entre sus marcadores
  `<!-- narrativetrace:skills:start -->` — el nombre y la descripción de las tres habilidades, de modo
  que un agente que nunca pensó en buscarlas igualmente sepa que existen.
- **Gemini** se ejecuta contra el mismo catálogo con una cadencia esporádica y limitada por cuota
  (ver [Tier B — pruebas con LLM](../../narrativetrace-skills/evals/README.md)) en lugar de en cada
  commit; todavía no tiene una disposición propia renderizada — copiar los ficheros de Claude o
  Codex de arriba es, por ahora, el camino más cercano.

## Cómo se construyen

Ninguna habilidad se edita a mano.
`narrativetrace-skills/src/main/java/ai/narrativetrace/skills/catalogue/` contiene las tres
fuentes de la verdad; sus pasos tipados renderizan tres páginas de Claude, tres de Codex y la
sección de `AGENTS.md` de este repositorio. Una prueba de deriva (`RenderDriftTest`, conectada
a `./gradlew check`) hace fallar la compilación si cualquiera de esas siete salidas se desvía
de la fuente tipada. El `name:`
renderizado de una habilidad es siempre su nombre canónico, nunca un "segmento de claude"
abreviado — un directorio `.claude/skills/` o `.agents/skills/` incluido en el propio repositorio
es un espacio de nombres plano, sin prefijo de plugin tras el que esconderse, así que el nombre
debe autoidentificarse globalmente por sí solo. Una segunda suite (`SkillReplayer`, Tier A2)
reproduce mecánicamente cada comando y cada `verify` comprobable por máquina que un paso nombra
contra el fixture real correspondiente, incluido el consumidor independiente de Clarity,
hoy mismo, de forma determinista, sin LLM — que esté en verde
significa que las instrucciones son literalmente ejecutables ahora mismo, no solo prosa plausible.
Un lint de Nivel A mantiene fuera de las páginas renderizadas las citas a notas de planificación privadas, al
hermano Pro de este repositorio, a nombres de ficheros de CI y a hashes de git: las frases de
razonamiento se publican, la cita que nombra la fuente no.

## Ver también

- [`narrativetrace-cli`](../../narrativetrace-cli/) — el verbo `doctor` que ejecuta `narrativetrace-doctor`
- [Ve una traza en 60 segundos](sesenta-segundos.md) — el recorrido de instalación y primera traza del que se extraen los pasos de `add-narrative-tracing`
- [Qué commitear](que-commitear.md) — el estado de trazas de aprobación que comprueba el cuarto paso del doctor
- [Tier B — pruebas con LLM](../../narrativetrace-skills/evals/README.md) — la disposición de casos neutral respecto al motor, la política esporádica de Codex/Gemini, y la matriz de promoción
