# Duplication detection

`./gradlew duplicationReport` runs PMD's CPD (Copy/Paste Detector — part of
the same PMD distribution `pmdMain`/`pmdTest` already use, not a separate
tool) over every module's Java sources and writes a report every commit.
`./gradlew duplicationCheck` reads that report and enforces the duplication
ratchet described below; it is part of `check`.

## What is measured

- **Language:** Java only, today (see the family-wide schema below).
- **Token floor: 60.** A match below 60 tokens is usually a coincidence —
  two unrelated methods that happen to share a short, common shape — not a
  structural copy worth acting on.
- **Identifiers and literals are ignored.** CPD then finds *structural*
  duplication (the same shape with different names and values), not merely
  pasted text with the same names.
- **Main and test sources are scanned separately.** The test tree is
  reported — its numbers are in `duplication.json` and the summary line —
  but it never gates `duplicationCheck`. Test scaffolding legitimately
  repeats (setup, fixture builders, assertion blocks); a fixed threshold
  there would be noise, not signal.

## The ratchet, not a fixed percentage

A single "fail above N%" number is the wrong instrument: the right number
depends on the token floor and on how much of the tree is naturally
repetitive (data tables, generated code), so a fixed threshold ends up
either loose enough to never fire or tight enough to block unrelated work.
Instead, `duplicationCheck` ratchets against a committed baseline,
`config/duplication/baseline.properties`:

- **Fails when the main-tree percentage rises more than 0.3 percentage
  points above the recorded baseline** — a small tolerance that absorbs
  token-count noise between runs, not real growth.
- **Fails when a non-exempt cluster is larger than the baseline's recorded
  largest cluster** — a single new large duplicate block is a finding on
  its own, even while the overall percentage stays flat.
- **The test tree never fails the check**, whatever its percentage.

Lower the baseline with the same commit that removes the duplication it
recorded. Never raise it to make a failure go away — add a reasoned
exemption instead (below), or leave the finding for a later pass.

## Exemptions are data

`config/duplication/exemptions.txt` lists deliberate duplication: code that
is intentionally structured as two parallel copies rather than one shared
abstraction. Each entry is a `globA :: globB` pair (matched against the
repository-root-relative path CPD reports) with a `# reason` line directly
above it. A cluster is exempt only when *every* one of its occurrences
matches one of the pair's two globs — a default-deny rule, so an
unclassified cluster over the floor is always a finding, never a silent
pass. A pair with no reason above it, or a malformed pair, fails the build
outright rather than being ignored.

## Reading the report

`build/reports/duplication/duplication.json` is the normalised result (the
same shape every NarrativeTrace runtime's duplication tooling emits, for
whichever languages it covers):

```json
{"tool":"pmd-cpd","language":"java","minTokens":60,
 "main":{"tokensTotal":N,"tokensDuplicated":N,"percent":x.y,
         "clusters":[{"tokens":N,"lines":N,
                       "occurrences":[{"file":"…","startLine":N,"endLine":N}]}]},
 "test":{"...":"same shape"}}
```

`tokensDuplicated` is a *union* over token positions, not a sum over
clusters: CPD's matches overlap routinely (a long clone contains shorter
ones inside it, or the same lines appear in several different pairs), and
counting every cluster's tokens once per occurrence double- and
triple-counts those positions — the first scan of this repository measured
over 300% duplication that way before the union fix. `percent` can
therefore never exceed 100%. `build/reports/duplication/main.xml` and
`test.xml` carry PMD's own unmodified CPD XML alongside the JSON, for tools
that already consume that format.

The Gradle log prints one summary line per run:

```
duplication: main 21.5% of tokens in 910 clusters (largest 1022 tokens
narrativetrace-clarity/.../AbbreviationDictionary.java:26 ↔ .../AbbreviationDictionary.java:122)
· test 39.2% in 2628 clusters (reported, not gated)
```

## Adding an exemption

1. Run `./gradlew duplicationReport` and find the cluster in
   `duplication.json` or the summary line.
2. Confirm it is deliberate — a genuine parallel structure kept apart on
   purpose, not duplication nobody has gotten around to removing.
3. Add a `# reason` line and a `globA :: globB` pair to
   `config/duplication/exemptions.txt`.
4. Re-run `./gradlew duplicationCheck` to confirm it passes.

## Lowering the baseline

Remove the duplication, run `./gradlew duplicationReport`, and update
`main.percent` / `main.largestCluster` in
`config/duplication/baseline.properties` to the newly measured numbers in
the same commit — the same "floored to measured" idiom this build already
uses for coverage and mutation-score floors.
