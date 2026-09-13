/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// contract-probe: a small, STANDALONE Gradle project (docs-vs-published-gate §2/§5.1 ruling 2) —
// it is not `include()`d by the root build's settings.gradle.kts, has its own Gradle wrapper, and
// consumes ONLY registry artifacts at a version given on the command line (`-PcontractVersion=`),
// never `mavenLocal()`, never `project(...)`, never a `file:` repository. That is the whole point:
// a claim documentation/contract.yaml makes is proved or disproved against what a real consumer
// would actually resolve, never against this checkout's own build output.
//
// Run directly (see documentation/contract-gate.md, or scripts/contract-check.sh for the
// fresh-temp-dir nightly wrapper):
//   ./gradlew runContract -PcontractVersion=0.2.1 \
//       -PcontractYaml=../documentation/contract.yaml -Pout=build/contract-result.json

plugins {
    application
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// troubleshooting.md "Parameters show as arg0, arg1": every consumer's manual setup needs this
// flag for NarrativeTrace to read real parameter names at all (the Gradle plugin adds it
// automatically, but contract-probe deliberately never applies its own plugin under test). Without
// it, name-based redaction (probed-parameter-name-redaction) cannot match "password" by name and
// this probe would report a false FAILS that is really "this harness forgot a compiler flag", not
// a defect in the published artifact.
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenCentral()
}

val contractVersion: String = (findProperty("contractVersion") as String?)
    ?: error("pass -PcontractVersion=<published version>, e.g. 0.2.1")

dependencies {
    // The published artifacts contract-probe's own probe classes call into — exactly the
    // coordinates a consumer following the docs would add (installation-guide.md #2).
    implementation("ai.narrativetrace:narrativetrace-core:$contractVersion")
    implementation("ai.narrativetrace:narrativetrace-proxy:$contractVersion")
    implementation("ai.narrativetrace:narrativetrace-junit5:$contractVersion")
    implementation("ai.narrativetrace:narrativetrace-slf4j:$contractVersion")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    implementation("org.junit.platform:junit-platform-launcher:1.11.4")
    implementation("ch.qos.logback:logback-classic:1.5.38")
    // Reads documentation/contract.yaml — the same library buildSrc's `contractLint` uses, so the
    // two never parse the schema two different ways.
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

application {
    mainClass.set("ai.narrativetrace.contract.ContractRunner")
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Runs every applicable contract.yaml entry against the published `contractVersion` and writes
 * the JSON result `scripts/contract-check.sh` reads. Exits non-zero (via `ContractRunner.main`'s
 * own `System.exit`) on any FAILS verdict — Gradle surfaces that as this task failing.
 */
tasks.register<JavaExec>("runContract") {
    group = "verification"
    description = "Proves or disproves every documentation/contract.yaml claim against a published version"
    mainClass.set("ai.narrativetrace.contract.ContractRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOfNotNull(
        "--version=$contractVersion",
        "--contract=${(findProperty("contractYaml") as String?) ?: "../documentation/contract.yaml"}",
        (findProperty("out") as String?)?.let { "--out=$it" },
        (findProperty("registryBase") as String?)?.let { "--registry-base=$it" }
    )
}
