/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// The soak harness's shop process (see ../../README.md). Deliberately independent of
// narrativetrace-spring / narrativetrace-servlet / narrativetrace-spring-web: those pin their own
// Spring Framework version, and this module wants a current, independently-managed Spring Boot BOM
// instead of reconciling two. Domain tracing comes entirely from the java agent (no NarrativeTrace
// import anywhere under ai.narrativetrace.soak.shop.web or ai.narrativetrace.examples.ecommerce);
// the only compile-time NarrativeTrace dependency is narrativetrace-agent, used directly for
// AgentRuntime.getContext() (request-scoped reset + inbound traceparent adoption) and Traceparent
// parsing — see TraceContextFilter.
// The dependency-management plugin is deliberately NOT applied: it patches version constraints
// onto every configuration in the project, tool configurations (spotbugsPlugins, pmd) included —
// that collided with this repo's pinned SpotBugs/BCEL/commons-lang3 versions (spotbugsMain died
// with NoClassDefFoundError org/apache/commons/lang3/Strings). A Gradle-native platform import
// scopes the BOM to the configurations it is actually declared on instead.
plugins {
    id("org.springframework.boot") version "3.5.6"
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.6"))

    implementation(project(":narrativetrace-api"))
    implementation(project(":narrativetrace-core"))
    implementation(project(":narrativetrace-agent"))
    implementation(project(":narrativetrace-examples:ecommerce"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    runtimeOnly("com.h2database:h2:2.3.232")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2:2.3.232")
}

tasks.named<Jar>("jar") {
    enabled = false
}
