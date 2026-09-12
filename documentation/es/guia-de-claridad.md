<!-- source: documentation/clarity-guide.md blob d48b3ebbdbb8 | translated: 2026-09-12 | reviewed: - -->
# Guía de claridad de NarrativeTrace Java
[English](../clarity-guide.md) | **Español** | [简体中文](../zh-CN/清晰度指南.md)

Si la traza es el código, entonces la calidad de la traza es la calidad del código. El módulo de claridad analiza los nombres de tus métodos, clases y parámetros, y puntúa qué tan bien comunican la intención.

## Inicio rápido

```java
var analyzer = new ClarityAnalyzer();
var result = analyzer.analyze(context.captureTrace());

var renderer = new ClarityReportRenderer();
System.out.println(renderer.render("Order Placement", result));
```

Con JUnit 5, los informes de claridad se generan automáticamente por defecto — sin necesidad de código, sin necesidad de configuración *(since 0.2.2, unreleased)*. `narrativetrace.output=false` lo desactiva junto con el resto de artefactos de traza.

## Qué se puntúa

La claridad produce una única puntuación global (0.0–1.0) a partir de cinco componentes ponderados:

| Componente | Peso | Qué mide |
|---|---|---|
| Nombres de métodos | 30% | Calidad del verbo, especificidad de los tokens, abreviaturas, número de tokens |
| Nombres de parámetros | 25% | Especificidad de dominio frente a tokens genéricos o sin significado |
| Nombres de clases | 20% | Calidad del sufijo de rol, especificidad del prefijo |
| Estructural | 15% | Penalizaciones por número de parámetros y profundidad de llamadas |
| Cohesión | 10% | Si los métodos se alinean con el sufijo de rol de la clase |

## La puntuación en la práctica

### Nombres de métodos

El primer token se trata como un verbo. Los verbos de dominio puntúan más alto; los verbos genéricos, más bajo:

| Categoría de verbo | Ejemplos | Puntuación |
|---|---|---|
| Dominio | `calculate`, `validate`, `reserve`, `dispatch` | 0.60 |
| Estándar | `create`, `find`, `delete`, `update` | 0.45 |
| Prefijo booleano | `is`, `has`, `can`, `contains` | 1.00 |
| Genérico | `get`, `set`, `process`, `handle`, `execute` | 0.10 |

Los métodos de varios tokens como `reserveInventory` puntúan más alto que los de un solo token como `reserve`, porque los tokens adicionales añaden especificidad.

### Nombres de clases

Se espera un sufijo de rol. Los sufijos de patrones de diseño y los funcionales puntúan bien cuando van acompañados de un prefijo de dominio:

| Patrón | Puntuación | Por qué |
|---|---|---|
| `OrderService` | 1.0 | Prefijo de dominio + sufijo funcional |
| `Service` | 0.0 | Sin prefijo — sin significado |
| `DataProcessor` | Baja | Prefijo vago + sufijo genérico |
| `BookingManager` | Media | Prefijo de dominio, pero `Manager` es genérico |

### Nombres de parámetros

Los nombres específicos del dominio puntúan alto; los nombres genéricos, bajo:

| Nivel | Ejemplos | Puntuación |
|---|---|---|
| Específico del dominio | `customerId`, `checkInDate`, `roomCategory` | 0.80+ |
| Genérico tipado | `id`, `name`, `count`, `status` | 0.50 |
| Vago | `data`, `info`, `result`, `object` | 0.10 |
| Sin significado | `x`, `foo`, `val`, `temp` | 0.00 |

### Penalizaciones estructurales

Los métodos con más de 4 parámetros o con una profundidad de llamadas superior a 5 se penalizan. Cada parámetro en exceso cuesta 0.1; cada nivel de profundidad en exceso cuesta 0.05.

### Cohesión

Los métodos se comparan con los verbos esperados para el sufijo de rol de la clase. De una clase `Repository` se esperan métodos como `find`, `save`, `delete`, `count`. Un método como `renderReport` en un `GuestRepository` se marca como desalineado.

## Incidencias y severidad

Los problemas de claridad se reportan como incidencias ordenadas por impacto:

| Severidad | Umbral | Ejemplos |
|---|---|---|
| HIGH | puntuación ≤ 0.20 | `DataProcessor.execute(data)` |
| MEDIUM | puntuación ≤ 0.50 | `BookingManager.handleBooking(name, type)` |
| LOW | puntuación > 0.50 | Uso menor de abreviaturas |

Las incidencias duplicadas (misma categoría y mismo elemento) se deduplican con un contador de ocurrencias. Las incidencias se ordenan por puntuación de impacto (peso de la severidad × ocurrencias).

## Notas por elemento

Las incidencias están limitadas por umbral — solo listan los nombres que caen por debajo de un corte de severidad. Las notas son lo contrario: **una nota en lenguaje llano por elemento en cada puntuación**, de modo que un buen nombre aprende *por qué* puntúa bien y uno débil aprende *qué cambiar*. Un 0.86 desnudo deja de ser un veredicto sin derecho a apelación.

Cada método, clase, parámetro y componente de record recibe una nota construida con los mismos diccionarios que usan los puntuadores:

| Elemento | Puntuación | Nota |
|---|---|---|
| `OrderService.reserveInventory` | 0.95 | Domain verb 'reserve' + domain noun 'inventory' |
| `Service.processData` | 0.30 | Generic verb 'process' + vague noun 'data' |
| `OrderManager` | 0.62 | Generic suffix 'Manager' — prefer a precise role |
| `amount` | 0.50 | Broad noun 'amount' — qualify it (e.g., orderAmount) |

Las notas aportan orientación concreta allí donde los diccionarios pueden proporcionarla. Un verbo genérico o no reconocido junto a un sustantivo de dominio conocido gana una sugerencia de renombrado `verbNoun` — `Generic verb 'process' + broad noun 'order' — consider: backorderOrder, cancelOrder, fulfillOrder` — y cualquier abreviatura penalizada se explica en línea: `; spell out: chk → check`. Los accesores de record se puntúan con la rúbrica de sustantivos y se expresan para componentes (`Domain-specific component 'customerId'`).

Las notas nunca entran en `issues[]` — un elogio no es una acción a realizar. Se muestran en la tabla **Elements** del informe y en el array `elements` de `clarity-results.json` (esquema 1.2).

## Tu propio vocabulario, a partir del glosario que ya tienes

Los diccionarios integrados conocen el inglés general del software. No saben
que `fold` es un verbo de tu dominio, que `tranche` es un sustantivo preciso, o
que `fx` es la abreviatura aceptada de tu equipo — y un nombre que no conocen
se puntúa como desconocido, no como específico del dominio.

Se los enseñas con el fichero de vocabulario que tu repositorio ya lleva: el
`glossary.json` comiteado (ADR-012). No hay un segundo fichero de diccionario
que mantener sincronizado.

| Entrada del glosario | Tipo | Qué aprende la claridad |
|---|---|---|
| `settle trade` | `verb-phrase` | `settle` es un verbo del dominio; `trade` es un sustantivo del dominio |
| `credit tranche` | `noun-phrase` | `credit` y `tranche` son sustantivos del dominio |
| `fx` | `word` | `fx` es un sustantivo del dominio |

Los términos de varias palabras enseñan token a token, porque los
identificadores se puntúan token a token. Todos los contextos delimitados
contribuyen: un identificador no lleva paquete, así que el alcance por contexto
no puede aplicarse en el momento de puntuar.

### La abreviatura aceptada se declara, no se infiere

Los términos enseñan vocabulario. **No** deciden que una grafía corta sea
aceptable por sí sola — comitear la frase `calc total` no dice nada sobre si un
método puede llamarse `calcTotal`. Esa decisión vive en su propia sección de
nivel raíz de `glossary.json` (esquema 2):

```json
{
  "schemaVersion": 2,
  "contexts": { },
  "abbreviations": { "fx": "foreign exchange", "calc": "calculate" },
  "terms": [ ]
}
```

Una abreviatura listada queda aceptada — nunca se penaliza ni se pide
desarrollarla — y el canal de notas la enseña con tu propia expansión:
`; project shorthand: fx → foreign exchange`. Una abreviatura que **no** hayas
listado conserva su tratamiento integrado, aunque aparezca dentro de una frase
comiteada.

La sección es de propiedad humana: la cosecha nunca la escribe y una fusión la
transporta intacta. Un glosario que no declara abreviaturas se queda en
`schemaVersion` 1 y su fichero es idéntico byte a byte al de siempre.

### Lo que el glosario no puede hacer

Los diccionarios integrados conservan su autoridad. Un proyecto puede enseñar a
los puntuadores una palabra que no conocen; no puede anular una que sí conocen.

- **Los verbos genéricos siguen siendo genéricos.** Comitear `process` o
  `handle` no los promociona — `Generic verb 'process'` sigue apareciendo en las
  notas y la puntuación del nombre del método sigue reflejándolo. Lo mismo vale
  para los prefijos booleanos (`is`, `has`).
- **Los marcadores sin significado siguen sin significado.** `temp`, `foo` y
  compañía no se rescatan por estar escritos.
- **Los sinónimos obsoletos nunca son vocabulario.** Un alias existe para ser
  señalado; promocionarlo silenciaría la incidencia `non-canonical-term` para la
  que está declarado.
- **Los términos `stale` no son vocabulario.** Marcar un término como stale dice
  que la palabra salió del dominio.

Solo cuenta el fichero *comiteado*. Nada de lo que una ejecución recolecte
realimenta las puntuaciones de esa misma ejecución — un vocabulario que se
expande solo haría las puntuaciones no deterministas y autocertificadas. El
commit es la aprobación humana.

### Dónde se aplica

| Superficie | Cómo se encuentra el glosario |
|---|---|
| Extensión de JUnit 5, regla de JUnit 4 | `narrativetrace.glossaryDir` (por defecto: el directorio de trabajo; el plugin de Gradle lo fija a la raíz del repositorio) |
| `clarityScan` | `--glossary-dir`, que el plugin de Gradle fija a la raíz del repositorio |

La lectura es incondicional — a diferencia de la recolección, que es opt-in
porque reescribe ficheros fuera del directorio de build. Un repositorio sin
`glossary.json` se puntúa exactamente como antes de que existiera esta
funcionalidad, y un glosario que no se puede leer degrada a los diccionarios
integrados con un aviso, en lugar de hacer fallar la suite.

## Salida del informe

### Escenario único

```java
renderer.render("Guest books a room", result);
```

Produce un informe en Markdown con una tabla de puntuaciones, una tabla de elementos y una tabla de incidencias (si las hay):

```markdown
## Clarity Report — Guest books a room
Overall: 0.95 (high)

| Component  | Score |
|------------|-------|
| Method     | 0.98  |
| Class      | 1.00  |
| Parameter  | 0.90  |
| Structural | 1.00  |
| Cohesion   | 0.85  |

## Elements

| Element | Score | Note |
|---------|-------|------|
| `ReservationService.confirmReservation` | 0.98 | Domain verb 'confirm' + domain noun 'reservation' |
| `ReservationService` | 1.00 | Role suffix 'Service' |
| `guestId` | 0.80 | Domain-specific noun 'guestId' |
```

La tabla de elementos se renderiza para cada escenario — incluidos los de puntuación alta — mientras que la tabla de incidencias se limita a los nombres por debajo del corte de severidad.

### Informe de la suite

```java
renderer.renderSuiteReport(Map.of(
    "Guest books a room", result1,
    "Legacy data processing", result2
));
```

Produce un resumen ordenado de todos los escenarios. Los escenarios con puntuación inferior a 0.7 reciben un desglose detallado con sus incidencias individuales; cada escenario — sea cual sea su puntuación — recibe una tabla de elementos, de modo que ninguna puntuación queda sin explicar.

## Integración con JUnit 5

Por defecto (salvo que `narrativetrace.output=false` esté en `junit-platform.properties`), la extensión de JUnit automáticamente:

1. Ejecuta `ClarityAnalyzer.analyze()` sobre la traza de cada prueba
2. Escribe `clarity-report.md` en el directorio de salida cuando terminan todas las pruebas
3. Imprime un resumen en consola con la distribución de puntuaciones:

```
NarrativeTrace — Suite complete
  2 scenarios recorded
  Clarity: 100% high | 0% moderate | 0% low
  Reports: build/narrativetrace
```

No se necesitan cambios en el código — solo habilita la salida y ejecuta tus pruebas.

## Cumplimiento en el build con `clarityCheck`

El plugin de Gradle proporciona una tarea `clarityCheck` que hace fallar el build cuando la calidad de los nombres cae por debajo de un umbral. Esto convierte la puntuación de claridad en algo exigible, no meramente consultivo.

### Configuración

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.1"
}

narrativeTrace {
    clarity {
        minScore.set(0.80)     // falla si algún escenario puntúa por debajo de 0.80
        maxHighIssues.set(0)   // falla si algún escenario tiene incidencias de severidad HIGH
    }
}
```

### Cómo funciona

1. `./gradlew test` — la extensión de JUnit produce `build/narrativetrace/clarity-results.json`
2. `clarityCheck` lee el JSON y compara cada escenario con los umbrales
3. `./gradlew check` ejecuta `test` y `clarityCheck` automáticamente

### Salida en caso de fallo

Cuando un escenario cae por debajo del umbral:

```
FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':clarityCheck'.
> Clarity check failed:
    'Legacy data processing': score 0.45 < threshold 0.80
    'Legacy data processing': 3 HIGH issues (max 0)
```

### Modo de solo advertencia

Para una adopción gradual, usa `warnOnly` para registrar las violaciones sin hacer fallar el build:

```kotlin
narrativeTrace {
    clarity {
        minScore.set(0.70)
        warnOnly.set(true)
    }
}
```

### Incidencias a nivel de suite

Las incidencias que pertenecen a la ejecución completa y no a un escenario concreto — hoy, las violaciones de vocabulario `non-canonical-term` reportadas por la recolección del glosario — aparecen en la sección **Suite Issues** del informe y en el array `suiteIssues` de nivel superior del JSON. Nunca afectan a las puntuaciones de los escenarios. La comprobación de vocabulario solo se ejecuta cuando existe un `glossary.json` versionado antes de la ejecución; un proyecto sin glosario nunca recibe incidencias de suite.

Por defecto las incidencias de suite son consultivas: `clarityCheck` las registra como advertencias sin hacer fallar el build. Activa una barrera estricta con `maxSuiteIssues`:

```kotlin
narrativeTrace {
    clarity {
        maxSuiteIssues.set(0)   // falla ante cualquier incidencia a nivel de suite
    }
}
```

### Contrato JSON

El archivo `clarity-results.json` es el contrato entre la ejecución de las pruebas y la tarea `clarityCheck`:

```json
{
  "version": "1.2",
  "scenarios": [
    {
      "name": "Customer places order",
      "overallScore": 0.85,
      "methodNameScore": 0.90,
      "classNameScore": 0.95,
      "parameterNameScore": 0.80,
      "structuralScore": 1.00,
      "cohesionScore": 0.70,
      "issues": [
        {
          "category": "param-name",
          "element": "data",
          "suggestion": "Use a domain-specific name",
          "severity": "MEDIUM",
          "occurrences": 2,
          "impactScore": 4.0
        }
      ],
      "elements": [
        {
          "kind": "parameter",
          "element": "data",
          "score": 0.10,
          "note": "Vague name 'data' — say what it holds"
        }
      ]
    }
  ],
  "suiteIssues": [
    {
      "category": "non-canonical-term",
      "element": "billing.OverdraftService.openAccountWithOverdraft",
      "suggestion": "use canonical term 'overdraft account' → rename to openOverdraftAccount",
      "severity": "MEDIUM",
      "occurrences": 2,
      "impactScore": 4.00
    }
  ]
}
```

El array `suiteIssues` de nivel superior (esquema 1.1) contiene las incidencias a nivel de suite; siempre está presente, vacío cuando la ejecución no produjo ninguna. El array `elements` de cada escenario (esquema 1.2) lleva una nota por elemento en cada puntuación. Ambas adiciones son puramente aditivas: los archivos con esquema 1.0/1.1 sin estos campos siguen siendo aceptados por `clarityCheck` y por los consumidores del JSON.

## Demo

Ejecuta la demo de claridad de reservas de hotel para ver la puntuación en cuatro niveles de calidad:

```bash
./gradlew :narrativetrace-examples:clarity:run
```

La demo traza cuatro escenarios con nombres progresivamente peores — desde `ReservationService.confirmReservation(guestId, roomCategory)` (excelente) hasta `DataProcessor.execute(data, val)` (pobre) — y genera un informe de claridad de la suite que muestra las diferencias de puntuación.

## Componentes de NLP

El módulo de claridad usa NLP codificado a mano, sin dependencias externas:

| Componente | Propósito |
|---|---|
| `IdentifierTokenizer` | Divide camelCase y snake_case en tokens |
| `VerbDictionary` | Categoriza más de 200 verbos (dominio, estándar, genérico, booleano) |
| `RoleSuffixDictionary` | Clasifica sufijos de clase (patrón de diseño, funcional, genérico) |
| `GenericTokenDetector` | Clasifica la especificidad de los tokens (sin significado → específico del dominio) |
| `AbbreviationDictionary` | Puntúa más de 140 abreviaturas en tres niveles (universal, bien conocida, ambigua) |
| `MorphologyAnalyzer` | Detecta categorías gramaticales mediante sufijos (-tion, -ize, -able) |
| `CohesionScorer` | Comprueba la alineación de los verbos de los métodos con las expectativas del rol de la clase |
| `ElementNoteComposer` | Convierte el mismo conocimiento de los diccionarios en una nota didáctica por elemento, en cada puntuación |
| `DomainVocabulary` | Las palabras propias del proyecto, leídas del glosario comiteado; extiende todos los diccionarios anteriores sin anularlos |

## Véase también

- [Guía de configuración](guia-de-configuracion.md) — niveles de tracing, configuración de la salida
- [Guía de anotaciones](guia-de-anotaciones.md) — `@Narrated`, `@OnError`, `@NotTraced`
- [Guía de instalación](guia-de-instalacion.md) — dependencias y vías de integración
