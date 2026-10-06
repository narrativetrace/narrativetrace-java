# redaction-gap

A deviation fixture for Tier B's `narrativetrace-doctor` case of the same name: NarrativeTrace is
already wired in (`narrativetrace-proxy` is a real declared dependency, so `trap.redaction-proof`
is in scope), the traced method takes a sensitive-looking parameter (`authToken`), and — the gap —
no test file anywhere asserts the literal `"[REDACTED]"`. `trap.redaction-proof` fails on this
fixture by construction; every other doctor finding holds. Scaffolded into a scratch copy for each
trial; never built or run by this repository's own Gradle (it is not `include()`d in
`settings.gradle.kts`).
