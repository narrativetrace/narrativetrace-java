# Choosing an integration

NarrativeTrace has one capture model — an enter/exit event published through
the pipeline — reached by five different attachment mechanisms. This page
answers "which module do I actually need," first as a lookup table, then as
a decision diagram, then with the caveats each path has.

## You want... / Start with...

| You want | Start with |
|---|---|
| Traces in tests, least wiring | Gradle plugin + `narrativetrace-junit5` |
| The same, on JUnit 4 | `narrativetrace-junit4` |
| To choose exactly what is wrapped, in plain Java | `narrativetrace-proxy` (JDK dynamic proxy) |
| Spring beans traced automatically | `narrativetrace-spring` |
| Spring HTTP request lifecycle in production | `narrativetrace-spring-web` (wires `narrativetrace-servlet`) |
| Any servlet app, no Spring | `narrativetrace-servlet` |
| Micronaut beans and requests | `narrativetrace-micronaut` + `narrativetrace-micronaut-http` |
| **Zero code changes** — an app you can't or won't modify | `narrativetrace-agent` (java agent) |
| Cross-thread async visibility (`@Async`, Reactor, executors) | `narrativetrace-micrometer`, or `ContextSnapshot` by hand |
| Traces in your production log stream | `narrativetrace-slf4j` |
| OpenTelemetry spans | `narrativetrace-opentelemetry` |

This is the same matrix the [root README](../README.md#choose-your-integration)
carries; it lives here too as the anchor for the diagram and detail below.

## The decision

The report this page grew from proposed a shorter diagram than the one
below — its version treated "not a Spring/Micronaut app" as the only reason
to reach for the agent. That is not how the framework integrations actually
work: `narrativetrace-spring` and `narrativetrace-micronaut` both wrap beans
with the same JDK dynamic proxy `narrativetrace-proxy` uses, so a Spring bean
with no interface is skipped exactly as a plain proxy target would be
(verified against `spring-integration-guide.md` and
`micronaut-integration-guide.md`: both state "bean must implement at least
one interface"). The diagram below has that branch in it.

```text
Do you control how the object is constructed (plain Java, a test)?
   |
   +-- yes --> does the service implement an interface?
   |             |
   |             +-- yes --> JDK proxy (narrativetrace-proxy)
   |             +-- no  --> Java agent (bytecode instrumentation,
   |                         no interface required)
   |
   +-- no  --> is it a Spring or Micronaut bean?
                 |
                 +-- yes --> does the bean implement an interface?
                 |             |
                 |             +-- yes --> framework integration
                 |             |           (narrativetrace-spring /
                 |             |            narrativetrace-micronaut)
                 |             +-- no  --> Java agent
                 |
                 +-- no  --> Java agent (zero code changes)
```

Two paths converge on the agent for the same underlying reason: it is the
only mechanism here that instruments bytecode directly and therefore never
asks the "does it have an interface" question at all. The trade is that the
agent has no equivalent of Spring's `basePackages` exclude granularity or the
proxy's per-call opt-in — see the caveats below and
[Stacking with other wrappers](#stacking-with-other-wrappers) for what
happens when it stacks with something else that also wraps the same classes.

## One thing every path shares

All five attachment mechanisms publish through the same `EventPipeline`
(`ai.narrativetrace.core.pipeline`); none of them define their own notion of
a captured call. Choosing an integration is a question of *how the call gets
wrapped*, never of what gets recorded once it is — one canonical capture
feeds every render path (human detail, AI-safe structure, OpenTelemetry
spans, aggregation), so they stay in parity by construction instead of by
convention.

## Caveats per path

- **JDK proxy** — target must implement the interface passed to
  `NarrativeTraceProxy.trace(...)`, or the call throws `ClassCastException`
  at the wrap site. Only interface methods are visible; a call made directly
  on the concrete instance bypasses the proxy entirely.
- **Spring / Micronaut** — same interface requirement, enforced silently: a
  bean with no interface is left untouched, not an error. Base packages are
  include-only.
- **Java agent** — every non-private, non-abstract method under the matched
  packages is instrumented; there is no per-method opt-out and no exclude
  list today (`AgentConfig` parses `packages`, `loggerName`, `level`,
  `loggingJars` — nothing else). Package matching is delimiter-aware, so
  `com.acme` never matches `com.acmeExtra`. On a host with no logging
  provider on the classpath — a bare app server, most often — the agent
  narrates nothing until you add one; see
  [Troubleshooting](troubleshooting.md#agent-traces-but-nothing-appears-in-my-logs).
- **Cross-thread work** — none of the five paths above merges an
  async/executor thread's calls into the parent trace automatically. Use
  `ContextSnapshot.wrap(...)` by hand, or register
  `NarrativeTraceThreadLocalAccessor` with Micrometer.

## Stacking with other wrappers

NarrativeTrace is rarely the only thing wrapping a method: AOP proxies,
contract libraries, container interceptors and other observability agents
can attach to the same call. Three rules hold across every attachment
mechanism above:

- **One frame per business-boundary crossing.** The goal is to narrate the
  calls your code makes, not the machinery around them — bridge methods,
  container-generated view stubs, another library's generated decorators,
  or NarrativeTrace's own rendering are not the target.
- **Nesting order changes how a trace *reads*, never what it *returns or
  throws*.** Whichever wrapper sits innermost or outermost can change the
  shape of the recorded call tree, but recording is exception-isolated on
  every path and never replaces an outcome — the business result or
  exception a caller sees is always the real one.
- **Scoping is include-only, everywhere.** The agent's `packages=`, Spring's
  and Micronaut's base packages, and the proxy's explicit target interface
  all match on a delimiter boundary (`com.acme` never matches
  `com.acmeExtra`), and none of them has an exclude list yet — a class that
  lives inside your matched packages is wrapped whether it is yours or
  another library's.

## Platform ceilings

Every path above assumes a server or desktop JVM. Android is not supported
today (three separate mechanisms — SPI discovery, the SLF4J listener
lookup, and reflective value rendering — degrade silently under
R8/ProGuard rather than failing loudly), and GraalVM native image is
untested (the proxy and agent paths rely on reflection with no shipped
reachability metadata). Full detail:
[Installation Guide § Compatibility](installation-guide.md#compatibility).

## Recipes

Every path in the matrix has a complete, copy-pasteable recipe in the
[Installation Guide](installation-guide.md) — this page answers *which*,
that one answers *how*.
