/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// Legacy insurance-claims example: an unmodified Jakarta EE-style application
// traced zero-code by attaching the NarrativeTrace java agent. The main source
// set must stay free of NarrativeTrace dependencies — the whole point is that
// the agent narrates classes that never heard of NarrativeTrace.

plugins {
    war
}

// User-supplied SLF4J binding handed to the standalone agent via its `loggingJars=` arg —
// mirrors how a real WildFly host provides its own provider jar (never packaged anywhere).
val dockerSlf4jProvider: Configuration by configurations.creating

dependencies {
    // Container-provided APIs only — never packaged into the WAR.
    "providedCompile"("jakarta.servlet:jakarta.servlet-api:6.0.0")
    "providedCompile"("jakarta.ejb:jakarta.ejb-api:4.0.1")
    "providedCompile"("jakarta.annotation:jakarta.annotation-api:2.1.1")
    "testImplementation"("org.mockito:mockito-core:5.14.2")
    "testImplementation"(project(":narrativetrace-clarity"))
    "testImplementation"("org.testcontainers:testcontainers:1.21.3")
    dockerSlf4jProvider("org.slf4j:slf4j-simple:2.0.16") { isTransitive = false }
}

tasks.war {
    // The examples parent injects slf4j-api (and logback at runtime) into every
    // example subproject; the legacy pitch is a WAR with an empty WEB-INF/lib,
    // so keep class directories and drop every jar.
    classpath = classpath?.filter { !it.name.endsWith(".jar") }
}

// Container harness (plan Phase 2 milestone 3): tests tagged `docker` deploy the WAR to
// WildFly-in-Docker with the standalone agent attached. Excluded from the default `test`
// task by the examples parent (like `network`); run explicitly via `dockerTest` on a
// Docker-capable host (the dev container reaches its dind sidecar through DOCKER_HOST).
// Untyped task reference: the shadow plugin's ShadowJar class is not on this build
// script's classpath (the plugin is applied only in :narrativetrace-agent).
val standaloneJarTask = project(":narrativetrace-agent").tasks.named("standaloneJar")
val testSourceSet = extensions.getByType<SourceSetContainer>()["test"]
tasks.register<Test>("dockerTest") {
    description = "Runs WildFly-in-Docker agent tests (tagged @Tag(\"docker\"))"
    group = "verification"
    useJUnitPlatform {
        includeTags("docker")
    }
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    dependsOn(tasks.war, standaloneJarTask)
    doFirst {
        systemProperty("narrativetrace.test.warFile", tasks.war.get().archiveFile.get().asFile.absolutePath)
        systemProperty("narrativetrace.test.standaloneJar", standaloneJarTask.get().outputs.files.singleFile.absolutePath)
        systemProperty("narrativetrace.test.slf4jProviderJar", dockerSlf4jProvider.singleFile.absolutePath)
    }
}
