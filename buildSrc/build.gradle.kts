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

// Nested-build output isolation, buildSrc half. The root build.gradle.kts's own block
// relocates every subproject's build directory under -Pnarrativetrace.buildRoot; buildSrc is a
// separate build entirely (no settings.gradle.kts, not a subproject), so that `allprojects`
// block never reaches it. Gradle builds buildSrc with the same StartParameter as the build that
// included it, so a -P given to the outer invocation is visible here too. Without this half, a
// nested GradleRunner build still recompiles buildSrc under THIS checkout's buildSrc/build even
// once every subproject is isolated — and narrativetrace-build-tests loads buildSrc's classes
// directly off disk (see its buildSrcClasspath system property), so the two builds race over
// exactly those files (B-58).
val isolatedBuildRoot = (findProperty("narrativetrace.buildRoot") as String?)?.takeIf { it.isNotBlank() }
if (isolatedBuildRoot != null) {
    require(File(isolatedBuildRoot).isAbsolute) {
        "narrativetrace.buildRoot must be an absolute directory: $isolatedBuildRoot"
    }
    layout.buildDirectory.set(file("$isolatedBuildRoot/buildSrc/build"))
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
    // Reads documentation/contract.yaml for contractLint (per-commit schema/anchor validation) —
    // a plain library dependency, same "library, not a plugin" idiom as jdepend/pmd-java above.
    implementation("org.yaml:snakeyaml:2.2")
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
//
// Opt-out for tooling that only needs the build's configuration, never its verdict:
// `-Pnarrativetrace.skipBuildSrcTests` (scripts/verify-publication.sh sets it to list the
// published coordinates). buildSrc's tasks cannot be named with `-x` from the root command line,
// so the property is the only handle; the default build, CI and every gate never set it.
if (!providers.gradleProperty("narrativetrace.skipBuildSrcTests").isPresent) {
    tasks.named("jar") {
        finalizedBy(tasks.test)
    }
}
