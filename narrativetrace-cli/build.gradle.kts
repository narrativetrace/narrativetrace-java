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

// The carrier, bundled (Phase 2 D4): `narrativetrace-cli-<v>.jar` carries the same
// META-INF/narrativetrace/skills/** resources the published narrativetrace-skills jar does, so
// `java -jar narrativetrace-cli.jar` can install the skills offline from the one jar a consumer
// already fetched. The SHARED DIRECTORY, not a copy task: a copy is a second set of bytes that can
// go stale, while a second resource root cannot — the two jars are then byte-identical there by
// construction, which is what SkillsCarrierJarTest asserts. The directory is checked-in build
// output of :narrativetrace-skills-catalogue:renderSkills, so nothing has to be built first.
sourceSets["main"].resources.srcDir(rootProject.file("narrativetrace-skills/src/main/resources"))

// The Tier A2 oracle replay (narrativetrace-skills-catalogue) needs to run this CLI's real `doctor` verb
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
    // A declared Main-Class is a promise that `java -jar narrativetrace-cli-<v>.jar` runs, and the
    // doctor's classes live in narrativetrace-tooling since the 2026-09-24 split — so this jar
    // carries that library's classes as well as its own. Scoped to `ai/narrativetrace/tooling/**`
    // rather than "everything on the runtime classpath": nothing else is ever supposed to be there
    // (ArchitectureTest fails the build if it is), and an unscoped bundle would silently absorb a
    // third-party jar instead. No META-INF collides, so the licence texts this module's own
    // packaging convention writes stay the only copies. CliExecutableJarTest proves the promise by
    // running the built jar, not by looking for the classes inside it.
    // The zipTree mapping below reads another module's jar, and a `map {}` over the configuration
    // does not carry that producer task with it — so the dependency is declared here. Without it the
    // ordering held only by luck of the task graph: any build that reaches this jar before
    // `:narrativetrace-tooling:jar` (a parallel run, or a task that asks for this jar directly)
    // fails Gradle's implicit-dependency validation.
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { classpath -> classpath.map(::zipTree) }) {
        include("ai/narrativetrace/tooling/**")
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

// The installer's half of the same oracle: the skills catalogue's last step is
// `./gradlew narrativetraceInit --diff`, which the sixty-seconds fixture cannot run (it applies no
// plugin, by design). This task is the fixture-scoped real invocation of the same installer library
// the plugin task calls in-process — `init --dry-run`, the CLI spelling of the identical option —
// and it writes the diff to a file so the replay can assert a diff was actually PRODUCED rather
// than only that the process exited 0. `--dry-run` writes nothing into the target, which is the
// property the replayed step is about.
tasks.register<JavaExec>("printInitDiff") {
    group = "verification"
    description =
        "Previews an install against -PnarrativetraceDoctorTarget (or this project) and writes " +
            "the diff to <target>/build/narrativetrace/init-diff.txt. Writes nothing else."
    mainClass.set("ai.narrativetrace.cli.Main")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("init", "--dry-run")
    isIgnoreExitValue = true
    doFirst {
        val target =
            providers
                .gradleProperty("narrativetraceDoctorTarget")
                .map { rootProject.file(it) }
                .getOrElse(project.projectDir)
        workingDir = target
        val diffFile = target.resolve("build/narrativetrace/init-diff.txt")
        diffFile.parentFile.mkdirs()
        standardOutput = FileOutputStream(diffFile)
    }
}

// The problem-report skill's half of the same oracle. `sixty-seconds` deliberately does not apply
// the ai.narrativetrace plugin, so its `narrativetraceFeedback` task does not exist there — this is
// the fixture-scoped real invocation of the same tooling-library classes that task calls
// in-process, exactly like printDoctorReport above.
//
// The exit value is NOT ignored, and that is the oracle: the verb exits 2 when a value-free rule
// refuses the report, so a run that refuses must fail this task rather than pass quietly. The text
// is fixed and deliberately dull - what the replay proves is that the verb runs and writes, not
// that it can be handed interesting prose.
tasks.register<JavaExec>("printFeedbackDraft") {
    group = "verification"
    description =
        "Drafts a problem report against -PnarrativetraceDoctorTarget (or this project) on the " +
            "channel -PnarrativetraceFeedbackChannel (draft by default). Fails when a value-free " +
            "rule refuses the report."
    mainClass.set("ai.narrativetrace.cli.Main")
    classpath = sourceSets["main"].runtimeClasspath
    doFirst {
        val target =
            providers
                .gradleProperty("narrativetraceDoctorTarget")
                .map { rootProject.file(it) }
                .getOrElse(project.projectDir)
        workingDir = target
        val channel =
            providers.gradleProperty("narrativetraceFeedbackChannel").getOrElse("draft")
        args =
            listOf(
                "feedback",
                channel,
                "--category",
                "library",
                "--step",
                "the install block",
                "--did",
                "applied the plugin and ran the build",
                "--happened",
                "nothing appeared under the output directory",
                "--expected",
                "one trace file per scenario",
            )
    }
}

// The docs' half of the same preview (rule 8, docs as tests): documentation/agent-skills.md shows
// what `init` reports before it writes anything, and a typed block there would be a promise nobody
// checks. Three deliberate differences from printInitDiff above:
//   * the target is an EMPTY directory this task owns, so the preview is always a FIRST install and
//     nothing a person did in a fixture can change what the page shows;
//   * the output is the `--json` envelope, which is the whole plan in fifteen lines — the human form
//     is the same list followed by a full unified diff of every file in it, which is hundreds of
//     lines and belongs in a terminal, not on a page;
//   * it runs the built JAR rather than the loose classes, because the carrier's coordinate comes
//     from the archive that carries it: from compiled classes it honestly reads `unknown`, and a
//     page must show a reader what a reader will see.
tasks.register<JavaExec>("printInitPlan") {
    group = "documentation"
    description =
        "Previews a first install into an empty directory and writes the --json envelope to " +
            "build/narrativetrace/init-plan.json (embedded by documentation/agent-skills.md)."
    val previewProject = layout.buildDirectory.dir("narrativetrace/init-preview-project")
    val planFile = layout.buildDirectory.file("narrativetrace/init-plan.json")
    classpath = files(tasks.jar.flatMap { it.archiveFile })
    mainClass.set("ai.narrativetrace.cli.Main")
    args = listOf("init", "--dry-run", "--json")
    outputs.file(planFile)
    doFirst {
        val target = previewProject.get().asFile
        target.deleteRecursively()
        target.mkdirs()
        workingDir = target
        val plan = planFile.get().asFile
        plan.parentFile.mkdirs()
        standardOutput = FileOutputStream(plan)
    }
}

// One dependency, by contract: the doctor itself lives in narrativetrace-tooling, the library the
// Gradle plugin embeds too — neither entry point depends on the other. That library takes zero
// dependencies of its own (licensing.properties: both are `open`), so a bare `java -jar` still
// carries nothing but NarrativeTrace's own code, and it never links against the runtime it
// diagnoses. See narrativetrace-cli/src/test/.../ArchitectureTest.java.
dependencies {
    implementation(project(":narrativetrace-tooling"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
}
