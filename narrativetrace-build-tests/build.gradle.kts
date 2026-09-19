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
    dependsOn(":narrativetrace-api:jar", ":narrativetrace-core:jar")
    systemProperty("narrativetrace.buildVersion", project.version.toString())
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
