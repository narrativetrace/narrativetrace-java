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

extra["publishName"] = "NarrativeTrace TestNG"
extra["publishDescription"] = "TestNG listener for automatic trace output"

// The module's own suite runs on JUnit 5 and drives nested `new TestNG()` runs over TestNG
// fixtures — the narrativetrace-junit4 shape (a JUnit 5 harness over the other framework's
// fixtures). The outer runner is therefore never also a TestNG run with this listener attached, so
// a fixture's run state, output and console are the nested run's alone.
//
// What a JUnit harness cannot prove is that Gradle's OWN TestNG runner picks the listener up
// through the service loader with nothing else configured — the adopter's whole wiring. The
// `testngSample` task below runs the sample suite exactly that way, and the `test` task's
// GradleTestNgSampleTest reads what it wrote.
val testngSampleOutput = layout.buildDirectory.dir("narrativetrace-sample")
val testngSampleWorkDir = layout.buildDirectory.dir("testng-sample-workdir")
val testSourceSet = the<SourceSetContainer>()["test"]

val testngSample = tasks.register<Test>("testngSample") {
    description = "Runs the sample TestNG suite through Gradle's useTestNG(), listener by service loader only"
    group = "verification"
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useTestNG()
    filter { includeTestsMatching("ai.narrativetrace.testng.sample.*") }
    // The sample carries one deliberately failing test: the failure pointer and INDEX.md are what
    // it exists to show. Its verdict is asserted by GradleTestNgSampleTest, not by this task.
    ignoreFailures = true
    val outputDir = testngSampleOutput.get().asFile
    systemProperty("narrativetrace.outputDir", outputDir.absolutePath)
    outputs.dir(outputDir)
    workingDir = testngSampleWorkDir.get().asFile
    doFirst {
        outputDir.deleteRecursively()
        workingDir.mkdirs()
    }
}

tasks.test {
    dependsOn(testngSample)
    systemProperty("narrativetrace.testngSampleDir", testngSampleOutput.get().asFile.absolutePath)
    // Nested fixtures that fall back to NarrativeTrace's working-directory-relative defaults
    // (build/narrativetrace, src/test/narratives) land under build/, never in the source tree —
    // the narrativetrace-junit5 pattern.
    workingDir = layout.buildDirectory.dir("test-workdir").get().asFile
    doFirst { workingDir.mkdirs() }
}

dependencies {
    api(project(":narrativetrace-core"))
    api(project(":narrativetrace-proxy"))
    api(project(":narrativetrace-diagrams"))
    api(project(":narrativetrace-clarity"))
    api(project(":narrativetrace-glossary"))
    api("org.testng:testng:7.10.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
}
