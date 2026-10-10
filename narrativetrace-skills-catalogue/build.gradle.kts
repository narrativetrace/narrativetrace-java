/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    // Deliberately NOT narrativetrace-publish: this module is the SOURCE the carrier is rendered
    // from, and nothing outside this repository consumes the typed catalogue, the renderers, the
    // lints or the evals. What ships is narrativetrace-skills, the resource-only carrier jar this
    // module's `renderSkills` writes. Same shape as narrativetrace-build-tests — present in the
    // source-available tree, absent from every publication.
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

// Zero production dependencies, the same contract as narrativetrace-api and narrativetrace-tooling:
// a catalogue entry names commands and check ids as data (strings), it never links against the
// runtime or the entry points it describes.
dependencies {
    testImplementation(gradleTestKit())
    // Test-only: the replayer counts the doctor's findings against the registry itself, and a
    // drift test holds the skills' "naming all N findings" verify to the number the doctor runs.
    testImplementation(project(":narrativetrace-tooling"))
    // Test-only: the replayer promotes a received structural trace with the same NarrativeApproval
    // call the plugin's approveNarratives task makes, on a scratch copy — never in the fixture.
    testImplementation(project(":narrativetrace-core"))
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

// Regenerates .claude/skills/*/SKILL.md, .agents/skills/*/SKILL.md, the carrier's resources,
// .claude-plugin/marketplace.json and the AGENTS.md managed section from the typed catalogue
// (RenderMain) — the drift check RenderDriftTest gates on every `./gradlew check`. Never hand-edit
// the rendered files; run this and commit its output instead (documentation/what-to-commit.md).
tasks.register<JavaExec>("renderSkills") {
    group = "documentation"
    description =
        "Regenerates .claude/skills/*/SKILL.md, .agents/skills/*/SKILL.md, the carrier resources," +
            " .claude-plugin/marketplace.json and the AGENTS.md managed section from the typed" +
            " catalogue"
    mainClass.set("ai.narrativetrace.skills.render.RenderMain")
    classpath = sourceSets["main"].runtimeClasspath
    args(rootProject.projectDir.absolutePath)
}
