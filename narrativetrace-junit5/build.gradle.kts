/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    id("narrativetrace-publish")
}

extra["publishName"] = "NarrativeTrace JUnit 5"
extra["publishDescription"] = "JUnit 5 extension for automatic trace output"

tasks.test {
    exclude("**/FailingTestFixture.class")
    // The suite's own fixtures (IoErrorFixture, MultiTestFixture, ...) carry real @Test methods
    // and can be selected directly (`--tests`, an IDE run) outside the runSuite() harness that
    // otherwise always points narrativetrace.outputDir at a @TempDir. A directly-run fixture then
    // falls back to NarrativeTrace's own default output location, which is relative to the JVM's
    // working directory — pointing that at build/ keeps a stray default trace, manifest, or
    // clarity report out of the source tree. Same pattern as narrativetrace-jcstress and
    // narrativetrace-security-tests.
    workingDir = layout.buildDirectory.dir("test-workdir").get().asFile
    doFirst { workingDir.mkdirs() }
}

// PIT's own processes (the coverage-analysis "main" JVM and the per-mutant "minion" JVMs) run this
// module's fixtures (IoErrorFixture and friends) directly, outside `tasks.test`'s workingDir
// control above, and a `:pitest` run wrote `manifest.json`, `clarity-report.md`,
// `clarity-results.json` and `traces/` straight into this module's source root — untracked,
// un-ignored, removable only by hand (nightly F1, 2026-09-17; still reproducing 2026-09-18).
//
// A `-Dnarrativetrace.outputDir` pin cannot fix this, and the 2026-09-17 attempt at one did not:
// PIT mutates the very lookup that reads it. `NarrativeTraceExtension.configParam` returns a
// String, so PIT's EmptyObjectReturnValsMutator produces a mutant that returns "" for EVERY
// configuration key — outputDir included — and the fixture that minion runs resolves `Path.of("")`
// against the process working directory. Audited stack, 2026-09-18:
//   TraceFileWriter.write(file=[manifest.json], cwd=.../narrativetrace-junit5)
//     <- ScenarioManifest.write <- NarrativeTraceExtension$GlobalTraceAccumulator.close
// No property survives a mutant of its own reader; the process working directory does. Pointing
// PIT's processes at a directory under `build/` is therefore the only guarantee available here —
// every working-directory-relative default a mutant falls back to (`build/narrativetrace`,
// `src/test/narratives`, the glossary's `user.dir`) then lands inside the build directory too.
// The minions inherit it: PIT forks them without a directory of their own.
//
// -Xmx384m on both process kinds keeps a full run's four concurrent minions inside a
// shared-runner-sized box; PitestFixtureOutputLocationTest (narrativetrace-build-tests, @Tag
// "mutation") runs this exact unscoped invocation nested and asserts the tree stays clean.
val pitestWorkingDir = layout.buildDirectory.dir("pitest-workdir")

configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    jvmArgs = listOf("-Xmx384m")
    mainProcessJvmArgs = listOf("-Xmx384m")
}

tasks.named<JavaExec>("pitest") {
    workingDir = pitestWorkingDir.get().asFile
    doFirst { workingDir.mkdirs() }
}

// The build answering for itself, rather than a regex over this file: the cheap half of
// PitestFixtureOutputLocationTest asserts on this in every `check`, while the nested real
// mutation run that proves the behaviour is tagged for the scheduled job.
tasks.register("printPitestWorkingDir") {
    description = "Prints the working directory PIT's own processes run in."
    group = "help"
    val dir = pitestWorkingDir.map { it.asFile.absolutePath }
    doLast { println(dir.get()) }
}

dependencies {
    api(project(":narrativetrace-core"))
    api(project(":narrativetrace-proxy"))
    api(project(":narrativetrace-diagrams"))
    api(project(":narrativetrace-clarity"))
    api(project(":narrativetrace-glossary"))
    api("org.junit.jupiter:junit-jupiter-api:5.11.4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
