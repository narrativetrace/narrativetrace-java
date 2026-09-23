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
    // The Tier A2 replay (SkillReplayer) starts nested Gradle builds through GradleTestKit: the
    // sixty-seconds tasks, and the clarity consumer fixture resolved against a local Maven file
    // repository (publishSkillsTestRepo, below) rather than an includeBuild composite of this
    // whole repo — the composite path used to push the standalone snapshot verify's JVM census
    // high enough to get the outer daemon OOM-killed (2026-09-23, "build daemon disappeared").
    // Without this convention each nested build still takes the default test-kit home and default
    // daemon sizes, the same failure the two other nested-build modules fixed on 2026-09-18 by
    // applying this convention.
    id("narrativetrace-nested-gradle-builds")
}

extra["publishName"] = "NarrativeTrace Skills"
extra["publishDescription"] =
    "The typed catalogue narrativetrace-doctor, add-narrative-tracing, and " +
        "add-narrativetrace-clarity render from: " +
        "SKILL.md and the AGENTS.md managed section are both build output, never hand-edited."

// Zero production dependencies, the same contract as narrativetrace-api and narrativetrace-cli:
// a catalogue entry names commands and check ids as data (strings), it never links against the
// runtime or the CLI it describes.
dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
}

val testMavenRepo = layout.buildDirectory.dir("test-repo")

tasks.withType<Test> {
    systemProperty("projectDir", rootProject.projectDir.absolutePath)
    // Tier A2's replay (replay/SkillReplayer) invokes these through GradleRunner — see its own
    // doc comment for why not a second, independent `./gradlew` process. publishSkillsTestRepo
    // (root build.gradle.kts) fills testMavenRepo with the plugin's marker+implementation jar and
    // every library it can add to a consumer, so clarityScan/clarityCheck's nested build never
    // has to includeBuild this whole checkout — SkillReplayer reads the path back from the system
    // property below, not a hard-coded relative path.
    dependsOn(":narrativetrace-cli:jar", ":sixty-seconds:testClasses", ":publishSkillsTestRepo")
    systemProperty("narrativetrace.testMavenRepo", testMavenRepo.get().asFile.absolutePath)
}

// Regenerates .claude/skills/*/SKILL.md, .agents/skills/*/SKILL.md, and the AGENTS.md managed
// section from the typed catalogue (RenderMain) — the drift check RenderDriftTest gates on every
// `./gradlew check`. Never hand-edit the rendered files; run this and commit its output instead
// (documentation/what-to-commit.md).
tasks.register<JavaExec>("renderSkills") {
    group = "documentation"
    description =
        "Regenerates .claude/skills/*/SKILL.md, .agents/skills/*/SKILL.md, and the AGENTS.md" +
            " managed section from the typed catalogue"
    mainClass.set("ai.narrativetrace.skills.render.RenderMain")
    classpath = sourceSets["main"].runtimeClasspath
    args(rootProject.projectDir.absolutePath)
}
