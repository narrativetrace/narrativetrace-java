/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// The cross-module security suite: test-only, no `main` source set.
//
// It depends on every module that renders or emits, so a fuzz target can reach
// the whole emitter surface from one place without any production module
// gaining a dependency it does not need. That is the whole reason this module
// exists rather than the targets living in `narrativetrace-core`: the redaction
// oracle has to assert over the Mermaid renderer (diagrams), the clarity report
// (clarity, glossary), the SLF4J line (slf4j) and the OTel attributes
// (opentelemetry) at once, and core may not see any of them.
//
// It has no `main` sources, so: not published, not in `aggregateJavadoc`, not
// in the pitest set, no JaCoCo bundle to verify and nothing for JDepend to
// measure. The root build lists it in the same exclusion sets as
// `narrativetrace-build-tests`, the module this one is shaped after.

dependencies {
    // Every module that renders or emits — the whole output surface, in one classpath.
    testImplementation(project(":narrativetrace-api"))
    testImplementation(project(":narrativetrace-core"))
    testImplementation(project(":narrativetrace-proxy"))
    testImplementation(project(":narrativetrace-diagrams"))
    testImplementation(project(":narrativetrace-glossary"))
    testImplementation(project(":narrativetrace-clarity"))
    testImplementation(project(":narrativetrace-junit5"))
    testImplementation(project(":narrativetrace-slf4j"))
    testImplementation(project(":narrativetrace-opentelemetry"))
    testImplementation("io.opentelemetry:opentelemetry-api:1.62.0")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.62.0")
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("ch.qos.logback:logback-classic:1.5.38")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")

    // Synthesises interfaces whose parameter NAMES come from the corpus, so the
    // capture path can be replayed with a real signature. Java reads parameter
    // names from the class file, so a data-driven name has to be compiled, and
    // that is the only reason this is here. Test-only.
    testImplementation("org.ow2.asm:asm:9.7.1")

    // Tier A — structured fuzz, the repo's existing PBT library.
    testImplementation("net.jqwik:jqwik:1.9.2")
    // Reading the corpus fixtures back, and reading every JSON artifact back as
    // a parser would: an oracle that says "this parses" must use a real parser.
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.10")
    testImplementation("com.networknt:json-schema-validator:1.5.6")
    // The frontmatter oracle needs a real YAML parser for the same reason.
    testImplementation("org.yaml:snakeyaml:2.3")

    // Tier B — coverage-guided fuzz. In `check` this replays the committed
    // corpus (regression mode, seconds); `./gradlew fuzz` sets JAZZER_FUZZ=1.
    testImplementation("com.code-intelligence:jazzer-junit:0.30.0")
}

tasks.withType<Test>().configureEach {
    // The repo root, so the suite can read the canonical schema where it lives
    // (`narrativetrace-core/src/test/resources/schema/`) instead of keeping a
    // copy that can drift. Same mechanism `narrativetrace-build-tests` uses.
    systemProperty("projectDir", rootProject.projectDir.absolutePath)
}

// Tier B on demand. Jazzer reads JAZZER_FUZZ from the environment; the per-target
// time budget lives in exactly one place, `FuzzBudget.PER_TARGET`, which the
// `@FuzzTest(maxDuration = ...)` annotations reference.
//
// ONE TASK PER TARGET, and the reason is not tidiness. Jazzer's JUnit engine fuzzes at
// most one `@FuzzTest` per JVM: the first one it reaches runs for its budget, and every
// other one is abandoned with "Only a single fuzz test should be executed per fuzzing
// run" — recorded as a skip, which Gradle reports as a green test task. For as long as
// `fuzz` was a single task over `ai.narrativetrace.security.fuzz.*`, it fuzzed
// OutputFormatFuzzTest for five minutes and skipped the other three, and said
// BUILD SUCCESSFUL. A 2026-09-01 bug hunt found it by reading the XML nobody reads.
//
// The aggregate below reads that XML on every run, so the failure mode cannot come back
// quietly: silence must not look like coverage.
val fuzzTargets = mapOf(
    "OutputFormat" to "ai.narrativetrace.security.fuzz.OutputFormatFuzzTest",
    "Template" to "ai.narrativetrace.security.fuzz.TemplateFuzzTest",
    "Traceparent" to "ai.narrativetrace.security.fuzz.TraceparentFuzzTest",
    "ValueRenderer" to "ai.narrativetrace.security.fuzz.ValueRendererFuzzTest",
)

val fuzzTargetTasks = fuzzTargets.map { (target, testClass) ->
    tasks.register<Test>("fuzz$target") {
        description = "Coverage-guided fuzzing (Jazzer) of $testClass"
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        filter { includeTestsMatching(testClass) }
        environment("JAZZER_FUZZ", "1")
        // Without this, Jazzer derives its instrumentation filter from the module's own
        // classes — and this module has none, so coverage guidance would see only the
        // fuzz targets and none of the code under test. Naming the whole library is what
        // makes this tier coverage-guided rather than a slow random sweep.
        systemProperty("jazzer.instrumentation_includes", "ai.narrativetrace.**")
        systemProperty("jazzer.custom_hook_includes", "ai.narrativetrace.**")
        maxHeapSize = "1g"
        // Jazzer refuses to fuzz when a coverage agent is on the command line
        // (it would fight over the same instrumentation), so the JaCoCo agent is
        // off here. `check` still measures coverage on the ordinary `test` run.
        extensions.configure<JacocoTaskExtension> { isEnabled = false }
        // Jazzer writes a reproducer beside the working directory when it finds a crash,
        // and its generated corpus under `.cifuzz-corpus`. Pointing the working directory
        // into `build/` keeps both out of the source tree and makes one artifact path
        // ("everything a fuzz run produced") enough for CI. One directory per target, so a
        // reproducer names the surface that produced it and two targets never share a
        // generated corpus.
        workingDir = layout.buildDirectory.dir("fuzz/$target").get().asFile
        doFirst { workingDir.mkdirs() }
        testLogging { showStandardStreams = true }
        outputs.upToDateWhen { false }
    }
}

// Deterministic order rather than whatever the scheduler picks: four targets fuzzing at
// once would divide one machine's cores by four and quietly change what each budget buys.
fuzzTargetTasks.zipWithNext { earlier, later -> later.configure { mustRunAfter(earlier) } }

tasks.register("fuzz") {
    description = "Coverage-guided fuzzing (Jazzer) over every security target, then verifies it happened"
    group = "verification"
    dependsOn(fuzzTargetTasks)
    doLast {
        val reports = fuzzTargets.map { (target, testClass) ->
            ai.narrativetrace.build.FuzzReportSupport.read(
                target,
                testClass,
                layout.buildDirectory.dir("test-results/fuzz$target").get().asFile,
            )
        }
        println("fuzz: ${fuzzTargets.size} targets, each for FuzzBudget.PER_TARGET")
        reports.forEach { println(ai.narrativetrace.build.FuzzReportSupport.line(it)) }
        val problems = ai.narrativetrace.build.FuzzReportSupport.problems(reports)
        if (problems.isNotEmpty()) {
            throw GradleException(
                "fuzz: a target did not fuzz — a green fuzz run must mean every surface was fuzzed:\n" +
                    problems.joinToString("\n") { "  - $it" }
            )
        }
    }
}
