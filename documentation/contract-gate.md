# The contract gate

`documentation/contract.yaml` is a machine-readable list of claims this
repository's documentation makes — a default, an entry point, a config
shape, or the effect a documented shape produces. `contract-probe/` proves
or disproves each one against a **published** install (Maven Central, never
`mavenLocal`, never a workspace build), so a doc page and the artifact
someone actually downloaded can never quietly disagree without a gate
noticing.

Every page in this repository describes the code it is committed with and
says nothing about versions — that is the standing documentation rule. This
gate is what keeps that promise honest across a release boundary: the page
says what the code does, and the probe proves the published artifact really
does it.

## What it catches

Four kinds of claim, each checked a different way:

| `kind` | What it proves | Example |
|---|---|---|
| `entry-point` | A coordinate resolves at all, on the real registry, at the version under test | `ai.narrativetrace:narrativetrace-core` |
| `reflectable-default` | A public constant already says what the docs claim — no execution needed | `BufferedEventConsumer.DEFAULT_CAPACITY` is `65536` |
| `probed-default` | A default only visible at runtime (a system property, a JUnit-extension-gated behavior) | `narrativetrace.approval` defaults to `false` |
| `config-shape` | A documented configuration shape produces the effect the docs claim | `narrativetrace-slf4j` on the classpath narrates with zero wiring |

No entry carries a version, and no entry is ever skipped. **The contract
describes the code on `main`, which is the published code**: development is
trunk-based and publishing is one step — the public snapshot and the artifacts
go out from the same commit — so the claims the gate reads and the install it
probes are the same code.

That is what lets an entry describe the code it is committed with, like every
other document here, instead of naming the version it started holding at.

## Two gates, two cadences

- **`./gradlew contractLint`** — part of `check`, every commit, no network.
  Validates `documentation/contract.yaml` itself: the schema parses, no two
  entries make the same claim, every entry's `probe` file exists, and every
  `page#anchor` pointer resolves to a heading that actually exists on that
  page.
- **`scripts/contract-check.sh`** — nightly, registry-backed, never per
  commit (the same "no network in the per-commit gate" rule
  `security-tooling.md` describes for the scanners). Resolves the version to
  check the way `scripts/verify-publication.sh` does (the newest `v*` tag
  reachable from HEAD, else Maven Central's `maven-metadata.xml <latest>`
  for `narrativetrace-core`), reads `documentation/contract.yaml` **from the
  working tree**, installs the artifacts into a **fresh temporary Gradle user
  home** — never this checkout's own `~/.gradle`, never a local
  `mavenLocal()` publish of the same version standing in for the real answer
  — and runs `contract-probe/`'s `runContract` task against it. Exits
  non-zero on any `fails`.

Run the nightly gate by hand against a specific version:

```bash
scripts/contract-check.sh <published-version>
scripts/contract-check.sh          # omit the version: checks the last published one
scripts/contract-check.sh --dry-run
```

`<published-version>` is a released version string, the same one the script
resolves for you when you omit it: the newest annotated `v*` tag reachable
from HEAD, else Maven Central's `maven-metadata.xml <latest>` for
`narrativetrace-core`.

A failure names all four facts in one line, so a skim is enough:

```
contract.yaml: probed-output-default documented default "true" but
ai.narrativetrace:narrativetrace-core <published-version> (published)
reads "false"
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
./gradlew runContract -PcontractVersion=<published-version> \
    -PcontractYaml=../documentation/contract.yaml -Pout=build/contract-result.json
```

`runContract` checks whatever `contractYaml` file you point it at — here, the
working tree's. That is the low-level escape hatch for developing a probe;
`scripts/contract-check.sh` is the gate, and it points the same task at the
same file, plus the fresh-install isolation this page describes.

Each contract entry dispatches to one probe class under
`contract-probe/src/main/java/ai/narrativetrace/contract/probes/` — the
entry's `probe` field names it, and `contractLint` fails if that file does
not exist. A probe returns one observed string; the entry holds when it
equals `documented_default` (or `expected_effect`, the spelling a
`config-shape` entry uses — both land in the same field).

## Adding an entry

`contract.yaml` is updated **in the same commit** as the feature that ships
a new documented default — the same discipline the Pro repository's
`pro/schema/*.json` files follow. Adding one:

1. Write the sentence in the doc page first, in the present tense and
   without naming a version — the page describes the code it ships with.
2. Add the entry to `documentation/contract.yaml`: `id`, `kind`, `page`
   (the doc path and the anchor of the heading carrying the sentence),
   `claim`, `documented_default`/`expected_effect`, and `probe`. No version:
   the entry ships with the code it describes.
3. Write the probe class under `contract-probe/src/main/java/...`. Use only
   stable public API that already exists at the OLDEST version the contract
   still checks — `contract-probe` compiles every probe class together
   against whichever single version is under test, so a probe referencing an
   API that only exists in a newer release breaks compilation for every
   other entry at the older version, not just its own.
4. `./gradlew contractLint` — confirms the shape and the anchor.
5. `cd contract-probe && ./gradlew runContract -PcontractVersion=<last published>`
   — runs your new entry against today's published artifact. Expect a `fails`
   line until the release carrying the feature goes out; that is the entry
   telling you it is ahead of the release, not a defect. The nightly gate
   reads the same file and says the same thing, so land the entry with the
   feature and let the release close it.

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
- **The short window between a commit and the release that publishes it.**
  `main` and the published artifact are the same code, published together, so
  there is no standing gap to model — but while a release is still in flight,
  a claim that landed with its feature is read against the previous artifact
  and says `fails`. That is the gate naming an unpublished commit, not a
  defect it is judging, and the release closes it.

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
