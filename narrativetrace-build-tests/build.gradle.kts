/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    // Every test here starts a nested Gradle build; the convention sizes those nested daemons and
    // ends them with the build that started them, instead of letting them accumulate.
    id("narrativetrace-nested-gradle-builds")
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.withType<Test> {
    systemProperty("projectDir", rootProject.projectDir.absolutePath)
    // JarLicensePackagingTest reads real archives rather than trusting the build script that
    // produced them, so the archives have to exist: one `open` module and one `free` one, the two
    // branches `narrativetrace-license-packaging` chooses between.
    // SkillsCarrierJarTest does the same for the carrier: the resource-only narrativetrace-skills
    // jar and the narrativetrace-cli jar that bundles the identical resources (Phase 2 D4).
    dependsOn(
        ":narrativetrace-api:jar",
        ":narrativetrace-core:jar",
        ":narrativetrace-skills:jar",
        ":narrativetrace-cli:jar"
    )
    systemProperty("narrativetrace.buildVersion", project.version.toString())
    // `projectDir` is a system PROPERTY, not a task input, so a test that reads a file through it
    // stays UP-TO-DATE when only that file changes. That hole is exactly how SkillsCarrierJarTest's
    // hard-coded seven-entry expectation stayed green through a whole `./gradlew check` after a
    // FOURTH skill shipped: the expectation was stale, the jar on disk was stale too, and the test
    // never re-ran to disagree with either. Its expectation is derived from the rendered layouts
    // now, so those layouts are declared here — the same fix narrativetrace-tooling's own test task
    // carries, for the same three stale greens.
    for (layout in listOf(".agents", ".claude")) {
        inputs
            .dir(rootProject.file("$layout/skills"))
            .withPropertyName("renderedSkills${layout.removePrefix(".")}")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
    // TaskInputInvalidationTest drives buildSrc's typed tasks in a throwaway fixture build, which
    // needs those classes (and what they call) on ITS build-script classpath. Taken from the
    // classes as loaded here — buildSrc's jar and its runtime dependencies are on every build
    // script's classpath — rather than from a path someone would have to keep in step.
    systemProperty(
        "buildSrcClasspath",
        listOf(
            ai.narrativetrace.build.JDependReportTask::class.java,
            jdepend.framework.JDepend::class.java,
            kotlin.Unit::class.java,
        ).joinToString(File.pathSeparator) { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }
    )
}

// The scheduled-job half of this module's suite: build tests that drive a real mutation run nested
// (PitestFixtureOutputLocationTest). Excluded from `test` — and so from `check` — for cost, the way
// `perfTest` is; the GitLab `mutation` job runs this beside `:pitest`, on the same cadence as the
// invocation it guards. Release rule 2: a guard that only ever skips is no guard, so this task
// fails rather than passes when the tag matches nothing.
val buildTestSourceSet = extensions.getByType<SourceSetContainer>()["test"]
tasks.register<Test>("mutationBuildTest") {
    description = "Runs build tests that drive a real PIT run (tagged @Tag(\"mutation\"))"
    group = "verification"
    useJUnitPlatform {
        includeTags("mutation")
    }
    testClassesDirs = buildTestSourceSet.output.classesDirs
    classpath = buildTestSourceSet.runtimeClasspath
    filter.isFailOnNoMatchingTests = true
}
