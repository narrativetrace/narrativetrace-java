# NarrativeTrace Java Clarity Guide

If the trace is the code, then trace quality is code quality. The clarity module analyzes your method, class, and parameter names and scores how well they communicate intent.

## Quick start

```java
var analyzer = new ClarityAnalyzer();
var result = analyzer.analyze(context.captureTrace());

var renderer = new ClarityReportRenderer();
System.out.println(renderer.render("Order Placement", result));
```

With JUnit 5, a registered `NarrativeTraceExtension` generates clarity reports automatically
from the calls your tests trace. There is no separate Clarity enable flag.
The Gradle plugin supplies dependencies and test configuration, but the test still needs to
register the extension and capture a trace. `narrativetrace.output=false` disables reports along
with the other trace artifacts.

JUnit 4 uses a `NarrativeTraceClassRule` linked to a per-test `NarrativeTraceRule`
instead of the extension. Both integrations produce the same Clarity artifacts and
work with `clarityCheck`; see [JUnit 4 integration](#junit-4-integration) below.

For an agent-guided first report or an optional build gate, use the
[add-narrativetrace-clarity](agent-skills.md) skill. It checks the real consumer `clarityScan`
task, distinguishes static scan artifacts from JUnit trace artifacts, and treats missing output as
a setup problem for `narrativetrace-doctor`.

## What gets scored

Clarity produces a single overall score (0.0–1.0) from five weighted components:

| Component | Weight | What it measures |
|---|---|---|
| Method names | 30% | Verb quality, token specificity, abbreviations, token count |
| Parameter names | 25% | Domain specificity vs generic/meaningless tokens |
| Class names | 20% | Role suffix quality, prefix specificity |
| Structural | 15% | Parameter count and call depth penalties |
| Cohesion | 10% | Whether methods align with the class's role suffix |

## Scoring in practice

### Method names

The first token is treated as a verb. Domain verbs score highest, generic verbs score lowest:

| Verb category | Examples | Score |
|---|---|---|
| Domain | `calculate`, `validate`, `reserve`, `dispatch` | 0.60 |
| Standard | `create`, `find`, `delete`, `update` | 0.45 |
| Boolean prefix | `is`, `has`, `can`, `contains` | 1.00 |
| Generic | `get`, `set`, `process`, `handle`, `execute` | 0.10 |

Multi-token methods like `reserveInventory` score higher than single-token methods like `reserve` because the additional tokens add specificity.

### Class names

A role suffix is expected. Design pattern and functional suffixes score well when paired with a domain prefix:

| Pattern | Score | Why |
|---|---|---|
| `OrderService` | 1.0 | Domain prefix + functional suffix |
| `Service` | 0.0 | No prefix — meaningless |
| `DataProcessor` | Low | Vague prefix + generic suffix |
| `BookingManager` | Medium | Domain prefix, but `Manager` is generic |

### Parameter names

Domain-specific names score high, generic names score low:

| Tier | Examples | Score |
|---|---|---|
| Domain-specific | `customerId`, `checkInDate`, `roomCategory` | 0.80+ |
| Typed generic | `id`, `name`, `count`, `status` | 0.50 |
| Vague | `data`, `info`, `result`, `object` | 0.10 |
| Meaningless | `x`, `foo`, `val`, `temp` | 0.00 |

### Structural penalties

Methods with more than 4 parameters or call depth beyond 5 are penalized. Each excess parameter costs 0.1; each excess depth level costs 0.05.

### Cohesion

Methods are checked against expected verbs for the class's role suffix. A `Repository` class is expected to have methods like `find`, `save`, `delete`, `count`. A method like `renderReport` on a `GuestRepository` is flagged as misaligned.

## Issues and severity

Clarity problems are reported as issues ranked by impact:

| Severity | Threshold | Examples |
|---|---|---|
| HIGH | score ≤ 0.20 | `DataProcessor.execute(data)` |
| MEDIUM | score ≤ 0.50 | `BookingManager.handleBooking(name, type)` |
| LOW | score > 0.50 | Minor abbreviation use |

Duplicate issues (same category and element) are deduplicated with an occurrence count. Issues are ranked by impact score (severity weight × occurrences).

## Per-element notes

Issues are threshold-gated — they list only the names that fall below a severity cutoff. Notes are the opposite: **one plain-language note per element at every score**, so a good name learns *why* it scores well and a weak one learns *what to change*. A bare 0.86 is no longer a verdict without an appeal.

Every method, class, parameter, and record component earns a note built from the same dictionaries the scorers use:

| Element | Score | Note |
|---|---|---|
| `OrderService.reserveInventory` | 0.95 | Domain verb 'reserve' + domain noun 'inventory' |
| `Service.processData` | 0.30 | Generic verb 'process' + vague noun 'data' |
| `OrderManager` | 0.62 | Generic suffix 'Manager' — prefer a precise role |
| `amount` | 0.50 | Broad noun 'amount' — qualify it (e.g., orderAmount) |

Notes carry concrete guidance wherever the dictionaries can supply it. A generic or unrecognized verb paired with a known domain noun earns a `verbNoun` rename hint — `Generic verb 'process' + broad noun 'order' — consider: backorderOrder, cancelOrder, fulfillOrder` — and any penalized abbreviation is spelled out inline: `; spell out: chk → check`. Record accessors are scored on the noun rubric and phrased for components (`Domain-specific component 'customerId'`).

The collocation dictionary is a positive signal only: a verb it lists for a noun confirms the name reads well, but a verb it does not list is never treated as evidence against the name — the rename hint fires only for a generic or genuinely unclear verb, never merely because the actual verb is missing from the sample.

Notes never enter `issues[]` — praise is not an actionable. They are surfaced in the report's **Elements** table and in the `elements` array of `clarity-results.json` (schema 1.2).

## Your own vocabulary, from the glossary you already have

The built-in dictionaries know general software English. They do not know that
`fold` is a verb in your domain, that `tranche` is a precise noun, or that `fx`
is your team's accepted shorthand — and a name they do not know scores as
unknown, not as domain-specific.

You teach them with the vocabulary file your repository already carries: the
committed `glossary.json` (ADR-012). There is no second dictionary file to keep
in sync.

| Glossary entry | Kind | What clarity learns |
|---|---|---|
| `settle trade` | `verb-phrase` | `settle` is a domain verb; `trade` is a domain noun |
| `credit tranche` | `noun-phrase` | `credit` and `tranche` are domain nouns |
| `fx` | `word` | `fx` is a domain noun |

Multi-word terms teach one token at a time, because identifiers are scored one
token at a time. Every bounded context contributes: an identifier carries no
package, so context scoping cannot apply at scoring time.

### Accepted shorthand is declared, not inferred

Terms teach vocabulary. They do **not** decide that a short spelling is
acceptable on its own — committing the phrase `calc total` says nothing about
whether a method may be called `calcTotal`. That decision lives in its own
root-level section of `glossary.json` (schema 2):

```json
{
  "schemaVersion": 2,
  "contexts": { },
  "abbreviations": { "fx": "foreign exchange", "calc": "calculate" },
  "terms": [ ]
}
```

A listed abbreviation is accepted — never scored down, never asked to be spelled
out — and the note channel teaches it from your own expansion:
`; project shorthand: fx → foreign exchange`. An abbreviation you have *not*
listed keeps its built-in treatment, even if it happens to appear inside a
committed phrase.

The section is human-owned: harvesting never writes it, and a merge carries it
through untouched. A glossary that declares no abbreviations stays at
`schemaVersion` 1 and its file is byte-identical to what it always was.

### What the glossary cannot do

The built-in dictionaries keep their authority. A project can teach the scorers
a word they do not know; it cannot overrule a word they do.

- **Generic verbs stay generic.** Committing `process` or `handle` does not
  promote them — `Generic verb 'process'` still appears in the notes, and the
  method-name score still reflects it. The same holds for boolean prefixes
  (`is`, `has`).
- **Meaningless placeholders stay meaningless.** `temp`, `foo`, `data`-as-a-
  single-letter and friends are not rescued by being written down.
- **Deprecated synonyms are never vocabulary.** An alias exists to be flagged;
  promoting it would silence the `non-canonical-term` issue it is declared for.
- **`stale` terms are not vocabulary.** Marking a term stale says the word left
  the domain.

Only the *committed* file counts. Nothing a run harvests feeds back into that
same run's scores — a self-expanding vocabulary would make scores
non-deterministic and self-certifying. The commit is the human approval.

### Where it applies

| Surface | How the glossary is found |
|---|---|
| JUnit 5 extension, JUnit 4 rule | `narrativetrace.glossaryDir` (default: the working directory; the Gradle plugin sets it to the repository root) |
| `clarityScan` | `--glossary-dir`, which the Gradle plugin sets to the repository root |

Reading is unconditional — unlike harvesting, which is opt-in because it
rewrites files outside the build directory. A repository with no
`glossary.json` scores exactly as it did before this feature existed, and a
glossary that cannot be read degrades to the built-in dictionaries with a
warning rather than failing the suite.

## Report output

### Single scenario

```java
renderer.render("Guest books a room", result);
```

Produces a Markdown report with a scores table, an Elements table, and an issues table (if any):

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

The Elements table renders for every scenario — including high scorers — while the Issues table stays scoped to names below the severity cutoff.

### Suite report

```java
renderer.renderSuiteReport(Map.of(
    "Guest books a room", result1,
    "Legacy data processing", result2
));
```

Produces a ranked summary of all scenarios. Scenarios scoring below 0.7 get a detailed breakdown with individual issues; every scenario — regardless of score — gets an Elements table, so no score is left unexplained.

## JUnit 5 integration

By default (unless `narrativetrace.output=false` is set in `junit-platform.properties`), the JUnit extension automatically:

1. Runs `ClarityAnalyzer.analyze()` on each test's trace
2. Writes `clarity-report.md` to the output directory after all tests complete
3. Prints a console summary with score distribution:

```
NarrativeTrace — Suite complete
  2 scenarios recorded
  Clarity: 100% high | 0% moderate | 0% low
  Reports: build/narrativetrace
```

Once the extension is registered and tests capture traces, run the tests with output enabled
(the default). Reports require nonempty captured traces.

Registration does not have to be per class: `NarrativeTraceExtension` is published as a JUnit
Platform `Extension` via `META-INF/services`, so setting
`junit.jupiter.extensions.autodetection.enabled=true` in `junit-platform.properties` registers it
suite-wide without any `@ExtendWith`. This turns on autodetection for *every*
ServiceLoader-published extension on the test classpath, not only NarrativeTrace's, so
`@ExtendWith` remains the explicit, per-class choice when that is what a project wants.

## JUnit 4 integration

Keep JUnit 4 and set `narrativeTrace { testFramework.set("junit4") }` in the
consumer's Gradle configuration. The plugin adds `narrativetrace-junit4`; a plain
JUnit 4 runner needs neither Jupiter nor `useJUnitPlatform()`. An existing JUnit
Platform/Vintage setup can remain in place.

Link the rules in the existing public test class:

```java
@ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();
@Rule public NarrativeTraceRule narrativeTrace = classRule.testRule();
```

Use `org.junit.ClassRule`, `org.junit.Rule`, and `org.junit.Test`; test methods must
be public. Trace calls with `NarrativeTraceProxy.trace(impl, Service.class,
narrativeTrace.context())`. The class rule analyzes nonempty captured traces after
each class and writes `clarity-report.md` and `clarity-results.json`, combining
completed classes in that test JVM. A standalone `new NarrativeTraceRule()` produces
per-test narratives but does not contribute to the aggregate Clarity report.

`NarrativeTestCase` wires both rules as an optional base class when the test has no
existing superclass to preserve. JUnit 4 reads `narrativetrace.output` (default:
`true`) and `narrativetrace.outputDir` as test JVM system properties, not from
`junit-platform.properties`. Run `./gradlew clean clarityCheck` and verify fresh,
nonempty test reports; missing JSON still causes a successful skip.

## Build enforcement with `clarityCheck`

The plugin's `clarityScan` task is a separate static path: it analyzes compiled production classes
without running tests and writes `clarity-scan-report.md` and `clarity-scan-results.json`. Those
files must remain distinct from the JUnit artifacts below because `clarityCheck` reads
`clarity-results.json`, which is produced from executed traces. A scan can give a first naming
report; it does not create test-run input for the gate.

The Gradle plugin provides a `clarityCheck` task that fails the build when naming quality drops below a threshold. This makes clarity scoring enforceable, not advisory.

### Setup

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.5"
}

narrativeTrace {
    clarity {
        minScore.set(0.80)     // fail if any scenario scores below 0.80
        maxHighIssues.set(0)   // fail if any scenario has HIGH-severity issues
    }
}
```

### How it works

1. `./gradlew test` — the JUnit 5 extension or linked JUnit 4 rules produce `build/narrativetrace/clarity-results.json`
2. `clarityCheck` reads the JSON and compares each scenario against thresholds
3. `./gradlew check` runs both `test` and `clarityCheck` automatically

`minScore` compares each scenario's **overallScore**, not its individual component
or element scores. An overall score of 0.82 passes a minimum of 0.80 even if
`methodNameScore` is 0.55, provided the issue-count limits also pass.
`maxHighIssues` limits HIGH-severity issues per scenario; `maxSuiteIssues` limits
suite-level issues separately.

Default thresholds impose no limits. Missing `clarity-results.json` causes a
successful skip, so verify fresh, nonempty test reports before relying on the gate.
Choose a test threshold from observed test reports, not static scan scores alone.

### Failure output

When a scenario falls below the threshold:

```
FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':clarityCheck'.
> Clarity check failed:
    'Legacy data processing': score 0.45 < threshold 0.80
    'Legacy data processing': 3 HIGH issues (max 0)
```

### Warn-only mode

For gradual adoption, use `warnOnly` to log violations without failing the build:

```kotlin
narrativeTrace {
    clarity {
        minScore.set(0.70)
        warnOnly.set(true)
    }
}
```

### Suite-level issues

Issues that belong to the whole run rather than to any single scenario — today the `non-canonical-term` vocabulary violations reported by the glossary harvest — land in the report's **Suite Issues** section and in the JSON's top-level `suiteIssues` array. They never affect scenario scores. The vocabulary check fires only when a committed `glossary.json` exists before the run; a project without a glossary never receives suite issues.

By default suite issues are advisory: `clarityCheck` logs them as warnings without failing the build. Opt into a hard gate with `maxSuiteIssues`:

```kotlin
narrativeTrace {
    clarity {
        maxSuiteIssues.set(0)   // fail on any suite-level issue
    }
}
```

### JSON contract

The `clarity-results.json` file is the contract between test execution and the `clarityCheck` task:

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

The top-level `suiteIssues` array (schema 1.1) holds suite-level issues; it is always present, empty when the run produced none. Each scenario's `elements` array (schema 1.2) carries one per-element note at every score. Both additions are purely additive: schema 1.0/1.1 files without these fields are still accepted by `clarityCheck` and by consumers of the JSON.

## Demo

Run the hotel booking clarity demo to see scoring across four quality levels:

```bash
./gradlew :narrativetrace-examples:clarity:run
```

The demo traces four scenarios with progressively worse naming — from `ReservationService.confirmReservation(guestId, roomCategory)` (excellent) down to `DataProcessor.execute(data, val)` (poor) — and generates a suite clarity report showing the score differences.

## NLP components

The clarity module uses hand-coded NLP with no external dependencies:

| Component | Purpose |
|---|---|
| `IdentifierTokenizer` | Splits camelCase and snake_case into tokens |
| `VerbDictionary` | Categorizes 200+ verbs (domain, standard, generic, boolean) |
| `RoleSuffixDictionary` | Classifies class suffixes (design pattern, functional, generic) |
| `GenericTokenDetector` | Ranks token specificity (meaningless → domain-specific) |
| `AbbreviationDictionary` | Scores 140+ abbreviations in three tiers (universal, well-known, ambiguous) |
| `MorphologyAnalyzer` | Detects parts of speech via suffixes (-tion, -ize, -able) |
| `CohesionScorer` | Checks method-verb alignment with class role expectations |
| `ElementNoteComposer` | Turns the same dictionary knowledge into one teaching note per element, at every score |
| `DomainVocabulary` | The project's own words, read from the committed glossary; extends every dictionary above without overriding it |

## See also

- [Configuration Guide](configuration-guide.md) — tracing levels, output configuration
- [Annotations Guide](annotations-guide.md) — `@Narrated`, `@OnError`, `@NotTraced`
- [Installation Guide](installation-guide.md) — dependencies and integration paths
