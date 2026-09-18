/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

// Every test here starts a nested Gradle build, and each nested build leaves JVMs resident: a
// test-kit Gradle daemon per distinct set of daemon parameters (512m heap + 384m metaspace apiece)
// and a Kotlin script-compiler daemon behind them, which idles for two hours by default. They do
// not run concurrently — this module's tasks and tests are sequential — they ACCUMULATE, and the
// accumulation is what the memory ceiling meets. Measured 2026-09-18 on the standalone snapshot
// verify (scripts/publish-public.sh --verify, a 5 GiB container): the peak was 4.97 GB with nine
// JVMs alive, all of it inside this module's test task, and the OOM killer took the outer Gradle
// daemon ("build daemon disappeared") twice the night before.
//
// One test-kit Gradle user home for the whole module, with its own gradle.properties capping what
// the nested daemons reserve. GradleRunner reads `org.gradle.testkit.dir`, and a Gradle user home
// reads its gradle.properties, so this reaches every nested build without a line in any test — and
// without weakening a single assertion. `clean` disposes of it with the rest of build/.
val testKitHome = layout.buildDirectory.dir("test-kit")

val testKitDaemonProperties by tasks.registering {
    description = "Caps the JVM sizes of the nested daemons GradleRunner starts"
    val propertiesFile = testKitHome.map { it.file("gradle.properties") }
    outputs.file(propertiesFile)
    doLast {
        val file = propertiesFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            // 384m: the nested builds here compile a fixture project or run one task in this repo,
            // not a 29-module check. Metaspace is the other half of a daemon's footprint and the
            // default 384m is sized for a build far larger than any of these.
            "org.gradle.jvmargs=-Xmx384m -XX:MaxMetaspaceSize=256m\n" +
                "kotlin.daemon.jvmargs=-Xmx256m\n" +
                // Nested builds are sequential and small; workers stacking is pure overhead here.
                "org.gradle.workers.max=1\n"
            // NOT org.gradle.daemon.idletimeout: shortening it to 20s was measured 2026-09-18 and
            // made the peak WORSE, not better (11 JVMs, exactly at the 5 GiB ceiling, against 9 at
            // 4.72 GB without it). Expiring a daemon mid-suite does not remove a JVM — the next
            // test starts a fresh one, and for a while both are resident. The daemons here are
            // large, not numerous by choice; what is left to win is their count, and that needs a
            // change to how these tests run, not a property.
        )
    }
}

tasks.withType<Test> {
    dependsOn(testKitDaemonProperties)
    systemProperty("org.gradle.testkit.dir", testKitHome.get().asFile.absolutePath)
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
