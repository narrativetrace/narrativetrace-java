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
plugins {
    id("narrativetrace-publish")
}

extra["publishName"] = "NarrativeTrace Tooling"
extra["publishDescription"] =
    "The free-tier tooling library: the read-only doctor that diagnoses toolchain, configuration, " +
        "and known traps against a project, zero network. Embedded by the narrativetrace CLI and " +
        "by the Gradle plugin; neither entry point depends on the other."

// Zero dependencies, by contract: like narrativetrace-api, this library never links against the
// runtime it diagnoses — it reads a project's build file, source tree, and rendered output as text,
// the same way the TypeScript reference's doctor reads package.json and node_modules without
// importing the traced library's own internals. See
// narrativetrace-tooling/src/test/.../ArchitectureTest.java, which also holds the other half of the
// D3 split: a library both entry points embed may never reach back into either of them.
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    // The installer's round-trip and idempotence properties are generative: "plan, apply, plan
    // again" must be empty and "install then uninstall" must leave the tree as it was, for
    // context files nobody wrote by hand. The same jqwik the core, api and security suites use.
    testImplementation("net.jqwik:jqwik:1.9.2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
}

// The installer's carrier tests open the REAL built jars rather than a fixture one: a jar a test
// assembles proves only that the test can assemble a jar, while `narrativetrace-skills` and
// `narrativetrace-cli` are the two archives a consumer actually gets (the second bundles the same
// carrier resources so `java -jar` installs offline). `projectDir` points them at this repo's own
// checked-in carrier resources, the same wiring `narrativetrace-build-tests` uses.
tasks.withType<Test>().configureEach {
    dependsOn(":narrativetrace-skills:jar", ":narrativetrace-cli:jar")
    systemProperty("projectDir", rootProject.projectDir.absolutePath)
    systemProperty("narrativetrace.buildVersion", project.version.toString())
    // `projectDir` is a system PROPERTY, not a task input, so a test that reads a file through it
    // stays UP-TO-DATE when only that file changes — the hole that has left drift gates unrun
    // elsewhere in this repository, and that hid three real failures in this very module on
    // 2026-10-04: rendering a fourth skill changed the checked-in carrier, and CarrierTest and
    // CatalogueReaderTest kept reporting a stale green through a whole `./gradlew check`.
    // Everything those tests read through `projectDir` is declared here, so editing any of it
    // must re-run this task.
    inputs
        .file(rootProject.file(".github/ISSUE_TEMPLATE/narrativetrace-report.yml"))
        .withPropertyName("issueForm")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .dir(rootProject.file("narrativetrace-skills/src/main/resources"))
        .withPropertyName("checkedInCarrier")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// PIT runs the same suite in its own minion JVMs, and those inherit nothing from the `test` task:
// without the two properties above, every carrier test fails "without mutation" and PIT refuses to
// start. The jars are declared here for the same reason the test task declares them.
plugins.withId("info.solidsoft.pitest") {
    configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
        jvmArgs.set(
            listOf(
                "-DprojectDir=${rootProject.projectDir.absolutePath}",
                "-Dnarrativetrace.buildVersion=${project.version}",
            )
        )
    }
    tasks.named("pitest") {
        dependsOn(":narrativetrace-skills:jar", ":narrativetrace-cli:jar")
    }
}
