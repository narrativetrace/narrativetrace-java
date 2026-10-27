/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// The end-to-end multithreading matrix: test-only, no `main` source set.
//
// Why a module of its own: every cell wires a production ASSEMBLY — a request-scope integration
// (servlet filter, Spring Web), a hand-over mechanism (ForkGroup, FireAndForgetGroup, the Spring
// TaskDecorator, @Async) and a trace route (the proxy, or the real standalone agent in a forked JVM)
// — and no single integration module may see the others. The servlet module has no Spring, the
// Spring module has no servlet, neither has the agent jar. The same reason narrativetrace-security-tests
// is its own module; it is shaped after that one and listed in the same exclusion sets in the root
// build (nothing to publish, mutate or measure here).
//
// The Micronaut cells live in narrativetrace-micronaut-http's own tests, where Micronaut's KSP
// wiring already is.

// The agent's task graph must exist before this module asks for the standalone jar.
evaluationDependsOn(":narrativetrace-agent")

dependencies {
    testImplementation(project(":narrativetrace-api"))
    testImplementation(project(":narrativetrace-core"))
    testImplementation(project(":narrativetrace-proxy"))
    testImplementation(project(":narrativetrace-servlet"))
    testImplementation(project(":narrativetrace-spring"))
    testImplementation(project(":narrativetrace-spring-web"))
    testImplementation(project(":narrativetrace-micrometer"))
    testImplementation(project(":narrativetrace-slf4j"))
    testImplementation("io.micrometer:context-propagation:1.1.2")
    testImplementation("org.springframework:spring-context:6.2.19")
    testImplementation("org.springframework:spring-web:6.2.19")
    testImplementation("org.springframework:spring-test:6.2.19")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("ch.qos.logback:logback-classic:1.5.38")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

val standaloneAgentJar = project(":narrativetrace-agent").tasks.named("standaloneJar")

tasks.named<Test>("test") {
    dependsOn(standaloneAgentJar)
    // The agent route forks a JVM with the real -standalone jar attached (DeepAgentEndToEndTest's
    // shape): the jar path and the application classpath arrive as properties.
    systemProperty("narrativetrace.test.standaloneJar", standaloneAgentJar.get().outputs.files.singleFile.absolutePath)
    doFirst {
        systemProperty(
            "narrativetrace.test.forkedClasspath",
            sourceSets["test"].runtimeClasspath.files.joinToString(File.pathSeparator) { it.absolutePath }
        )
    }
    // The 20 000-request soak and the forked JVMs: modest heap, bounded on purpose.
    maxHeapSize = "512m"
}
