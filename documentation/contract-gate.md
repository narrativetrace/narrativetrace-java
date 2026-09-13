# The contract gate

`documentation/contract.yaml` is a machine-readable list of claims this
repository's documentation makes — a default, an entry point, a config
shape, or the effect a documented shape produces. `contract-probe/` proves
or disproves each one against a **published** install (Maven Central, never
`mavenLocal`, never a workspace build), so a doc page and the artifact
someone actually downloaded can never quietly disagree without a gate
noticing.

This is the third leg of the docs-vs-published family, alongside the
`*(since X.Y.Z[, unreleased])*` markers inline in prose and the generated
banner under this documentation's own index — see those markers throughout
`documentation/*.md` for the disclosure half of the same problem. This page
is the enforcement half: a marker says "this is new"; the contract gate says
"and it is really true of what shipped."

## What it catches

Four kinds of claim, each checked a different way:

| `kind` | What it proves | Example |
|---|---|---|
| `entry-point` | A coordinate resolves at all, on the real registry, at the version under test | `ai.narrativetrace:narrativetrace-core` |
| `reflectable-default` | A public constant already says what the docs claim — no execution needed | `BufferedEventConsumer.DEFAULT_CAPACITY` is `65536` |
| `probed-default` | A default only visible at runtime (a system property, a JUnit-extension-gated behavior) | `narrativetrace.approval` defaults to `false` |
| `config-shape` | A documented configuration shape produces the effect the docs claim | `narrativetrace-slf4j` on the classpath narrates with zero wiring |

Each entry also carries `since`: the version the claim first holds. An entry
whose `since` is **later than the version actually installed** that run is
reported `not-applicable-before-since` — never `fails` — so a documented
default for a feature that has not shipped yet does not fail the gate before
its own release does. The exemption is keyed on the version genuinely
installed, never on this repository's own `gradle.properties` version, which
this project keeps at the last published number until a release tag bumps it
(see the `*(since X.Y.Z, unreleased)*` markers already on many pages).

## Two gates, two cadences

- **`./gradlew contractLint`** — part of `check`, every commit, no network.
  Validates `documentation/contract.yaml` itself: the schema parses, every
  `since` is a real version string, no two entries make the same claim,
  every entry's `probe` file exists, every `page#anchor` pointer resolves to
  a heading that actually exists on that page, and every
  `*(since X.Y.Z, unreleased)*` marker anywhere in the English docs has at
  least one contract entry recording that version — the mechanical link
  between the inline markers and this file.
- **`scripts/contract-check.sh`** — nightly, registry-backed, never per
  commit (the same "no network in the per-commit gate" rule
  `security-tooling.md` describes for the scanners). Resolves the version to
  check the way `scripts/verify-publication.sh` does (the newest `v*` tag
  reachable from HEAD, else Maven Central's `maven-metadata.xml <latest>`
  for `narrativetrace-core`), installs it into a **fresh temporary Gradle
  user home** — never this checkout's own `~/.gradle`, never a local
  `mavenLocal()` publish of the same version standing in for the real
  answer — and runs `contract-probe/`'s `runContract` task against it.
  Exits non-zero on any `fails`.

Run the nightly gate by hand against a specific version:

```bash
scripts/contract-check.sh 0.2.1
scripts/contract-check.sh          # omit the version: checks the last published one
scripts/contract-check.sh --dry-run
```

A failure names all four facts in one line, so a skim is enough:

```
documentation/contract.yaml: probed-output-default documented default "true"
(since 0.2.2) but ai.narrativetrace:narrativetrace-core 0.2.2 (published) reads "false"
```

## `contract-probe/`

A small, **standalone** Gradle project — its own `settings.gradle.kts`, its
own Gradle wrapper — deliberately not `include()`d by this repository's own
`settings.gradle.kts`. That separation is the point: it consumes only
`ai.narrativetrace:*:<version>` coordinates resolved from `mavenCentral()`,
so what it proves is true of what a consumer would actually download, never
of this checkout's own build output. It ships in the public snapshot: it is
real code proving a documented claim, not private verification machinery.

Run it directly:

```bash
cd contract-probe
./gradlew runContract -PcontractVersion=0.2.1 \
    -PcontractYaml=../documentation/contract.yaml -Pout=build/contract-result.json
```

Each contract entry dispatches to one probe class under
`contract-probe/src/main/java/ai/narrativetrace/contract/probes/` — the
entry's `probe` field names it, and `contractLint` fails if that file does
not exist. A probe returns one observed string; the entry holds when it
equals `documented_default` (or `expected_effect`, the name the design note
uses for a `config-shape` entry — both land in the same field).

## Adding an entry

`contract.yaml` is updated **in the same commit** as the feature that ships
a new documented default — the same discipline the Pro repository's
`pro/schema/*.json` files follow. Adding one:

1. Write the sentence in the doc page first, with its
   `*(since X.Y.Z, unreleased)*` marker if the version has not tagged yet.
2. Add the entry to `documentation/contract.yaml`: `id`, `kind`, `page`
   (the doc path and the anchor of the heading carrying the sentence),
   `claim`, `since`, `documented_default`/`expected_effect`, and `probe`.
3. Write the probe class under `contract-probe/src/main/java/...`. Use only
   stable public API that already exists at the OLDEST version the contract
   still checks — `contract-probe` compiles every probe class together
   against whichever single version is under test, so a probe referencing an
   API that only exists in a newer release breaks compilation for every
   other entry at the older version, not just its own.
4. `./gradlew contractLint` — confirms the shape, the anchor and the
   since-marker link.
5. `cd contract-probe && ./gradlew runContract -PcontractVersion=<last published>`
   — confirms the new entry reports `not-applicable-before-since` against
   today's published version (it should, if the feature has not released
   yet) and, once released, reports `holds` against the version it landed
   in.

## What this deliberately does not cover

- **Prose accuracy outside `contract.yaml`.** A documented explanation that
  is simply wrong, incomplete, or confusing is a different failure mode —
  review catches that, not a runtime probe.
- **Anything the 60-second tutorial's own test already owns** — see
  `sixty-seconds/README.md`; `contract.yaml` is for defaults and shapes
  documented elsewhere, not a second copy of that page's own proof.
- **Full behavioral equivalence of a complex config object** — one named,
  checkable `expected_effect` per `config-shape` entry, never a spec of the
  whole feature the shape configures.
- **A marker correctly flagged `unreleased` for a version genuinely ahead of
  the one installed** — that is disclosure's job (the inline marker and the
  generated banner), not this gate's; a `since` later than the installed
  version is skipped, on purpose, every time.

## See also

- [Duplication Detection](duplication.md) — the other ratchet-style gate
  this repository runs the same way: a report every commit, an enforcement
  task wired into `check`.
- [Security Tooling](security-tooling.md) — the per-commit/nightly split
  this gate follows for the same reason (network calls do not belong in a
  gate every commit waits on).
- [Sixty Seconds](sixty-seconds.md) — the tutorial project this gate's
  registry-install technique is written to be reused by, once that page's
  own cold walk exists.
