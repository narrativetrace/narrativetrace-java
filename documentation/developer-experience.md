# Developer experience

What it takes to go from an empty project to reading your first narrative,
and where each piece of the experience lives. This page describes what ships
today; planned improvements are tracked in the private backlog.

## One-line setup

Apply the Gradle plugin and write a test. The plugin adds the NarrativeTrace
artifacts, configures the JUnit Platform, and supplies the Jupiter test
engine for the default JUnit 5 mode — the documented minimal setup runs a real test green with no further
dependencies. See the installation guide for the exact snippet per build
style. To produce narratives, register `NarrativeTraceExtension` on the test
and execute traced calls, for example through `NarrativeTraceProxy.trace`.
The plugin supplies dependencies and test configuration; it does not register
the extension.

For an existing JUnit 4 suite, keep that framework and set
`narrativeTrace { testFramework.set("junit4") }`. Link a public static
`@ClassRule NarrativeTraceClassRule` to a public `@Rule` created by
`classRule.testRule()`, then trace through the per-test rule's context.
The plugin supplies the JUnit 4 integration but does not add those rules to tests.

## Per-test narratives

With the extension registered and output enabled (the default), the
`narrativetrace-junit5` module writes narratives for tests that capture traces.
Artifacts land beside the build output in the canonical formats
(`.nt` narrative text, structural and canonical JSON, chapter documents) —
the same formats every NarrativeTrace runtime emits, validated by the schemas
shipped in this repository.

JUnit 4's `NarrativeTraceRule` provides the equivalent per-test context and output.
It reads test JVM system properties rather than `junit-platform.properties`.

## Naming feedback and agent skills

In a consumer project with the Gradle plugin applied, `./gradlew clean clarityScan`
scores compiled production classes without running tests. Read the fresh
`clarity-scan-report.md` and `clarity-scan-results.json` in the configured
output directory (default: `build/narrativetrace`) for scores and per-element notes.

For feedback on executed calls, the registered JUnit extension analyzes nonempty
test traces and writes `clarity-report.md` and `clarity-results.json` at suite
completion. In JUnit 4, the linked class rule writes the same reports after each
class, combining completed classes in that test JVM. A standalone per-test rule
does not produce aggregate Clarity reports. There is no separate Clarity enable flag;
`narrativetrace.output=false`
disables these reports along with the other trace artifacts.

Optional CI enforcement uses `./gradlew clean clarityCheck`. Its default input
is the test-generated JSON. `minScore` compares each scenario's `overallScore`;
issue-count limits are separate. Missing JSON causes a successful skip, so check
that fresh, nonempty reports exist before relying on a green build. Static scan
results alone do not establish that this gate passed.

The [agent skills](agent-skills.md) guide covers three entry points:

- `add-narrative-tracing` installs tracing and produces the first trace.
- `narrativetrace-doctor` diagnoses an existing setup without changing it.
- `add-narrativetrace-clarity` produces and explains a first naming report,
  verifies the artifacts, and adds a quality gate when requested.

See the [Clarity guide](clarity-guide.md) for scoring and enforcement options.

## Trying it without a project

`./demo.sh` launches the example applications (e-commerce and friends) and
narrates real scenarios to the console — the fastest way to see what the
output reads like before wiring anything.

## Consuming a local build

Evaluating unreleased changes, or building an integration against this repo,
uses Gradle's composite builds — and needs **both** inclusion roles: a
`pluginManagement { includeBuild(...) }` so the plugin resolves, and a
top-level `includeBuild(...)` so the library coordinates the plugin adds
substitute to your local checkout. The plugin documentation carries the
complete `settings.gradle.kts` recipe.

## Safety while you develop

Redaction is part of the development experience, not an afterthought: a
`@NotTraced` component never appears in any rendered output — not through a
wrapper's `toString()`, not through a collection, not through an enclosing
class's own hand-written `toString()`, and not through a narration template
that names its path. If a narrative needs a value, the deliberate,
reviewable act is removing the annotation, never working around it.
