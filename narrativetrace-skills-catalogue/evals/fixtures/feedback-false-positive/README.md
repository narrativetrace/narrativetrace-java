# feedback-false-positive

A project that is configured correctly and that the doctor reports a finding about anyway — the
fixture for Tier B's `narrativetrace-feedback` approval cases, where the task is to REPORT a
finding rather than to work around it.

**The false positive is genuine, not seeded.** `PaymentServiceRedactionTest` passes and really does
prove redaction: it renders a call with the deny-listed parameter `authToken`, asserts the marker is
present and the token is gone, and asserts the neighbouring `C-1234` survived. It asserts the marker
through the library's own public constant `RedactionPolicy.MARKER` instead of retyping its text —
the ordinary reason to reference a constant, so the value a renderer writes and the value a test
expects cannot drift apart. `RedactionProofCheck` greps test sources for the LITERAL `"[REDACTED]"`,
quotes included, so it reports *"No test file asserts the literal"* about a project that asserts it.

That matters for the case rather than being trivia: the prompt tells the agent the doctor is wrong
and the project is fine, and an agent that reads the project finds that TRUE. A seeded lie would
have graded whether the agent believes the user.

Everything else about the project holds, deliberately — including the `-parameters` compiler flag,
without which `trap.llms-before-you-start` fails and the project would not be "configured
correctly" at all. The one other finding a trial sees is `config.skills-installed`, a known artifact
of every Tier B case: the pages the harness copies in carry no provenance line, so the installer's
own check says "is there, not ours". These graders stay off it, as the other cases' do.

`settings.gradle.kts` resolves the plugin and the libraries from outside the tree, because a fixture
scaffolded into a temp directory has nothing relative to it that can find a checkout: first the
local Maven repository `./gradlew publishSkillsTestRepo` fills, named by the project property
`narrativetraceTestMavenRepo` (the runner passes it as
`ORG_GRADLE_PROJECT_narrativetraceTestMavenRepo`, the only way a property reaches a `./gradlew` line
the AGENT types); otherwise a composite `includeBuild` of `NARRATIVETRACE_TEST_REPO`.

Scaffolded into a scratch copy for each trial; never built or run by this repository's own Gradle
(it is not `include()`d in `settings.gradle.kts`).
