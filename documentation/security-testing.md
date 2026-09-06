# Security testing

NarrativeTrace reads input it does not control. Wire headers arrive from
strangers, configuration arrives from deployment scripts, and — above all —
the value renderer walks arbitrary object graphs eagerly, including
third-party DTOs whose `toString()` can throw, recurse or block. The narrative
it writes is then read by a language model as often as by a person.

This document describes the suite that attacks all of that on purpose:
`narrativetrace-security-tests`, a test-only module that depends on every
module which renders or emits, so one assertion can reach the whole output
surface.

Two tiers, both real gates:

| Tier | What it is | When it runs | Cost |
|---|---|---|---|
| **A — structured fuzz** | jqwik properties fed by a shared hostile corpus | every `./gradlew check` | seconds |
| **B — coverage-guided fuzz** | Jazzer `@FuzzTest` targets whose inputs the code's own coverage steers | seeds replayed in every `check`; time-budgeted on the weekly JDK-21 job | seconds / minutes |

**Every NarrativeTrace port mirrors these targets and this corpus.** The
corpus is data, copied between repositories verbatim the way the conformance
schemas are, so the same hostile case hits all five renderers; only the corpus
reader and the object-graph builder are written per language.

## Running it

```bash
./gradlew check                                   # Tier A, plus Tier B's seed replay
./gradlew :narrativetrace-security-tests:test     # the suite on its own
./gradlew fuzz                                    # Tier B, coverage-guided, budgeted
```

`fuzz` sets `JAZZER_FUZZ=1` and gives each target the budget in
`FuzzBudget.PER_TARGET`. It writes Jazzer's generated corpus and any
reproducer file under `narrativetrace-security-tests/build/fuzz/`, which the
scheduled CI job publishes as an artifact whether the job passed or failed — a
crash is only actionable with the input that caused it.

To fuzz one target:

```bash
./gradlew fuzz --tests '*ValueRendererFuzzTest*'
```

## The oracles

A crash is not the only defect, and "it did not throw" is not an oracle. Every
target asserts from this list; a port implements the same seven.

1. **No uncaught exception.** A hostile input degrades — it never propagates.
   This is the pipeline contract in one line: an observability failure must
   never become an application failure. Where a method declares a guard, the
   oracle is *the declared result or the declared exception, never a third
   thing*.
2. **Bounded time and size.** A narration costs O(size) of its input. No input
   hangs, and no input produces unbounded output.
3. **Well-formedness, read back by the consumer's own parser.** JSON parses
   with a real JSON parser *and* validates against the canonical
   `chapter-tree` schema; a Mermaid diagram has one statement per line and no
   raw control characters; Markdown frontmatter parses as YAML. An escaper
   that is merely plausible passes every eyeball test until the day it does
   not.
4. **Redaction.** A value marked `@NotTraced` — or matched by the name-based
   deny-list — appears in **no** output of **any** format at **any** depth.
   The suite plants a fresh random token behind the annotation, renders the
   graph, drives every emitter the product ships, and asserts the token is in
   no byte of any of them, whole or by prefix. A partial leak through a
   truncating emitter is still a leak.
5. **Idempotence.** Rendering the same input twice produces the same bytes, so
   nothing has leaked in from time, an identity hash or an iteration order.
6. **No thread or hook left behind.** Rendering starts no background thread.
7. **AI-consumer containment.** An instruction-shaped value comes back as
   *exactly one value* when the output is parsed or lexed again. It never
   terminates the enclosing JSON string, Mermaid label, Markdown fence or
   frontmatter block, and never appears outside the format's value
   delimiters — so text saying "ignore previous instructions" stays inert data
   to a model reading the narrative. The oracle is deliberately not a filter:
   filtering prose is a losing game, and a narrative that quietly rewrites what
   your code returned is worse than one that quotes it safely.

8. **A name is a path, and a path stays where it was put.** The test class and
   method name become directories and file names, so they are input too. Every
   element the writers build fits the filesystem's per-element limit — measured
   in bytes, because 200 three-byte characters overflow an element 200 ASCII
   ones fit — no name places an artifact outside the output directory it was
   given, and two names that differ at all resolve to two artifacts, so
   shortening a long one never silently overwrites another's baseline.

The seventh is checked by comparing against a **benign baseline**. The same
trace is rendered twice, once with a harmless value and once with the payload,
and the document's shape, the Mermaid statement count, the frontmatter key set
and the code-fence counts must match. A well-formedness check alone would
happily accept a forged field; a shape comparison is what makes "one value"
mean something.

## The targets

In priority order, which is the order every port implements them in.

| # | Target | The oracle that matters most |
|---|---|---|
| 1 | `Traceparent` and any other wire reader | never throws; round-trips what it accepts |
| 2 | `ValueRenderer` over hostile object graphs | redaction, at any depth, through any container |
| 3 | Every output format | well-formedness, bounded size |
| 4 | Template parsing and rendering | a redacted path or object renders the marker |
| 5 | Clarity and glossary scanners | scores stay inside their range; no crash on a name bytecode produced |
| 6 | Configuration loading | the declared result or the declared exception |
| 7 | Every output format, again | AI-consumer containment |
| 8 | The artifact writers, over hostile names | every path element fits the filesystem; nothing lands outside the output directory |

## The hostile corpus

`narrativetrace-security-tests/src/test/resources/hostile-corpus/` holds six
JSON fixtures with a `README.md` beside them describing every case shape:

| File | What it holds |
|---|---|
| `strings.json` | hostile scalar values: control characters, bidi and zero-width, combining sequences, unpaired surrogates, template lookalikes, JSON/Mermaid/Markdown/YAML metacharacters, values up to 1 MiB |
| `headers.json` | `traceparent` and `tracestate` values: wrong lengths, non-hex, all-zero ids, forbidden versions, trailing garbage, embedded CRLF |
| `templates.json` | `@Narrated`/`@OnError` templates: nesting, unterminated braces, paths into redacted members at every depth, unicode identifiers |
| `graphs.json` | declarative object-graph *shapes*: depth, width, cycles, self-reference, wrapper chains, throwing/blocking/recursive `toString`, huge collections |
| `injection.json` | prompt-injection payloads arriving as captured values: override phrasings, role and turn markers, tool-call lookalikes, link exfiltration, fence and frontmatter terminators, homoglyph variants |
| `names.json` | test class and method names as the writers receive them: separators and parent traversal, control characters, lone surrogates, noncharacters, and names past the filesystem's per-element limit in characters *and* in bytes |

Two rules keep the corpus portable, and both are enforced by a test rather
than by agreement:

- **The fixtures are ASCII on disk.** Every hostile code point is written as a
  `\uXXXX` escape that the JSON parser turns back into the real character. A
  corpus carrying raw control bytes and bidi overrides is unreadable in a diff
  and gets silently normalised by editors — which is exactly how a case stops
  testing what it was written for.
- **A large input is generated, not stored.** A `repeat` object expands to the
  1 MiB string and the hundred-thousand-element list, so the whole corpus is
  about 25 kB.

### Adding a case

1. Append an object to the relevant array with a stable kebab-case `id` and a
   `description` that says **what breaks**, not what the bytes are.
2. Write hostile characters as `\uXXXX` escapes. Use `repeat` for anything
   large.
3. For an object graph, describe the *shape* (`layers` or `kind`) rather than
   serialising an object — the corpus README has the table of shapes. Set
   `payload: "secret-record"` to have the builder plant a redaction sentinel
   inside it.
4. Run `./gradlew :narrativetrace-security-tests:test`. The new case is picked
   up by every property that reads that fixture; nothing needs registering.
5. Copy the fixture file to the other ports.

### Adding a target

Add the emitter to `Emitters` rather than to individual tests. Every oracle
asserts over the map that class returns, so an emitter added there is covered
by redaction, well-formedness, boundedness and injection containment at once —
which is the point. A test that names formats inline goes stale the day a
format is added.

## From a crash to a regression test

Jazzer writes the input that caused a failure into the run's working
directory, and the scheduled job publishes it as an artifact. The path from
there is deliberately manual, because a crashing input is a fact worth reading
before it becomes a file:

1. **Reproduce it.** Copy the input beside the target's seed corpus,
   `src/test/resources/<package path>/<TestClass>Inputs/<method>/`, and give
   it a name that says what it is (`deep-chain-overflow`, not `crash-8f3a`).
   Running the suite now replays it in every `check`, so it is a regression
   test from the moment it lands.
2. **Understand it before fixing it.** The bytes are one instance; the defect
   is a class. Add a corpus case describing the *shape*, so every port inherits
   it and the generated half of Tier A explores around it.
3. **Fix it in the module that owns it**, with a named unit test there. The
   security suite proves the property across modules; the owning module's test
   is what a future reader finds when they change that code.
4. **Commit the seed and the fix together**, so the repository never contains a
   seed that is known to fail.

## What the suite does not do

- It does not replace the property tests inside each module. Those own their
  module's behaviour; this owns the invariants that only hold across modules.
- It does not fuzz third-party libraries, only NarrativeTrace's own readers,
  renderers and writers.
- It does not assert on the *content* of a narrative, only on its structure and
  its containment. What a trace says is the rest of the test suite's business.
