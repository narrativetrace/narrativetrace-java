/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    id("narrativetrace-publish")
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

extra["publishName"] = "NarrativeTrace Agent"
extra["publishDescription"] = "Java bytecode agent for automatic method tracing"

// Two published artifacts, one per consumption mode:
//  - the main (slim) jar is for builds: ASM is bundled relocated (never a POM dependency, never
//    clashing with an application's own ASM); core arrives via the POM like any dependency.
//  - the `-standalone` classifier jar is for `-javaagent` attach on hosts with no build tool
//    (legacy servers, plain java): nothing resolves the POM at attach time, so it additionally
//    bundles core, the narrativetrace-slf4j listener, and un-relocated slf4j-api. No SLF4J
//    provider is ever bundled — the host's logging is unknown, the binding is the user's
//    (the ejb4 example's WildFlyAgentNarrationTest is the acceptance case for this).
val shade: Configuration by configurations.creating
val standalone: Configuration by configurations.creating

// User-supplied SLF4J binding for the attach acceptance test — deliberately NOT part of any
// published artifact, mirroring how a real legacy host provides its own provider jar.
val standaloneTestProvider: Configuration by configurations.creating

configurations.named("compileOnly") { extendsFrom(shade) }
configurations.named("testImplementation") { extendsFrom(shade) }

dependencies {
    implementation(project(":narrativetrace-core"))
    shade("org.ow2.asm:asm:9.7.1")
    shade("org.ow2.asm:asm-commons:9.7.1")
    compileOnly(project(":narrativetrace-slf4j"))
    standalone(project(":narrativetrace-slf4j")) // brings core + slf4j-api transitively
    standaloneTestProvider("org.slf4j:slf4j-simple:2.0.16") { isTransitive = false }

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
    testImplementation(project(":narrativetrace-slf4j"))
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("ch.qos.logback:logback-classic:1.5.38")
}

val agentManifestAttributes = mapOf(
    "Premain-Class" to "ai.narrativetrace.agent.NarrativeTraceAgent",
    "Can-Retransform-Classes" to "true"
)

tasks.jar {
    archiveClassifier.set("plain")
    manifest {
        attributes(agentManifestAttributes)
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    configurations = listOf(shade)
    relocate("org.objectweb.asm", "ai.narrativetrace.agent.internal.asm")
    manifest {
        attributes(agentManifestAttributes)
    }
}

tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("standaloneJar") {
    archiveClassifier.set("standalone")
    from(sourceSets["main"].output)
    configurations = listOf(shade, standalone)
    relocate("org.objectweb.asm", "ai.narrativetrace.agent.internal.asm")
    // Bundled dependencies' module descriptors would misdeclare the fat jar as their module.
    exclude("module-info.class", "META-INF/versions/*/module-info.class")
    // This jar bundles core and the slf4j listener, and every NarrativeTrace jar now carries its
    // own META-INF/LICENSE and META-INF/NOTICE (narrativetrace-license-packaging). The texts are
    // byte-identical to this module's, so the archive would otherwise hold three copies of each.
    // Scoped to those two paths by name, deliberately: a blanket duplicates strategy on a fat jar
    // silently drops the first or last of every colliding resource, service registrations included.
    // The bundled api's META-INF/LICENSE-APACHE has its own path and stays — the Apache-licensed
    // classes in here travel with their licence, which is the point.
    filesMatching(listOf("META-INF/LICENSE", "META-INF/NOTICE")) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    manifest {
        attributes(agentManifestAttributes)
    }
}

publishing {
    publications.named<MavenPublication>("mavenJava") {
        artifact(tasks.named("standaloneJar"))
    }
}

// The self-contained shadow jar IS the published/consumed artifact (POM keeps core, drops ASM).
listOf("apiElements", "runtimeElements").forEach { variantName ->
    configurations.named(variantName) {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.named("shadowJar"))
    }
}

tasks.test {
    dependsOn(tasks.named("shadowJar"), tasks.named("standaloneJar"))
    doFirst {
        systemProperty(
            "narrativetrace.test.agentJar",
            tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")
                .get().archiveFile.get().asFile.absolutePath
        )
        systemProperty(
            "narrativetrace.test.standaloneJar",
            tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("standaloneJar")
                .get().archiveFile.get().asFile.absolutePath
        )
        systemProperty(
            "narrativetrace.test.slf4jProviderJar",
            standaloneTestProvider.singleFile.absolutePath
        )
    }
}

// PIT runs the suite in its own minion JVMs, which see none of `tasks.test`'s doFirst system
// properties and none of its task dependencies. The jar-composition/attach tests read the three
// `narrativetrace.test.*Jar` paths and need the jars actually built, so without this block every
// pitest run died in the coverage phase with "16 tests did not pass without mutation"
// (nightly java-pitest, 2026-09-05..08 — the agent module joined mutation testing 2026-09-03 and
// its pitest task had never been runnable). Same three paths, handed to the minions as -D flags;
// lazy providers so configuration never forces the jar tasks.
tasks.named("pitest") {
    dependsOn(tasks.named("shadowJar"), tasks.named("standaloneJar"))
}
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    jvmArgs.set(
        provider {
            listOf(
                "-Dnarrativetrace.test.agentJar=" +
                    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")
                        .get().archiveFile.get().asFile.absolutePath,
                "-Dnarrativetrace.test.standaloneJar=" +
                    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("standaloneJar")
                        .get().archiveFile.get().asFile.absolutePath,
                "-Dnarrativetrace.test.slf4jProviderJar=" + standaloneTestProvider.singleFile.absolutePath
            )
        }
    )
}
