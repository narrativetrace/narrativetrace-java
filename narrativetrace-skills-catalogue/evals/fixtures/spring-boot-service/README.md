# spring-boot-service

The init prompt on the runtime's main web framework (Phase 6, D4): an existing Spring Boot
application with one `@Service` behind an interface (`AccountService` and
`DefaultAccountService`), one `@RestController` (`GET /accounts/{accountId}`) and a passing test,
and no NarrativeTrace anywhere — no dependency, no plugin, no wrapped call, no `@EnableNarrativeTrace`.
The doctor's `config.spring-enabled` and `config.spring-web-filter` fixes are what the faithful path
applies, and the trace the grader reads is the one a request to the endpoint produces.

Spring Boot itself resolves the usual way (`mavenCentral()`, the plugin portal); NarrativeTrace
resolves from the local test repository the eval runner points `narrativetraceTestMavenRepo` at,
exactly as `feedback-value-free` does, so a trial measures this checkout's doctor and libraries.
Scaffolded into a scratch copy for each trial; never built or run by this repository's own Gradle
(it is not `include()`d in `settings.gradle.kts`).
