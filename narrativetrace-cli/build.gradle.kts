/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import java.io.FileOutputStream

plugins {
    id("narrativetrace-publish")
    application
}

extra["publishName"] = "NarrativeTrace CLI"
extra["publishDescription"] =
    "The free-tier narrativetrace CLI: a read-only doctor verb diagnosing toolchain, " +
        "configuration, and known traps against a project, zero network."

application {
    mainClass.set("ai.narrativetrace.cli.Main")
}

// The Tier A2 oracle replay (narrativetrace-skills) needs to run this CLI's real `doctor` verb
// against a fixture project directory elsewhere in this repo (sixty-seconds/) without leaving the
// closed per-port command vocabulary ({ ./gradlew, git } — no `cd`, no shell scripting). Pointing
// `run`'s working directory at a Gradle property keeps the whole replayed command a single
// `./gradlew ...` invocation.
tasks.named<JavaExec>("run") {
    workingDir =
        providers
            .gradleProperty("narrativetraceDoctorTarget")
            .map { rootProject.file(it) }
            .getOrElse(project.projectDir)
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "ai.narrativetrace.cli.Main")
    }
}

// The same replay's actual oracle: `run` above is what a person types, but a JavaExec whose
// subprocess exits 1 (findings present — a perfectly valid doctor outcome) makes Gradle itself
// report BUILD FAILED, which a nested GradleRunner replay cannot tell apart from a real crash.
// This task never fails on the doctor's own exit code — it writes the JSON report to a file
// instead, which the replay reads and asserts against directly. The report lands under the
// TARGET project's own build/narrativetrace/doctor-report.json (not this module's build/) so this
// task and the Gradle plugin's real `narrativetraceDoctor` task (narrativetrace-gradle-plugin,
// which calls the same doctor classes in-process) leave the report at the identical relative
// path — the one the skills catalogue's adopter-facing verify prose names.
tasks.register<JavaExec>("printDoctorReport") {
    group = "verification"
    description =
        "Runs the doctor CLI against -PnarrativetraceDoctorTarget (or this project) and writes " +
            "its JSON report to <target>/build/narrativetrace/doctor-report.json. Never fails " +
            "the build on findings."
    mainClass.set("ai.narrativetrace.cli.Main")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("doctor", "--json")
    isIgnoreExitValue = true
    doFirst {
        val target =
            providers
                .gradleProperty("narrativetraceDoctorTarget")
                .map { rootProject.file(it) }
                .getOrElse(project.projectDir)
        workingDir = target
        val reportFile = target.resolve("build/narrativetrace/doctor-report.json")
        reportFile.parentFile.mkdirs()
        standardOutput = FileOutputStream(reportFile)
    }
}

// Zero dependencies, by contract: like narrativetrace-api, this is a standalone tool a project's
// own build (or a bare `java -jar`) invokes directly, and it never links against the runtime it
// diagnoses — it reads a project's build file, source tree, and rendered output as text, the same
// way the TypeScript reference's doctor reads package.json and node_modules without importing the
// traced library's own internals. See narrativetrace-cli/src/test/.../ArchitectureTest.java.
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
}
