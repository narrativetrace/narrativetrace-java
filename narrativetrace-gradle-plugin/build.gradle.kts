/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    id("com.gradle.plugin-publish") version "2.0.0"
    // This module publishes to the Gradle Plugin Portal on `plugin-publish`'s own wiring rather
    // than through `narrativetrace-publish`, so it asks for the licence packaging directly —
    // otherwise it would be the one published artifact shipping without its licence.
    id("narrativetrace-license-packaging")
}


dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

// The lockstep invariant is only testable if the tests know what the module
// publishes; VersionResolver reads the generated resource, this is the other side.
tasks.withType<Test>().configureEach {
    systemProperty("narrativetrace.test.publishedVersion", project.version.toString())
}

tasks.named<ProcessResources>("processResources") {
    val propsFile = layout.buildDirectory.file("resources/main/narrativetrace-version.properties")
    // Read at configuration time: reaching for `project` inside `doLast` is deprecated and fails
    // outright under the configuration cache.
    val moduleVersion = project.version.toString()
    // Declared as an input, or a version bump leaves the task UP-TO-DATE and a warm
    // build directory keeps serving the previous version's file.
    inputs.property("moduleVersion", moduleVersion)
    outputs.file(propsFile)
    doLast {
        propsFile.get().asFile.writeText("version=$moduleVersion\n")
    }
}

gradlePlugin {
    website.set("https://narrativetrace.ai")
    vcsUrl.set("https://github.com/narrativetrace/narrativetrace-java")
    plugins {
        create("narrativeTrace") {
            id = "ai.narrativetrace"
            implementationClass = "ai.narrativetrace.gradle.NarrativeTracePlugin"
            displayName = "NarrativeTrace Gradle Plugin"
            description = "Auto-generates human-readable execution traces from method and parameter names. " +
                "Manages dependencies, compiler flags, test configuration, and clarity quality gates."
            tags.set(listOf("testing", "tracing", "documentation", "code-quality", "narrative"))
        }
    }
}

// Functional test source set
val functionalTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[functionalTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[functionalTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

val functionalTestTask = tasks.register<Test>("functionalTest") {
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(functionalTestTask)
}

// Central Portal validation requires every publication in the aggregated
// bundle to be signed and fully described — including the two plugin-publish
// generates here (pluginMaven and the plugin marker), which the nmcp
// settings aggregation sweeps up alongside the libraries. The first 0.2.0
// release bundle was rejected over exactly this. This module deliberately
// does not apply `narrativetrace-publish` (plugin-publish owns its
// publications), so the POM facts and the two signing modes are mirrored
// from that convention here.
apply(plugin = "signing")

publishing.publications.withType<MavenPublication>().configureEach {
    pom {
        name.set("NarrativeTrace Gradle Plugin")
        description.set(
            "Applies NarrativeTrace to a Gradle build: wires the agent, the " +
                "test integrations and the clarity gate, resolving the " +
                "matching library version from its own."
        )
        url.set("https://narrativetrace.ai")
        licenses {
            license {
                name.set("Business Source License 1.1")
                url.set("https://mariadb.com/bsl11/")
                comments.set(
                    "Free to use in production, source-available. Change Date: " +
                        "four years from the date this version is published. " +
                        "Change License: Apache License, Version 2.0."
                )
            }
        }
        developers {
            developer {
                id.set("danijel")
                name.set("Danijel Arsenovski")
                email.set("danijel.arsenovski@empoweragile.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/narrativetrace/narrativetrace-java.git")
            developerConnection.set("scm:git:ssh://github.com/narrativetrace/narrativetrace-java.git")
            url.set("https://github.com/narrativetrace/narrativetrace-java")
        }
    }
}

val inMemoryKey: String? = System.getenv("GPG_PRIVATE_KEY")?.takeIf { it.isNotBlank() }
configure<SigningExtension> {
    if (inMemoryKey != null) {
        useInMemoryPgpKeys(inMemoryKey, System.getenv("GPG_PASSPHRASE") ?: "")
    } else {
        useGpgCmd()
    }
    sign(publishing.publications)
}
val skipSigning = providers.gradleProperty("skipSigning").isPresent
val signingCredentialsPresent = inMemoryKey != null || rootProject.file("secret.properties").exists()
tasks.withType<Sign>().configureEach {
    onlyIf { !skipSigning && signingCredentialsPresent }
}
