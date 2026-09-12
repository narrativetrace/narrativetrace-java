# Troubleshooting

Symptom → cause → fix, for the failure modes people actually hit. Some
entries are the full explanation; others point at the guide that already
carries it in more detail rather than repeating it here — one home per fact.

## Parameters show as `arg0`, `arg1`

**Cause:** the `-parameters` compiler flag is missing, so the compiled class
carries no real parameter names for NarrativeTrace to read.

**Fix:**

```kotlin
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
```

The Gradle plugin adds this automatically — this only matters for a manual
setup.

## `Cannot create Launcher without at least one TestEngine`, or `Could not start Gradle Test Executor 1: Failed to load JUnit Platform`

**Cause:** `useJUnitPlatform()` needs both a JUnit 5 engine and
`org.junit.platform:junit-platform-launcher` on the test runtime classpath.
Gradle 8 supplies a version of the launcher itself when only the engine is
declared — a deprecated behaviour it warns about on every run — and Gradle 9
removes that auto-management outright, so the test *process* fails before any
engine, extension, or test class runs. Reproduced against a real Gradle 9.0.0
build: the second message above is its exact wording.

**Fix:**

```kotlin
dependencies {
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
```

The Gradle plugin adds both automatically *(since 0.2.2, unreleased)* — this only matters for a manual
setup.

## No trace output files

**Cause:** output writes by default *(since 0.2.2, unreleased)* to `build/narrativetrace`, so a missing
file usually means one of: `narrativetrace.output=false` is set somewhere
(`junit-platform.properties`, a system property, or the Gradle plugin's
`enabled.set(false)`); the trace was empty because no call went through a
traced proxy/agent; or `narrativetrace.outputDir` points somewhere other than
where you're looking.

**Fix:** confirm the property isn't set to `false`, and check
`build/narrativetrace/` (or your configured `narrativetrace.outputDir`) —
see the [Configuration Guide](configuration-guide.md) for every property and
its default.

## I don't see anything in my terminal

**Cause:** the per-test summary and the suite footer are written to the test
process's standard output. Gradle's `test` task captures that into the
XML/HTML report by default — a plain `./gradlew test` shows nothing on a
vanilla terminal, even though every file under `build/narrativetrace/` is
written correctly.

**Fix:** enable standard-stream logging on the `test` task:

```kotlin
tasks.test {
    testLogging.showStandardStreams = true
}
```

This is a Gradle behavior, not something NarrativeTrace does — see
[Installation Guide § Option D](installation-guide.md#option-d-junit-4-auto-context--trace-output)
for the same note in the JUnit 4 recipe.

## Proxy throws `ClassCastException`

**Cause:** the target does not implement the interface passed to
`NarrativeTraceProxy.trace(...)`.

**Fix:** make sure the concrete class implements that interface, and pass
the interface `Class`, not the implementation's.

## Spring beans not being traced

**Cause:** either the bean's package is outside `basePackages`, or the bean
implements no interface — `narrativetrace-spring` uses the same JDK dynamic
proxy as `narrativetrace-proxy`, which cannot wrap a class with no
interface. A bean with no interface is left untouched silently; this is not
an error.

**Fix:** add the package to `@EnableNarrativeTrace(basePackages = ...)`, and
give the bean an interface if it does not have one.

## Micronaut HTTP filter injects no user context

**Cause:** a bean implementing `RequestContextProvider` was written against
the wrong type. NarrativeTrace ships two same-named-sounding interfaces:
`ai.narrativetrace.api.export.RequestContextProvider<REQUEST>` (generic,
framework-agnostic, what the servlet filter and Spring Web bind to
`HttpServletRequest`) and a Micronaut-specific one the HTTP filter actually
looks up. A bean implementing the API-jar type is silently not a candidate —
the filter injects nothing and runs without user context, with no error at
all. The symptom is every span showing empty `enduserId`/`tenantId` fields.

**Fix:** implement the Micronaut type, not the API jar's. Full detail —
including why the two types stay separate — in
[Micronaut Integration Guide](micronaut-integration-guide.md) and
[API Surface](api-surface.md).

## Clarity score seems wrong

**Cause:** usually a generic name the NLP analysis flags — `get`, `set`,
`process`, `handle`, `data`, `info`, `temp` and similar score low regardless
of context.

**Fix:** review the issues list in `clarity-report.md` and replace the
flagged name with a domain-specific one (`getData()` → `fetchOrderHistory()`).
See the [Clarity Guide](clarity-guide.md) for the full scoring model.

## Cross-thread traces are empty

**Cause:** `captureTrace()`/`events()` are thread-scoped — they return only
the calling thread's trace. Either the context was never propagated to the
async thread, or `captureTrace()` was called on a different thread than the
one that recorded the events.

**Fix:**

```java
var snapshot = context.snapshot();
executor.submit(snapshot.wrap(() -> service.process()));
```

Or register `NarrativeTraceThreadLocalAccessor` with Micrometer for
automatic propagation. If you only need the forked work itself, call
`captureTrace()` inside the task, on the recording thread.

## Agent doesn't instrument classes

**Cause:** the `packages=` filter doesn't match, or the boundary rules do
not match what you expected — `com.example` matches `com.example.*`, never
`com.exampleExtra`.

**Fix:** check the `packages=` argument; wildcards (`com.example.*`) and
multiple packages (semicolon-separated, `packages=com.a.*;com.b.*`) are both
supported. There is no exclude list — only inclusion.

## Agent traces but nothing appears in my logs

**Cause:** the agent deliberately brings no SLF4J provider of its own — a
minimal host (an app server with no reachable classpath, most often) has
nothing to write narration through, so SLF4J logs a one-time warning and
defaults to a no-op logger. Nothing is broken: `captureTrace()` still
returns the trace, and the files under `build/narrativetrace/` (if output is
on) are unaffected — only live log narration is silent.

**Fix:** put a provider on the classpath (`logback-classic`, `slf4j-simple`,
…), or pass `loggingJars=/path/to/provider.jar` for a host with no reachable
classpath. To silence narration on purpose instead, attach with
`loggerName=` (empty) or `-Dnarrativetrace.narration=off`. Full recipe in
[Installation Guide § Narration needs an SLF4J provider](installation-guide.md#narration-needs-an-slf4j-provider--the-agent-never-brings-one).

## `@NotTraced` fails to compile: "package ai.narrativetrace.api.annotation does not exist"

**Cause:** the Gradle plugin's default `scope` is `"test"`, so
`narrativetrace-api` — where every annotation lives — sits on
`testImplementation` only. Referencing `@NotTraced` (or `@Narrated`,
`@OnError`, `@NarrativeSummary`) from a class under `src/main/java` fails to
compile before a test ever runs.

**Fix:** add the API jar on `compileOnly` (it has no runtime dependencies of
its own):

```kotlin
dependencies {
    compileOnly("ai.narrativetrace:narrativetrace-api:0.2.1")
}
```

Or set `scope.set("production")` if NarrativeTrace is meant to run in
production anyway. See [Configuration Guide § Gradle Plugin
DSL](configuration-guide.md#gradle-plugin-dsl), where `scope` is documented.

## Approval mode wrote `.received.nt`

**Cause:** this is the intended behavior, not a failure — either no
`.approved.nt` baseline exists yet for the scenario, or a passing test's
structure has drifted from the one that is committed.

**Fix:** review the `.received.nt` diff, and if the new shape is correct,
promote it:

```bash
./gradlew approveNarratives
```

Never rename it by hand into place without reading the diff first — that is
exactly the review approval mode exists to force. Full format and file
layout in [Structural Trace Format](structural-trace-format.md); what to do
with each file day to day is in [What to Commit](what-to-commit.md).
