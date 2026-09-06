/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    id("narrativetrace-publish")
}

extra["publishName"] = "NarrativeTrace Spring"
extra["publishDescription"] = "Spring Framework integration with BeanPostProcessor"

dependencies {
    implementation(project(":narrativetrace-core"))
    implementation(project(":narrativetrace-proxy"))
    implementation("org.springframework:spring-context:6.2.19")
    compileOnly(project(":narrativetrace-slf4j"))
    // ContextPropagatingTaskDecorator only: context-propagation is required on the
    // consumer's runtime classpath when the decorator is used, SLF4J is optional
    // (MDC propagation switches itself off when it is absent).
    compileOnly("io.micrometer:context-propagation:1.1.2")
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.springframework:spring-test:6.2.19")
    testImplementation("io.micrometer:context-propagation:1.1.2")
    testImplementation(project(":narrativetrace-micrometer"))
    testImplementation(project(":narrativetrace-slf4j"))
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("ch.qos.logback:logback-classic:1.5.38")
    testImplementation(testFixtures(project(":narrativetrace-core")))
    testImplementation("com.github.noconnor:junitperf-junit5:1.37.0")
}
