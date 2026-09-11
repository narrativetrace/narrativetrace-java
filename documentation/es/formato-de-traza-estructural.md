<!-- source: documentation/structural-trace-format.md blob 752b803add4d | translated: 2026-09-09 | reviewed: - -->
# Formato de traza estructural (`.nt`)

[English](../structural-trace-format.md) | **Español** | [简体中文](../zh-CN/结构化追踪格式.md)

El artefacto de traza estructural seguro para IA (ADR-002): un fichero por
escenario de prueba que contiene solo la *forma* del comportamiento
redactada por el desarrollador — cero valores en tiempo de ejecución. Este
formato es **multiplataforma**: cada implementación de NarrativeTrace emite
el formato idéntico, lo que permite que las líneas base de aprobación y los
fixtures de conformidad viajen entre plataformas.

## Ficheros y nomenclatura (decisiones multiplataforma, 2026-08-24)

| Fichero | Rol |
|---|---|
| `build/narrativetrace/structural/<TestClass>/<scenario>.nt` | Se emite en la vía de markdown; el fichero en disco es la **última línea base en verde** — una ejecución no verde se compara contra él (delta en consola, informe de fallos) pero nunca lo sobrescribe. "Verde" es el veredicto completo: un test que pasó pero cuya estructura la aprobación *rechazó* termina en rojo, así que una estructura rechazada nunca se convierte en la línea base y revertir el cambio no reporta ningún delta |
| `src/test/narratives/<TestClass>/<scenario>.approved.nt` | Línea base de aprobación comiteada (`NarrativeApproval`; opt-in mediante `narrativetrace.approval=true`, directorio configurable mediante `narrativetrace.approvedDir`) — una prueba que pasa pero cuya estructura difiere falla con un diff legible |
| `<scenario>.received.nt` | Se escribe junto a la línea base cuando la aprobación no coincide (o cuando aún no existe una línea base); revísalo y luego promuévelo mediante la tarea de Gradle `approveNarratives` |
| `<scenario>.incomplete.nt` | El mismo contenido, escrito en lugar de `.received.nt` cuando la propia ejecución fue incompleta (la ruta de mejor esfuerzo descartó eventos, o rechazó un ámbito async al alcanzar el tope de adopción). `approveNarratives` lo ignora por nombre: una ejecución corta nunca debe convertirse en la línea base comiteada, o cada ejecución completa posterior se leería como si hubiera *añadido* llamadas. Una ejecución así se compara por contención de subsecuencia en lugar de por igualdad — las ausencias se toleran y se nombran, cualquier cosa añadida o reordenada sigue fallando |

La extensión del formato va al final (`.approved.nt`, convención de
ApprovalTests) para que los editores y los visores de diffs se guíen por
`.nt`. Nota: `.nt` colisiona con RDF N-Triples en algunos mapas de
resaltado de sintaxis; registra una anulación en `.gitattributes` donde
importe.

### Identidad de artefacto (multiplataforma, 2026-09-09)

`<scenario>` arriba es la **identidad de artefacto** de una invocación de
test, y todos los runtimes la escriben igual — un artefacto escrito por
un runtime se encuentra bajo el mismo nombre desde otro:

- Un método de test ordinario es su nombre convertido a slug: camel-case
  separado con `_`, en minúsculas, y todo lo que quede fuera de
  `[a-z0-9_]` reemplazado por `_` — `customerPlacesOrder` →
  `customer_places_order`.
- Una invocación de un método que se ejecuta más de una vez
  (parametrizado, repetido) añade `-<índice>-<etiqueta>`: el número de
  invocación base 1 rellenado con ceros a tres dígitos, y después el
  nombre visible de la invocación por la misma regla de slug, con las
  secuencias de `_` colapsadas y los extremos recortados —
  `equipment_can_be_found-002-find_tent`. Una etiqueta cuyo slug queda
  vacío se omite, dejando `equipment_can_be_found-002`.
- `-` es el separador precisamente porque el alfabeto del slug no puede
  producirlo. El índice — no la etiqueta — es lo que hace el esquema a
  prueba de colisiones: dos invocaciones siempre difieren en él, así que
  nombres visibles que solo se diferencian en caracteres que una ruta no
  puede llevar (`find/TENT` frente a `find TENT`) reciben archivos
  distintos. La etiqueta es lo que hace el nombre legible.
- El nombre es estable entre ejecuciones, máquinas y procesos, que es lo
  que permite comitear el `.approved.nt` de una invocación. Cuando un
  nombre supera el límite de 255 bytes por elemento de ruta, se trunca la
  mitad de *método* y se le añaden ocho caracteres hexadecimales del
  `String.hashCode` de Java sobre el slug completo — está especificado,
  por tanto es idéntico en todas partes; un hash por proceso invalidaría
  en silencio cada línea base que tocara.

Como los nombres de artefacto son derivados y no anunciados, una
ejecución también escribe `<outputDir>/manifest.json`: una fila por
escenario trazado que nombra su test, su número de invocación y cada
archivo que le pertenece. Léelo cuando conozcas el escenario y quieras el
archivo.

> La cabecera `scenario:` es un nombre visible, y la plantilla de nombre
> visible de un test parametrizado interpola argumentos en ella. Los
> cuerpos de llamada siguen sin valores; la cabecera y el nombre de
> archivo no. No interpoles un secreto en una plantilla de nombre
> visible.

## Contenido

```
scenario: Weekend trip settles with three transfers

- TripSettlementService.recordExpense(tripName, expense)
  - ExpenseValidator.ensureValid(expense)
  - TripLedger.recordExpense(tripName, expense)
- TripSettlementService.settleTrip(tripName) → value
  - TripLedger.expensesOf(tripName) → value
  ~ fork [2]
    - BalanceCalculator.computeBalances(expenses) → value
    - StockService.check() → value
```

- **Cabecera:** `scenario: <humanized test name>` + línea en blanco. Nada
  más — sin resultado, sin ids/nombres de traza, sin fechas.
- **Línea de llamada:** `ClassName.methodName(paramName, paramName)` —
  solo nombres, en el orden de captura, con dos espacios de indentación
  por nivel de profundidad.
- **Tipos de resultado:** retorno no-void ` → value`; void: nada (el
  contrato de retorno nulo); lanzada ` !! ExceptionSimpleName` (el tipo es
  estructura; el mensaje es un valor y nunca aparece); enter sin
  emparejar ` ?? incomplete`.
- **Concurrencia:** los grupos fork se renderizan como `~ fork [n]` y el
  trabajo adoptado de una instantánea de contexto propagada (`@Async` de
  Spring, Micrometer, cualquier `snapshot.activate()` manual) se
  renderiza como `~ async [n]`, ambos con miembros **ordenados por
  `Class.method`** — el orden de captura entre hilos es una elección del
  planificador, no comportamiento, así que el artefacto expresa el
  conjunto y el anidamiento del trabajo concurrente pero nunca su orden.
  Los grupos async se indexan por el span que los lanza, de modo que
  cada hijo async de una llamada forma un único grupo, y también
  aparecen a nivel raíz cuando el trabajo sobrevive a quien lo llamó.
  Fire-and-forget (lanzar y olvidar) se renderiza como
  `~ fire-and-forget` + hijos. Los nombres/ids de hilo nunca aparecen.
- **Excluido por diseño:** todos los valores de argumentos/retorno, los
  mensajes de excepción, las duraciones, las marcas de tiempo, la
  identidad del hilo, los ids de traza/span, los nombres de traza, los
  resultados de ejecución y la narración (la narración resuelta incrusta
  valores; la *plantilla* de narración se sumará cuando
  `nt.narrationTemplate` llegue con la Fase 6 del glosario).
- **Codificación:** UTF-8, LF, salto de línea final. Los identificadores
  pasan por saneamiento de caracteres de control.

## Identidad en los hermanos JSON

El artefacto `.nt` no lleva ninguna identidad — eso es lo que lo hace
determinista byte a byte. Sus hermanos JSON sí: los arrays de entradas
`<test>.canonical.json` / `<test>.structural.json` por prueba, el
envoltorio de capítulo (`chapter.schema.json`) y el documento de árbol de
capítulos (`chapter-tree.schema.json`, que el capítulo incrusta en
`nt.chapterTree`). Los tres emisores resuelven la identidad de la misma
forma, así que un capítulo nunca puede nombrar una traza mientras el
árbol que contiene nombra otra. Ahí los tres campos de identidad están
**siempre presentes**, sea cual sea la forma en que se hizo la captura:

| Campo | Cómo se resuelve |
|---|---|
| `trace_id` | Adoptado de un `traceparent` entrante, si no, heredado de la traza bajo la que corrió la captura, si no, generado — siempre un id real, único, con forma W3C (32 hex en minúsculas, nunca todo ceros). Nunca una constante compartida, y nunca regenerado por entrada. |
| `nt.storyId` | Heredado cuando la traza ya lleva uno, si no, derivado de la primera llamada de nivel raíz como `Class.method`. Nunca generado. |
| `nt.chapterId` | Heredado cuando la traza ya lleva uno, si no, igual a `nt.storyId` — el capítulo de este servicio para esa historia. Nunca generado. |

`nt.traceName` se deriva de `trace_id` (la frase de tres palabras), así
que siempre concuerda con él, y `service` recurre a `unknown_service:java`
cuando nada lo suministró. Un árbol cuyos nodos perdieron su contexto de
span — uno ensamblado a mano, reproducido a partir de un artefacto o
producido por un análisis estático — se exporta igualmente como la única
traza que es: la identidad pertenece al árbol, se resuelve una sola vez,
y la comparten el capítulo, el árbol que incrusta, y cada entrada de ese
capítulo.

Por eso el bloque `trace` del documento de árbol de capítulos siempre
lleva `traceId` y `traceName`. Sus campos restantes — `serviceName`,
`environment`, `httpMethod`, `httpRoute` y los identificadores de ámbito
de la petición — solo se escriben cuando el árbol realmente llevaba un
contexto de span del que heredarlos: una identidad generada sabe *cuál*
traza es esta y nada sobre quién la llamó, e inventar un nombre de
servicio sería peor que omitirlo. `chapter-tree.schema.json` marca todo
el bloque como opcional, algo que la forma siempre-presente satisface;
el esquema es un piso, no el contrato entre los emisores.

Por lo tanto, dos ejecuciones del mismo comportamiento producen ficheros
`.nt` idénticos y `trace_id` *distintos*. Esa es la división pretendida:
los campos cuyo trabajo es agrupar o describir son derivados y estables;
los campos cuyo trabajo es ser únicos se generan (ADR-014). Una
comparación de conformidad entre ejecuciones o entre implementaciones
normaliza los campos únicos antes de comparar.

## Garantías

1. **Determinista:** comportamiento idéntico ⇒ fichero idéntico byte a
   byte. Esto es lo que convierte al artefacto en la línea base de las
   pruebas de aprobación y en el formato de referencia (golden format)
   de los fixtures de conformidad.
2. **Libre de valores:** superficie de prompt-injection nula, cero PII,
   tokens mínimos — seguro para entregar por defecto a un agente de IA
   (la salida de Nivel 1 del nivel gratuito).
3. **División del trabajo:** el artefacto afirma la *forma* del
   comportamiento; la corrección de los valores sigue siendo trabajo de
   las aserciones de la prueba. Un cambio que solo altera un valor de
   retorno con estructura idéntica no cambia el artefacto — por diseño.

Implementado en este repositorio por `core: StructuralTraceRenderer`, emitido
por `TraceTestSupport` junto a los acompañantes `.md`/`.json`/`.mmd`.
