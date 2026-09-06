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

extra["publishName"] = "NarrativeTrace Spring Web"
extra["publishDescription"] = "Spring Web integration for servlet filter configuration"

dependencies {
    implementation(project(":narrativetrace-core"))
    implementation(project(":narrativetrace-servlet"))
    implementation(project(":narrativetrace-spring"))
    implementation("org.springframework:spring-context:6.2.19")
    // The filter bean is typed on the servlet request now that RequestContextProvider is
    // generic in the API jar; compileOnly keeps the servlet API out of the runtime graph.
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")
    compileOnly(project(":narrativetrace-slf4j"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation(project(":narrativetrace-slf4j"))
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.springframework:spring-test:6.2.19")
    testImplementation("org.springframework:spring-web:6.2.19")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    testImplementation("ch.qos.logback:logback-classic:1.5.38")
    testImplementation(testFixtures(project(":narrativetrace-core")))
    testImplementation("com.github.noconnor:junitperf-junit5:1.37.0")
}
