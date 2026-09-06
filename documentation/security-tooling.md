# Security tooling

Four tools, four different cadences, one rule behind all of them: a tool that
needs the network never sits in a per-commit gate, and every tool has a named
build-script entry point that CI only invokes — no version, flag or threshold
ever appears in a CI YAML file. This is the thin-CI convention every gate in
this repository follows; see [Security Testing](security-testing.md) for the
fuzz suite that predates it and follows the same shape.

| Tool | What it catches | Entry point | Runs |
|---|---|---|---|
| [gitleaks](#gitleaks--secrets) | Secrets landing in a commit | `gitleaksScan` (Gradle) + a local pre-commit hook | Pre-commit hook on every commit (staged diff); `gitleaksScan` on demand — offline, but no cadence was specified, so none was invented |
| [Semgrep](#semgrep--static-analysis-security-rules) | Static security anti-patterns (community rules) | `semgrepScan` (Gradle) | Private CI, merge requests + weekly schedule — never a plain push |
| [OSV-Scanner](#osv-scanner--dependency-vulnerabilities) | Known vulnerabilities in resolved dependencies | `osvScan` (Gradle) | Private CI, weekly schedule + manual web trigger — never a merge request or a push |
| [FindSecBugs](#findsecbugs--bytecode-security-analysis) | Security bug patterns in compiled bytecode | `spotbugsMain` (Gradle, via the SpotBugs plugin) | Every `./gradlew check` — offline-capable, like PMD |

Also in this document: [dependency-verification metadata](#dependency-verification-metadata)
(checksums, not a scanner) and what a maintainer needs to install locally.

## gitleaks — secrets

Two entry points, same tool:

- **A local pre-commit hook** runs `gitleaks protect --staged` over the whole
  staged diff before every commit — not just `.java`/`.kts` files, since a
  secret can land in any file type. Blocks the commit on a finding; warns and
  passes when the `gitleaks` binary is absent from `PATH`, the same
  degrade-gracefully convention every other step in that hook follows.
- **`gitleaksScan`** (Gradle, `verification` group) runs `gitleaks detect`
  against the full git history, for a periodic sweep or a machine that never
  ran the hook. JSON report under `build/reports/gitleaks/`. Warns and passes
  when the binary is absent; not wired into `check` or a CI job — gitleaks
  itself is fast and fully offline, but no cadence was specified for the
  full-history sweep, so none was invented.

A full-history sweep (2026-09-02) found two flagged strings,
both the same false positive: `.gitsecret/paths/mapping.cfg`, a SHA-256
*lookup hash* naming which encrypted path a key can decrypt, not a credential
— git-secret's own design commits this file in the clear. Allowlisted by path
and rule in `.gitleaks.toml`, with the reasoning inline. No real secret was
found in the repository's history.

## Semgrep — static analysis security rules

`semgrepScan` (Gradle, `verification` group) runs the community `p/java`
registry ruleset (OSS rules only — no custom rules; see "Scope" below) over
the tracked source tree, JSON report under `build/reports/semgrep/`. Warns
and passes when the `semgrep` binary is absent.

Fetching the registry ruleset needs network, so this is not in `check`:
private CI runs it on merge requests and the weekly schedule only, never
a plain push.

A run against both `p/java` and the broader `p/security-audit` pack
(2026-09-02) found **zero findings from either** — consistent with a manual
security audit having gone over this code earlier the same week and every
finding it raised already being fixed. The entry point uses `p/java` since it
names the language directly, matching what the tool is asked to cover.

**Scope:** custom Semgrep rules are deliberately out of scope. A custom rule
would encode this project's own vulnerability classes in a public,
security-relevant form — the tooling that runs is public, but a finding it
would encode is not.

## OSV-Scanner — dependency vulnerabilities

`osvScan` (Gradle, `verification` group) scans an aggregated dependency
manifest against the [OSV database](https://osv.dev/). The manifest is a
CycloneDX SBOM: `org.cyclonedx.bom` is applied at the **root project only**,
which auto-aggregates every subproject's dependencies into one
`build/reports/cyclonedx/bom.json`. `osvScan` depends on `cyclonedxBom` and
points the scanner at that one file — never at a bare directory scan, which
also matches POM files cached by nested test harnesses (GradleTestKit's own
dependency cache under a functional-test module's `build/`) as if they were
this project's own resolved graph.

Querying the OSV database needs network, so this is not in `check`:
private CI runs it on the weekly schedule and a manual web trigger only
— never a merge request, never a push.

This automates the manual OSV pass from an earlier adversarial security audit
— that pass was a one-time, by-hand check of direct dependencies; `osvScan`
repeats it on a schedule against the *actual resolved graph*, transitive
dependencies included.

A run while online (2026-09-02) triaged every advisory against the SBOM's own
dependency graph. Fixed, with the gate confirmed green:

- `logback-classic` 1.5.15 → 1.5.38 — `logback-core`, a transitive child,
  still carried four open advisories below 1.5.34.
- `com.github.noconnor:junitperf-junit5` 1.35.0 → 1.37.0.
- `narrativetrace-core`: an explicit `jackson-databind` 2.18.10 test floor,
  mirroring the pin `narrativetrace-security-tests` already carried.
- Root `build.gradle.kts`: `io.netty:*` 4.2.x forced to 4.2.17.Final
  (GHSA-8c42-7qj2-3j46) — Micronaut still resolved 4.2.16.Final at its newest
  4.x patch; Netty releases the 4.2.x family in lockstep, so forcing it
  together is safe.

Ledgered, not fixed — each is a transitive-only advisory with no in-scope
lever (test-only scope, or a breaking major-version jump on the tool that
pulls it in): `commons-compress` via a docker-test-only dependency,
three transitives via a perf-test-only dependency excluded from `test`, and
`commons-lang3`/`jackson-core` via the mutation-testing and Micronaut
tooling's own internal dependencies. One advisory pair was found and
deliberately *not* taken: bumping PMD's `toolVersion` to the fixed release
trips a large number of pre-existing violations under this repository's
ruleset — PMD Designer, the tool the advisories are actually in, is a GUI
this repository never invokes, so the fix is deferred to a dedicated
PMD-conformance pass rather than forced through a broken gate.

## FindSecBugs — bytecode security analysis

FindSecBugs is a [SpotBugs](https://spotbugs.github.io/) plugin — it rides on
the `com.github.spotbugs` Gradle plugin, applied to every subproject beside
the existing PMD gates. Unlike the three tools above, it needs no network at
scan time (only the one-time dependency resolution every other declared
dependency already pays for), so it runs in `spotbugsMain` on **every**
`./gradlew check`, same as `pmdMain`.

`spotbugsTest` is registered by the plugin but disabled
(`tasks.named("spotbugsTest") { enabled = false }`) rather than removed from
`check`'s task graph. The fuzz and hostile-input test fixtures in
`narrativetrace-security-tests` deliberately do the things a security linter
exists to flag on production code — deserializing untrusted bytes, building
strings that look like injection payloads — because that is the point of the
test, and a bytecode scanner cannot tell "this is the test subject" from "this
is the vulnerability."

Findings are triaged the same way Semgrep's are: a real finding gets fixed; a
false positive in context gets a targeted, justified suppression. Suppression
lives in `config/spotbugs/exclude.xml`, not `@SuppressFBWarnings` at the call
site — `narrativetrace-api` publishes with zero dependencies (enforced by
`ArchitectureTest`), and even a `compileOnly` annotation dependency would be a
class reference the architecture rule rejects. One filter file, applied
project-wide, keeps every module's suppressions in one place a reviewer can
audit without touching source.

Only the `SECURITY` bug category is enforced. `STYLE`, `BAD_PRACTICE` and
`MT_CORRECTNESS`/`PERFORMANCE` are vanilla-SpotBugs categories PMD
(bestpractices + errorprone) and JDepend already cover or that are unrelated
to FindSecBugs's purpose here; `MALICIOUS_CODE` (`EI_EXPOSE_REP`/`_REP2`) is
excluded for this phase specifically because it found real findings — ~130
mutable-field-exposure sites across 21 fields in 8 modules — that need a
proper audited fix, not a same-session tack-on. See
`config/spotbugs/exclude.xml`'s own header comment for the follow-up.

Three per-site findings are suppressed as false positives / non-issues in
context, each with its reasoning in `config/spotbugs/exclude.xml`:
`SpanIdGenerator` uses `ThreadLocalRandom` for trace/span IDs, which are
identifiers, not secrets; `ConfigResolver.loadFromClasspath` resolves a
hardcoded classpath resource name, not attacker-reachable input, so
`URLCONNECTION_SSRF_FD` does not apply; the EJB4 example's `ClaimsServlet`
serves `text/plain`, so `XSS_SERVLET` does not apply.

## Dependency-verification metadata

Gradle's [dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html)
records a checksum for every dependency artifact Gradle resolves, in
`gradle/verification-metadata.xml`. This is not a scanner — it does not know
about vulnerabilities — it is a supply-chain integrity check: a dependency
whose bytes do not match its recorded checksum fails the build immediately,
instead of silently compiling and shipping tampered or corrupted bytes.

### Updating it

A version bump to any dependency needs its checksum regenerated in the same
change, or the next resolution of that dependency fails the build with a
verification error that names the artifact and gives no further clue by
itself:

```bash
./gradlew --write-verification-metadata sha256 check
```

Run this after any dependency version change (a direct bump, or a transitive
one moved by a `resolutionStrategy` force), then review the diff to
`gradle/verification-metadata.xml` before committing it alongside the version
bump — the diff should be exactly the changed artifacts' entries, nothing
broader. Use `check` rather than a lighter task like `help`: several plugin
configurations (pitest, jcstress, the spotbugs/FindSecBugs plugin classpath
itself) are only resolved once their owning plugin's extension is configured
or their task graph is built, which `help` never triggers. A narrower task
also works if you know exactly which configuration moved and want a smaller
diff to review.

## GitHub Actions

`.github/workflows/*.yml` are already SHA-pinned to a specific commit rather
than a floating tag — a floating tag is mutable at the source, so a pinned
SHA is what makes "this workflow runs the action version it was reviewed
against" actually true. No further action here.

## Installing the scanners locally

None of `gitleaks`, `semgrep` or `osv-scanner` is a Gradle dependency — each
entry point shells out to a binary on `PATH` and warns-and-passes when it is
absent, so a machine without them still gets a working `check`/pre-commit.
For a machine that wants a real local scan:

- **gitleaks**: a standalone binary — see the
  [releases page](https://github.com/gitleaks/gitleaks#installing).
- **semgrep**: `pip install semgrep`, or see the
  [getting-started guide](https://semgrep.dev/docs/getting-started/) for
  other install methods.
- **osv-scanner**: a standalone binary — see the
  [installation guide](https://google.github.io/osv-scanner/installation/).

FindSecBugs needs nothing extra: it is a regular Gradle plugin dependency,
resolved the same way every other declared dependency is.
