/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("info.solidsoft.gradle.pitest:gradle-pitest-plugin:1.19.0")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.2")
    implementation("jdepend:jdepend:2.9.1")
    // CPD (Copy/Paste Detector) ships inside PMD's own distribution — no separate plugin.
    // `pmd-java` pulls `pmd-core` transitively, giving buildSrc the `net.sourceforge.pmd.cpd`
    // API (CpdAnalysis/CPDConfiguration) directly, the same way jdepend above is used as a
    // plain library rather than through a Gradle plugin. Pinned to the same version the
    // `pmd` extension below configures for every subproject (see `toolVersion` there) so the
    // duplication report and the PMD lint gate always analyse with one PMD release.
    implementation("net.sourceforge.pmd:pmd-java:7.8.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.1.10-1.0.29")
    // OSV-Scanner needs a manifest of this project's actual resolved dependency
    // graph, not a Gradle lockfile this repo doesn't otherwise want — an
    // aggregated CycloneDX SBOM is the documented way to get one for a
    // multi-module Gradle build. See documentation/security-tooling.md.
    implementation("org.cyclonedx:cyclonedx-gradle-plugin:3.4.1")
    // FindSecBugs rides on the SpotBugs Gradle plugin (beside the existing
    // PMD gates, offline-capable, wired into `check`).
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:6.5.11")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // Declared rather than auto-loaded: Gradle 9 removes the automatic loading of test framework
    // implementation dependencies, and without this the buildSrc suite warns on every run that
    // actually executes it.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Gradle 8 no longer runs buildSrc tests as part of the main build, leaving
// them green-but-silent in CI. A finalizer (not dependsOn — test compilation
// needs the jar, so a dependency edge is circular) makes every build that
// produces the buildSrc jar also run its tests; both stay up-to-date when
// buildSrc is unchanged, so the steady-state cost is zero.
tasks.named("jar") {
    finalizedBy(tasks.test)
}
