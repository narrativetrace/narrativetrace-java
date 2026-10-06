# NarrativeTrace Gradle Plugin Guide

The `ai.narrativetrace` Gradle plugin is the recommended way to use NarrativeTrace in Gradle projects. It handles dependency management, compiler flags, test configuration, and quality gates — all from a single DSL block.

## Table of Contents

- [Quick Start](#quick-start)
- [What the Plugin Does Automatically](#what-the-plugin-does-automatically)
- [Properties](#properties) — [enabled](#enabled) | [mode](#mode) | [testFramework](#testframework) | [scope](#scope) | [format](#format) | [tracingLevel](#tracinglevel) | [outputDir](#outputdir) | [approval](#approval) | [approvedDir](#approveddir)
- [Modules Block](#modules-block)
- [Agent Block](#agent-block)
- [Clarity Block](#clarity-block)
- [Tasks](#tasks) — [clarityCheck](#claritycheck) | [clarityScan](#clarityscan) | [glossaryScan](#glossaryscan) | [approveNarratives](#approvenarratives) | [narrativetraceDoctor](#narrativetracedoctor) | [narrativetraceInit](#narrativetraceinit) | [narrativetraceUninstall](#narrativetraceuninstall) | [narrativetraceRefreshSkills](#narrativetracerefreshskills)
- [Requirements](#requirements)
- [Version Resolution](#version-resolution)
- [Common Recipes](#common-recipes)
- [Groovy DSL](#groovy-dsl)
- [Validation](#validation)
- [Full DSL Reference](#full-dsl-reference)

## Quick Start

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.5"
}
```

That's it. Run `./gradlew test` and trace output appears in `build/narrativetrace/`.

You do not need to add a JUnit dependency. With the default `testFramework = "junit5"` the plugin switches the test task onto the JUnit Platform *and* puts the Jupiter engine (`org.junit.jupiter:junit-jupiter-engine`, pinned to the version NarrativeTrace is tested against) on `testRuntimeOnly`, because the platform refuses to start without one. Declaring your own JUnit version still works — Gradle's conflict resolution picks the higher of the two.

## What the Plugin Does Automatically

| Action | Detail |
|---|---|
| Adds `-parameters` compiler flag | On all `JavaCompile` tasks, skipped if already present |
| Adds dependencies | Based on `mode`, `modules`, and `testFramework`; version auto-detected from plugin JAR |
| Adds the JUnit Platform engine | `testFramework = "junit5"` only: `testRuntimeOnly org.junit.jupiter:junit-jupiter-engine:5.11.4`, so the first `gradle test` runs instead of failing with *"Cannot create Launcher without at least one TestEngine"*. JUnit 4 gets none — it is not switched onto the platform |
| Sets test JVM properties | `narrativetrace.output=true`, `narrativetrace.outputDir`, and optionally `narrativetrace.format`, `narrativetrace.level`, the glossary pair (`glossary=true`), and the approval pair (`approval=true`) |
| Registers `clarityCheck` task | Reads `clarity-results.json`, enforces thresholds, wired into `check` lifecycle |
| Registers `clarityScan` task | Standalone clarity analysis from compiled classes (no tests required) |
| Registers `glossaryScan` task | Standalone glossary harvest from compiled classes, including annotation templates |
| Registers `approveNarratives` task | Promotes reviewed `*.received.nt` narratives to `*.approved.nt` baselines |
| Registers the `narrativetrace` task group | `narrativetraceDoctor`, `narrativetraceInit`, `narrativetraceUninstall` — diagnosis and the agent-skills installer, none of which runs on its own |
| Registers `narrativetraceRefreshSkills` task | Runs before `classes`; rewrites installed agent skills older than the resolved carrier, and does nothing at all in a project that never ran `narrativetraceInit` |
| Configures agent JVM arg | When `mode = "agent"`: resolves agent JAR, adds `-javaagent` to Test tasks |

## Properties

### `enabled`

Controls whether the plugin does anything. When `false`, no tasks are registered, no dependencies added, no compiler flags set.

```kotlin
narrativeTrace {
    enabled.set(false)  // disable in this subproject
}
```

Default: `true`

### `mode`

Selects the interception strategy. This determines which core library dependency the plugin adds.

| Mode | Dependencies added (besides core + clarity + diagrams + test framework) |
|---|---|
| `proxy` | `narrativetrace-proxy` — JDK dynamic proxy, interface-based |
| `agent` | `narrativetrace-agent` — bytecode instrumentation, no interface required |
| `spring` | `narrativetrace-spring` + `narrativetrace-proxy` — Spring BeanPostProcessor auto-wrapping |

Default: `"proxy"`

**Proxy mode** is the simplest: wrap services with `NarrativeTraceProxy.trace()` in test code.

**Agent mode** additionally creates a `narrativeTraceAgent` configuration, resolves the agent JAR, and adds `-javaagent` to all `Test` tasks. Use the `agent { }` block to specify which packages to instrument.

**Spring mode** adds the Spring BeanPostProcessor that auto-wraps eligible beans. Use `@EnableNarrativeTrace` in your configuration class.

### `testFramework`

| Value | Dependency added |
|---|---|
| `"junit5"` | `narrativetrace-junit5` |
| `"junit4"` | `narrativetrace-junit4` |

Default: `"junit5"`

### `scope`

Controls which Gradle configuration receives the library dependencies.

| Scope | Library dependencies | Test framework dependency |
|---|---|---|
| `"test"` | `testImplementation` | `testImplementation` |
| `"production"` | `implementation` | `testImplementation` (always) |

Default: `"test"`

Use `"production"` when deploying NarrativeTrace in a running application (e.g., with the servlet filter for request-level tracing). The test framework dependency always stays on `testImplementation` regardless of scope.

### `format`

Output format for trace files. Only forwarded to test tasks when explicitly set — if omitted, the JUnit extension uses its own default (markdown).

| Value | Description |
|---|---|
| `"markdown"` | Human-readable Markdown with Mermaid diagrams |
| `"text"` | Plain indented text |
| `"mermaid"` | Mermaid sequence diagram only |
| `"plantuml"` | PlantUML sequence diagram only |

No default convention — omit to let the JUnit extension decide.

### `tracingLevel`

Controls how much detail is captured in traces. Only forwarded to test tasks when explicitly set.

| Value | Behavior |
|---|---|
| `"OFF"` | No tracing captured |
| `"ERRORS"` | Only exception paths captured |
| `"SUMMARY"` | Root entry, deepest leaf, and full exception chains |
| `"NARRATIVE"` | Full call flow, parameter values suppressed |
| `"DETAIL"` | Full call flow with parameter values and return values |

No default convention — omit to let the runtime decide.

### `outputDir`

Directory for trace files, clarity reports, and diagrams.

Default: `layout.buildDirectory.dir("narrativetrace")` (i.e., `build/narrativetrace/`)

### `approval`

Approval mode for structural narratives. When `true`, the plugin forwards `narrativetrace.approval=true` and `narrativetrace.approvedDir` to test tasks: a **passing** test whose traced structure differs from its committed `*.approved.nt` baseline fails with a readable diff, and the current structure is written beside the baseline as `*.received.nt` for review. Accept an intended change with the [`approveNarratives`](#approvenarratives) task.

Default: `false`

```kotlin
narrativeTrace {
    approval.set(true)
}
```

### `approvedDir`

Directory of committed narrative baselines, laid out as `<dir>/<TestClassSimpleName>/<artifact_name>.approved.nt` — the same class-directory and artifact-identity rules as every other per-test artifact, so a method that runs more than once (parameterized, repeated) has one baseline per invocation.

Default: `layout.projectDirectory.dir("src/test/narratives")`

## Modules Block

Fine-grained opt-in for additional NarrativeTrace modules. All default to `false`.

```kotlin
narrativeTrace {
    modules {
        slf4j.set(true)        // narrativetrace-slf4j
        micrometer.set(true)   // narrativetrace-micrometer
        servlet.set(true)      // narrativetrace-servlet
        springWeb.set(true)    // narrativetrace-spring-web + narrativetrace-servlet
    }
}
```

| Flag | Artifact | Notes |
|---|---|---|
| `slf4j` | `narrativetrace-slf4j` | Route trace events through SLF4J/logback |
| `micrometer` | `narrativetrace-micrometer` | Cross-thread trace propagation via Micrometer context-propagation |
| `servlet` | `narrativetrace-servlet` | Servlet filter for per-request trace lifecycle |
| `springWeb` | `narrativetrace-spring-web` + `narrativetrace-servlet` | Spring auto-configuration for the servlet filter; automatically adds the servlet module |

Setting `springWeb` when `mode` is not `"spring"` produces a warning (but does not fail).

## Agent Block

Only relevant when `mode = "agent"`. Configures which packages the bytecode agent instruments.

```kotlin
narrativeTrace {
    mode.set("agent")
    agent {
        packages.set(listOf("com.example.app", "com.example.shared"))
    }
}
```

When packages is empty (the default), the agent instruments all classes. Packages are joined with `;` in the `-javaagent` argument.

The agent JAR is resolved lazily at test execution time from a dedicated `narrativeTraceAgent` Gradle configuration.

## Clarity Block

Configures the `clarityCheck` quality gate.

```kotlin
narrativeTrace {
    clarity {
        minScore.set(0.80)       // fail if any scenario scores below 0.80
        maxHighIssues.set(0)     // fail if any scenario has HIGH issues
        maxSuiteIssues.set(0)    // fail on any suite-level issue (e.g. vocabulary violation)
        warnOnly.set(true)       // log warnings instead of failing
    }
}
```

### `minScore`

Minimum overall clarity score (0.0–1.0). Any scenario below this threshold fails the build.

Default: `0.0` (no gate)

### `maxHighIssues`

Maximum number of HIGH-severity issues per scenario. Exceeding this fails the build.

Default: `Integer.MAX_VALUE` (no gate)

### `maxSuiteIssues`

Maximum number of suite-level issues — issues that belong to the whole run rather than to a single scenario, such as `non-canonical-term` vocabulary violations from the glossary harvest. Exceeding this fails the build; below the threshold they are logged as warnings. Vocabulary violations are only reported when a committed `glossary.json` exists before the run.

Default: `Integer.MAX_VALUE` (advisory only)

### `warnOnly`

When `true`, threshold violations produce warnings instead of build failures.

Default: `false`

## Tasks

### `clarityCheck`

Reads `build/narrativetrace/clarity-results.json` (produced by the JUnit extension during `test`) and enforces configured thresholds.

- **Depends on**: `test`
- **Wired into**: `check` (runs automatically with `./gradlew check`)
- **Skips silently** when `clarity-results.json` does not exist (e.g., no tests ran)

### `clarityScan`

Analyzes naming clarity of compiled classes without running tests. Uses reflection to scan class files and produce a clarity report.

- **Depends on**: `classes`
- **Classpath**: `testRuntimeClasspath` (needs the clarity module)
- **Arguments**: `--classes-dir` and `--output-dir` derived from plugin configuration
- **Output**: `clarity-scan-report.md` and `clarity-scan-results.json` in `outputDir` — deliberately distinct from the test-run artifacts (`clarity-report.md` / `clarity-results.json`), so a scan never overwrites what the `clarityCheck` gate reads
- **Scope**: public and package-private types; private nested, anonymous, local, and lambda classes are skipped as implementation details

Run standalone:

```bash
./gradlew clarityScan
```

### `glossaryScan`

Harvests the domain glossary from compiled classes without running tests.
This is the **only** mode that harvests `@Narrated` / `@OnError` templates:
a captured trace carries narration with parameter values already
interpolated, so harvesting it there would write runtime data into a
committed file.

- **Depends on**: `classes`
- **Classpath**: `testRuntimeClasspath` (needs the glossary module)
- **Arguments**: `--classes-dir` (`build/classes/java/main`), `--glossary-dir`
  (the repository root), `--output-dir` from plugin configuration

```bash
./gradlew glossaryScan
```

To harvest during the test run instead — from real traces, without
templates — enable it on the extension:

```kotlin
narrativeTrace {
    glossary.set(true)
}
```

That sets `narrativetrace.glossary=true` and points
`narrativetrace.glossaryDir` at the repository root. It is off by default
because it writes `glossary.json` / `glossary.md` outside the build
directory.

Translated trace views are not a build task: attach a `TranslationSubscriber` from the glossary module to the event pipeline (locale + committed `glossary.json`, located via `narrativetrace.glossary.path` or the classpath) and every run — test or production — emits its translated stream live, to the `narrativetrace.i18n.<locale>` logger or as per-trace Markdown files. The earlier `translateTraces` task and `translationLocales` property were retired in favor of this pipeline shape.

### `approveNarratives`

Accepts intended structural changes in [approval mode](#approval): promotes every reviewed `*.received.nt` file under `approvedDir` to its `*.approved.nt` baseline.

```bash
./gradlew approveNarratives
```

- **Group**: `verification`
- Always safe to run — prints `Approved: <path>` per promoted baseline, or `No received narratives to approve.` when there is nothing to promote
- Never runs tests: review the received files first, approve, then re-run the suite green

### `narrativetraceDoctor`

Runs the doctor's twelve read-only checks against this project, in process — the same checks the
standalone launcher's `doctor` verb runs. Writes `build/narrativetrace/doctor-report.json` and
prints the human report.

```bash
./gradlew narrativetraceDoctor
```

- **Group**: `narrativetrace`
- **Read-only**: reads build files, sources, rendered output and installed agent skills; changes nothing
- **Never fails the build on its own findings** — a finding is a normal outcome, not a crash
- `--json` prints the machine-readable report instead of the human one

### `narrativetraceInit`

Installs the [agent skills](agent-skills.md) into this project and writes the marked `AGENTS.md`
section. Reads them from `ai.narrativetrace:narrativetrace-skills`, resolved at task time from the
repositories the project already declares — the same version every other NarrativeTrace artifact
resolves at.

```bash
./gradlew narrativetraceInit --diff    # show the plan, write nothing
./gradlew narrativetraceInit           # apply it
```

- **Group**: `narrativetrace`
- **Runs only when you type it.** No lifecycle task depends on it

| Option | Effect |
|---|---|
| `--diff` | Preview: prints the plan and a unified diff of every file it would write, and writes nothing. Never fails |
| `--write-existing` | Permission to touch an `AGENTS.md` or `CLAUDE.md` that is already there without our markers |
| `--force` | Permission to overwrite a skill directory that somebody else owns (a page without our stamp) |
| `--only <half>` | `skills` or `agents-md` — install one half only |
| `--vendor <v>` | `claude` or `none` — force the vendor copy on or off. Detected by default from a `.claude/` directory or a `CLAUDE.md` |
| `--json` | The `{carrier, actions, exitCode}` envelope instead of human text |

The preview flag is `--diff`, not `--dry-run`: Gradle's own built-in `--dry-run` skips every task in
the graph, so a task option of that name could never run. The launcher's verb, where nothing shadows
it, keeps `narrativetrace init --dry-run`. Both set the same option.

A refusal — two marker pairs in one `AGENTS.md`, a file sitting where a skill directory belongs —
is printed with the flag that would allow it, and fails the task. Every other planned action is
still applied.

There are no DSL properties for any of this: nothing about the installer is configured in
`narrativeTrace { }`, because every choice it makes belongs to the run you are typing, not to the
build's committed configuration.

### `narrativetraceUninstall`

Removes exactly what `narrativetraceInit` wrote, and nothing beside it: a skill page only when it
carries the installer's own stamp, a file only when the installer created it and nothing of yours is
left in it, the `AGENTS.md` section only between its own markers.

```bash
./gradlew narrativetraceUninstall --diff
./gradlew narrativetraceUninstall
```

- **Group**: `narrativetrace`
- **Options**: `--diff`, `--only <half>`, `--json`, as above
- The one line it leaves behind is the `@AGENTS.md` import in a `CLAUDE.md` it did not create

### `narrativetraceRefreshSkills`

Bookkeeping, not something you type: it runs before `classes` and rewrites installed skill pages
that carry our stamp with an older release than the carrier the project now resolves, plus the
`AGENTS.md` section the same way. It prints one line naming what it rewrote.

- **No group** — it is the build's own housekeeping
- **Only ever rewrites.** It never creates a skill, a section or a file, so a project that never ran
  `narrativetraceInit` is never touched and never even resolves the carrier
- Offline, or with no repository providing the carrier, it warns once and carries on

## Requirements

The plugin checks its environment when it is applied, and fails with one line
naming the requirement rather than letting the build reach a confusing error
later:

- **Gradle 8.0 or newer.** Developed and tested against 8.14.2.
- **Java 17 or newer** — the configured toolchain when the build sets one,
  otherwise the JVM running Gradle. NarrativeTrace is written in Java 17
  (records, sealed interfaces, pattern-matching switches), so this is a
  language-level requirement, not a preference.

Neither check runs when `enabled.set(false)`, and a version string the plugin
cannot parse is treated as acceptable — a guess must never stop a build.

## Version Resolution

The plugin auto-detects its version from the plugin JAR at runtime, and that version is used for all managed NarrativeTrace dependencies. Plugin and libraries are released in lockstep from one version in `gradle.properties`, so the plugin always installs libraries of exactly its own version.

If the version cannot be detected (e.g., running from source without the properties file), the plugin falls back to `0.0.0-unknown`.

To pin a different version — for example to dogfood a local `-SNAPSHOT` whose libraries are ahead of the plugin's embedded version — set `libraryVersion`:

```kotlin
narrativeTrace {
    libraryVersion.set("<your-snapshot-version>")
}
```

When set, every managed NarrativeTrace dependency resolves at that version; when unset (the default), the embedded version is used and behavior is unchanged.

## Common Recipes

### Test-only tracing (default)

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.5"
}
```

Adds all dependencies on `testImplementation`. Production code has zero NarrativeTrace classes on the classpath.

### Production tracing with servlet filter

```kotlin
narrativeTrace {
    scope.set("production")
    mode.set("spring")
    modules {
        springWeb.set(true)
        slf4j.set(true)
    }
}
```

Adds dependencies on `implementation` so the servlet filter and Spring auto-configuration are available at runtime.

### Agent-based instrumentation

```kotlin
narrativeTrace {
    mode.set("agent")
    agent {
        packages.set(listOf("com.example.app"))
    }
}
```

The plugin creates a `narrativeTraceAgent` configuration, resolves the agent JAR, and adds `-javaagent:path/to/agent.jar=com.example.app` to all Test tasks.

### CI with strict clarity gates

```kotlin
narrativeTrace {
    tracingLevel.set("NARRATIVE")
    clarity {
        minScore.set(0.80)
        maxHighIssues.set(0)
    }
}
```

`./gradlew check` fails if any scenario has clarity below 0.80 or any HIGH issues.

### Narrative approval traces

```kotlin
narrativeTrace {
    approval.set(true)
}
```

The first run writes each scenario's value-free structure as `src/test/narratives/<TestClass>/<scenario>.received.nt` and fails; review, run `./gradlew approveNarratives`, commit the `*.approved.nt` files. From then on any structural drift in a passing test — a new call, a dropped call, a changed outcome — fails the build with a readable diff until it is explicitly approved. Baselines are value-free, so they never leak runtime data and survive data-only changes.

### Disable in a subproject

```kotlin
narrativeTrace {
    enabled.set(false)
}
```

Nothing happens — no dependencies, no tasks, no compiler flags.

### Consuming a local checkout (composite build)

Before the artifacts are on a repository you can resolve — or when you want to try a change to NarrativeTrace against your own code — point your project at a sibling checkout. This needs **both** halves, and neither is optional:

```kotlin
// settings.gradle.kts
pluginManagement {
    // 1. Resolves the plugin itself: `id("ai.narrativetrace")` with no version.
    includeBuild("../narrative-trace-java")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// 2. Substitutes the `ai.narrativetrace:*` library coordinates the plugin adds
//    on your behalf. Without this, the build fails resolving artifacts that are
//    sitting right there on disk.
includeBuild("../narrative-trace-java")

rootProject.name = "my-app"
```

```kotlin
// build.gradle.kts
plugins {
    java
    id("ai.narrativetrace")  // no version — the included build supplies it
}

repositories { mavenCentral() }
```

Why both: `pluginManagement { includeBuild(...) }` and a top-level `includeBuild(...)` are two different mechanisms. The first makes the plugin *marker* resolvable; the second makes Gradle substitute project dependencies for the *library* coordinates `DependencyConfigurator` adds (`ai.narrativetrace:narrativetrace-core` and friends). With only the first, the plugin applies and the build then fails at dependency resolution.

This is ordinary Gradle behaviour rather than anything NarrativeTrace does, but the plugin is what puts those library coordinates in your build in the first place, so it is where you meet it.

Two things worth knowing:

- **Nothing needs publishing.** Do not run `publishToMavenLocal` — composite substitution replaces the coordinates before resolution, so a local publish would only add a stale copy that may shadow your edits.
- **The version does not have to match.** Substitution is by group and module name, so the included build's own version satisfies whatever version the plugin asks for. If you would rather resolve real artifacts and skip the composite, set `libraryVersion` instead — see [Version Resolution](#version-resolution).

### Multi-project setup

Apply the plugin only in subprojects that have tests:

```kotlin
// settings.gradle.kts
rootProject.name = "my-app"
include("core", "web", "shared")
```

```kotlin
// core/build.gradle.kts
plugins {
    java
    id("ai.narrativetrace")
}
```

Each subproject gets its own `clarityCheck` and `clarityScan` tasks.

## Groovy DSL

All examples above use Kotlin DSL. The Groovy equivalent:

```groovy
plugins {
    id 'ai.narrativetrace' version '0.2.5'
}

narrativeTrace {
    mode = 'proxy'
    testFramework = 'junit5'
    scope = 'test'

    modules {
        slf4j = true
    }

    clarity {
        minScore = 0.80
        maxHighIssues = 0
    }
}
```

## Validation

The plugin validates all string properties at configuration time (inside `afterEvaluate`). Invalid values produce a clear error message:

```
> Invalid narrativeTrace mode 'invalid'. Valid values: proxy, agent, spring
> Invalid narrativeTrace scope 'compile'. Valid values: test, production
> Invalid narrativeTrace format 'xml'. Valid values: markdown, text, mermaid, plantuml
> Invalid narrativeTrace tracingLevel 'VERBOSE'. Valid values: OFF, ERRORS, SUMMARY, NARRATIVE, DETAIL
```

## Full DSL Reference

Copy-paste starting point with every property shown:

```kotlin
narrativeTrace {
    enabled.set(true)                          // default: true
    mode.set("proxy")                          // "proxy" (default) | "agent" | "spring"
    testFramework.set("junit5")               // "junit5" (default) | "junit4"
    scope.set("test")                          // "test" (default) | "production"
    format.set("markdown")                     // "markdown" | "text" | "mermaid" | "plantuml"
    tracingLevel.set("DETAIL")                 // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
    outputDir.set(layout.buildDirectory.dir("narrativetrace"))
    glossary.set(false)                        // default: false — harvest glossary.json at suite end
    approval.set(false)                        // default: false — verify structure against committed baselines
    approvedDir.set(layout.projectDirectory.dir("src/test/narratives"))
    // libraryVersion.set("<your-snapshot-version>")  // default: plugin's embedded version — override to dogfood a snapshot

    modules {                                  // fine-grained opt-in (all default false)
        slf4j.set(false)
        micrometer.set(false)
        servlet.set(false)
        springWeb.set(false)                   // implies servlet
    }

    agent {                                    // only relevant when mode = "agent"
        packages.set(listOf("com.example.app"))
    }

    clarity {
        minScore.set(0.80)                     // default: 0.0 (no gate)
        maxHighIssues.set(0)                   // default: Integer.MAX_VALUE (no gate)
        maxSuiteIssues.set(0)                  // default: Integer.MAX_VALUE (advisory only)
        warnOnly.set(false)                    // default: false
    }
}
```

## See Also

- [Installation Guide](installation-guide.md) — manual setup without the plugin, integration paths
- [Configuration Guide](configuration-guide.md) — tracing levels, JUnit/Spring/SLF4J configuration
- [Clarity Guide](clarity-guide.md) — scoring model, NLP components, clarity report format
- [Spring Integration Guide](spring-integration-guide.md) — `@EnableNarrativeTrace`, `@Async` propagation, servlet filter
- [Annotations Guide](annotations-guide.md) — `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`
