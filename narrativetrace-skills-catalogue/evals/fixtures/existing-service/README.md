# existing-service

The init prompt's OTHER branch: a project that already exists. A small Gradle build with one
real service boundary (`InvoiceService` and its implementation) and a passing test, and no
NarrativeTrace anywhere — no dependency, no plugin, no wrapped call. The prompt's step 2 says
"work inside the existing project and trace one real service boundary", and this is the
project it has to work inside. Scaffolded into a scratch copy for each trial; never built or
run by this repository's own Gradle (it is not `include()`d in `settings.gradle.kts`).
