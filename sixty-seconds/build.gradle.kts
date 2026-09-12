/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// "sixty-seconds" IS documentation/sixty-seconds.md, "See a trace in 60 seconds": every
// src/main/java file below is embedded verbatim into the page (snippetCheck/snippetSync, root
// build.gradle.kts, next to translationCheck) and its one test proves the call the page shows
// actually produces the narrative the page claims — rule 8 (docs as tests).
//
// This build file itself is deliberately NOT one of the embedded blocks: the page's own
// build.gradle.kts block targets a standalone consumer (Maven Central coordinates), while this
// module has to depend on the in-tree modules by source (`project(...)`) so a change here is
// caught the moment this module rebuilds, never against a possibly-stale published jar. Proving
// the page's published-coordinate block is the cold walk's job (layer 2, not yet built), not this
// module's.
dependencies {
    implementation(project(":narrativetrace-core"))
    implementation(project(":narrativetrace-proxy"))

    testImplementation(project(":narrativetrace-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")

    // "Send it to your logger" postscript: narrativetrace-slf4j attaches itself to the pipeline
    // reflectively once it is on the classpath (documentation/configuration-guide.md §7). Kept out
    // of `implementation`/`runtimeOnly` here so the base `./gradlew run` — the page's "3. Run it"
    // output block — never carries it; testRuntimeOnly only routes this module's own test logging.
    // `runWithLogger` below is the second run mode that keeps the postscript's own output true.
    testRuntimeOnly(project(":narrativetrace-slf4j"))
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.38")
}

plugins {
    application
}

application {
    mainClass.set("com.example.orders.Main")
}

// Resolvable-only classpath for the postscript's second run mode: everything `run` already has,
// plus the two dependencies the page's diff block adds. src/main/resources/logback.xml (already on
// every classpath, including `run`'s) is inert without these two jars, so the base run stays
// exactly what "3. Run it" shows.
val loggerRuntime: Configuration by configurations.creating {
    extendsFrom(configurations["runtimeClasspath"])
    isCanBeConsumed = false
}

dependencies {
    add(loggerRuntime.name, project(":narrativetrace-slf4j"))
    add(loggerRuntime.name, "ch.qos.logback:logback-classic:1.5.38")
}

tasks.register<JavaExec>("runWithLogger") {
    group = "application"
    description =
        "Runs Main via the SLF4J/Logback-wired classpath " +
            "(sixty-seconds.md \"Send it to your logger\")"
    mainClass.set("com.example.orders.Main")
    classpath = sourceSets["main"].output + loggerRuntime
}
