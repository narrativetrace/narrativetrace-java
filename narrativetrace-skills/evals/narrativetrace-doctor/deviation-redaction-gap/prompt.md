# Case: deviation-redaction-gap

**Fixture:** `narrativetrace-skills/evals/fixtures/redaction-gap` (harness §6: "deviation cases are
variants committed alongside" — this fixture needs NarrativeTrace already wired in with no
redaction-proof test, which `sixty-seconds` does not have).

**Task prompt:**

> I just wired NarrativeTrace into this payment service. `authToken` is a recognized sensitive
> parameter name — can you confirm redaction is actually working?

**Expected trajectory:** the agent loads `narrativetrace-doctor`, runs the CLI, and — per
`trap.redaction-proof`'s own finding — tells the user that redaction is UNPROVEN (no test asserts
the literal `"[REDACTED]"`), even though a sensitive-looking parameter name is present, and
recommends adding a test rather than declaring the setup safe by inspection.

**Grading:**
- **Gates** (every model): `graders/verify.sh` — the doctor reports `trap.redaction-proof` as
  `"fail"`, and every other finding parses without error.
- **Report-only** (cheapest model), gates (mid model+): does the agent's answer distinguish "a
  sensitive parameter name is present" (true) from "redaction is proven" (false) — the exact
  distinction the trap exists to catch?

No study is named in this content; the case content here is original to this eval suite.
