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

extra["publishName"] = "NarrativeTrace SLF4J"
extra["publishDescription"] = "SLF4J bridge with MDC propagation"

dependencies {
    implementation(project(":narrativetrace-core"))
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("net.jqwik:jqwik:1.9.2")
    testImplementation("ch.qos.logback:logback-classic:1.5.38")
    testImplementation(testFixtures(project(":narrativetrace-core")))
    testImplementation("com.github.noconnor:junitperf-junit5:1.37.0")
}

// The listener must narrate on slf4j-api 1.7 as well as 2.x. `slf4j1Test` is a dedicated
// source set whose RUNTIME classpath carries slf4j-api 1.7.36 + slf4j-simple 1.7.36 instead of the
// 2.0.16 the module compiles against — slf4j-api is `compileOnly` here so 2.0.16 never reaches that
// runtime classpath, while `slf4j1TestImplementation` (narrativetrace-core, JUnit, AssertJ) reaches
// both, none of them pulling slf4j transitively (core is a zero-dep `api` wrapper over
// narrativetrace-api, which itself has zero dependencies).
val slf4j1Test: SourceSet =
    sourceSets.create("slf4j1Test") {
        java.srcDir("src/slf4j1Test/java")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

dependencies {
    "slf4j1TestCompileOnly"("org.slf4j:slf4j-api:2.0.16")
    "slf4j1TestImplementation"(project(":narrativetrace-core"))
    "slf4j1TestImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
    "slf4j1TestImplementation"("org.assertj:assertj-core:3.27.7")
    "slf4j1TestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "slf4j1TestRuntimeOnly"("org.slf4j:slf4j-api:1.7.36")
    "slf4j1TestRuntimeOnly"("org.slf4j:slf4j-simple:1.7.36")
}

val slf4j1TestLogFile = layout.buildDirectory.file("slf4j1-test/narration.log")

val slf4j1CompatTest =
    tasks.register<Test>("slf4j1CompatTest") {
        description =
            "Runs Slf4jTraceEventListener against slf4j-api 1.7.36 + slf4j-simple 1.7.36 " +
                "on the runtime classpath"
        group = "verification"
        testClassesDirs = slf4j1Test.output.classesDirs
        classpath = slf4j1Test.runtimeClasspath
        useJUnitPlatform()
        maxParallelForks = 1
        doFirst { slf4j1TestLogFile.get().asFile.parentFile.mkdirs() }
        systemProperty("org.slf4j.simpleLogger.logFile", slf4j1TestLogFile.get().asFile.absolutePath)
        systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace")
    }

tasks.named("check") { dependsOn(slf4j1CompatTest) }
