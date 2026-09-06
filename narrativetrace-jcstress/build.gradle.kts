/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

val jcstressVersion = "0.16"

dependencies {
    implementation(project(":narrativetrace-core"))
    implementation("org.openjdk.jcstress:jcstress-core:$jcstressVersion")
    annotationProcessor("org.openjdk.jcstress:jcstress-core:$jcstressVersion")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("jcstress")
    manifest {
        attributes["Main-Class"] = "org.openjdk.jcstress.Main"
    }
    mergeServiceFiles()
}

// The run is a JavaExec, which would otherwise use whatever JVM happens to be running Gradle.
// jcstress forks its own JVMs from `java.home`, so pinning the launcher to the project toolchain is
// what makes `-Pnarrativetrace.toolchain=21` actually stress the JVM the scheduled job exists for.
val jcstressLauncher = extensions.getByType<JavaToolchainService>()
    .launcherFor(extensions.getByType<JavaPluginExtension>().toolchain)

// Modes, and who uses which:
//   -PjcstressMode=sanity  seconds; "do these scenarios still run at all"
//   -PjcstressMode=quick   minutes; the scheduled CI job
//   (none)                 jcstress's own default: hours, for the overnight sweep
tasks.register<JavaExec>("jcstress") {
    description = "Runs OpenJDK jcstress concurrency tests"
    group = "verification"
    dependsOn("shadowJar")
    javaLauncher.set(jcstressLauncher)
    classpath(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile)
    mainClass.set("org.openjdk.jcstress.Main")
    val testFilter = findProperty("jcstressFilter") as? String ?: "ai.narrativetrace"
    val mode = findProperty("jcstressMode") as? String
    val reportDir = layout.buildDirectory.dir("reports/jcstress").get().asFile
    // jcstress writes its binary result blob and any fontconfig scratch into the working directory.
    // Both belong under build/, not next to the sources, so CI can archive one directory and a
    // local run leaves the tree clean.
    workingDir = layout.buildDirectory.get().asFile
    args = buildList {
        add("-t")
        add(testFilter)
        add("-r")
        add(reportDir.absolutePath)
        if (mode != null) {
            add("-m")
            add(mode)
        }
    }
    jvmArgs = listOf("-Xmx512m")
    // A stress run is never up to date: the scheduler it raced last time is not this one.
    outputs.upToDateWhen { false }
    doFirst {
        workingDir.mkdirs()
        reportDir.mkdirs()
    }
}
