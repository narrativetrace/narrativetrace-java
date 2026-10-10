<!-- source: documentation/agent-skills.md blob 323fe87c2f42 | translated: 2026-10-09 | reviewed: - -->
# Habilidades de agente

NarrativeTrace incluye **habilidades** (*skills*): procedimientos cargables por un agente que
ejecutan comandos probados y condicionan su finalización a un paso `verify`, en lugar de una
documentación que un agente podría leer o no. Una habilidad es deliberadamente delgada — la lógica
de comprobación, diagnóstico o generación vive en código de biblioteca probado; el trabajo propio
de la habilidad es saber cuándo actuar, invocar ese código probado e interpretar el resultado en
contexto.

## Seis habilidades: configuración, diagnóstico, claridad, informes, verificación y depuración

- **`add-narrative-tracing`** — instala NarrativeTrace en un proyecto y lo lleva a su primera
  traza: instala con el toolchain real, envuelve una clase con `NarrativeTraceProxy.trace`,
  renderiza y ejecuta la primera traza, y luego conecta un logger real (`narrativetrace-slf4j` más
  Logback). Justo después de instalar, además ejecuta el doctor y aplica, en orden, cada corrección
  `config.<framework>-*` que imprime — la página no nombra ningún framework; la tabla de frameworks
  del propio doctor instalado decide cuáles necesita el proyecto. Ejecuta `narrativetrace-doctor` y le cede el testigo — la costura entre ambas
  habilidades — y cierra previsualizando `narrativetraceInit`, para que la siguiente sesión
  encuentre estas habilidades ya instaladas. Previsualiza y nunca aplica: una habilidad que
  escribiera en `AGENTS.md` por iniciativa propia sería exactamente el hook de postinstalación que
  el instalador existe para evitar.
- **`narrativetrace-doctor`** — solo diagnóstico, y de **solo lectura**: nunca edita, genera ni
  borra un fichero. Ejecuta la CLI probada, lee su informe, y recorre las partes que la salida
  simple de una CLI no puede cubrir por sí sola: demostrar la ocultación en una prueba, leer una
  traza renderizada antes de hacer aserciones sobre ella, y el flujo de trazas de aprobación
  (marcado como no estudiado todavía — su propia celda de evaluación sigue pendiente). Cada
  hallazgo que lee nombra además la habilidad que corrige esa clase de problema (`skill` en el
  informe JSON, `skill:` bajo la corrección en la forma de texto), de modo que un agente con un
  informe en la mano sabe cuál de estos procedimientos seguir a continuación; un hallazgo que
  ninguna habilidad corrige lo dice con un `null`.
- **`add-narrativetrace-clarity`** — ejecuta un primer escaneo estático de nombres, comprueba
  sus artefactos recién generados, explica las incidencias ordenadas y las notas por elemento,
  y añade una puerta de calidad explícita solo cuando se solicita.
- **`narrativetrace-feedback`** — informa de un defecto en NarrativeTrace mismo: una comprobación
  del doctor que está equivocada o cuya corrección no funciona, un paso de una habilidad que no se
  puede seguir, una redacción del prompt de instalación que llevó a otra parte, o la biblioteca
  comportándose mal en un proyecto correctamente configurado. El verbo probado que está detrás
  redacta el informe a partir del proyecto (las coordenadas de instalación, el propio informe JSON
  del doctor y, como máximo, una traza estructural) y **se niega a escribir un informe que lleve un
  valor de tus trazas**, nombrando la regla que lo rechazó, para que haya algo concreto que
  arreglar en lugar de un aviso que ignorar. La habilidad muestra entonces el borrador completo y
  pregunta una sola vez si se presenta públicamente. No envía nada a ninguna parte y no presenta
  nada sin una respuesta dada en un turno propio.
- **`narrativetrace-verify`** — lee lo que un cambio hizo de verdad antes de que el agente diga que
  está terminado. Se ejecuta cuando las pruebas ya están en verde y primero decide si merece la pena
  trazar el cambio — una función pura o una edición de una sola clase no lo merecen, y la habilidad
  lo dice y se detiene. Si no, escribe la intención antes de ejecutar nada (qué colaboradores, en qué
  orden, en qué rama, cuántas veces), ejecuta el camino real más pequeño con las trazas activas, lee
  la traza estructural sin valores contra esa intención, abre valores solo en el span que parece
  incorrecto, corrige y vuelve a leer, y después fija el flujo como línea base `.approved.nt` —
  activa el modo de aprobación si está apagado, muestra el `.received.nt` completo y solo lo
  promueve tras tu sí, en un turno propio. Su informe cita ids de span (`#2.1`), la posición que
  todas las variantes imprimen para la misma llamada, de modo que una afirmación sobre la traza se
  puede comprobar contra la traza.
- **`narrativetrace-debug`** — encuentra la causa de un resultado incorrecto leyendo lo que el
  código hizo con los valores, no recorriéndolo paso a paso. Empieza por un síntoma, no por un
  cambio: lo reproduce con la entrada más pequeña y las trazas activas, lee primero el diagrama de
  secuencia cuando el camino cruza hilos, y después nombra — por su id de span, antes de tocar
  código — el primer span cuyas entradas son correctas y cuyo resultado no lo es. Acota por span,
  nunca por fichero: lee el subárbol bajo ese id y, cuando el trabajo dentro del span no está
  trazado, envuelve un colaborador más en lugar de ocultar nada (`@NotTraced` oculta un valor; no
  delimita una traza). Corrige el defecto en ese span, vuelve a ejecutar la misma entrada y
  comprueba que nada más se ha movido — una ejecución en rojo no escribe ningún `.nt`, así que la
  forma anterior a la corrección son las líneas de llamada del Markdown de la reproducción sin sus
  valores. Conserva la reproducción como prueba de regresión, fija su traza estructural con la
  misma puerta de aprobación que `narrativetrace-verify` e informa de la causa raíz por id de span.
  Cuando la traza y el código no coinciden, o el defecto es de NarrativeTrace, se lo pasa a
  `narrativetrace-feedback` en lugar de parchear alrededor.

Se combinan: un proyecto totalmente nuevo empieza con `add-narrative-tracing`; un proyecto que ya
tiene NarrativeTrace instalado, donde algo no funciona, empieza con `narrativetrace-doctor`.
Ambos caminos terminan en el doctor — a partir de ahí, el diagnóstico es suyo.
`add-narrativetrace-clarity` se encarga de los primeros informes estáticos de nombres y de la
aplicación opcional de umbrales de claridad; no instala trazas ni cosecha un glosario.
`narrativetrace-feedback` es donde termina un camino cuando el problema resulta ser nuestro y no
del proyecto — la propia regla de cierre del doctor apunta a él. `narrativetrace-verify` es lo que
hace una sesión con NarrativeTrace instalado después de cada cambio que merezca trazarse — el último
paso de la habilidad de instalación dirige a la siguiente sesión hacia ella, y el hallazgo
`config.approval-mode` del doctor (líneas base que nada compara) se corrige con su paso de fijación.
`narrativetrace-debug` es por donde empieza un síntoma reportado; comparte con la habilidad de
verificación su referencia de lectura (qué variante responde a qué pregunta, y las formas que
indican que algo salió mal) y su fijación, y termina en `narrativetrace-feedback` cuando el defecto
es nuestro.

## Instalarlas

Un solo comando, en el proyecto donde las quieres:

```bash
./gradlew narrativetraceInit --diff
```

Eso PREVISUALIZA la instalación y no escribe nada. Informa del plan — con `--json`, ese mismo plan
como un envoltorio sobre el que un script puede decidir:

```json
{
  "carrier": "ai.narrativetrace:narrativetrace-cli:0.3.0",
  "actions": [
    {"kind": "create", "path": ".agents/skills/narrativetrace-doctor/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/add-narrative-tracing/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/add-narrativetrace-clarity/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/narrativetrace-feedback/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/narrativetrace-verify/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/narrativetrace-debug/SKILL.md", "status": "planned"},
    {"kind": "create", "path": "AGENTS.md", "status": "planned"}
  ],
  "exitCode": 0
}
```

Léelo y luego ejecuta el mismo comando sin la bandera para aplicarlo. Nada se escribe hasta
entonces; no hay hook de postinstalación ni paso de build que instale habilidades a tus espaldas.
Lo que escribe:

- `.agents/skills/<nombre>/SKILL.md` para cada habilidad del catálogo — siempre, con cualquier
  agente.
- `.claude/skills/<nombre>/SKILL.md` además, cuando el proyecto tiene un directorio `.claude/` o un
  `CLAUDE.md` (o con `--vendor claude`; `--vendor none` lo desactiva).
- una sección marcada en `AGENTS.md`, creada si el fichero no existe, reemplazada entre sus propios
  marcadores si una ejecución anterior escribió una. Un `AGENTS.md` que ya existe sin nuestros
  marcadores requiere `--write-existing`, de modo que una primera ejecución nunca pueda añadir texto
  a un fichero que no esperabas que tocara.
- una única línea de importación `@AGENTS.md` en un `CLAUDE.md` existente, y nunca un `CLAUDE.md`
  propio.

Repetir la ejecución es seguro: una acción que escribiría lo que ya está ahí se descarta, así que un
proyecto al día no planifica nada. `./gradlew narrativetraceUninstall` elimina exactamente lo que se
instaló — una página solo cuando lleva el sello del propio instalador, un fichero solo cuando el
instalador lo creó y no queda nada tuyo dentro. La referencia completa de tareas y banderas está en
la [Guía del plugin de Gradle](guia-del-plugin-de-gradle.md#narrativetraceinit).

Sin el plugin de Gradle, el mismo instalador es un verbo del lanzador:
`narrativetrace init --dry-run` previsualiza y `narrativetrace init` aplica (la CLI conserva
`--dry-run`, donde nada lo tapa; la tarea de Gradle no puede, porque el propio `--dry-run` de Gradle
omite todas las tareas del grafo). Ejecutado a través del plugin, el plan de arriba nombra
`ai.narrativetrace:narrativetrace-skills` como su portador en lugar del lanzador — las mismas
páginas desde el archivo que el proyecto ya haya descargado.

### A mano, el respaldo

Copiar también funciona, y es el único camino para un agente sin build propio:

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
  `<!-- narrativetrace:skills:start -->` — el nombre y la descripción de las seis habilidades, de modo
  que un agente que nunca pensó en buscarlas igualmente sepa que existen.
- **Gemini** se ejecuta contra el mismo catálogo con una cadencia esporádica y limitada por cuota
  (ver [Tier B — pruebas con LLM](../../narrativetrace-skills-catalogue/evals/README.md)) en lugar de en cada
  commit; todavía no tiene una disposición propia renderizada — copiar los ficheros de Claude o
  Codex de arriba es, por ahora, el camino más cercano.

### El doctor informa sobre esto

Una de sus comprobaciones, `config.skills-installed`, lee cada
`SKILL.md` instalado y el sello que el instalador dejó en él, y falla cuando las habilidades no
están, cuando falta alguna, o cuando su sello nombra una versión distinta de la que el proyecto
resuelve — de modo que una copia obsoleta aparece como un hallazgo en vez de como un agente
siguiendo en silencio las instrucciones de la versión anterior. Un directorio situado en la ruta
de una habilidad sin ese sello se reporta como de otra persona y nunca se cuenta como instalado.
Cuando el portador no puede resolverse en absoluto — una compilación sin red, o una sin
repositorio que lo provea — la comprobación dice que no puede saberlo y pasa: estar sin red no es
un defecto.

## Desde un registro

Un proyecto puede llevar estas habilidades sin que nadie aquí ejecute nunca el instalador, en uno
de tres estados:

1. **Instaladas por `init`** — commiteadas, las del equipo. El único estado que
   `config.skills-installed` aprueba: las páginas llevan la línea de procedencia y coinciden con
   la versión que este proyecto resuelve.
2. **Una instalación personal desde un registro** (una caché de plugins de Claude Code) — solo
   tuya. Invisible para el doctor por diseño: diagnostica el proyecto, y una instalación personal
   no llega a ningún compañero de equipo ni a ningún otro agente.
3. **Una instalación de registro dentro del proyecto** (`npx skills add`, `gemini skills install
   --scope workspace`) — las propias páginas renderizadas de este repositorio, dejadas por un
   registro en lugar de por `init`, así que todavía no llevan línea de procedencia.

Para probarlas tú mismo, sin tocar el proyecto:

```text
/plugin marketplace add narrativetrace/narrativetrace-java
/plugin install narrativetrace-java@narrativetrace-java
```

luego ejecuta `./gradlew narrativetraceInit --diff`, lee el diff, y ejecútalo sin la bandera para
que `AGENTS.md` apunte a ellas.

Para instalarlas en el proyecto desde el registro de estándar abierto:

```text
npx skills add narrativetrace/narrativetrace-java
```

luego ejecuta `./gradlew narrativetraceInit --diff`, lee el diff, y ejecútalo sin la bandera para
que `AGENTS.md` apunte a ellas.

Una página que deja atrás un registro nunca se rechaza solo por estar ahí. `init` la compara, byte
a byte salvo la línea de procedencia, con lo que ella misma habría renderizado. Una idéntica a la
propia página de esta versión queda **adoptada** — el plan lo dice así, en vez de "reemplazada",
porque quien lo lee tiene que saber que no se sobrescribió nada de nadie. Este es el propio texto
del plan, citado, nunca retecleado aquí:

```java
  private static final String ADOPTED =
      "adopted: identical to this carrier's page, so only the provenance line is added";
```

Una página que difiere — otra versión, o editada a mano — conserva el rechazo ordinario para el
que está `--force`. `npx skills add` también deja `.claude/skills/<nombre>` como un enlace
simbólico a la página de estándar abierto; `init` nunca escribe a través de un enlace así. Un
enlace cuyo destino adoptaría o ya es suyo se reemplaza por un directorio real con el sabor
correcto; cualquier otro enlace se rechaza, porque `--force` cubre contenido, nunca un enlace.

Y esta es la propia corrección del doctor, citada del mismo modo, para un proyecto donde las
páginas están pero no llevan nada de esto:

```java
  /** Both spellings of the same command: Gradle owns {@code --dry-run}, so the task says diff. */
  private static final String INIT_COMMANDS =
      "./gradlew narrativetraceInit --diff (or narrativetrace init --dry-run)";

  /** What a skill directory that is present but not ours is called in a message. */
  private static final String NOT_OURS = " (there, but not ours)";

  /**
   * What a page with no provenance line most often IS: a registry install (D5 state 3) — `npx
   * skills add`, or a workspace skills install — of this repository's own rendered pages. Naming
   * the case matters because the obvious reading of "not ours" is "somebody else's work", which
   * invites a `--force` nobody needs: `init` ADOPTS a page identical to this release's.
   */
  private static final String FROM_A_REGISTRY =
      " Pages that are there without our line usually came from a registry (npx skills add, a"
          + " plugin or workspace install). Run "
          + INIT_COMMANDS
          + ", read the diff, then run it without the flag — a page identical to this release's is"
          + " adopted, and no --force is needed.";
```

## Cómo se construyen

Ninguna habilidad se edita a mano.
`narrativetrace-skills-catalogue/src/main/java/ai/narrativetrace/skills/catalogue/` contiene las seis
fuentes de la verdad; sus pasos tipados renderizan seis páginas de Claude, seis de Codex, la copia
que el portador guarda de ambos sabores más su índice `catalogue.json` — lo que el jar publicado
entrega a `narrativetraceInit` — la sección de `AGENTS.md` de este repositorio, y el listado
`.claude-plugin/marketplace.json` que convierte este repositorio en un marketplace de plugins de
Claude Code: una prueba de deriva (`RenderDriftTest`, conectada a `./gradlew check`) hace fallar la
compilación si cualquiera de esas veintisiete salidas se desvía de la fuente tipada, y una segunda la
hace fallar si aparece cualquier otra cosa dentro del portador. El `name:`
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
razonamiento se publican, la cita que nombra la fuente no. Un segundo lint vigila el único campo
del frontmatter cuya ausencia es una característica: una habilidad cuyos pasos pueden hacer algo
público — hoy, `narrativetrace-feedback` — no debe declarar ningún `allowed-tools`, porque ese
campo preaprueba las herramientas que enumera durante el turno que carga la habilidad, y una
habilidad de informes que preaprobara su propio comando de informe dejaría de hacer preguntar al
entorno justo donde preguntar es el objetivo. Un tercer lint limita una habilidad que promueve una
línea base de aprobación — hoy, `narrativetrace-verify` y `narrativetrace-debug` — a ninguna herramienta permitida más allá
de `find`, de solo lectura, por la misma razón: la promoción pasa por `./gradlew`, y el sí que
espera es el tuyo.

## Ver también

- [`narrativetrace-cli`](../../narrativetrace-cli/) — el verbo `doctor` que ejecuta `narrativetrace-doctor`
- [Privacidad y ocultación de datos](privacidad-y-ocultacion.md) — la lista de denegación y las formas de valor con las que se miden las reglas sin-valores del informe de problemas
- [Ve una traza en 60 segundos](sesenta-segundos.md) — el recorrido de instalación y primera traza del que se extraen los pasos de `add-narrative-tracing`
- [Qué commitear](que-commitear.md) — el estado de trazas de aprobación que comprueba el cuarto paso del doctor
- [Tier B — pruebas con LLM](../../narrativetrace-skills-catalogue/evals/README.md) — la disposición de casos neutral respecto al motor, la política esporádica de Codex/Gemini, y la matriz de promoción
