# NarrativeTrace documentation

[English](README.md) | [Español](LEAME.md) | [Português](LEIAME.md) | [简体中文](自述文件.md)

The index of every document in this repository. The published documentation —
including pages that have no file here — is at
[narrativetrace.ai/docs](https://narrativetrace.ai/docs.html).

This index lists every document in English. Switch language with the menu
above — each language's index lists that language's translated guides.

## Start here

| Document | What it covers |
|---|---|
| [See a trace in 60 seconds](sixty-seconds.md) | A plain `main`, one call, a trace in your terminal — no log statements, no test framework, run for real against the published artifacts |
| [Developer Experience](developer-experience.md) | From empty project to first narrative: one-line setup, per-test artifacts, the demo launcher, local composite builds, redaction as part of the workflow |
| [Installation Guide](installation-guide.md) | Dependencies, the five integration paths (JDK proxy, Spring, Micronaut, JUnit 5/4, java agent), trace output setup, compatibility matrix |
| [Choosing an Integration](choosing-an-integration.md) | Which module you actually need: a decision diagram plus the caveats each of the five attachment paths has |
| [Lifecycle Guide](lifecycle-guide.md) | Where NarrativeTrace belongs in your process: development, CI/acceptance, production — and the privacy posture at each stage |
| [Configuration Guide](configuration-guide.md) | Every configuration surface: system properties, `junit-platform.properties`, Gradle, Spring, Micronaut, SLF4J; capture flags, MDC keys and redaction defaults |
| [Annotations Guide](annotations-guide.md) | `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`, and the purity contract they imply |
| [Privacy and Redaction](privacy-and-redaction.md) | The row-by-row redaction contract verified against the code, the guarantees and non-guarantees list, and the production loss model |
| [What to Commit](what-to-commit.md) | Which generated files are CI artifacts and which are reviewed baselines you commit |
| [Troubleshooting](troubleshooting.md) | Symptom → cause → fix for the failure modes people actually hit, from `arg0` parameters to a silent agent |

## Framework integration

| Document | What it covers |
|---|---|
| [Spring Integration Guide](spring-integration-guide.md) | Bean tracing via `BeanPostProcessor`, the servlet filter, `@Async` propagation with `ContextPropagatingTaskDecorator` |
| [Micronaut Integration Guide](micronaut-integration-guide.md) | Bean tracing, the reactive HTTP filter, configuration properties |
| [Gradle Plugin Guide](gradle-plugin-guide.md) | DSL reference, the quality gates the plugin registers, recipes |
| [Maven Guide](maven-guide.md) | The manual recipe for a plain Maven build: dependencies, the `-parameters` compiler flag, Surefire wiring for trace output and the JUnit 5 provider, and what to do instead of each Gradle-plugin-only task |

## Analysis and output

| Document | What it covers |
|---|---|
| [Clarity Guide](clarity-guide.md) | The five-dimension scoring model, the NLP components behind it, JUnit integration and the `clarityCheck` gate |
| [Feature Guide](feature-guide.md) | The canonical catalog: every feature, its tier, its implementation, its status |
| [Structural Trace Format](structural-trace-format.md) | The value-free `.nt` artifact behind delta reporting and approval testing — the cross-platform format spec |
| [Duplication Detection](duplication.md) | PMD CPD over main/test sources: the 60-token floor, the ratchet against a committed baseline (not a fixed percentage), and how to read or exempt a finding |

## Design and rationale

| Document | What it covers |
|---|---|
| [Dual-Path Event Pipeline](dual-path-pipeline.md) | The default pipeline's contract: the crash-durable synchronous narration path, the never-blocking buffered path, counted loss, and capture-time redaction upstream of both |
| [API Surface](api-surface.md) | What is in `narrativetrace-api` and why: the admission rule, one row per admitted type, and the types deliberately kept out |
| [Security Testing](security-testing.md) | The two-tier fuzz suite: the shared hostile corpus, the seven oracles every target asserts, how to add a case, and how a crash becomes a regression test |
| [Security Tooling](security-tooling.md) | Secrets scanning, SAST, dependency-vulnerability scanning and bytecode security analysis: what runs when, each tool's entry point, and the dependency-verification-metadata update workflow |
| [Concurrency Testing](concurrency-testing.md) | The jcstress suite over the dual-path pipeline: the invariant table every NarrativeTrace runtime mirrors, the result taxonomy, how to run it, and the two defects it found |

## For AI agents and tools

- [`llms.txt`](llms.txt) — machine-readable index following the
  [llmstxt.org](https://llmstxt.org) convention.
- [`llms-full.md`](llms-full.md) — the complete reference in one file: API,
  module map, configuration, integration recipes, troubleshooting.
- Every published module ships a Javadoc-rich sources jar
  ([javadoc.io](https://javadoc.io/doc/ai.narrativetrace)); the Javadoc is
  written for LLM consumption, with `INTENT` and `@llmNote` tags.
- [`i18n/manifest.json`](i18n/manifest.json) — the machine-readable
  declaration behind the language menus above: every translated language,
  where its directory/index/root file live, its completion status, and the
  set of user documents in scope for translation. `./gradlew translationCheck`
  reads it to enforce completeness, structural parity with the English
  source, and index/menu integrity; `./gradlew translationStatus` reads it to
  print the full coverage and review-status dashboard.

## Keeping this index honest

Adding or removing a document under `documentation/` means updating this file
in the same change. A document that is not listed here is invisible to anyone
browsing the repository.
