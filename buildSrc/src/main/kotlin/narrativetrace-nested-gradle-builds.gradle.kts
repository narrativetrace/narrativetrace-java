/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// Applied by every module whose tests start nested Gradle builds through GradleTestKit
// (narrativetrace-build-tests, narrativetrace-gradle-plugin). Each nested build leaves JVMs
// resident: a Gradle daemon per distinct set of daemon parameters, and a Kotlin compile daemon
// behind it, which idles for two hours by default. They do not run concurrently — these tasks and
// tests are sequential — they ACCUMULATE, and the accumulation is what a memory ceiling meets.
//
// Measured 2026-09-18 on the standalone snapshot verify (a 5 GiB container): the peak was 4.98 GB
// with eight JVMs alive — the outer build, TWO nested test-kit daemons and THREE Kotlin compile
// daemons — and the OOM killer took the outer Gradle process ("build daemon disappeared").
//
// One test-kit Gradle user home per module, with its own gradle.properties telling every nested
// build how to size itself. GradleRunner reads `org.gradle.testkit.dir`, and a Gradle user home
// reads its gradle.properties, so this reaches every nested build without a line in any test — and
// without weakening a single assertion. `clean` disposes of it with the rest of build/.

val testKitHome = layout.buildDirectory.dir("test-kit")

val testKitDaemonProperties by tasks.registering {
    description = "Sizes the nested daemons GradleRunner starts, and holds them to one"
    group = "build setup"
    val propertiesFile = testKitHome.map { it.file("gradle.properties") }
    outputs.file(propertiesFile)
    doLast {
        val file = propertiesFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            // Well under a daemon's defaults (512m heap, 384m metaspace): the nested builds compile
            // a fixture project or run one task in this repo, not a 29-module check. Not smaller
            // than this, though — a nested build that compiles the Kotlin example module does that
            // compile IN this process (below), and 384m could not carry it under load.
            "org.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=384m\n" +
                // Kotlin compiles IN the nested build's own process: a compile daemon is a second
                // JVM that outlives the build that started it, and three of them were 2 GB of the
                // peak above. Nothing here compiles enough Kotlin to need one, and an in-process
                // compile cannot outlive its build by construction.
                "kotlin.compiler.execution.strategy=in-process\n" +
                "kotlin.daemon.jvmargs=-Xmx256m\n" +
                // Nested builds are sequential and small; workers stacking is pure overhead here.
                "org.gradle.workers.max=1\n"
            // These settings are identical for every nested build in a module, which is what holds
            // the module to ONE nested daemon: Gradle reuses a daemon whose parameters match, and
            // starts another when they do not.
            //
            // NOT org.gradle.daemon=false. A single-use daemon exiting with its build is exactly
            // what one wants, and it was measured 2026-09-18 to break a suite whose fixtures carry
            // Kotlin-DSL build scripts: the kotlin-dsl script cache is left half-written, and the
            // next nested build fails reading it back (`NoSuchFileException … /instrumented/
            // classes`). One shared daemon per module is bounded too — one JVM, sized above.
            //
            // NOT org.gradle.daemon.idletimeout: shortening it to 20s was measured the same day and
            // made the peak WORSE, not better (11 JVMs, exactly at the ceiling, against 9 at
            // 4.72 GB without it). Expiring a daemon mid-suite does not remove a JVM — the next
            // test starts a fresh one, and for a while both are resident.
        )
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(testKitDaemonProperties)
    systemProperty("org.gradle.testkit.dir", testKitHome.get().asFile.absolutePath)
    // One nested build at a time: two test JVMs each starting their own is two outer-sized daemons
    // for no parallel gain on suites this small.
    maxParallelForks = 1
}
