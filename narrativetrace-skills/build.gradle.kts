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

extra["publishName"] = "NarrativeTrace Skills"
extra["publishDescription"] =
    "The typed catalogue narrativetrace-doctor and add-narrative-tracing render from: " +
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

tasks.withType<Test> {
    systemProperty("projectDir", rootProject.projectDir.absolutePath)
    // Tier A2's replay (replay/SkillReplayer) invokes these through GradleRunner — see its own
    // doc comment for why not a second, independent `./gradlew` process.
    dependsOn(":narrativetrace-cli:jar", ":sixty-seconds:testClasses")
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
