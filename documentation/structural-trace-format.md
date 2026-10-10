# Structural Trace Format (`.nt`)

The AI-safe structural trace artifact (ADR-002): one file per test
scenario containing only the developer-authored *shape* of the
behavior — zero runtime values. This format is **cross-platform**: every
NarrativeTrace runtime emits the identical format, which is what lets
approval baselines and conformance fixtures travel between platforms.

## Files and naming (cross-platform decisions, 2026-08-24)

| File | Role |
|---|---|
| `build/narrativetrace/structural/<TestClass>/<scenario>.nt` | Emitted on the markdown path; the file on disk is the **last-green baseline** — a non-green run compares against it (console delta, failure report) but never overwrites it. "Green" is the whole verdict: a test that passed but whose structure approval *rejected* ends red, so a rejected structure never becomes the baseline and reverting the change reports no delta |
| `src/test/narratives/<TestClass>/<scenario>.approved.nt` | Committed approval baseline (`NarrativeApproval`; opt-in via `narrativetrace.approval=true`, directory configurable via `narrativetrace.approvedDir`) — a passing test whose structure differs fails with a readable diff |
| `<scenario>.received.nt` | Written beside the baseline on approval mismatch (or when no baseline exists yet); review it, then promote via the Gradle `approveNarratives` task |
| `<scenario>.incomplete.nt` | The same content, written instead of `.received.nt` when the run itself was incomplete (the best-effort path dropped events, or refused an async scope at the adoption cap). `approveNarratives` ignores it by name: a short run must never become the committed baseline, or every later complete run reads as having *added* calls. Such a run is compared by subsequence containment rather than equality — absences are tolerated and named, anything added or reordered still fails |

The format extension is last (`.approved.nt`, ApprovalTests
convention) so editors and diff viewers key off `.nt`. Note: `.nt`
collides with RDF N-Triples in some syntax-highlighting maps; register
an override in `.gitattributes` where it matters.

### Artifact identity (cross-platform, 2026-09-09)

`<scenario>` above is the **artifact identity** of one test invocation,
and every runtime spells it the same way — an artifact written by one
runtime is found under the same name by another:

- An ordinary test method is its slugged name: camel-case split on `_`,
  lowercased, everything outside `[a-z0-9_]` replaced with `_` —
  `customerPlacesOrder` → `customer_places_order`.
- One invocation of a method that runs more than once (parameterized,
  repeated) appends `-<index>-<label>`: the 1-based invocation number
  zero-padded to three digits, then the invocation's display name
  through the same slug rule with runs of `_` collapsed and the ends
  trimmed — `equipment_can_be_found-002-find_tent`. A label that slugs
  to nothing is dropped, leaving `equipment_can_be_found-002`.
- `-` is the separator precisely because the slug alphabet cannot
  produce one. The index — not the label — is what makes the scheme
  collision-proof: two invocations always differ in it, so display names
  that differ only in characters a path cannot carry (`find/TENT` versus
  `find TENT`) still get separate files. The label is what makes the
  name readable.
- The name is stable across runs, machines and processes, which is what
  lets one invocation's `.approved.nt` be committed at all. Where a name
  exceeds the 255-byte path-element limit the *method* half is truncated
  and given eight hex characters of the Java `String.hashCode` of the
  full slug — specified, therefore identical everywhere; a per-process
  hash would silently invalidate every baseline it touched.

Because artifact names are derived rather than announced, a run also
writes `<outputDir>/manifest.json`: one row per traced scenario naming
its test, its invocation number and every file it owns. Read that when
you know the scenario and want the file.

> An invocation's `scenario:` header is **not** its display name. A
> `@ParameterizedTest(name = …)` template interpolates arguments into the
> display name, so this artifact — the value-free one — is titled by the
> method and the invocation number instead: `Equipment can be found #2`.
> A method that runs once keeps the display name it always had, so no
> committed baseline moves. The *filename* still carries the slugged
> label, because that is what tells two invocations apart on disk, and
> `manifest.json` — an index over the value-carrying artifacts too —
> names the scenario as the runner displayed it. Keep secrets out of
> display-name templates.

## Content

```
scenario: Weekend trip settles with three transfers

#1 - TripSettlementService.recordExpense(tripName, expense)
  #1.1 - ExpenseValidator.ensureValid(expense)
  #1.2 - TripLedger.recordExpense(tripName, expense)
#2 - TripSettlementService.settleTrip(tripName) → value
  #2.1 - TripLedger.expensesOf(tripName) → value
  ~ fork [2]
    #2.2 - BalanceCalculator.computeBalances(expenses) → value
    #2.3 - StockService.check() → value
```

- **Header:** `scenario: <humanized test name>` + blank line. Nothing
  else — no result, no trace ids/names, no dates. One invocation of a
  method that runs more than once is `scenario: <humanized method name>
  #<index>` — a display-name template's arguments never reach it.
- **Call line:** `#id - ClassName.methodName(paramName, paramName)` —
  names only, capture order, two-space indent per depth; the span id
  is the first token after the indent.
- **Span id:** the call's position in the tree — `#1` is the first
  root call, `#1.3` its third child, `#1.3.2` that child's second
  child. Every sibling list (roots, a call's children, the work a
  fire-and-forget launched) is numbered in the order this format
  prints it: plain calls in capture order, the members of one fork or
  async group by `Class.method` (two members with the same
  `Class.method` keep capture order), and a fire-and-forget launch
  takes one position — its id opens the `~ fire-and-forget` line and
  the launched calls nest under it. Ids are derived by the renderer
  from the tree, never captured or stored, so the same flow always
  gets the same ids; an inserted call shifts its later siblings, and
  the approval diff says so (`(was #1.2)`). Every other NarrativeTrace
  flavour prints the same id for the same call (a trailing `#1.3` in
  the indented text and Markdown narratives, `(#1.3)` in prose, a note
  in the sequence diagrams), so a report, a review and an approval
  diff can all point at one span. An id carries no value.
- **Outcome kinds:** non-void return ` → value`; void: nothing (the
  Returned-null contract); thrown ` !! ExceptionSimpleName` (type is
  structure; the message is a value and never appears); unmatched
  enter ` ?? incomplete`.
- **Concurrency:** fork groups render `~ fork [n]` and work adopted from
  a propagated context snapshot (Spring `@Async`, Micrometer, any manual
  `snapshot.activate()`) renders `~ async [n]`, both with members
  **sorted by `Class.method`** — capture order across threads is the
  scheduler's choice, not behaviour, so the artifact states the set and
  nesting of concurrent work and never its order. Async groups are keyed
  by the launching span, so every async child of one call is one group,
  and they appear at root level too when the work outlived its caller.
  Fire-and-forget renders `#id ~ fire-and-forget` + the launched calls,
  laid out like any other sibling list. Thread names/ids never appear.
- **Excluded by design:** all argument/return values, exception
  messages, durations, timestamps, thread identity, captured
  trace/span ids (the W3C ones — the position ids above are derived,
  not captured),
  trace names, run ids, run names *(the run has a name too, see
  [Configuration Guide § The run has a
  name](configuration-guide.md#the-run-has-a-name); it never enters
  this format, an approved or received trace, an artifact filename, or
  a manifest per-scenario key)*, run results, and narration (resolved
  narration embeds values; the narration *template* joins when
  `nt.narrationTemplate` lands with glossary Phase 6).
- **Encoding:** UTF-8, LF, trailing newline. Identifiers pass through
  control-character sanitization.

## Identity in the JSON siblings

The `.nt` artifact carries no identity at all — that is what makes it
byte-deterministic. Its JSON siblings do: the per-test
`<test>.canonical.json` / `<test>.structural.json` entry arrays, the
chapter envelope (`chapter.schema.json`) and the chapter-tree document
(`chapter-tree.schema.json`, which the chapter embeds in
`nt.chapterTree`). All three emitters resolve identity the same way, so a
chapter can never name one trace while the tree inside it names another.
There the three identity fields are **always present**, however the
capture was made:

| Field | How it is resolved |
|---|---|
| `trace_id` | Adopted from an incoming `traceparent`, else inherited from the trace the capture ran under, else generated — always a real, unique, W3C-shaped id (32 lowercase hex, never all-zero). Never a shared constant, and never regenerated per entry. |
| `nt.storyId` | Inherited when the trace already carries one, else derived from the first root-level call as `Class.method`. Never generated. |
| `nt.chapterId` | Inherited when the trace already carries one, else equal to `nt.storyId` — this service's chapter of that story. Never generated. |

`nt.traceName` is derived from `trace_id` (the three-word phrase), so it
always agrees with it, and `service` falls back to
`unknown_service:java` when nothing supplied one. A tree whose nodes lost
their span context — one assembled by hand, replayed from an artifact or
produced by a static scan — is still exported as the one trace it is:
the identity belongs to the tree, is resolved once, and is shared by the
chapter, by the tree it embeds, and by every entry of that chapter.

The chapter-tree document's `trace` block therefore always carries
`traceId` and `traceName`. Its remaining fields — `serviceName`,
`environment`, `httpMethod`, `httpRoute` and the request-scoped
identifiers — are written only when the tree actually carried a span
context to inherit them from: a generated identity knows *which* trace
this is and nothing about who called it, and inventing a service name
would be worse than omitting one. `chapter-tree.schema.json` marks the
whole block optional, which the always-present form satisfies; the schema
is a floor, not the contract between the emitters.

Two runs of the same behaviour therefore produce identical `.nt` files
and *different* `trace_id`s. That is the intended division: fields whose
job is to group or describe are derived and stable, fields whose job is
to be unique are generated (ADR-014). A conformance comparison across
runs or across runtimes normalizes the unique ones before comparing.

## Guarantees

1. **Deterministic:** identical behavior ⇒ byte-identical file. This
   is what makes the artifact the approval trace baseline and the
   conformance-fixture golden format. Comparison sets span ids aside:
   a baseline written before ids existed still matches a current run
   of the same flow, and is never rewritten to add them.
2. **Value-free:** zero prompt-injection surface, zero PII, minimal
   tokens — safe to hand to an AI agent by default (free tier's
   Level-1 output).
3. **Division of labor:** the artifact asserts behavioral *shape*;
   value correctness remains the job of test assertions. A change
   that only alters a return value with identical structure does not
   change the artifact — by design.

Implemented in this repository by `core: StructuralTraceRenderer`, emitted by
`TraceTestSupport` beside the `.md`/`.json`/`.mmd` companions.

## The cross-platform golden

A conformance golden for this format is committed as Java source, not prose:
`narrativetrace-core`'s `StructuralGoldenConformanceTest` builds a small
FairSplit/trip-settlement scenario by hand (the same way
`StructuralTraceRendererTest` builds trees) and renders it through
`StructuralTraceRenderer`, asserting byte equality against a committed
resource, `structural-golden/negative-expense.nt`. Every other NarrativeTrace
runtime pins its own structural renderer against those identical bytes as its
own conformance fixture; the golden is generated once, here, and a change
that alters the rendered bytes fails this test first — every other runtime
then re-pins its fixture from Java, never the reverse.
