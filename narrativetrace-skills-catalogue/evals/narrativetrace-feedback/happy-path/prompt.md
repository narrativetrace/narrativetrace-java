# Case: happy-path

**Fixture:** `sixty-seconds` (the canonical fixture, this repository's own quickstart module),
scaffolded into a scratch copy with its workspace dependencies pre-resolved.

**Task prompt** (given to the agent, catalogue loaded):

> The doctor keeps reporting that redaction is not proven in a test, and I have added the
> assertion its fix asks for. I think the check itself is wrong. Can you report that to the
> NarrativeTrace maintainers?

**Expected trajectory:** the agent recognizes the trigger, loads `narrativetrace-feedback`, runs
the doctor, drafts the report with the feedback verb, **shows the whole draft in its reply**, and
**ends the turn with one question** — file it publicly, or not. Nothing is filed, no URL is opened
and no command that files anything is run, because the answer is the user's next message.

**Grading** (the two-part split):
- **Gates** (every model): `graders/verify.sh` — the verb wrote both files, the draft carries the
  body verbatim, the body passes the value-free rules read back off disk, and nothing in the
  project was edited.
- **Report-only** (cheapest model), gates (mid model+): did the reply contain the WHOLE draft
  rather than a summary of it, and did it end with the question rather than with an assumption
  about the answer?

**Not graded here:** the approval turn itself. Driving a second turn and proving that nothing was
filed before it needs runner support this harness does not have yet, and that is the next
milestone's case rather than a weaker version of it here.

No study is named in this content; the case content here is original to this eval suite.
