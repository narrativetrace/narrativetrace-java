# What to commit

NarrativeTrace writes two kinds of file: generated artifacts that describe
one run, and reviewed baselines that describe an intended contract. Commit
the second kind, not the first.

| Artifact | Commit? | Why |
|---|---|---|
| `build/narrativetrace/traces/*.md` | No | Regenerated every run; usually a CI artifact, not source |
| `build/narrativetrace/traces/*.json` | No | Same trace as canonical JSON — regenerated every run |
| `build/narrativetrace/diagrams/*.mmd` | No | Regenerated every run |
| `build/narrativetrace/structural/*.nt` | No | The last-green *local* baseline the console delta and failure reports compare against — not the approval baseline (see below) |
| `build/narrativetrace/clarity-report.md` | No | A generated report, not a decision — `clarityCheck` reads `clarity-results.json` next to it, also generated |
| `src/test/narratives/<Class>/<scenario>.approved.nt` | **Yes** | The reviewed approval baseline (only exists if [approval mode](structural-trace-format.md) is on). This is the one file in the list that is a deliberate decision, not output |
| `src/test/narratives/<Class>/<scenario>.received.nt` | No | Written on an approval mismatch, or when no baseline exists yet. Review it, run `./gradlew approveNarratives` to promote it, then delete or let the task remove it — never commit the received file itself |
| `src/test/narratives/<Class>/<scenario>.incomplete.nt` | No | Written instead of `.received.nt` when the run itself was incomplete (best-effort loss, or a refused async scope). `approveNarratives` ignores it by name on purpose — see [Structural Trace Format](structural-trace-format.md) |
| `glossary.json` / `glossary.md` | **Yes**, if glossary harvesting is used | Committed at the repository root by `glossaryScan` / `glossary.set(true)`; the committed file is what clarity scoring and vocabulary checks read back. "One file, one review workflow" |

Everything under `build/` is already covered by the shipped `.gitignore`
(`build/` is the first line). `src/test/narratives/` is not — `.approved.nt`
files there are meant to be tracked, but a `.received.nt` sitting beside one
is not automatically excluded. If your team is not disciplined about
deleting a reviewed `.received.nt` before committing, add an explicit
ignore rule for it:

```gitignore
src/test/narratives/**/*.received.nt
src/test/narratives/**/*.incomplete.nt
```

## The rule in one sentence

If a file only exists because a test ran, it is output — do not commit it.
If a file exists because a human reviewed and accepted it, it is a
baseline — commit it, and expect its diffs to be read in code review the
same way a snapshot test's diff would be.

## Approval baselines are value-free by construction

A `.approved.nt` file never contains parameter or return values (ADR-002) —
only call structure, names, and outcome kinds. That is what makes it safe to
commit and stable across runs: a return value changing without a structural
change never touches the baseline, and reviewing a diff never means reading
runtime data in a pull request. See
[Privacy and Redaction](privacy-and-redaction.md) for the rest of what
NarrativeTrace does and does not put in a file your team will share.

## Approval mode, end to end

```text
test passes
   |
   v
compare current structure to approved baseline
   |
   +-- same      --> pass, nothing written
   +-- different --> write .received.nt and fail
                     |
                     v
                human reviews the diff
                     |
                     v
              ./gradlew approveNarratives
                     |
                     v
              .approved.nt updated, commit it
```

The failing state is deliberate: a passing test whose *shape* changed —
including a change an AI agent slipped into an otherwise-correct refactor —
has to be looked at and explicitly approved, not merely compile. Nothing is
silently accepted, and nothing is silently lost: an incomplete run writes
`.incomplete.nt` instead and is compared by subsequence containment rather
than equality, so a short run can never become the committed baseline.
