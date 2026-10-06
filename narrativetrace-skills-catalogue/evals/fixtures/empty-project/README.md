# empty-project

A cold-install starting point for Tier B's `add-narrative-tracing` happy-path case and for the
published init prompt's console-app branch (`init-prompt-empty-project`): nothing installed yet,
no NarrativeTrace dependency, no wrapped call. Scaffolded into a scratch copy for each trial;
never built or run by this repository's own Gradle (it is not `include()`d in
`settings.gradle.kts`).

The service is the pair the published pages list — `interface OrderService` in
`com.example.orders` and `class DefaultOrderService` beside it, the same package, signature and
body `documentation/llms.txt`'s "Install and first trace" block and
[sixty-seconds.md](../../../../documentation/sixty-seconds.md#2-the-program) show. That is not
decoration: `NarrativeTraceProxy` wraps an INTERFACE, so a fixture whose only service is a
concrete class sends an agent off to extract one and name it itself, and the name it picks is not
the name the grader looks for. Shipping the published shape leaves the faithful path with no
refactor to invent, and leaves `run_the_program.sh`'s near-miss guard its full protection.
